package com.daejin.woojintoday.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 학기당 한 번 구축한 전체 인덱스를 로컬에 캐싱한다 — "과목코드-분반 → 강의계획서 상세 링크"뿐
 *  아니라, 전공 조회 과정에서 자연히 알게 되는 "과목코드-분반 → 학과명"도 같이 저장한다(교양/교직/
 *  일선 과목은 학과가 없어 이 맵엔 안 들어있음). 구축 비용(교필/교직/일선/전공 50개/교선 19개 =
 *  총 72번 요청)이 커서, 매번 다시 만들지 않도록 (연도, 학기) 단위로 한 번 만든 걸 계속 재사용한다. */
class SyllabusIndexStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getLinks(year: Int, semester: Int): Map<String, String>? =
        readMap(linksKeyFor(year, semester))

    fun getDepartments(year: Int, semester: Int): Map<String, String>? =
        readMap(departmentsKeyFor(year, semester))

    /** 과목코드-분반 → 교선 영역 라벨(예: "1영역"). [SyllabusClient.buildGenEdAreaIndex]가 채우며,
     *  전공 조회와 무관하게 교선 19개 영역만 훑은 결과라 [getDepartments]와는 별도로 캐싱한다. */
    fun getGenEdAreas(year: Int, semester: Int): Map<String, String>? =
        readMap(genEdAreasKeyFor(year, semester))

    fun save(year: Int, semester: Int, links: Map<String, String>, departments: Map<String, String>) {
        prefs.edit()
            .putString(linksKeyFor(year, semester), json.encodeToString(links))
            .putString(departmentsKeyFor(year, semester), json.encodeToString(departments))
            .apply()
    }

    fun saveGenEdAreas(year: Int, semester: Int, areas: Map<String, String>) {
        prefs.edit()
            .putString(genEdAreasKeyFor(year, semester), json.encodeToString(areas))
            .apply()
    }

    private fun readMap(key: String): Map<String, String>? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrNull()
    }

    private fun linksKeyFor(year: Int, semester: Int) = "syllabus_links_${year}_$semester"
    private fun departmentsKeyFor(year: Int, semester: Int) = "syllabus_departments_${year}_$semester"
    private fun genEdAreasKeyFor(year: Int, semester: Int) = "syllabus_gen_ed_areas_${year}_$semester"

    companion object {
        private const val PREFS_NAME = "sugang_syllabus_index"
    }
}
