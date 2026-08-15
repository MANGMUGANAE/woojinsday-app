package com.daejin.woojintoday.data.network

import android.util.Log
import com.daejin.woojintoday.data.model.AcademicEvent
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
import java.time.LocalDate

private const val TAG = "AcademicScheduleClient"

sealed class AcademicScheduleResult {
    data class Success(val events: List<AcademicEvent>) : AcademicScheduleResult()
    data class NetworkError(val message: String) : AcademicScheduleResult()
}

/**
 * Daejin's monthly academic-schedule (학사일정) page — public, but routed through the same
 * daejin.ac.kr SSO redirect chain the notice board hits, so it needs a persistent CookieJar too.
 */
class AcademicScheduleClient(
    private val client: OkHttpClient = defaultClient
) {
    suspend fun fetchMonth(year: Int, month: Int): AcademicScheduleResult = withContext(Dispatchers.IO) {
        try {
            val body = "year=$year&month=$month".toRequestBody(FORM_MEDIA_TYPE)
            val request = Request.Builder()
                .url(URL)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("User-Agent", USER_AGENT)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                val events = parseEvents(html)
                Log.d(TAG, "$year-$month 응답 코드=${response.code}, 일정 수=${events.size}")
                AcademicScheduleResult.Success(events)
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            AcademicScheduleResult.NetworkError("학사일정을 불러오지 못했습니다.")
        }
    }

    private fun parseEvents(html: String): List<AcademicEvent> {
        val document = Jsoup.parse(html)
        return document.select("div.applyList table tbody tr").mapNotNull { row ->
            val dateText = row.selectFirst("th")?.text()?.trim() ?: return@mapNotNull null
            val description = row.selectFirst("td")?.text()?.trim() ?: return@mapNotNull null
            if (description.isBlank()) return@mapNotNull null

            val dates = DATE_REGEX.findAll(dateText).map { match ->
                LocalDate.of(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt()
                )
            }.toList()
            val begin = dates.getOrNull(0) ?: return@mapNotNull null
            val end = dates.getOrNull(1) ?: begin

            AcademicEvent.of(begin, end, description)
        }
    }

    companion object {
        private const val URL = "https://www.daejin.ac.kr/schdulmanage/daejin/19/monthSchdul.do"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        private val DATE_REGEX = Regex("""(\d{4})\.(\d{2})\.(\d{2})""")

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .cookieJar(ScheduleCookieJar())
                .build()
        }
    }
}

private class ScheduleCookieJar : CookieJar {
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
