package com.daejin.woojintoday.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.nio.charset.Charset

private const val TAG = "AdvisorClient"

sealed class AdvisorResult {
    data class Success(val name: String) : AdvisorResult()
    data class NetworkError(val message: String) : AdvisorResult()
}

/**
 * "학적 정보 조회" 페이지에서 지도교수 성함만 뽑아온다. 인증은 [StudentProfileClient]와 동일한
 * userId/orgCd 쿠키 쌍(userId는 로그인 시 저장해둔 userId2, orgCd는 고정값)만으로 충분하다.
 */
class AdvisorClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchAdvisorName(userId2: String): AdvisorResult = withContext(Dispatchers.IO) {
        try {
            val cookie = "userId=$userId2; orgCd=$ORG_CD;"
            val request = Request.Builder()
                .url(ADVISOR_URL)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.bytes()?.toString(EUC_KR).orEmpty()
                val name = parseAdvisorName(html)
                Log.d(TAG, "지도교수 조회 응답 코드=${response.code}, 파싱 결과=$name")
                if (name == null) {
                    AdvisorResult.NetworkError("지도교수 정보를 찾을 수 없습니다.")
                } else {
                    AdvisorResult.Success(name)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            AdvisorResult.NetworkError("서버에 연결할 수 없습니다.")
        }
    }

    private fun parseAdvisorName(html: String): String? {
        val labelCell = Jsoup.parse(html).select("td.tr_chrm1_n")
            .firstOrNull { it.text().trim() == "지도교수" } ?: return null
        val valueCell = labelCell.nextElementSibling() ?: return null
        return valueCell.text().trim().takeIf { it.isNotBlank() }
    }

    companion object {
        private const val ADVISOR_URL = "https://dreams2.daejin.ac.kr/sugang/center/Bshr020101.jsp"
        private const val ORG_CD = "ZjzT1v9Ax6ybAbmfZIWOsA%3D%3D"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val EUC_KR = Charset.forName("EUC-KR")
    }
}
