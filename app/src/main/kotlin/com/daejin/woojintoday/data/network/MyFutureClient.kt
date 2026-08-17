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
import okhttp3.MultipartBody
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

/** [countsByTermGrade]의 키는 (학기 1|2, 학년 1~4), 값은 그 학기·학년에 인정된 지도교수 상담 횟수.
 *  [advisorStaffNo]는 같은 응답 안에 박힌 로그인 사용자 정보(LoginVO)의 `guidStaffNo` —
 *  상담 신청 시 상담교수 기본값(comProfId)으로 쓴다. */
data class AdvisorCounselStatus(
    val totalCount: Int,
    val goalCount: Int,
    val countsByTermGrade: Map<Pair<Int, Int>, Int>,
    val advisorStaffNo: String?
)

sealed class AdvisorCounselStatusResult {
    data class Success(val status: AdvisorCounselStatus) : AdvisorCounselStatusResult()
    data class NetworkError(val message: String) : AdvisorCounselStatusResult()
}

data class ProfessorSearchResult(val staffNo: String, val name: String, val department: String)

sealed class ProfessorSearchListResult {
    data class Success(val professors: List<ProfessorSearchResult>, val totalCount: Int) : ProfessorSearchListResult()
    data class NetworkError(val message: String) : ProfessorSearchListResult()
}

sealed class CounselApplyResult {
    data object Success : CounselApplyResult()
    data class NetworkError(val message: String) : CounselApplyResult()
}

data class CounselHistoryEntry(val cnsKeyId: String, val date: String, val method: String, val status: String)

sealed class CounselHistoryResult {
    data class Success(val entries: List<CounselHistoryEntry>) : CounselHistoryResult()
    data class NetworkError(val message: String) : CounselHistoryResult()
}

/** [answerDate]/[answerContent]는 아직 답변 전(예: 신청 직후, 학생 취소)이면 null — 교수가
 *  답변을 남긴 뒤에만 두 필드가 응답에 함께 실려온다. */
data class CounselDetail(
    val date: String,
    val status: String,
    val content: String,
    val answerDate: String?,
    val answerContent: String?
)

sealed class CounselDetailResult {
    data class Success(val detail: CounselDetail) : CounselDetailResult()
    data class NetworkError(val message: String) : CounselDetailResult()
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

