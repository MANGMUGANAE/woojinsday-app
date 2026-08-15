package com.daejin.woojintoday.data.network

import android.util.Log
import com.daejin.woojintoday.data.model.Departments
import com.daejin.woojintoday.data.model.GenEdAreas
import com.daejin.woojintoday.data.model.Syllabus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val TAG = "SyllabusClient"

/** [departmentName]이 있으면(=전공 조회) 그 응답에서 찾은 모든 과목이 그 학과 소속이라는 뜻이라,
 *  링크 인덱스를 만드는 김에 학과명도 같이 태깅한다. 교양/교직/일선은 학과가 없어 null. */
private data class ListQuery(val params: Map<String, String>, val departmentName: String? = null)

/** 이 학기 전체(교필/교직/일선/전공 50개 학과/교선 19개 영역)를 훑을 때 쓸 (ic_kwa..) 쿼리 파라미터
 *  조합 전부. 전공 필터 같은 "지금 화면 상태"에 기대지 않고, 매 학기 딱 한 번 이걸 전부 돌려서
 *  과목코드-분반 → 상세링크 인덱스를 만들어두면 그 뒤로는 어떤 과목이든 즉시 조회된다. */
private fun allListQueries(): List<ListQuery> = buildList {
    add(ListQuery(mapOf("ic_kwa" to "B41001"))) // 교필
    add(ListQuery(mapOf("ic_kwa" to "B41004"))) // 교직
    add(ListQuery(mapOf("ic_kwa" to "B41020"))) // 일선
    Departments.ALL.forEach { department ->
        // 전공(전필/전선/전기 다 섞여 나옴)
        add(ListQuery(mapOf("ic_kwa" to "B41005", "ic_kwa_1" to department.code), department.name))
    }
    GenEdAreas.ALL.forEach { area ->
        add(ListQuery(mapOf("ic_kwa" to "B41002", "ic_kwa_2" to area.code))) // 교선
    }
}

/** 교선 영역 라벨(예: "1영역")만 필요할 때 쓰는 가벼운 쿼리 목록 — 교필/교직/일선/전공 50개는 빼고
 *  교선 19개 영역만 훑는다. 전체 인덱스([allListQueries], 72번 요청)보다 훨씬 가벼워서, 강의계획서를
 *  열기 전(과목 리스트/필터)에도 미리 실행해둘 수 있다. */
private fun genEdAreaListQueries(): List<Pair<Map<String, String>, String>> =
    GenEdAreas.ALL.map { area -> mapOf("ic_kwa" to "B41002", "ic_kwa_2" to area.code) to area.label }

data class SyllabusIndex(
    val links: Map<String, String>,
    /** 과목코드-분반 → 학과명. 전공 조회에서 나온 과목만 들어있다(교양/교직/일선 과목은 없음). */
    val departments: Map<String, String>
)

sealed class SyllabusDetailResult {
    data class Success(val syllabus: Syllabus) : SyllabusDetailResult()
    data class NetworkError(val message: String) : SyllabusDetailResult()
}

/**
 * 강의계획서 조회. 학기당 한 번 [buildIndex]로 전체(교필/교직/일선/전공/교선) 목록을 병렬로 훑어
 * "과목코드-분반 → Blsn020303.jsp 상세 링크" 인덱스를 만들고(호출부가 로컬에 캐싱), 그 뒤로는
 * [fetchDetail]로 특정 과목의 링크만 바로 요청해 파싱한다. CourseCatalogClient와 동일한 dreams2
 * 세션 쿠키를 그대로 재사용한다.
 */
class SyllabusClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    /** 진행 상황 표시를 [buildIndex] 첫 콜백 전에도 정확한 총 개수로 보여주기 위한 값. */
    fun totalQueryCount(): Int = allListQueries().size


    /** 서버에 한 번에 너무 많은 연결을 열지 않도록 동시 요청 수를 제한하며 전체 목록을 훑는다.
     *  개별 요청이 실패해도 나머지는 계속 진행하고(부분 인덱스라도 남기는 게 전체 실패보다 낫다),
     *  [onProgress]로 진행 상황(완료 개수/전체 개수)을 알려준다. */
    suspend fun buildIndex(
        year: Int,
        semester: Int,
        session: DaejinSession,
        onProgress: (done: Int, total: Int) -> Unit
    ): SyllabusIndex = withContext(Dispatchers.IO) {
        val queries = allListQueries()
        val semaphore = Semaphore(CONCURRENCY)
        var completed = 0
        val jobs = queries.map { query ->
            async {
                val html = semaphore.withPermit {
                    runCatching { execute(buildListUrl(year, semester, query.params), session) }.getOrNull()
                }
                synchronized(this) {
                    completed += 1
                    onProgress(completed, queries.size)
                }
                val links = html?.let { runCatching { parseSyllabusListLinks(it) }.getOrNull() }
                links to query.departmentName
            }
        }
        val links = mutableMapOf<String, String>()
        val departments = mutableMapOf<String, String>()
        jobs.awaitAll().forEach { (partialLinks, departmentName) ->
            partialLinks?.let { partial ->
                links.putAll(partial)
                if (departmentName != null) {
                    partial.keys.forEach { courseKey -> departments[courseKey] = departmentName }
                }
            }
        }
        SyllabusIndex(links = links, departments = departments)
    }

    /** 교선 영역 라벨만 필요할 때 쓰는 가벼운 인덱스 구축(19번 요청) — [buildIndex]와 달리 강의계획서를
     *  열기 전에도 미리(예: 과목 리스트 진입 시) 돌려서 "교선(1영역)" 같은 표시/필터에 쓴다. */
    suspend fun buildGenEdAreaIndex(
        year: Int,
        semester: Int,
        session: DaejinSession,
        onProgress: (done: Int, total: Int) -> Unit
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val queries = genEdAreaListQueries()
        val semaphore = Semaphore(CONCURRENCY)
        var completed = 0
        val jobs = queries.map { (params, areaLabel) ->
            async {
                val html = semaphore.withPermit {
                    runCatching { execute(buildListUrl(year, semester, params), session) }.getOrNull()
                }
                synchronized(this) {
                    completed += 1
                    onProgress(completed, queries.size)
                }
                val links = html?.let { runCatching { parseSyllabusListLinks(it) }.getOrNull() }
                links to areaLabel
            }
        }
        val areas = mutableMapOf<String, String>()
        jobs.awaitAll().forEach { (partialLinks, areaLabel) ->
            partialLinks?.keys?.forEach { courseKey -> areas[courseKey] = areaLabel }
        }
        areas
    }

    suspend fun fetchDetail(session: DaejinSession, relativeUrl: String): SyllabusDetailResult =
        withContext(Dispatchers.IO) {
            try {
                val url = LIST_URL.toHttpUrl().resolve(relativeUrl)
                    ?: return@withContext SyllabusDetailResult.NetworkError("강의계획서 링크가 올바르지 않아요.")
                val html = execute(url, session)
                SyllabusDetailResult.Success(parseSyllabusDetail(html))
            } catch (e: IOException) {
                Log.e(TAG, "네트워크 오류", e)
                SyllabusDetailResult.NetworkError("강의계획서를 불러오지 못했습니다. 다시 시도해주세요.")
            }
        }

    private fun buildListUrl(year: Int, semester: Int, params: Map<String, String>): HttpUrl {
        val builder = LIST_URL.toHttpUrl().newBuilder()
            .addQueryParameter("fhd_yyyy", year.toString())
            .addQueryParameter("fhd_shtm", semester.toString())
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private fun execute(url: HttpUrl, session: DaejinSession): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "JSESSIONID=${session.jsessionId}; $EXTRA_SESSION_COOKIES; WMONID=${session.wmonid}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            return response.body?.string().orEmpty()
        }
    }

    companion object {
        private const val CONCURRENCY = 6
        private const val LIST_URL = "https://dreams2.daejin.ac.kr/sugang/center/Blsn020302.jsp"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        // CourseCatalogClient와 동일 — dreams2가 이 쿠키들 없이는 www.daejin.ac.kr로 리다이렉트해버린다.
        private const val EXTRA_SESSION_COOKIES =
            "user_id=fete%2BXVnWzvaBMSTp1X%2BFQ%3D%3D; " +
                "userId=fete%2BXVnWzvaBMSTp1X%2BFQ%3D%3D; " +
                "nm=8p13%2B7QRPNTh19POT6%2FJPw%3D%3D; " +
                "userFlag=M0KvE%2FndjQ2bDxDXFBSM6A%3D%3D; " +
                "userMjCd=AFkB9N2kRhFOtBPWa6nzxg%3D%3D; " +
                "resd1=kffK4oSHCEBxASZ5sQOZeQ%3D%3D; " +
                "resd2=KOXkLxIFhOL33Ka%2BYe1Yrw%3D%3D; " +
                "orgCd=ZjzT1v9Ax6ybAbmfZIWOsA%3D%3D; " +
                "seq=b%2B3V6U777TAsKzSC6a5IAA%3D%3D; " +
                "wkkdCd=AFkB9N2kRhFOtBPWa6nzxg%3D%3D; " +
                "passwd=AFkB9N2kRhFOtBPWa6nzxg%3D%3D; " +
                "cookWebUser=btH3g7sAv6imoh3YAJBUEA%3D%3D"
    }
}
