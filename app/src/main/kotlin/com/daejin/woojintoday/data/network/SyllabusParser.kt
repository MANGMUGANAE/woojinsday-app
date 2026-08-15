package com.daejin.woojintoday.data.network

import com.daejin.woojintoday.data.model.Syllabus
import com.daejin.woojintoday.data.model.SyllabusAssignment
import com.daejin.woojintoday.data.model.SyllabusCompetencyRow
import com.daejin.woojintoday.data.model.SyllabusEvaluationRow
import com.daejin.woojintoday.data.model.SyllabusField
import com.daejin.woojintoday.data.model.SyllabusMethodRow
import com.daejin.woojintoday.data.model.SyllabusSection
import com.daejin.woojintoday.data.model.SyllabusWeek
import com.daejin.woojintoday.data.model.SyllabusWeekSession
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private val SECTION_TITLE_REGEX = Regex("""^\d+[.\-]""")

// jsoup은 &nbsp;를 일반 공백이 아니라 U+00A0(줄바꿈 없는 공백)로 남겨두는데, 이건 String.trim()으로
// 안 지워져서 "빈칸"인데 빈 문자열로 안 보일 수 있다 — 빈칸 판정에는 항상 이걸 써야 한다.
private fun String.isBlankNbsp(): Boolean = replace(' ', ' ').trim().isEmpty()
private fun String.trimNbsp(): String = replace(' ', ' ').trim()

/** Blsn020302.jsp(이수구분별 과목 목록) 응답 하나에 들어있는 모든 행을 "코드-분반" → Blsn020303.jsp
 *  상세 링크(href) 매핑으로 뽑아낸다. 전체 인덱스를 구축할 때 카테고리/학과별로 이 함수를 반복 호출해
 *  결과를 합친다. */
fun parseSyllabusListLinks(html: String): Map<String, String> {
    val document = Jsoup.parse(html)
    val result = mutableMapOf<String, String>()
    for (row in document.select("tr.tr_a_chrm")) {
        val cells = row.select("> td")
        if (cells.size < 2) continue
        val link = cells[1].selectFirst("a") ?: continue
        val courseKey = link.text().trim()
        if (courseKey.isEmpty()) continue
        result[courseKey] = link.attr("href")
    }
    return result
}

/** Blsn020303.jsp(강의계획서 상세) 응답을 상단 정보(과목명/교수/시간 등)와 번호 매겨진 절
 *  (1. 수업의 개요 ~ 11. 연계 비교과 프로그램)로 나눠 파싱한다. */
