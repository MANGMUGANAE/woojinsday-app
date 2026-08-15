package com.daejin.woojintoday.data.network

import android.util.Log
import com.daejin.woojintoday.data.model.Notice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException

private const val TAG = "NoticeClient"

sealed class NoticeListResult {
    data class Success(val notices: List<Notice>) : NoticeListResult()
    data class NetworkError(val message: String) : NoticeListResult()
}

/**
 * Fetches and parses the 학사 notice board — a public page needing no login, but it does route
 * through an SSO redirect chain (checkToken.do -> nsso -> back) that only terminates if cookies
 * are carried across the hops, so this client needs its own CookieJar (the default OkHttpClient
 * has none) or the redirects loop until OkHttp gives up.
 */
class NoticeClient(
    private val client: OkHttpClient = defaultClient
) {
    suspend fun fetchNotices(): NoticeListResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LIST_URL)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                val notices = parseNotices(html)
                Log.d(TAG, "공지 응답 코드=${response.code}, 파싱된 공지 수=${notices.size}")
                NoticeListResult.Success(notices)
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            NoticeListResult.NetworkError("공지사항을 불러오지 못했습니다.")
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
        private const val LIST_URL = "https://www.daejin.ac.kr/daejin/1003/subview.do"
        const val BASE_URL = "https://www.daejin.ac.kr"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .cookieJar(InMemoryCookieJar())
                .build()
        }
    }
}

/**
 * Minimal per-host cookie store, keyed exactly by [Cookie.domain] (none of the SSO redirect
 * chain's Set-Cookie headers specify a Domain attribute, so per RFC 6265 they're host-only —
 * exact-host matching is spec-correct here, not a shortcut).
 */
private class InMemoryCookieJar : CookieJar {
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
