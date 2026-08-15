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
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "CafeteriaClient"

data class CafeteriaEntry(val corner: String, val menu: String, val price: String?)

data class CafeteriaDay(
    val date: LocalDate,
    val breakfast: List<CafeteriaEntry>,
    val lunch: List<CafeteriaEntry>,
    val dinner: List<CafeteriaEntry>
)

sealed class CafeteriaWeekResult {
    data class Success(val days: List<CafeteriaDay>) : CafeteriaWeekResult()
    data class NetworkError(val message: String) : CafeteriaWeekResult()
}

/**
 * Pulls a full week of student-hall cafeteria menus in one call. [fetchWeek] takes the Monday of
 * the week the caller wants *displayed* and sends it as-is — the server returns exactly that week
 * (verified: monday=2026.08.10 -> "2026.08.10 ~ 2026.08.16").
 */
class CafeteriaClient(
    private val client: OkHttpClient = defaultClient
) {
    suspend fun fetchWeek(displayMonday: LocalDate): CafeteriaWeekResult = withContext(Dispatchers.IO) {
        try {
            val apiMonday = displayMonday.format(DATE_FORMAT)
            val body = "monday=$apiMonday&week=".toRequestBody(FORM_MEDIA_TYPE)
            val request = Request.Builder()
                .url(MENU_URL)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("User-Agent", USER_AGENT)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                val days = parseWeek(html, displayMonday)
                Log.d(TAG, "$displayMonday 주 응답 코드=${response.code}, 일수=${days.size}")
                CafeteriaWeekResult.Success(days)
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            CafeteriaWeekResult.NetworkError("학식 정보를 불러오지 못했습니다.")
        }
    }

    private fun parseWeek(html: String, displayMonday: LocalDate): List<CafeteriaDay> {
        val document = Jsoup.parse(html)
        val table = document.selectFirst("div.box-table table")
            ?: return (0 until 7).map { emptyDay(displayMonday.plusDays(it.toLong())) }

        val dayHeaders = table.select("thead th").drop(1)
        val dates = (0 until 7).map { index ->
            val headerText = dayHeaders.getOrNull(index)?.text().orEmpty()
            DATE_REGEX.find(headerText)?.value?.let {
                runCatching { LocalDate.parse(it, DATE_FORMAT) }.getOrNull()
            } ?: displayMonday.plusDays(index.toLong())
        }

        val breakfast = Array(7) { mutableListOf<CafeteriaEntry>() }
        val lunch = Array(7) { mutableListOf<CafeteriaEntry>() }
        val dinner = Array(7) { mutableListOf<CafeteriaEntry>() }
        var currentSection: Array<MutableList<CafeteriaEntry>>? = null

        for (row in table.select("tbody tr")) {
            val header = row.selectFirst("th")
            if (header != null) {
                currentSection = when {
                    header.text().contains("조식") -> breakfast
                    header.text().contains("중식") || header.text().contains("점심") -> lunch
                    header.text().contains("석식") || header.text().contains("저녁") -> dinner
                    else -> null
                }
            }
            val section = currentSection ?: continue
            row.select("td").forEachIndexed { index, cell ->
                if (index >= 7) return@forEachIndexed
                parseCellEntry(cell)?.let { section[index] += it }
            }
        }

        return dates.mapIndexed { index, date ->
            CafeteriaDay(date = date, breakfast = breakfast[index], lunch = lunch[index], dinner = dinner[index])
        }
    }

    private fun parseCellEntry(cell: Element): CafeteriaEntry? {
        if (cell.hasClass("no-data")) return null
        val menuRaw = cell.textNodes().firstOrNull { it.text().isNotBlank() }?.text()?.trim()
            ?: return null
        if (menuRaw.isBlank()) return null

        val cornerMatch = CORNER_REGEX.find(menuRaw)
        var menu = menuRaw
        val corner = cornerMatch?.groupValues?.get(1)?.trim()
        if (cornerMatch != null) menu = menu.removeRange(cornerMatch.range)
        menu = menu.trim().trim('/').replace("/", " · ")

        val priceText = cell.selectFirst("span")?.text()
        val price = priceText?.let { PRICE_REGEX.find(it)?.groupValues?.get(1)?.trim() }

        return CafeteriaEntry(corner = corner ?: "학생회관", menu = menu, price = price)
    }

    private fun emptyDay(date: LocalDate) = CafeteriaDay(date, emptyList(), emptyList(), emptyList())

    companion object {
        private const val MENU_URL = "https://www.daejin.ac.kr/diet/daejin/2/view.do"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        private val DATE_REGEX = Regex("""\d{4}\.\d{2}\.\d{2}""")
        private val CORNER_REGEX = Regex("""^\[(.+?)]""")
        private val PRICE_REGEX = Regex("""가격\s*:\s*(.+?)]""")

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .cookieJar(CafeteriaCookieJar())
                .build()
        }
    }
}

private class CafeteriaCookieJar : CookieJar {
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
