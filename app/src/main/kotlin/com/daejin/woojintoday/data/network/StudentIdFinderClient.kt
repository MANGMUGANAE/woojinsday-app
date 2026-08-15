package com.daejin.woojintoday.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

private const val TAG = "StudentIdFinder"

sealed class FindStudentIdResult {
    data class Found(val studentNo: String) : FindStudentIdResult()
    object NotFound : FindStudentIdResult()
    data class NetworkError(val message: String) : FindStudentIdResult()
}

/** Daejin's public "학번 찾기" (find student number) lookup — no login required. */
class StudentIdFinderClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun findStudentId(
        name: String,
        birthYearYY: String,
        birthMonth: String,
        birthDay: String,
        mobile1: String,
        mobile2: String,
        mobile3: String
    ): FindStudentIdResult = withContext(Dispatchers.IO) {
        try {
            val birthday = birthYearYY + birthMonth + birthDay
            val body = "findType=2" +
                "&srchGubun=1" +
                "&srchNm=${URLEncoder.encode(name, "UTF-8")}" +
                "&srchBirthday=$birthday" +
                "&srchMobile1=$mobile1" +
                "&srchMobile2=$mobile2" +
                "&srchMobile3=$mobile3"
            val request = Request.Builder()
                .url(URL)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("User-Agent", USER_AGENT)
                .post(body.toRequestBody(FORM_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                Log.d(TAG, "응답 코드=${response.code}, 본문 길이=${html.length}")
                val found = FOUND_REGEX.find(html)?.groupValues?.get(1)
                if (found != null) FindStudentIdResult.Found(found) else FindStudentIdResult.NotFound
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            FindStudentIdResult.NetworkError("학번 조회에 실패했습니다.")
        }
    }

    companion object {
        private const val URL = "https://www.daejin.ac.kr/userInfoSearch/daejin/1/idSearch.do"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        private val FOUND_REGEX = Regex("""조회된\s*학번은\s*<strong>\s*(\d+)\s*</strong>""")
    }
}
