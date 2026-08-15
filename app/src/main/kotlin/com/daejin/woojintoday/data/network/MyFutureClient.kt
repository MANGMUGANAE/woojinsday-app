package com.daejin.woojintoday.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import kotlin.math.ceil

private const val TAG = "MyFutureClient"

private const val DEFAULT_SHYR_CD = "3"
private const val PAGE_SIZE = 5

sealed class MyGradeResult {
    data class Success(val gpa: Double) : MyGradeResult()
    data class NetworkError(val message: String) : MyGradeResult()
}

sealed class DepartmentGradesResult {
    data class Success(val grades: List<Double>, val totalCount: Int) : DepartmentGradesResult()
    data class NetworkError(val message: String) : DepartmentGradesResult()
}

data class MileageSummary(val myScore: Int, val myRank: Int, val top5: List<Int>)

sealed class MileageSummaryResult {
    data class Success(val summary: MileageSummary) : MileageSummaryResult()
    data class NetworkError(val message: String) : MileageSummaryResult()
}

data class MileageHistoryEntry(val date: String, val category: String, val activityName: String, val points: Int)

sealed class MileageHistoryResult {
    data class Success(val entries: List<MileageHistoryEntry>) : MileageHistoryResult()
    data class NetworkError(val message: String) : MileageHistoryResult()
}

/**
 * Together (T-WIN) "myFuture" grade-comparison pages. T-WIN has its own login/session, separate
 * from both the general portal (www.daejin.ac.kr) and dreams2 — confirmed 2026-08-09 by seeing a
 * reused www.daejin.ac.kr JSESSIONID land on a "페이지를 찾을 수 없습니다" error page here. So
 * this client logs in fresh (`login()`) with the already-saved credentials before any grade call,
 * using one shared per-instance CookieJar so the resulting session carries over automatically.
 */
class MyFutureClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(TogetherCookieJar())
        .build()
) {
    /** Must be called once, successfully, before [fetchMyGrade] / [fetchDepartmentGrades]. */
    suspend fun login(studentNo: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = "targetUrl=$TARGET_URL_ENCODED" +
                "&userId=${URLEncoder.encode(studentNo, "UTF-8")}" +
                "&userPw=${URLEncoder.encode(password, "UTF-8")}"
            val request = Request.Builder()
                .url(LOGIN_URL)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Origin", ORIGIN)
                .header("Referer", ORIGIN)
                .header("User-Agent", USER_AGENT)
                .post(body.toRequestBody(FORM_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val finalPath = response.request.url.encodedPath
                Log.d(TAG, "로그인 최종 응답 코드=${response.code}, 최종 경로=$finalPath")
                response.isSuccessful && !finalPath.contains("/login/")
            }
        } catch (e: IOException) {
            Log.e(TAG, "로그인 네트워크 오류", e)
            false
        }
    }

    suspend fun fetchMyGrade(sustCd: String, shyrCd: String = DEFAULT_SHYR_CD): MyGradeResult = withContext(Dispatchers.IO) {
        try {
            val body = "sustCd=$sustCd&shyrCd=$shyrCd&pageIndex=1&searchType=STD_GROUP&roadMapType=SAMEGRADE"
            val html = post(MY_GRADE_URL, MY_GRADE_REFERER, body)
            val gpa = Jsoup.parse(html).selectFirst("li.grade_my strong")?.text()?.trim()?.toDoubleOrNull()
            if (gpa == null) {
                Log.e(TAG, "성적 파싱 실패, 응답 본문: $html")
                val title = Jsoup.parse(html).title()
                val snippet = html.replace(Regex("\\s+"), " ").trim().take(2500)
                MyGradeResult.NetworkError("성적 정보를 찾을 수 없습니다. [제목: $title] [응답: $snippet]")
            } else {
                MyGradeResult.Success(gpa)
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            MyGradeResult.NetworkError("성적 정보를 불러오지 못했습니다.")
        }
    }

    suspend fun fetchDepartmentGrades(sustCd: String, shyrCd: String = DEFAULT_SHYR_CD): DepartmentGradesResult = withContext(Dispatchers.IO) {
        try {
            val firstPageHtml = fetchDeptPage(sustCd, pageIndex = 1, shyrCd = shyrCd)
            val totalCount = TOTAL_COUNT_REGEX.find(firstPageHtml)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val grades = mutableListOf<Double>()
            grades += parseGrades(firstPageHtml)

            val totalPages = if (totalCount > 0) ceil(totalCount / PAGE_SIZE.toDouble()).toInt() else 1
            if (totalPages > 1) {
                val rest = coroutineScope {
                    (2..totalPages).map { pageIndex ->
                        async { parseGrades(fetchDeptPage(sustCd, pageIndex, shyrCd)) }
                    }.awaitAll()
                }
                rest.forEach { grades += it }
            }

            DepartmentGradesResult.Success(grades, totalCount)
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            DepartmentGradesResult.NetworkError("학과 성적 정보를 불러오지 못했습니다.")
        }
    }

    /** "마일리지 현황" — 내 점수/순위와 전체 top5. [login]이 먼저 성공해있어야 한다. */
    suspend fun fetchMileageSummary(): MileageSummaryResult = withContext(Dispatchers.IO) {
        try {
            val html = post(MY_STATUS_URL, MY_STATUS_REFERER, "listType=mileage")
            val doc = Jsoup.parse(html)
            val myScore = doc.selectFirst("div.mymileage_score p strong")?.text()?.trim()?.toIntOrNull()
            val myRank = Regex("""\d+""").find(doc.selectFirst("div.ranking")?.ownText().orEmpty())
                ?.value?.toIntOrNull()
            if (myScore == null || myRank == null) {
                Log.e(TAG, "마일리지 요약 파싱 실패, 응답 본문: $html")
                return@withContext MileageSummaryResult.NetworkError("마일리지 정보를 찾을 수 없습니다.")
            }
            // "N위" th를 가진 행만 top5 표(마일리지 현황)에 해당 — 바로 아래 "구분별 등수" 표는
            // th가 "구분"/"학교전체" 같은 문자열이라 이 패턴에 안 걸려 자연스럽게 제외된다.
            val top5 = doc.select("tr").mapNotNull { row ->
                val rank = Regex("""^(\d+)위$""").find(row.selectFirst("th")?.text()?.trim().orEmpty())
                    ?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                val value = row.selectFirst("td")?.text()?.trim()?.toIntOrNull() ?: return@mapNotNull null
                rank to value
            }.sortedBy { it.first }.map { it.second }
            MileageSummaryResult.Success(MileageSummary(myScore, myRank, top5))
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            MileageSummaryResult.NetworkError("마일리지 정보를 불러오지 못했습니다.")
        }
    }

    /** "마일리지 취득 현황" — 페이지가 남았는지를 빈 응답으로 판단하면 안 된다(실측 결과, 범위 밖
     *  pageIndex를 줘도 서버가 1페이지 내용을 그대로 반복해서 돌려줘 무한루프처럼 동작함). 대신
     *  fetchDepartmentGrades와 같은 방식으로 1페이지 응답에 박혀있는 `var totalCnt = '5';`를 파싱해
     *  정확한 페이지 수만 병렬로 받는다. */
    suspend fun fetchMileageHistory(userId: String, sustCd: String, shyrCd: String): MileageHistoryResult =
        withContext(Dispatchers.IO) {
            try {
                val firstPageHtml = fetchMileageHistoryPage(userId, sustCd, shyrCd, pageIndex = 1)
                val totalCount = TOTAL_COUNT_REGEX.find(firstPageHtml)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val entries = mutableListOf<MileageHistoryEntry>()
                entries += parseMileageHistoryRows(firstPageHtml)

                val totalPages = if (totalCount > 0) ceil(totalCount / PAGE_SIZE.toDouble()).toInt() else 1
                if (totalPages > 1) {
                    val rest = coroutineScope {
                        (2..totalPages).map { pageIndex ->
                            async { parseMileageHistoryRows(fetchMileageHistoryPage(userId, sustCd, shyrCd, pageIndex)) }
                        }.awaitAll()
                    }
                    rest.forEach { entries += it }
                }
                MileageHistoryResult.Success(entries)
            } catch (e: IOException) {
                Log.e(TAG, "네트워크 오류", e)
                MileageHistoryResult.NetworkError("마일리지 지급내역을 불러오지 못했습니다.")
            }
        }

    private fun fetchMileageHistoryPage(userId: String, sustCd: String, shyrCd: String, pageIndex: Int): String {
        val body = "userId=$userId&sustCd=$sustCd&shyrCd=$shyrCd&pageIndex=$pageIndex"
        return post(MILEAGE_HISTORY_URL, MY_STATUS_REFERER, body)
    }

    private fun parseMileageHistoryRows(html: String): List<MileageHistoryEntry> =
        Jsoup.parse(html).select("table tbody tr").mapNotNull { row ->
            val cells = row.select("th, td")
            if (cells.size < 4) return@mapNotNull null
            MileageHistoryEntry(
                date = cells[0].text().trim(),
                category = cells[1].text().trim(),
                activityName = cells[2].text().trim(),
                points = cells[3].text().trim().toIntOrNull() ?: 0
            )
        }

    private fun fetchDeptPage(sustCd: String, pageIndex: Int, shyrCd: String): String {
        val body = "sustCd=$sustCd&shyrCd=$shyrCd&searchType=STD_GROUP&pageIndex=$pageIndex"
        return post(DEPT_LIST_URL, DEPT_LIST_REFERER, body)
    }

    private fun parseGrades(html: String): List<Double> =
        Jsoup.parse(html).select("table tbody tr").mapNotNull { row ->
            row.select("td").getOrNull(1)?.text()?.trim()?.toDoubleOrNull()
        }

    private fun post(url: String, referer: String, body: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("IS_AJAX", "isAjax")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", ORIGIN)
            .header("Referer", referer)
            .header("User-Agent", USER_AGENT)
            .post(body.toRequestBody(FORM_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val html = response.body?.string().orEmpty()
            Log.d(TAG, "$url 응답 코드=${response.code}, 본문 길이=${html.length}")
            return html
        }
    }

    companion object {
        private const val LOGIN_URL = "https://together.daejin.ac.kr/login/a/n/loginProc.do"
        private const val TARGET_URL_ENCODED = "%2FclientMain%2Fa%2Ft%2Fmain.do"
        private const val MY_GRADE_URL = "https://together.daejin.ac.kr/myFuture/a/n/getResultDivInfoPage.do"
        private const val DEPT_LIST_URL = "https://together.daejin.ac.kr/myFuture/r/n/getGrStdCompareListAjax.do"
        private const val ORIGIN = "https://together.daejin.ac.kr"
        private const val MY_GRADE_REFERER = "https://together.daejin.ac.kr/myFuture/a/m/myFutureView.do"
        private const val DEPT_LIST_REFERER = "https://together.daejin.ac.kr/myFuture/a/m/myFutureView.do"
        private const val MY_STATUS_URL = "https://together.daejin.ac.kr/myStatus/a/n/getMyStatusListAjax.do"
        private const val MILEAGE_HISTORY_URL = "https://together.daejin.ac.kr/myStatus/a/n/getMileageStatusInfoAjax.do"
        private const val MY_STATUS_REFERER = "https://together.daejin.ac.kr/myStatus/a/m/myStatusView.do"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        private val TOTAL_COUNT_REGEX = Regex("""var\s+totalCnt\s*=\s*'(\d+)'""")
    }
}

private class TogetherCookieJar : CookieJar {
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