fun parseSyllabusDetail(html: String): Syllabus {
    val document = Jsoup.parse(html)

    // 상단 정보 테이블 — 문서에서 class="table1"인 첫 번째 테이블이 항상 이 표다(다른 절 내용은
    // 전부 그 뒤에 나오는 별개의 table1 테이블들이라 첫 번째 것만 집으면 섞이지 않는다).
    val headerTable = document.select("table.table1").firstOrNull()
    val fields = mutableListOf<SyllabusField>()
    headerTable?.select("tr")?.drop(1)?.forEach { row ->
        val cells = row.select("> td")
        var i = 0
        while (i + 1 < cells.size) {
            val label = cells[i].text().trim()
            val value = cells[i + 1].text().trim()
            if (label.isNotEmpty()) fields += SyllabusField(label, value)
            i += 2
        }
    }

    // 번호 매겨진 절들 — 전부 같은 바깥 테이블의 형제 <tr>들이라, 그 테이블을 한 번 찾으면
    // 순서대로 훑으면서 "N. 제목" 헤더 행을 만날 때마다 새 절로 끊는다.
    val firstHeaderCell = document.select("b").firstOrNull { SECTION_TITLE_REGEX.containsMatchIn(it.text().trim()) }
    val outerTable = firstHeaderCell?.closest("tr")?.parent()
    val sections = mutableListOf<SyllabusSection>()
    if (outerTable != null) {
        var currentTitle: String? = null
        val currentBody = StringBuilder()
        var currentEvaluationRows: List<SyllabusEvaluationRow> = emptyList()
        var currentClassFormat: List<SyllabusField> = emptyList()
        var currentMethods: List<SyllabusMethodRow> = emptyList()
        var currentMedia: List<String> = emptyList()
        var currentWeeklyPlan: List<SyllabusWeek> = emptyList()
        var currentAssignments: List<SyllabusAssignment> = emptyList()
        var currentCompetencies: List<SyllabusCompetencyRow> = emptyList()
        fun flush() {
            val title = currentTitle ?: return
            val body = currentBody.toString().trim()
            if (body.isNotEmpty() || currentEvaluationRows.isNotEmpty() || currentClassFormat.isNotEmpty() ||
                currentMethods.isNotEmpty() || currentMedia.isNotEmpty() || currentWeeklyPlan.isNotEmpty() ||
                currentAssignments.isNotEmpty() || currentCompetencies.isNotEmpty()
            ) {
                sections += SyllabusSection(
                    title, body, currentEvaluationRows, currentClassFormat, currentMethods, currentMedia,
                    currentWeeklyPlan, currentAssignments, currentCompetencies
                )
            }
            currentBody.clear()
            currentEvaluationRows = emptyList()
            currentClassFormat = emptyList()
            currentMethods = emptyList()
            currentMedia = emptyList()
            currentWeeklyPlan = emptyList()
            currentAssignments = emptyList()
            currentCompetencies = emptyList()
        }
        for (row in outerTable.children()) {
            if (row.tagName() != "tr") continue
            val headerB = row.selectFirst("b")?.takeIf { SECTION_TITLE_REGEX.containsMatchIn(it.text().trim()) }
            if (headerB != null) {
                flush()
                currentTitle = headerB.text().trim()
            } else if (currentTitle != null) {
                val text = row.text().trim()
                if (text.isNotEmpty()) currentBody.appendLine(text)
                // "3-1)/3-2)", "4. 수업형태", "5. 수업방법", "6. 수업매체", "7. 학습 평가방법", "9. 과제",
                // "10. 주별 수업계획"은 항상 같은 표 구조라, 텍스트로 뭉개지 않고 각각 따로 구조화해서
                // 파싱한다.
                when {
                    currentTitle!!.startsWith("10.") ->
                        row.selectFirst("table")?.let { table -> currentWeeklyPlan = parseWeeklyPlan(table) }
                    currentTitle!!.startsWith("9.") ->
                        row.selectFirst("table")?.let { table -> currentAssignments = parseAssignments(table) }
                    currentTitle!!.startsWith("7.") ->
                        row.selectFirst("table")?.let { table -> currentEvaluationRows = parseEvaluationRows(table) }
                    currentTitle!!.startsWith("6.") ->
                        row.selectFirst("table")?.let { table -> currentMedia = parseMedia(table) }
                    currentTitle!!.startsWith("5.") ->
                        row.selectFirst("table")?.let { table -> currentMethods = parseTeachingMethods(table) }
                    currentTitle!!.startsWith("4.") ->
                        row.selectFirst("table")?.let { table -> currentClassFormat = parseClassFormat(table) }
                    currentTitle!!.startsWith("3-1)") || currentTitle!!.startsWith("3-2)") ->
                        row.selectFirst("table")?.let { table -> currentCompetencies = parseCompetencies(table) }
                }
            }
        }
        flush()
    }

    return Syllabus(fields = fields, sections = sections)
}

// "이론수업( ● ) 실습(  ) 이론수업+실습(  )"처럼 여러 선택지 중 하나가 "●"로 표시되는 패턴에서
// 실제로 선택된 항목의 이름만 뽑아낸다.
private val CLASS_FORMAT_OPTION_REGEX = Regex("""([^()]+?)\(([^)]*)\)""")

private fun selectedOption(text: String): String? =
    CLASS_FORMAT_OPTION_REGEX.findAll(text)
        .firstOrNull { it.groupValues[2].contains("●") }
        ?.groupValues?.get(1)?.trim()

/** "4. 수업형태" 표 파싱 — 첫 번째 칸은 이론/실습 형태, 두 번째 칸은 강의실/온라인 진행 방식 중
 *  "●"로 표시된 걸 골라서 보여준다. */
