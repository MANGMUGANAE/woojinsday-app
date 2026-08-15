package com.daejin.woojintoday.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val client: OkHttpClient = OkHttpClient()
) {

    suspend fun login(studentNo: String, password: String): LoginResult = withContext(Dispatchers.IO) {
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

            client.newCall(request).execute().use { response ->
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
                val userId2 = extractUserId2(locations)
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
