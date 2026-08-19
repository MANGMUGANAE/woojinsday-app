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

/** 이수구분표 과목 한 줄. [category]는 "교필"/"교선" 같은 2글자 코드고, 교양선택은 실제로는
 *  코드 뒤에 세부영역이 붙어("교선4균형") 오기 때문에 원본 그대로는 [subArea]에 남겨둔다. */
data class TranscriptCourseRow(
    val category: String,
    val subArea: String?,
    val term: String,
    val courseCode: String,
    val courseName: String,
    val credit: Double,
    val grade: String
)

/** 리포트 안에 있는 "구분/기준/취득" 졸업사정 표 한 칸 — 졸업학점·졸업평점평균·교필·교선·전기·전필·
 *  전선·복전처럼 이수구분마다(그리고 총 졸업학점/평점마다) 기준값과 지금까지 취득한 값이 나온다.
 *  값 자체가 "126", "2.00"처럼 형식이 다양해서 문자열 그대로 들고 있는다. */
data class GraduationRequirement(
    val label: String,
    val requiredValue: String?,
    val earnedValue: String?
)

/** "이수구분표 원본" 화면(파싱하지 않은 원본 그대로 보여주는 화면)용 — MML의 `<TL>` 셀 하나를
 *  그대로 담는다. [to]/[le]/[ri]/[bo]는 MML 자체 좌표 단위(실제 화면 픽셀이 아님)라, 화면에 그릴
 *  땐 crownix 뷰어의 `adjustCoord` 공식(1px ≈ 10.3 단위)으로 dp 변환해서 쓴다. [ha]는 정렬
 *  (0=left/1=center/2=right), [fontSizePt]/[bold]/[color]는 HEAD의 TALIST(`<TA>`)를 tid로
 *  찾아 온 스타일이다(TL 자체에 pt/cl/cb가 있으면 그게 우선). */
data class RawCell(
    val tid: String,
    val to: Int,
    val le: Int,
    val ri: Int,
    val bo: Int,
    val ha: Int,
    val text: String,
    val fontSizePt: Float,
    val bold: Boolean,
    val color: String
)

/** MML의 `<LN>`(선) — 표 구분선. [color]/[widthMml]은 HEAD의 LALIST(`<LA>`)를 lid로 찾아 온 것. */
data class RawLine(val sx: Int, val sy: Int, val ex: Int, val ey: Int, val color: String, val widthMml: Float)

/** MML의 `<RA>`(사각형) — 표 테두리/배경. [fillColor]는 FALIST(`<FA>`)를 fid로, 테두리
 *  [lineColor]/[lineWidthMml]은 LALIST를 lid로 찾아 온 것. */
data class RawRect(
    val sx: Int,
    val sy: Int,
    val ex: Int,
    val ey: Int,
    val fillColor: String?,
    val lineColor: String?,
    val lineWidthMml: Float
)

data class TranscriptDetail(
    val rows: List<TranscriptCourseRow>,
    val requirements: List<GraduationRequirement>,
    // 교양선택(교선)은 "교양영역"(1~9, A/B/C, 실용/외국어/심화)별로도 기준·취득 학점이 따로 있다.
    val areaRequirements: List<GraduationRequirement>,
    val rawCells: List<RawCell>,
    val rawLines: List<RawLine>,
    val rawRects: List<RawRect>
)

sealed class TranscriptDetailResult {
    data class Success(val detail: TranscriptDetail) : TranscriptDetailResult()
    data class NetworkError(val message: String) : TranscriptDetailResult()
}