private fun parseClassFormat(table: Element): List<SyllabusField> {
    val cells = table.select("tr").flatMap { it.select("> td") }
    val labels = listOf("수업 형태", "진행 방식")
    return cells.mapIndexedNotNull { index, cell ->
        val selected = selectedOption(cell.text()) ?: return@mapIndexedNotNull null
        SyllabusField(labels.getOrElse(index) { "형태 ${index + 1}" }, selected)
    }
}

/** "7. 학습 평가방법" 표 파싱 — "학습태도(루브릭평가 포함)"처럼 rowspan으로 여러 행을 묶는 그룹이
 *  있어서, 그룹 라벨이 있는 행(4칸)을 만나면 기억해뒀다가 그 다음 이어지는 하위 행(3칸, colspan 없음)
 *  에도 같은 그룹으로 붙여준다. "출석"처럼 단독 행(3칸, 첫 칸 colspan=2)을 만나면 그룹을 해제한다. */
private fun parseEvaluationRows(table: Element): List<SyllabusEvaluationRow> {
    val rows = mutableListOf<SyllabusEvaluationRow>()
    var currentGroup: String? = null
    for (tr in table.select("tr")) {
        val cells = tr.select("> td")
        val texts = cells.map { it.text().trim() }
        if (texts.all { it.isEmpty() }) continue
        when (cells.size) {
            4 -> {
                currentGroup = texts[0]
                rows += SyllabusEvaluationRow(currentGroup, texts[1], texts[2], texts[3])
            }
            3 -> {
                if (texts[0] == "평가요소") continue // 헤더 행
                val isStandalone = cells[0].attr("colspan") == "2"
                if (isStandalone) currentGroup = null
                rows += SyllabusEvaluationRow(if (isStandalone) null else currentGroup, texts[0], texts[1], texts[2])
            }
        }
    }
    return rows
}

/** "5. 수업방법" 표 파싱 — 강의/팀티칭/PBL/캡스톤디자인 등 수십 개 방법이 다 나열돼 있고 그중
 *  "●"로 체크된 것만 실제로 쓰는 방법이라, 체크 안 된 행은 버리고 체크된 것만 남긴다. "선진교수법"처럼
 *  rowspan으로 여러 세부 방법을 묶는 그룹은 [parseEvaluationRows]와 같은 방식으로 붙여준다. 헤더 행
 *  ("방법"/"개요")과 "기타1~3" 자유기입 행은 칸 수가 2개뿐이라 자연히 걸러진다. */
private fun parseTeachingMethods(table: Element): List<SyllabusMethodRow> {
    val rows = mutableListOf<SyllabusMethodRow>()
    var currentGroup: String? = null
    for (tr in table.select("tr")) {
        val cells = tr.select("> td")
        val texts = cells.map { it.text().trim() }
        if (texts.all { it.isEmpty() }) continue
        when (cells.size) {
            4 -> {
                currentGroup = texts[0]
                if (texts[2].isNotEmpty()) rows += SyllabusMethodRow(currentGroup, texts[1], texts[3])
            }
            3 -> {
                val isStandalone = cells[0].attr("colspan") == "2"
                if (isStandalone) currentGroup = null
                if (texts[1].isNotEmpty()) {
                    rows += SyllabusMethodRow(if (isStandalone) null else currentGroup, texts[0], texts[2])
                }
            }
        }
    }
    return rows
}

/** "6. 수업매체" 표 파싱 — "라벨 5칸 행" 다음에 "●/빈칸 5칸 행"이 오는 쌍이 반복되는 구조라, 쌍을
 *  이루는 두 행을 같이 보면서 "●" 표시가 있는 칸의 라벨만 골라낸다. "기타1/기타2"는 라벨+자유기입
 *  값 2칸짜리 단독 행이라 별도로 처리하고(값이 있을 때만 포함), 빈 값이면 버린다. */
private fun parseMedia(table: Element): List<String> {
    val items = mutableListOf<String>()
    val rows = table.select("tr")
    var i = 0
    while (i < rows.size) {
        val cells = rows[i].select("> td")
        if (cells.size == 2) {
            val label = cells[0].text().trim()
            val value = cells[1].text().trim()
            if (value.isNotEmpty()) items += "$label: $value"
            i += 1
        } else {
            val labels = cells.map { it.text().trim() }
            val marks = rows.getOrNull(i + 1)?.select("> td")?.map { it.text().trim() }.orEmpty()
            labels.forEachIndexed { index, label ->
                if (label.isNotEmpty() && marks.getOrNull(index) == "●") items += label
            }
            i += 2
        }
    }
    return items
}

