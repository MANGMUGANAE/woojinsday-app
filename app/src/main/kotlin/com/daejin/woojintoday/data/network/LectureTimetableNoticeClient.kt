package com.daejin.woojintoday.data.network

import android.util.Log
import com.daejin.woojintoday.data.model.Notice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder

private const val TAG = "LectureTimetableNoticeClient"

/**
 * "종합강의시간표" 게시판 — 실측해보니 SessionStore에 저장된 WMONID/JSESSIONID(로그인 리다이렉트
 * 체인 중 dreams2.daejin.ac.kr에서 잡힌 세션)를 그대로 www.daejin.ac.kr에 보내면 "접근 권한이
 * 없습니다"로 거부된다 — 같은 쿠키 이름이어도 두 앱서버의 세션이 서로 다른 것. 그래서 이 클라이언트는
 * SessionStore를 재사용하지 않고, 저장된 학번/비밀번호로 www.daejin.ac.kr에 직접 한 번 더 로그인해
 * (OkHttp의 호스트별 CookieJar가 www.daejin.ac.kr 세션만 정확히 골라 담아준다) 그 세션으로 조회한다.
 */
class LectureTimetableNoticeClient {

    suspend fun fetchNotices(studentNo: String?, password: String?): NoticeListResult =
        withContext(Dispatchers.IO) {
            if (studentNo == null || password == null) {
                return@withContext NoticeListResult.NetworkError("로그인 정보가 없습니다.")
            }
            try {
                val client = OkHttpClient.Builder().cookieJar(LectureTimetableCookieJar()).build()

                val loginRequest = Request.Builder()
                    .url(LOGIN_URL)
                    .header("User-Agent", USER_AGENT)
                    .post(loginFormBody(studentNo, password).toRequestBody(FORM_MEDIA_TYPE))
                    .build()
                client.newCall(loginRequest).execute().close()

                val listRequest = Request.Builder()
                    .url(LIST_URL)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                client.newCall(listRequest).execute().use { response ->
                    val html = response.body?.string().orEmpty()
                    val rowCount = Jsoup.parse(html).select("table.board-table tbody tr").size
                    Log.d(TAG, "응답 코드=${response.code}, body 길이=${html.length}, board-table 행 수=$rowCount")
                    if (rowCount == 0) {
                        Log.d(TAG, "board-table이 없어 응답 본문 앞부분을 로그로 남김:\n${html.take(4000)}")
                    }
                    val notices = parseNotices(html)
                    Log.d(TAG, "파싱된 게시물 수=${notices.size}")
                    NoticeListResult.Success(notices)
                }
            } catch (e: IOException) {
                Log.e(TAG, "네트워크 오류", e)
                NoticeListResult.NetworkError("종합강의시간표를 불러오지 못했습니다.")
            }
        }

    private fun parseNotices(html: String): List<Notice> {
        val document = Jsoup.parse(html)
        return document.select("table.board-table tbody tr").mapNotNull { row ->
            val link = row.selectFirst("td.td-subject a") ?: return@mapNotNull null
            val title = (link.selectFirst("strong")?.text() ?: link.ownText()).trim()
            if (title.isBlank()) return@mapNotNull null

            Notice(
                title = title,
                writer = row.selectFirst("td.td-write")?.text()?.trim().orEmpty(),
                date = row.selectFirst("td.td-date")?.text()?.trim().orEmpty(),
                views = row.selectFirst("td.td-access")?.text()?.trim().orEmpty(),
                hasAttachment = row.selectFirst("td.td-file p.file-y") != null,
                isNew = link.selectFirst("span.new") != null,
                detailPath = link.attr("href")
            )
        }
    }

    companion object {
        // 상세보기 WebView도 이 로그인 세션이 있어야 열리므로(NoticeBoardDialog에서 postUrl로 재사용) public으로 둔다.
        const val LOGIN_URL = "https://www.daejin.ac.kr/subLogin/daejin/login.do"
        private const val LIST_URL =
            "https://www.daejin.ac.kr/daejin/7482/subview.do?enc=Zm5jdDF8QEB8JTJGYmJzJTJGZGFlamluJTJGMTU3MyUyRmFydGNsTGlzdC5kbyUzRmJic0NsU2VxJTNEJTI2YmJzT3BlbldyZFNlcSUzRCUyNmlzVmlld01pbmUlM0RmYWxzZSUyNnNyY2hDb2x1bW4lM0RzaiUyNnNyY2hXcmQlM0QlRUMlQTIlODUlRUQlOTUlQTklRUElQjAlOTUlRUMlOUQlOTglRUMlOEIlOUMlRUElQjAlODQlRUQlOTElOUMlMjY%3D"
        const val BASE_URL = "https://www.daejin.ac.kr"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded;charset=UTF-8".toMediaType()

        /** DaejinAuthClient.login()과 동일한 로그인 폼 인코딩 — 상세보기 WebView가 postUrl로 같은
         *  로그인을 한 번 더 태울 때도 재사용한다(NoticeBoardDialog). */
        fun loginFormBody(studentNo: String, password: String): String =
            "layout=&pwdCrtfcNo=&pwdInputExcessYn=&userId2=&userId=${URLEncoder.encode(studentNo, "UTF-8")}" +
                "&userPwd=${URLEncoder.encode(password, "UTF-8")}"
    }
}

/** dreams2용 EXTRA_SESSION_COOKIES 같은 우회 없이, www.daejin.ac.kr 호스트에 실제로 발급된 쿠키만
 *  정확히 담아 재전송하기 위한 최소 CookieJar(NoticeClient의 것과 동일한 패턴). */
private class LectureTimetableCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val existing = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { newCookie ->
            existing.removeAll { it.name == newCookie.name }
            existing.add(newCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[url.host]?.filter { it.expiresAt > now } ?: emptyList()
    }
}
