package com.daejin.woojintoday.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset

private const val TAG = "StudentProfile"

data class StudentProfile(val name: String, val department: String, val grade: String)

sealed class StudentProfileResult {
    data class Success(val profile: StudentProfile) : StudentProfileResult()
    data class NetworkError(val message: String) : StudentProfileResult()
}

/**
 * Pulls the student's basic info (name/department/grade) from the "개인 신상정보 수정" page.
 * Auth here is just the `userId`/`orgCd` cookie pair — userId is the SSO redirect's `uid` value
 * captured at login time (see DaejinAuthClient.extractUserId2), orgCd is a fixed constant.
 */
class StudentProfileClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchProfile(userId2: String, password: String): StudentProfileResult = withContext(Dispatchers.IO) {
        try {
            val encodedPassword = URLEncoder.encode(password, "UTF-8")
            val cookie = "userId=$userId2; orgCd=$ORG_CD;"
            val body = "forward_url=/sugang_wshr0610.jsp&pwd=$encodedPassword".toRequestBody(FORM_MEDIA_TYPE)
            Log.d(TAG, "요청 Cookie=$cookie, body=forward_url=/sugang_wshr0610.jsp&pwd=(masked, len=${encodedPassword.length})")
            val request = Request.Builder()
                .url(PROFILE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                .header("Origin", ORIGIN)
                .header("Referer", REFERER)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookie)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.bytes()?.toString(EUC_KR).orEmpty()
                val profile = parseProfile(html)
                val redirectChain = generateSequence(response) { it.priorResponse }
                    .toList().reversed().joinToString(" -> ") { "${it.code}(${it.request.url})" }
                Log.d(TAG, "프로필 응답 체인=$redirectChain, 최종 본문 길이=${html.length}, 파싱 결과=$profile")
                if (response.code != 200 || profile == null) {
                    Log.w(TAG, "프로필 응답 헤더: ${response.headers}")
                    html.chunked(800).forEachIndexed { index, chunk ->
                        Log.w(TAG, "본문[$index]: $chunk")
                    }
                }
                if (profile == null) {
                    StudentProfileResult.NetworkError("학생 정보를 불러오지 못했습니다.")
                } else {
                    StudentProfileResult.Success(profile)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            StudentProfileResult.NetworkError("서버에 연결할 수 없습니다.")
        }
    }

    private fun parseProfile(html: String): StudentProfile? {
        val document = Jsoup.parse(html)
        val fields = mutableMapOf<String, String>()
        document.select("td.tr_chrm_n").forEach { labelCell: Element ->
            val label = normalizeLabel(labelCell.text())
            val valueCell = labelCell.nextElementSibling() ?: return@forEach
            if (!valueCell.hasClass("tr_a_n")) return@forEach
            fields[label] = valueCell.text().trim()
        }
        val name = fields["성명"]?.takeIf { it.isNotBlank() } ?: return null
        val department = fields["학과"]?.takeIf { it.isNotBlank() } ?: return null
        val grade = fields["학년"]?.takeIf { it.isNotBlank() } ?: return null
        return StudentProfile(name, department, grade)
    }

    private fun normalizeLabel(text: String): String = text.replace(" ", "").replace(" ", "")

    companion object {
        private const val PROFILE_URL = "https://dreams2.daejin.ac.kr/sugang/sec/SecCheckProc.jsp"
        private const val ORIGIN = "https://dreams2.daejin.ac.kr"
        private const val REFERER = "https://dreams2.daejin.ac.kr/sugang/sec/SecCheck.jsp?forward_url=/sugang_wshr0610.jsp"
        private const val ORG_CD = "ZjzT1v9Ax6ybAbmfZIWOsA%3D%3D"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        private val EUC_KR = Charset.forName("EUC-KR")
    }
}