/** "10. 주별 수업계획" 표 파싱 — 한 주차가 보통 2회차 수업이라 rowspan=2로 묶여있다. 주차 칸이 있는
 *  행(6칸)을 만나면 새 주차를 시작하고, 이어지는 칸(5칸, 주차 없음)은 같은 주차의 다음 회차로 붙인다.
 *  선행학습/교재 칸은 대부분 비어있어 여기선 주제/내용/방법만 사용한다. */
private fun parseWeeklyPlan(table: Element): List<SyllabusWeek> {
    val weeks = mutableListOf<SyllabusWeek>()
    var currentWeek: String? = null
    var currentSessions = mutableListOf<SyllabusWeekSession>()
    fun flush() {
        val week = currentWeek ?: return
        if (currentSessions.isNotEmpty()) weeks += SyllabusWeek(week, currentSessions.toList())
        currentSessions = mutableListOf()
    }
    for (tr in table.select("tr")) {
        val cells = tr.select("> td")
        val texts = cells.map { it.text().trim() }
        if (texts.all { it.isEmpty() }) continue
        if (texts[0] == "주차") continue // 헤더 행
        when (cells.size) {
            6 -> {
                flush()
                currentWeek = texts[0]
                currentSessions.add(SyllabusWeekSession(texts[1], texts[2], texts[3]))
            }
            5 -> currentSessions.add(SyllabusWeekSession(texts[0], texts[1], texts[2]))
        }
    }
    flush()
    return weeks
}

/** "9. 과제" 표 파싱 — ①~⑤ 다섯 자리가 항상 다 나오는데 실제로는 대부분 비어있는 빈 자리라, 과제명이
 *  채워진 행만 남긴다(빈칸이 "&nbsp;"라 일반 trim으로 안 걸러져서 [isBlankNbsp]를 써야 한다). */
private fun parseAssignments(table: Element): List<SyllabusAssignment> {
    val result = mutableListOf<SyllabusAssignment>()
    for (tr in table.select("tr")) {
        val cells = tr.select("> td")
        if (cells.size < 6) continue
        val texts = cells.map { it.text().trimNbsp() }
        if (texts[0] == "번호") continue // 헤더 행
        val name = texts[1]
        if (name.isBlankNbsp()) continue // 빈 자리(②~⑤ 등)
        result += SyllabusAssignment(
            name = name,
            type = texts[2],
            reference = texts[3],
            dueDate = texts[4],
            feedbackDate = texts[5]
        )
    }
    return result
}

/** "3-1) 핵심역량 및 수업목표" / "3-2) 전공역량 및 수업목표" 표 파싱 — 둘 다 [역량명, 비율, 수업목표]
 *  3칸 행이 반복되는 같은 구조라 하나로 처리한다. 3-1에만 있는 "※ 대진대학교 6대 핵심역량 정의"
 *  참고표는 과목별 데이터가 아니라 학교 전체 공통 설명이라, 그 표시가 나오면 그 뒤로는 다 무시한다. */
private fun parseCompetencies(table: Element): List<SyllabusCompetencyRow> {
    val rows = mutableListOf<SyllabusCompetencyRow>()
    for (tr in table.select("tr")) {
        val cells = tr.select("> td")
        val texts = cells.map { it.text().trim() }
        if (texts.all { it.isEmpty() }) continue
        if (texts[0].startsWith("※")) break // 여기부터는 학교 공통 참고자료라 중단
        if (texts[0] == "핵심역량" || texts[0] == "전공역량") continue // 헤더 행
        if (cells.size == 3) rows += SyllabusCompetencyRow(texts[0], texts[1], texts[2])
    }
    return rows
}

private fun Element.closest(tagName: String): Element? {
    var el: Element? = this
    while (el != null && el.tagName() != tagName) el = el.parent()
    return el
}
