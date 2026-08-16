package com.daejin.woojintoday.data.network

import android.util.Log
import com.daejin.woojintoday.data.model.FixedCourseCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.UUID

private const val TAG = "AcademicTranscript"

sealed class AcademicTranscriptResult {
    data class Success(val completedCourseNames: Set<String>) : AcademicTranscriptResult()
    data class NetworkError(val message: String) : AcademicTranscriptResult()
}

/**
 * 학생의 이수구분표(지금까지 수강한 과목 전체 이력)를 조회해 이미 이수한 과목명을 뽑아온다 — AI
 * 시간표 생성 시 이미 들은 과목을 제외하는 데 쓰인다. 과목 코드는 학기마다 바뀔 수 있어 과목명으로
 * 매칭한다.
 *
 * Crownix/InfoTalk 리포트 뷰어를 그대로 흉내내는 2단계 흐름: 1단계로 뷰어 HTML을 받아 그 안의
 * `openFile("A","B")` 파라미터를 뽑고, 2단계로 그 값을 리포트 서버에 그대로 되돌려줘 실제 표
 * 데이터(MML XML)를 받는다.
 */
class AcademicTranscriptClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchCompletedCourseNames(userId2: String): AcademicTranscriptResult = withContext(Dispatchers.IO) {
        try {
            val cookie = "userId=$userId2; orgCd=$ORG_CD;"

            val viewerRequest = Request.Builder()
                .url(VIEWER_URL)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .get()
                .build()
            val viewerHtml = client.newCall(viewerRequest).execute().use { it.body?.string().orEmpty() }

            val match = OPEN_FILE_REGEX.find(viewerHtml)
            if (match == null) {
                Log.w(TAG, "openFile 파라미터를 찾지 못함, 본문 길이=${viewerHtml.length}")
                return@withContext AcademicTranscriptResult.NetworkError("이수구분표를 불러오지 못했습니다.")
            }
            val mrdPath = match.groupValues[1]
            val mrdParam = match.groupValues[2]

            // mrdPath/mrdParam은 이미 한 번 퍼센트 인코딩된 상태 — FormBody가 값 인코딩을 한 번 더
            // 입혀서 실제 브라우저(crownix-viewer.js)가 보내는 것과 같은 이중 인코딩이 된다.
            val reportBody = FormBody.Builder()
                .add("opcode", "700")
                .add("mrd_path", mrdPath)
                .add("mrd_param", mrdParam)
                .add("mrd_plain_param", "")
                .add("mrd_data", "")
                .add("runtime_param", "")
                .add("mmlVersion", "0")
                .add("protocol", "sync")
                .add("use_cache", "false")
                .add("html5_uuid", UUID.randomUUID().toString())
                .add("enc_type", "5")
                .build()
            val reportRequest = Request.Builder()
                .url(REPORT_URL)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .post(reportBody)
                .build()
            val mml = client.newCall(reportRequest).execute().use { it.body?.string().orEmpty() }

            val names = parseCompletedCourseNames(mml)
            Log.d(TAG, "이수구분표 파싱된 과목 수=${names.size}, 목록=${names.joinToString(" | ")}")
            AcademicTranscriptResult.Success(names)
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            AcademicTranscriptResult.NetworkError("이수구분표를 불러오지 못했습니다.")
        }
    }

    /** `<TL tid="3">` 값들이 순서대로 [이수영역, 년도학기, 과목코드, 교과목명, 학점, 성적] 6개씩
     *  한 행을 이루는데, 그 사이사이 "교필 취득학점 계 : 13" 같은 합계 줄도 같은 tid="3"로 섞여
     *  있다. 값이 정확히 [FixedCourseCategories] 중 하나와 일치할 때만 행의 시작으로 보고 6개를
     *  한 묶음으로 읽어 4번째(교과목명)를 뽑는다 — 합계 줄은 이 조건에 안 걸려 자연히 건너뛴다. */
    private fun parseCompletedCourseNames(mml: String): Set<String> {
        val values = TL_REGEX.findAll(mml)
            .filter { it.groupValues[1].contains("tid=\"3\"") }
            .map { decodeEntities(it.groupValues[2].trim()) }
            .toList()

        val names = mutableSetOf<String>()
        var i = 0
        while (i < values.size) {
            if (values[i] in FixedCourseCategories && i + 5 < values.size) {
                names += values[i + 3]
                i += 6
            } else {
                i += 1
            }
        }
        return names
    }

    private fun decodeEntities(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    companion object {
        private const val VIEWER_URL = "https://dreams2.daejin.ac.kr/sugang/sugang_wlsn0555.jsp"
        private const val REPORT_URL = "https://dreams2.daejin.ac.kr/ReportingServer/service"
        private const val ORG_CD = "ZjzT1v9Ax6ybAbmfZIWOsA%3D%3D"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val OPEN_FILE_REGEX = Regex("""openFile\("([^"]+)"\s*,\s*"([^"]+)"\)""")
        private val TL_REGEX = Regex("""<TL\s+([^>]*)>([^<]*)</TL>""")
    }
}
