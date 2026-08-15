package com.daejin.woojintoday.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.io.IOException

private const val TAG = "MyTimetable"

sealed class MyTimetableResult {
    data class Success(val courseKeys: Set<String>) : MyTimetableResult()
    data class NetworkError(val message: String) : MyTimetableResult()
}

/**
 * Fetches the student's own registered timetable from BlsnTotalTimeTableLst.jsp for a given
 * year/semester and extracts each row's 교과번호+분반 as a "code-section" key matching
 * [com.daejin.woojintoday.data.model.Course.courseKey], for matching against the already-loaded
 * course catalog.
 */
class MyTimetableClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchMyTimetableCourseKeys(
        year: Int,
        semester: Int,
        session: DaejinSession,
        userId2: String
    ): MyTimetableResult = withContext(Dispatchers.IO) {
        try {
            val body = "selectYear=$year$semester".toRequestBody(FORM_MEDIA_TYPE)
            val request = Request.Builder()
                .url(URL)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", "JSESSIONID=${session.jsessionId}; ${sessionCookies(userId2)}; WMONID=${session.wmonid}")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                Log.d(TAG, "불러오기 응답 코드=${response.code}, 본문 길이=${html.length}")
                val keys = parseCourseKeys(html)
                Log.d(TAG, "파싱된 과목 수=${keys.size}")
                MyTimetableResult.Success(keys)
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            MyTimetableResult.NetworkError("시간표를 불러오지 못했습니다. 다시 시도해주세요.")
        }
    }

    /** 텍스트 시간표 표(id="tTbl")의 첫 행(헤더: 교과번호/교과목명/분반/...)을 건너뛰고,
     *  각 데이터 행의 교과번호(0번째 셀)+분반(2번째 셀)을 courseKey 형식("코드-분반")으로 합친다. */
    private fun parseCourseKeys(html: String): Set<String> {
        val document = Jsoup.parse(html)
        val rows = document.select("table#tTbl tr")
        return rows.drop(1).mapNotNull { row ->
            val cells = row.select("> td")
            if (cells.size < 3) return@mapNotNull null
            val code = cells[0].text().trim()
            val section = cells[2].text().trim()
            if (code.isEmpty()) return@mapNotNull null
            "$code-$section"
        }.toSet()
    }

    companion object {
        private const val URL = "https://dreams2.daejin.ac.kr/sugang/center/BlsnTotalTimeTableLst.jsp"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded;charset=UTF-8".toMediaType()

        // user_id/userId만 로그인한 학생의 실제 userId2로 채운다 — 이 엔드포인트는 개인 등록
        // 시간표를 돌려주므로 CourseCatalogClient처럼 남의 계정 값을 하드코딩해두면 전원이 그
        // 계정의 시간표를 받게 된다(카탈로그/강의계획서는 공개 데이터라 무관했지만 여긴 다르다).
        // 나머지 필드(nm/userFlag/userMjCd/...)는 StudentProfileClient가 userId+orgCd만으로
        // 인증에 성공하는 걸 봐서 로그인 식별과 무관한 고정값으로 보이지만, 다른 계정으로
        // 실제 검증은 아직 안 됐다.
        private fun sessionCookies(userId2: String): String =
            "user_id=$userId2; " +
                "userId=$userId2; " +
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