/**
 * 학생의 이수구분표(지금까지 수강한 과목 전체 이력 + 졸업사정 기준표)를 조회한다.
 *
 * Crownix/InfoTalk 리포트 뷰어를 그대로 흉내내는 2단계 흐름: 1단계로 뷰어 HTML을 받아 그 안의
 * `openFile("A","B")` 파라미터를 뽑고, 2단계로 그 값을 리포트 서버에 그대로 되돌려줘 실제 표
 * 데이터(MML XML)를 받는다.
 *
 * MML은 표를 `<TL to="세로위치" le="가로위치" ...>텍스트</TL>` 셀들의 나열로 표현한다 — 셀 개수가
 * 줄마다 다르고(과목코드가 없는 과목도 있다), 교양선택 과목은 이수영역 값 자체가 "교선4균형"처럼
 * 세부영역까지 붙어 나오는 등 고정폭으로 자르면 깨지는 지점이 많아서, 실제 좌표(`to`/`le`)로 줄과
 * 칸을 다시 묶는 방식으로 파싱한다.
 */
class AcademicTranscriptClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchCompletedCourseNames(userId2: String): AcademicTranscriptResult = withContext(Dispatchers.IO) {
        try {
            val mml = fetchTranscriptMml(userId2)
                ?: return@withContext AcademicTranscriptResult.NetworkError("이수구분표를 불러오지 못했습니다.")
            val names = parseTranscriptRows(parseCells(mml)).map { it.courseName }.toSet()
            Log.d(TAG, "이수구분표 파싱된 과목 수=${names.size}, 목록=${names.joinToString(" | ")}")
            AcademicTranscriptResult.Success(names)
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            AcademicTranscriptResult.NetworkError("이수구분표를 불러오지 못했습니다.")
        }
    }

    /** 졸업학점 화면용 — 과목 목록과 졸업사정 기준표, 그리고 원본 그대로의 전체 줄까지 한 번에 담아온다. */
    suspend fun fetchTranscriptDetail(userId2: String): TranscriptDetailResult = withContext(Dispatchers.IO) {
        try {
            val mml = fetchTranscriptMml(userId2)
                ?: return@withContext TranscriptDetailResult.NetworkError("이수구분표를 불러오지 못했습니다.")
            val cells = parseCells(mml)
            val head = parseHead(mml)
            val detail = TranscriptDetail(
                rows = parseTranscriptRows(cells),
                requirements = parseRequirementTable(cells, headerLabel = "구분"),
                areaRequirements = parseRequirementTable(cells, headerLabel = "교양영역"),
                rawCells = parseRawCells(mml, head),
                rawLines = parseRawLines(mml, head),
                rawRects = parseRawRects(mml, head)
            )
            Log.d(
                TAG,
                "이수구분표 파싱된 과목 수=${detail.rows.size}, 기준표 항목 수=${detail.requirements.size}, " +
                    "교양영역 항목 수=${detail.areaRequirements.size}"
            )
            TranscriptDetailResult.Success(detail)
        } catch (e: IOException) {
            Log.e(TAG, "네트워크 오류", e)
            TranscriptDetailResult.NetworkError("이수구분표를 불러오지 못했습니다.")
        }
    }

    /** Crownix/InfoTalk 리포트 뷰어를 흉내내는 2단계 흐름 — 1단계로 뷰어 HTML을 받아 그 안의
     *  `openFile("A","B")` 파라미터를 뽑고, 2단계로 그 값을 리포트 서버에 그대로 되돌려줘 실제
     *  표 데이터(MML XML)를 받는다. openFile 파라미터를 못 찾으면 null. */
    private fun fetchTranscriptMml(userId2: String): String? {
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
            return null
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
        return client.newCall(reportRequest).execute().use { it.body?.string().orEmpty() }
    }

    private data class MmlCell(val tid: String, val to: Int, val le: Int, val text: String)

    /** MML 안의 `<TL>` 셀을 전부(어떤 tid든) 뽑는다 — 표/헤더/안내문구/페이지 정보까지 전부
     *  여기 한 군데를 거쳐가므로, "원본 그대로" 뷰도 이걸 그대로 재사용한다. */
    private fun parseCells(mml: String): List<MmlCell> =
        TL_REGEX.findAll(mml).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val tid = TID_REGEX.find(attrs)?.groupValues?.get(1) ?: return@mapNotNull null
            val to = TO_REGEX.find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val le = LE_REGEX.find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val text = decodeEntities(match.groupValues[2].trim())
            MmlCell(tid, to, le, text)
        }.toList()

    private data class HeadTextAttr(val pt: Int, val color: String, val bold: Boolean)
    private data class HeadLineAttr(val widthMml: Float, val color: String)
    private data class HeadFaceAttr(val fillColor: String)
    private data class HeadInfo(
        val textAttrs: Map<String, HeadTextAttr>,
        val lineAttrs: Map<String, HeadLineAttr>,
        val faceAttrs: Map<String, HeadFaceAttr>
    )

    private fun parseAttrs(attrs: String): Map<String, String> =
        ATTR_REGEX.findAll(attrs).associate { it.groupValues[1] to it.groupValues[2] }

    /** MML `<HEAD>` 안의 TALIST/LALIST/FALIST — 표의 텍스트 스타일(폰트크기/색/굵기), 선(색/굵기),
     *  채우기(배경색)를 id로 찾아볼 수 있게 미리 맵으로 만들어 둔다. "원본" 뷰가 이 스타일을 그대로
     *  써야 실제 뷰어(crownix-viewer.js)와 같은 모양이 된다. */
    private fun parseHead(mml: String): HeadInfo {
        val headBlock = HEAD_REGEX.find(mml)?.groupValues?.get(1) ?: return HeadInfo(emptyMap(), emptyMap(), emptyMap())

        val textAttrs = TA_REGEX.findAll(headBlock).mapNotNull { m ->
            val a = parseAttrs(m.groupValues[1])
            val id = a["id"] ?: return@mapNotNull null
            id to HeadTextAttr(pt = a["pt"]?.toIntOrNull() ?: 100, color = a["cl"] ?: "#000000", bold = a["cb"] == "1")
        }.toMap()

        val lineAttrs = LA_REGEX.findAll(headBlock).mapNotNull { m ->
            val a = parseAttrs(m.groupValues[1])
            val id = a["id"] ?: return@mapNotNull null
            id to HeadLineAttr(widthMml = a["wd"]?.toFloatOrNull() ?: 10f, color = a["lc"] ?: "#000000")
        }.toMap()

        val faceAttrs = FA_REGEX.findAll(headBlock).mapNotNull { m ->
            val a = parseAttrs(m.groupValues[1])
            val id = a["id"] ?: return@mapNotNull null
            id to HeadFaceAttr(fillColor = a["fc"] ?: "#FFFFFF")
        }.toMap()

        return HeadInfo(textAttrs, lineAttrs, faceAttrs)
    }

    /** "원본" 뷰용 — `<TL>` 셀을 tid/to/le 외에 ri/bo/ha(정렬)까지, 스타일은 TL 자체 속성이
     *  있으면 그걸(원본 뷰어 로직과 동일하게) 우선하고 없으면 TALIST를 tid로 찾아 채운다. */
    private fun parseRawCells(mml: String, head: HeadInfo): List<RawCell> =
        TL_REGEX.findAll(mml).mapNotNull { match ->
            val a = parseAttrs(match.groupValues[1])
            val tid = a["tid"] ?: return@mapNotNull null
            val to = a["to"]?.toIntOrNull() ?: return@mapNotNull null
            val le = a["le"]?.toIntOrNull() ?: return@mapNotNull null
            val text = decodeEntities(match.groupValues[2].trim())
            if (text.isEmpty()) return@mapNotNull null

            val style = head.textAttrs[tid]
            RawCell(
                tid = tid,
                to = to,
                le = le,
                ri = a["ri"]?.toIntOrNull() ?: le,
                bo = a["bo"]?.toIntOrNull() ?: to,
                ha = a["ha"]?.toIntOrNull() ?: 0,
                text = text,
                fontSizePt = (a["pt"]?.toIntOrNull() ?: style?.pt ?: 100) / 10f,
                bold = (a["cb"] ?: if (style?.bold == true) "1" else "0") == "1",
                color = a["cl"] ?: style?.color ?: "#000000"
            )
        }.toList()

    private fun parseRawLines(mml: String, head: HeadInfo): List<RawLine> =
        LN_REGEX.findAll(mml).mapNotNull { match ->
            val a = parseAttrs(match.groupValues[1])
            if (a["vs"] == "0") return@mapNotNull null
            val sx = a["sx"]?.toIntOrNull() ?: return@mapNotNull null
            val sy = a["sy"]?.toIntOrNull() ?: return@mapNotNull null
            val ex = a["ex"]?.toIntOrNull() ?: return@mapNotNull null
            val ey = a["ey"]?.toIntOrNull() ?: return@mapNotNull null
            val lineAttr = head.lineAttrs[a["lid"]]
            RawLine(sx, sy, ex, ey, lineAttr?.color ?: "#000000", lineAttr?.widthMml ?: 10f)
        }.toList()

    private fun parseRawRects(mml: String, head: HeadInfo): List<RawRect> =
        RA_REGEX.findAll(mml).mapNotNull { match ->
            val a = parseAttrs(match.groupValues[1])
            val sx = a["sx"]?.toIntOrNull() ?: return@mapNotNull null
            val sy = a["sy"]?.toIntOrNull() ?: return@mapNotNull null
            val ex = a["ex"]?.toIntOrNull() ?: return@mapNotNull null
            val ey = a["ey"]?.toIntOrNull() ?: return@mapNotNull null
            val lineAttr = head.lineAttrs[a["lid"]]
            val faceAttr = head.faceAttrs[a["fid"]]
            RawRect(sx, sy, ex, ey, faceAttr?.fillColor, lineAttr?.color, lineAttr?.widthMml ?: 0f)
        }.toList()

    /** 과목 표는 `tid="3"` 셀들로 이뤄지는데, 한 줄(`to` 같음) 안에 과목이 1개(가로폭이 좁을 때)
     *  또는 2개(왼쪽/오른쪽 두 칸으로 나뉠 때)까지 같이 올 수 있고, 과목코드가 없는 과목도 있어
     *  칸 개수가 5개 또는 6개로 들쭉날쭉하다. 그래서 정해진 개수로 자르는 대신, [FixedCourseCategories]
     *  코드로 시작하는 셀이 나올 때마다 "새 과목 시작"으로 보고 그 지점부터 다음 시작 지점(또는 줄 끝)
     *  까지를 한 과목으로 묶는다 — "교필 취득학점 계 : 13" 같은 합계 줄은 셀이 1개뿐이라 자연히
     *  걸러진다(5개 미만은 버림). */
    private fun parseTranscriptRows(cells: List<MmlCell>): List<TranscriptCourseRow> {
        val rows = mutableListOf<TranscriptCourseRow>()
        cells.filter { it.tid == "3" }
            .groupBy { it.to }
            .values
            .forEach { rowCells ->
                val sorted = rowCells.sortedBy { it.le }
                val markerIdxs = sorted.indices.filter { idx ->
                    FixedCourseCategories.any { code -> sorted[idx].text.startsWith(code) }
                }
                markerIdxs.forEachIndexed { m, start ->
                    val end = if (m + 1 < markerIdxs.size) markerIdxs[m + 1] else sorted.size
                    val group = sorted.subList(start, end).map { it.text }
                    if (group.size < 5) return@forEachIndexed
                    val label = group[0]
                    val category = FixedCourseCategories.first { label.startsWith(it) }
                    val subArea = label.takeIf { it != category }
                    if (group.size >= 6) {
                        rows += TranscriptCourseRow(category, subArea, group[1], group[2], group[3], group[4].toDoubleOrNull() ?: 0.0, group[5])
                    } else {
                        rows += TranscriptCourseRow(category, subArea, group[1], "", group[2], group[3].toDoubleOrNull() ?: 0.0, group[4])
                    }
                }
            }
        return rows
    }

    /** "[헤더]/기준/취득" 3줄짜리 표를 찾아 컬럼별로 정리한다 — 졸업사정 표("구분"/졸업학점·교필·
     *  교선·...)와 교양영역별 표("교양영역"/1~9·A·B·C·실용·외국어·심화) 둘 다 같은 모양이라 이
     *  함수 하나를 헤더 라벨만 바꿔 재사용한다. 줄 위치(`to`)는 과목 수에 따라 매번 달라지므로
     *  좌표를 하드코딩하지 않고, 첫 칸이 [headerLabel]인 줄을 헤더로 찾은 뒤 바로 다음 두 줄
     *  (기준/취득)을 가져온다. 각 값은 헤더 컬럼과 가로 위치(`le`)가 가장 가까운 셀을 매칭한다 —
     *  기초처럼 값 자체가 비어있는(셀이 없는) 컬럼도 안 밀리고 그대로 비게 된다. */
    private fun parseRequirementTable(cells: List<MmlCell>, headerLabel: String): List<GraduationRequirement> {
        val tid1Rows = cells.filter { it.tid == "1" }
            .groupBy { it.to }
            .toSortedMap()
            .values
            .map { rowCells -> rowCells.sortedBy { it.le } }

        val headerIdx = tid1Rows.indexOfFirst { row -> row.firstOrNull()?.text?.replace(" ", "") == headerLabel }
        if (headerIdx == -1 || headerIdx + 2 >= tid1Rows.size) return emptyList()

        val header = tid1Rows[headerIdx]
        val requiredRow = tid1Rows[headerIdx + 1]
        val earnedRow = tid1Rows[headerIdx + 2]
        if (requiredRow.firstOrNull()?.text?.replace(" ", "") != "기준") return emptyList()
        if (earnedRow.firstOrNull()?.text?.replace(" ", "") != "취득") return emptyList()

        fun valueNear(row: List<MmlCell>, columnLe: Int): String? =
            row.drop(1)
                .minByOrNull { kotlin.math.abs(it.le - columnLe) }
                ?.takeIf { kotlin.math.abs(it.le - columnLe) < 200 }
                ?.text

        return header.drop(1).map { column ->
            GraduationRequirement(
                label = column.text.replace(" ", ""),
                requiredValue = valueNear(requiredRow, column.le),
                earnedValue = valueNear(earnedRow, column.le)
            )
        }
    }

    private fun decodeEntities(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    companion object {
        private const val VIEWER_URL = "https://dreams2.daejin.ac.kr/sugang/sugang_wlsn0555.jsp"
        private const val ORG_CD = "ZjzT1v9Ax6ybAbmfZIWOsA%3D%3D"
        private const val REPORT_URL = "https://dreams2.daejin.ac.kr/ReportingServer/service"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val OPEN_FILE_REGEX = Regex("""openFile\("([^"]+)"\s*,\s*"([^"]+)"\)""")
        private val TL_REGEX = Regex("""<TL\s+([^>]*)>([^<]*)</TL>""")
        private val TID_REGEX = Regex("""\btid="(\d+)"""")
        private val TO_REGEX = Regex("""\bto="(-?\d+)"""")
        private val LE_REGEX = Regex("""\ble="(-?\d+)"""")
        private val ATTR_REGEX = Regex("""(\w+)="([^"]*)"""")
        private val HEAD_REGEX = Regex("""<HEAD>([\s\S]*?)</HEAD>""")
        private val TA_REGEX = Regex("""<TA\s+([^/>]*)/>""")
        private val LA_REGEX = Regex("""<LA\s+([^/>]*)/>""")
        private val FA_REGEX = Regex("""<FA\s+([^/>]*)/>""")
        private val LN_REGEX = Regex("""<LN\s+([^/>]*)/>""")
        private val RA_REGEX = Regex("""<RA\s+([^/>]*)/>""")
    }
}
