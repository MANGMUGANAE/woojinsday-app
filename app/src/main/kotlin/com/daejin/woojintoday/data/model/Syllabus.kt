package com.daejin.woojintoday.data.model

/** 강의계획서 상단 정보 한 줄(예: "담당교수" → "주신윤") — 순서가 있어야 해서 Map 대신 Pair 리스트로 둔다. */
data class SyllabusField(val label: String, val value: String)

/** "7. 학습 평가방법" 표의 한 행 — [group]은 "학습태도(루브릭평가 포함)"처럼 여러 행을 묶는 상위
 *  분류가 있을 때만 값이 있고(rowspan), 없으면 null(출석/중간시험처럼 단독 행). */
data class SyllabusEvaluationRow(
    val group: String?,
    val element: String,
    val percent: String,
    val detail: String
)

/** "5. 수업방법" 표에서 실제로 "●"로 체크된 방법만 뽑은 한 행. [group]은 "선진교수법"처럼 여러 세부
 *  방법(PBL/PJBL/FL 등)을 묶는 상위 분류가 있을 때만 값이 있고(rowspan), 없으면 null. */
data class SyllabusMethodRow(val group: String?, val method: String, val description: String)

/** "10. 주별 수업계획" 표의 한 주차 — 대부분 한 주에 2회차(rowspan) 수업이 들어있어 [sessions]가
 *  리스트다. */
data class SyllabusWeekSession(val topic: String, val content: String, val method: String)
data class SyllabusWeek(val week: String, val sessions: List<SyllabusWeekSession>)

/** "9. 과제" 표에서 실제로 이름이 채워진 과제 한 건(①~⑤ 중 빈 자리는 걸러내고 남은 것만). */
data class SyllabusAssignment(
    val name: String,
    val type: String,
    val reference: String,
    val dueDate: String,
    val feedbackDate: String
)

/** "3-1) 핵심역량 및 수업목표" / "3-2) 전공역량 및 수업목표" 표의 한 행 — 두 절이 같은 표 구조라
 *  공유한다. 3-2는 없는 과목도 있어서 이 필드가 비어있으면 그냥 그 절 자체가 없는 것. */
data class SyllabusCompetencyRow(val name: String, val percent: String, val goal: String)

/** 강의계획서 번호가 매겨진 절 하나(예: "1. 수업의 개요와 유용성" → 본문). [evaluationRows]는
 *  "7. 학습 평가방법", [classFormat]은 "4. 수업형태", [teachingMethods]는 "5. 수업방법", [media]는
 *  "6. 수업매체", [assignments]는 "9. 과제", [weeklyPlan]은 "10. 주별 수업계획", [competencies]는
 *  "3-1)"/"3-2)" 절에서만 채워지고, 그 외 절은 전부 빈 리스트로 남아 본문은 body 텍스트만 사용한다. */
data class SyllabusSection(
    val title: String,
    val body: String,
    val evaluationRows: List<SyllabusEvaluationRow> = emptyList(),
    val classFormat: List<SyllabusField> = emptyList(),
    val teachingMethods: List<SyllabusMethodRow> = emptyList(),
    val media: List<String> = emptyList(),
    val weeklyPlan: List<SyllabusWeek> = emptyList(),
    val assignments: List<SyllabusAssignment> = emptyList(),
    val competencies: List<SyllabusCompetencyRow> = emptyList()
)

data class Syllabus(
    val fields: List<SyllabusField>,
    val sections: List<SyllabusSection>
)
