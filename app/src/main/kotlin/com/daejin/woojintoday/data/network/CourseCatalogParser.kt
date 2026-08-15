package com.daejin.woojintoday.data.network

import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.data.model.parseClassSessions
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parses the legacy JSP course-catalog table (id="tableData") into [Course]s.
 * Data rows are marked with class tr_a0_chrm/tr_a1_chrm (alternating stripe), which
 * conveniently also excludes the tr_chrm_n header row without needing to skip by index.
 */
fun parseCourseCatalog(html: String): List<Course> {
    val document = Jsoup.parse(html)
    return document.select("tr[class^=tr_a]").mapNotNull(::parseCourseRow)
}

private fun parseCourseRow(row: Element): Course? {
    val cells = row.select("> td")
    if (cells.size < 14) return null

    val seq = cells[0].text().trim().toIntOrNull() ?: return null
    val codeSection = cells[1].text().trim()
    val separatorIndex = codeSection.lastIndexOf('-')
    val code = if (separatorIndex > 0) codeSection.substring(0, separatorIndex) else codeSection
    val section = if (separatorIndex > 0) codeSection.substring(separatorIndex + 1) else ""
    val timeText = cells[7].text().trim()

    return Course(
        seq = seq,
        code = code,
        section = section,
        name = cells[2].text().trim(),
        category = cells[3].text().trim(),
        credit = cells[4].text().trim().toDoubleOrNull() ?: 0.0,
        grade = cells[5].text().trim(),
        professor = cells[6].text().trim(),
        timeText = timeText,
        sessions = parseClassSessions(timeText),
        room = cells[8].text().trim(),
        isCancelled = cells[9].text().trim().isNotEmpty(),
        capacity = cells[10].text().trim().toIntOrNull() ?: 0,
        waitlistApplicants = cells[11].text().trim().toIntOrNull() ?: 0,
        ratio = cells[12].text().trim(),
        note = cells[13].text().trim()
    )
}
