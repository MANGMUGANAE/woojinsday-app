package com.daejin.woojintoday.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val TAG = "LinkPortal"

/**
 * Fallback for the userId2 that [DaejinAuthClient]'s uid-from-Location parsing misses when the
 * "password not changed in 6 months" notice page is skipped during login — that branch's SSO
 * redirect chain never puts uid in a URL at all (confirmed from real request/response captures).
 *
 * Instead this replays the site's own "link to sugang portal" SSO handoff:
 * LinkPortal.jsp -> nsso pmi-sso2.jsp -> LinkPortal.jsp -> login_post_proc.jsp -> AdmSso,
 * whose last hop sets the real `userId` cookie directly.
 *
 * Must be called with the *same* [OkHttpClient] (and its cookie jar) that just ran
 * [DaejinAuthClient.login] — a first attempt seeding only WMONID into a fresh cookie jar got
 * bounced by LinkPortal.jsp back to www.daejin.ac.kr, because nsso.daejin.ac.kr apparently needs
 * more than WMONID (some cookie minted during login.do's own SSO leg, e.g.
 * `_SSO_Global_Logout_url`) to recognize the request as a live, just-authenticated session rather
 * than just a recognized device.
 */
class LinkPortalClient {
    suspend fun fetchUserId2(client: OkHttpClient): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LINK_PORTAL_URL)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.daejin.ac.kr/")
                .build()
            client.newCall(request).execute().use { response ->
                val chain = generateSequence(response) { it.priorResponse }.toList().reversed()
                chain.forEach {
                    Log.d(TAG, "<- ${it.code} ${it.request.url} Location=${it.header("Location")}")
                    it.headers("Set-Cookie").forEach { sc -> Log.d(TAG, "   Set-Cookie: $sc") }
                }
                val setCookies = chain.flatMap { it.headers("Set-Cookie") }
                // 같은 이름이 한 응답 안에서 두 번(자리표시자 값 → 실제 값 순서로) 올 수 있으므로
                // 마지막 매치를 채택한다.
                val userId2 = setCookies.mapNotNull { USER_ID_REGEX.find(it)?.groupValues?.get(1) }.lastOrNull()
                if (userId2 == null) {
                    Log.d(TAG, "userId 쿠키를 못 찾음. 최종 응답 본문(앞 2000자)=${response.peekBody(2000).string()}")
                }
                userId2
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            null
        }
    }

    companion object {
        private const val LINK_PORTAL_URL = "https://dreams2.daejin.ac.kr/sugang/LinkPortal.jsp?dvd=P"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val USER_ID_REGEX = Regex("""^userId=([^;]*)""")
    }
}