    /** "지도교수 상담(의무) 인정" — 마일리지 요약과 같은 화면(getMyStatusListAjax.do)의 다른 탭.
     *  값들이 깨끗한 HTML이 아니라 응답에 박힌 `<script>`의 JS 리터럴(`parseInt("N")` 호출들과
     *  `stdShyr`/`shregSt` 비교식)에만 있어서, 그 자바스크립트가 하던 계산(학년·학기별 합산,
     *  편입생 여부에 따른 목표 횟수 4/12 분기)을 그대로 옮겨온다. [login]이 먼저 성공해있어야 한다. */
    suspend fun fetchAdvisorCounselStatus(): AdvisorCounselStatusResult = withContext(Dispatchers.IO) {
        try {
            val html = post(MY_STATUS_URL, MY_STATUS_REFERER, "userId=&listType=cns&cnsDiv=CNSGB01")
            val status = parseCounselStatus(html)
            if (status == null) {
                Log.e(TAG, "지도교수 상담 현황 파싱 실패, 응답 본문: $html")
                AdvisorCounselStatusResult.NetworkError("지도교수 상담 현황을 찾을 수 없습니다.")
            } else {
                AdvisorCounselStatusResult.Success(status)
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            AdvisorCounselStatusResult.NetworkError("지도교수 상담 현황을 불러오지 못했습니다.")
        }
    }

    private fun parseCounselStatus(html: String): AdvisorCounselStatus? {
        val entries = COUNSEL_ENTRY_REGEX.findAll(html).map { match ->
            val cnt = match.groupValues[1].toInt()
            val grade = match.groupValues[2].toInt()
            val term = match.groupValues[3].toInt()
            Triple(term, grade, cnt)
        }.toList()
        if (entries.isEmpty()) return null

        val countsByTermGrade = entries.associate { (term, grade, cnt) -> (term to grade) to cnt }
        val totalCount = entries.sumOf { it.third }
        // 편입학생(shregSt == "B01003")은 4회, 그 외는 12회가 졸업 기준 — 원본 JS의
        // `("B01021" == 'B01003' ? '4' : '12')` 삼항식에서 좌변 코드만 뽑아 같은 판정을 한다.
        val shregSt = GOAL_SHREG_REGEX.find(html)?.groupValues?.get(1)
        val goalCount = if (shregSt == "B01003") 4 else 12
        val advisorStaffNo = GUID_STAFF_NO_REGEX.find(html)?.groupValues?.get(1)
        return AdvisorCounselStatus(totalCount, goalCount, countsByTermGrade, advisorStaffNo)
    }

    /** 상담교수 검색 팝업과 같은 API — [query]가 비어있으면 전체 목록(페이지당 5명)을 반환한다. */
    suspend fun searchProfessors(query: String, pageIndex: Int = 1): ProfessorSearchListResult =
        withContext(Dispatchers.IO) {
            try {
                val body = buildString {
                    append("pageIndex=$pageIndex")
                    if (query.isNotBlank()) append("&korNm=${URLEncoder.encode(query, "UTF-8")}")
                }
                val html = post(PROF_SEARCH_URL, MY_CNS_MAIN_URL, body)
                val totalCount = TOTAL_PROF_COUNT_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                ProfessorSearchListResult.Success(parseProfessorRows(html), totalCount)
            } catch (e: IOException) {
                Log.e(TAG, "네트워크 오류", e)
                ProfessorSearchListResult.NetworkError("교수 검색에 실패했습니다.")
            }
        }

    private fun parseProfessorRows(html: String): List<ProfessorSearchResult> =
        Jsoup.parse(html).select("table tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 3) return@mapNotNull null
            val name = cells[0].text().trim()
            val department = cells[1].text().trim()
            val staffNo = cells[2].selectFirst("a")?.attr("data-param1")?.trim().orEmpty()
            if (name.isBlank() || staffNo.isBlank()) return@mapNotNull null
            ProfessorSearchResult(staffNo, name, department)
        }

    /** 온라인 지도교수 상담 신청 제출 — 원본 `#insertForm`이 `enctype="multipart/form-data"`라
     *  그대로 multipart로 보낸다. [login]이 먼저 성공해있어야 한다. */
    suspend fun submitOnlineCounselApply(
        professorStaffNo: String,
        content: String,
        phone: String,
        email: String
    ): CounselApplyResult = withContext(Dispatchers.IO) {
        try {
            val emailParts = email.split("@", limit = 2)
            val emailLocal = emailParts.getOrElse(0) { "" }
            val emailDomain = emailParts.getOrElse(1) { "" }
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("radio1", "on")
                .addFormDataPart("comProfId", professorStaffNo)
                .addFormDataPart("comApplyCont", content)
                .addFormDataPart("comApplyPhone", phone)
                .addFormDataPart("comApplyEmail1", emailLocal)
                .addFormDataPart("comApplyEmail2", emailDomain)
                .addFormDataPart("comApplyEmail", email)
                .addFormDataPart("CNSGB01_idx", "1")
                .build()
            val request = Request.Builder()
                .url(CNS_ONLINE_APPLY_URL)
                .header("User-Agent", USER_AGENT)
                .header("Origin", ORIGIN)
                .header("Referer", MY_CNS_MAIN_URL)
                .header("X-Requested-With", "XMLHttpRequest")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(TAG, "상담 신청 응답 코드=${response.code}, 본문=$body")
                if (RTN_CODE_SUCCESS_REGEX.containsMatchIn(body)) {
                    CounselApplyResult.Success
                } else {
                    CounselApplyResult.NetworkError("상담 신청에 실패했습니다.")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            CounselApplyResult.NetworkError("상담 신청에 실패했습니다.")
        }
    }

    /** "상담 이력" — 학번(regId) 기준 전체 신청 내역을 페이지당 5건씩 병렬로 다 받아온다
     *  (마일리지 지급내역 조회와 같은 방식). */
    suspend fun fetchCounselHistory(studentNo: String): CounselHistoryResult = withContext(Dispatchers.IO) {
        try {
            val firstPageHtml = fetchCounselHistoryPage(studentNo, pageIndex = 1)
            val totalCount = TOTAL_COUNT_REGEX.find(firstPageHtml)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val entries = mutableListOf<CounselHistoryEntry>()
            entries += parseCounselHistoryRows(firstPageHtml)

            val totalPages = if (totalCount > 0) ceil(totalCount / PAGE_SIZE.toDouble()).toInt() else 1
            if (totalPages > 1) {
                val rest = coroutineScope {
                    (2..totalPages).map { pageIndex ->
                        async { parseCounselHistoryRows(fetchCounselHistoryPage(studentNo, pageIndex)) }
                    }.awaitAll()
                }
                rest.forEach { entries += it }
            }
            CounselHistoryResult.Success(entries)
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            CounselHistoryResult.NetworkError("상담 이력을 불러오지 못했습니다.")
        }
    }

    private fun fetchCounselHistoryPage(studentNo: String, pageIndex: Int): String {
        val body = "pageIndex=$pageIndex&cnsDiv=CNSGB01&regId=$studentNo"
        return post(CNS_HIS_LIST_URL, MY_CNS_MAIN_URL, body)
    }

    /** No/신청일시/상담방식/상태/상세보기/취소, 6개 td 중 상세보기 셀의 버튼 data-param1이 cnsKeyId. */
    private fun parseCounselHistoryRows(html: String): List<CounselHistoryEntry> =
        Jsoup.parse(html).select("table tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 5) return@mapNotNull null
            val cnsKeyId = cells[4].selectFirst("button")?.attr("data-param1")?.trim().orEmpty()
            if (cnsKeyId.isBlank()) return@mapNotNull null
            CounselHistoryEntry(
                cnsKeyId = cnsKeyId,
                date = cells[1].text().trim(),
                method = cells[2].text().trim(),
                status = cells[3].text().trim()
            )
        }

    /** 상담 이력 한 건의 상세 — 첨부파일 행은 그냥 조회 안 해서 자연히 무시된다. */
    suspend fun fetchCounselDetail(cnsKeyId: String): CounselDetailResult = withContext(Dispatchers.IO) {
        try {
            val body = "cnsKeyId=$cnsKeyId&cnsOnoffDiv=ON&cnsDiv=CNSGB01"
            val html = post(CNS_DETAIL_URL, MY_CNS_MAIN_URL, body)
            val detail = parseCounselDetail(html)
            if (detail == null) {
                CounselDetailResult.NetworkError("상세 정보를 찾을 수 없습니다.")
            } else {
                CounselDetailResult.Success(detail)
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            CounselDetailResult.NetworkError("상세 정보를 불러오지 못했습니다.")
        }
    }

    private fun parseCounselDetail(html: String): CounselDetail? {
        val fields = mutableMapOf<String, String>()
        Jsoup.parse(html).select("table tbody tr").forEach { row ->
            val label = row.selectFirst("th")?.text()?.trim() ?: return@forEach
            val value = row.selectFirst("td")?.text()?.trim() ?: return@forEach
            fields[label] = value
        }
        val date = fields["신청일시"] ?: return null
        val status = fields["상태"] ?: return null
        val content = fields["신청 내용"] ?: return null
        return CounselDetail(
            date = date,
            status = status,
            content = content,
            answerDate = fields["답변일시"],
            answerContent = fields["답변 내용"]
        )
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
        private const val MY_CNS_MAIN_URL = "https://together.daejin.ac.kr/myCns/a/m/getMyCnsMain.do"
        private const val PROF_SEARCH_URL = "https://together.daejin.ac.kr/careerCns/a/n/getProfSearchListPop.do"
        private const val CNS_ONLINE_APPLY_URL = "https://together.daejin.ac.kr/careerCns/w/n/getCnsOnlineApplyAjax.do"
        private const val CNS_HIS_LIST_URL = "https://together.daejin.ac.kr/cnsHis/a/n/getCnsHisListAjax.do"
        private const val CNS_DETAIL_URL = "https://together.daejin.ac.kr/cnsHis/a/n/getCnsDetailInfoPop.do"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        private val TOTAL_COUNT_REGEX = Regex("""var\s+totalCnt\s*=\s*'(\d+)'""")
        private val COUNSEL_ENTRY_REGEX = Regex(
            "var\\s+cnt\\s*=\\s*parseInt\\(\"(\\d+)\"\\)\\s*,\\s*grade\\s*=\\s*parseInt\\(\"(\\d+)\"\\);" +
                "[\\s\\S]*?\\$\\(\"#term\"\\+(\\d+)\\+\"_\"\\+grade\\)\\.text"
        )
        private val GOAL_SHREG_REGEX = Regex("var\\s+goalCnt\\s*=\\s*\\(\"([^\"]+)\"\\s*==\\s*'B01003'")
        private val GUID_STAFF_NO_REGEX = Regex("""guidStaffNo=(\d+)""")
        private val TOTAL_PROF_COUNT_REGEX = Regex("""Total:\s*(\d+)""")
        private val RTN_CODE_SUCCESS_REGEX = Regex(""""rtnCode"\s*:\s*"0"""")
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
