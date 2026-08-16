package com.daejin.woojintoday.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder

private const val TAG = "DaejinAuth"

data class DaejinSession(val wmonid: String?, val jsessionId: String?, val userId2: String? = null)

sealed class LoginResult {
    data class Success(val session: DaejinSession) : LoginResult()
    data class Failed(val message: String) : LoginResult()
    data class NetworkError(val message: String) : LoginResult()
}

/**
 * Mirrors the backend's daejinLogin(): POST the account form to the Daejin
 * University portal login endpoint, following redirects (matching the
 * reference RestClient's HttpClient.Redirect.ALWAYS), then classify the
 * final page's body by keyword exactly like the reference parseResponse().
 */
class DaejinAuthClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val linkPortalClient: LinkPortalClient = LinkPortalClient()
) {

    suspend fun login(studentNo: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        // 이 호출 하나(로그인 + 필요하면 LinkPortal.jsp 폴백)에서만 쓰는 전용 쿠키 저장소 —
        // login.do가 www.daejin.ac.kr/nsso.daejin.ac.kr에 세팅하는 쿠키 전부(WMONID뿐 아니라
        // nsso 쪽 세션 쿠키까지)를 폴백 호출에도 그대로 이어서 보내기 위함. 매 login() 호출마다
        // 새로 만들어서 이전 시도의 쿠키가 다음 시도로 새는 걸 막는다.
        val sessionClient = client.newBuilder()
            .cookieJar(AccumulatingCookieJar())
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                Log.d(TAG, "-> ${req.method} ${req.url}")
                Log.d(TAG, "   Cookie=${req.header("Cookie")}")
                val resp = chain.proceed(req)
                Log.d(TAG, "<- ${resp.code} ${resp.request.url} Location=${resp.header("Location")}")
                resp.headers("Set-Cookie").forEach { Log.d(TAG, "   Set-Cookie: $it") }
                resp
            }
            .build()
        try {
            Log.d(TAG, "로그인 시작: stdNo=$studentNo")
            val encodedStudentNo = URLEncoder.encode(studentNo, "UTF-8")
            val encodedPassword = URLEncoder.encode(password, "UTF-8")
            val body =
                "layout=&pwdCrtfcNo=&pwdInputExcessYn=&userId2=&userId=$encodedStudentNo&userPwd=$encodedPassword"
                    .toRequestBody(FORM_MEDIA_TYPE)

            val request = Request.Builder()
                .url(LOGIN_URL)
                .header("User-Agent", USER_AGENT)
                .post(body)
                .build()

            sessionClient.newCall(request).execute().use { response ->
                // Redirects are followed (matching the reference HttpClient.Redirect.ALWAYS), so
                // the login POST's own Set-Cookie header lives on a prior response in the chain,
                // not on the final one — walk the whole chain to find it.
                val cookies = generateSequence(response) { it.priorResponse }
                    .flatMap { it.headers("Set-Cookie") }
                    .toList()
                val wmonid = cookies.firstNotNullOfOrNull { WMONID_REGEX.find(it)?.groupValues?.get(1) }
                val jsessionId = cookies.firstNotNullOfOrNull { JSESSIONID_REGEX.find(it)?.groupValues?.get(1) }
                val locations = generateSequence(response) { it.priorResponse }
                    .mapNotNull { it.header("Location") }
                    .toList()
                val html = response.body?.string().orEmpty()
                Log.d(TAG, "로그인 응답 코드=${response.code} (${response.priorResponse?.code}에서 리다이렉트됨), 본문 길이=${html.length}")

                val failureMessage = when {
                    html.contains(USER_NOT_FOUND_KEYWORD) -> USER_NOT_FOUND_KEYWORD
                    html.contains(FAIL_KEYWORD) -> {
                        val remaining = REMAINING_TRIES_PATTERN.find(html)?.groupValues?.get(1)
                        if (remaining != null) "$FAIL_KEYWORD (${remaining}회 더 잘못 입력하면 잠깁니다)" else FAIL_KEYWORD
                    }
                    else -> null
                }

                if (failureMessage != null) {
                    Log.d(TAG, "로그인 실패: $failureMessage")
                    return@withContext LoginResult.Failed(failureMessage)
                }

                // 6개월 비밀번호 미변경 안내 페이지가 안 뜨면 리다이렉트 체인에 uid가 아예
                // 안 실려서 위 파싱이 null을 반환한다 — 이때는 LinkPortal.jsp SSO 핸드오프로
                // 같은 값을 다시 받아온다. WMONID만 심은 별도 클라이언트로는 nsso가 "진짜 방금
                // 로그인함"으로 인식하지 못해 튕겨나가는 걸 확인해서, login.do 호출 때 쓴
                // sessionClient(누적된 쿠키 전부)를 그대로 재사용한다.
                val userId2 = extractUserId2(locations) ?: linkPortalClient.fetchUserId2(sessionClient)
                Log.d(TAG, "로그인 성공: WMONID=$wmonid, JSESSIONID=$jsessionId, userId2=$userId2")
                LoginResult.Success(DaejinSession(wmonid, jsessionId, userId2))
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            LoginResult.NetworkError("서버에 연결할 수 없습니다. 네트워크 상태를 확인해주세요.")
        }
    }

    /**
     * The `uid` inside the SSO redirect chain's `Location` header can show up at two different
     * encoding depths depending on whether the "password not changed in 6 months" notice page is
     * interposed first — normalize both cases down to the same single-decoded value.
     */
    private fun extractUserId2(locationHeaders: List<String>): String? {
        for (raw in locationHeaders) {
            val target = when {
                raw.contains("uid%253D") -> runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() ?: continue
                raw.contains("uid%3D") -> raw
                else -> continue
            }
            USER_ID2_REGEX.find(target)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    /** 이름별 마지막 값만 남기는 단순 누적 저장소 — 로그인 응답 체인에서 오가는 쿠키를 그대로
     *  다음 요청(들)에 실어 보내기 위함. 이 클라이언트는 daejin.ac.kr 서브도메인끼리만 오가는
     *  좁은 용도라 호스트 구분 없이 공유해도 안전하다. */
    private class AccumulatingCookieJar : CookieJar {
        private val store = mutableMapOf<String, String>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { store[it.name] = it.value }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            store.map { (name, value) -> Cookie.Builder().name(name).value(value).domain(url.host).build() }
    }

    companion object {
        private const val LOGIN_URL = "https://www.daejin.ac.kr/subLogin/daejin/login.do"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded;charset=UTF-8".toMediaType()

        private const val FAIL_KEYWORD = "입력하신 계정정보가 올바르지 않습니다"
        private const val USER_NOT_FOUND_KEYWORD = "회원정보이(가) 존재 하지 않습니다"
        private val REMAINING_TRIES_PATTERN = Regex("(\\d+)회\\s*더\\s*잘못입력")

        private val WMONID_REGEX = Regex("WMONID=(.*?);")
        private val JSESSIONID_REGEX = Regex("JSESSIONID=(.*?);")
        private val USER_ID2_REGEX = Regex("""uid%3D(.+?)(?:%26|&|$)""")
    }
}
