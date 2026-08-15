package com.daejin.woojintoday.data.network

import android.util.Log
import com.daejin.woojintoday.data.model.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val TAG = "CourseCatalog"

sealed class CourseCatalogResult {
    data class Success(val courses: List<Course>) : CourseCatalogResult()
    data class NetworkError(val message: String) : CourseCatalogResult()
}

/**
 * Fetches the open-course catalog for a given year/semester. Reuses the WMONID/JSESSIONID
 * cookies captured from the portal login (DaejinAuthClient), combined with a fixed extra
 * cookie set dreams2.daejin.ac.kr also requires — see [EXTRA_SESSION_COOKIES].
 */
class CourseCatalogClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchCourses(
        year: Int,
        semester: Int,
        session: DaejinSession,
        departmentCode: String? = null
    ): CourseCatalogResult =
        withContext(Dispatchers.IO) {
            try {
                val urlBuilder = BASE_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("fhd_yyyy", year.toString())
                    .addQueryParameter("fhd_shtm", semester.toString())
                if (departmentCode != null) {
                    urlBuilder
                        .addQueryParameter("ic_kwa", "B41005")
                        .addQueryParameter("ic_kwa_1", departmentCode)
                } else {
                    urlBuilder.addQueryParameter("ic_kwa", "%")
                }
                val url = urlBuilder.build()

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", "JSESSIONID=${session.jsessionId}; $EXTRA_SESSION_COOKIES; WMONID=${session.wmonid}")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val html = response.body?.string().orEmpty()
                    Log.d(TAG, "조회 응답 코드=${response.code}, 본문 길이=${html.length}")
                    val courses = parseCourseCatalog(html)
                    Log.d(TAG, "파싱된 과목 수=${courses.size}")
                    CourseCatalogResult.Success(courses)
                }
            } catch (e: IOException) {
                Log.e(TAG, "네트워크 오류", e)
                CourseCatalogResult.NetworkError("개설 과목을 불러오지 못했습니다. 다시 시도해주세요.")
            }
        }

    companion object {
        private const val BASE_URL = "https://dreams2.daejin.ac.kr/sugang/center/ProLsnAply06.jsp"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        // dreams2 requires these identity cookies alongside JSESSIONID/WMONID or it redirects to
        // www.daejin.ac.kr instead of serving the catalog — confirmed empirically 2026-08-08.
        // Origin unknown; kept verbatim as given rather than re-derived per-session.
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
