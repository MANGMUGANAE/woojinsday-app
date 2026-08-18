package com.daejin.woojintoday.ui.screens.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextAlign
import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.data.model.Syllabus
import com.daejin.woojintoday.data.model.SyllabusEvaluationRow
import com.daejin.woojintoday.data.model.SyllabusField
import com.daejin.woojintoday.data.model.SyllabusAssignment
import com.daejin.woojintoday.data.model.SyllabusCompetencyRow
import com.daejin.woojintoday.data.model.SyllabusMethodRow
import com.daejin.woojintoday.data.model.SyllabusSection
import com.daejin.woojintoday.data.model.SyllabusWeek
import com.daejin.woojintoday.ui.components.ResponsiveContainer
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.ErrorRed
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary

@Composable
fun SyllabusDialog(
    course: Course,
    year: Int,
    semester: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SyllabusViewModel = viewModel(factory = SyllabusViewModel.Factory(context))

    LaunchedEffect(course.courseKey) {
        viewModel.load(year, semester, course)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Background)) {
        ResponsiveContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    IconArrowBack(tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "강의계획서",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }

            when {
                viewModel.indexBuildProgress != null -> {
                    val (done, total) = viewModel.indexBuildProgress!!
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "강의계획서 정보를 처음 준비하고 있어요 ($done/$total)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "이 학기엔 한 번만 하면 돼요, 조금만 기다려주세요!",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                viewModel.isLoadingDetail -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
                viewModel.errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = viewModel.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorRed
                        )
                    }
                }
                viewModel.syllabus != null -> {
                    SyllabusContent(syllabus = viewModel.syllabus!!)
                }
            }
        }
        }
        }
    }
}

@Composable
private fun SyllabusContent(syllabus: Syllabus) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (syllabus.fields.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .padding(16.dp)
                ) {
                    syllabus.fields.forEach { field -> SyllabusFieldRow(field) }
                }
            }
        }
        items(syllabus.sections) { section -> SyllabusSectionCard(section) }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun SyllabusFieldRow(field: SyllabusField) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = field.value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f, fill = false),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun SyllabusSectionCard(section: SyllabusSection) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Text(text = section.title, style = MaterialTheme.typography.titleSmall, color = Primary)
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            when {
                section.weeklyPlan.isNotEmpty() -> SyllabusWeeklyPlanList(weeks = section.weeklyPlan)
                section.assignments.isNotEmpty() -> SyllabusAssignmentList(assignments = section.assignments)
                section.competencies.isNotEmpty() -> SyllabusCompetencyList(rows = section.competencies)
                section.evaluationRows.isNotEmpty() -> SyllabusEvaluationTable(rows = section.evaluationRows)
                section.teachingMethods.isNotEmpty() -> SyllabusMethodList(rows = section.teachingMethods)
                section.media.isNotEmpty() ->
                    Text(text = section.media.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                section.classFormat.isNotEmpty() -> section.classFormat.forEach { field -> SyllabusFieldRow(field) }
                else -> Text(text = section.body, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            }
        }
    }
}

/** "7. 학습 평가방법" 표 전용 렌더링 — 요소/비율/세부내용을 표처럼 정렬하고, "학습태도(루브릭평가
 *  포함)"처럼 여러 행을 묶는 그룹은 소제목으로 한 번만 보여준다. */
@Composable
private fun SyllabusEvaluationTable(rows: List<SyllabusEvaluationRow>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(
                text = "평가요소",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "비율",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.End
            )
        }

        var lastGroup: String? = null
        rows.forEach { row ->
            if (row.group != null && row.group != lastGroup) {
                Text(
                    text = row.group,
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }
            lastGroup = row.group

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (row.group != null) 12.dp else 0.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = row.element, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    if (row.detail.isNotBlank()) {
                        Text(
                            text = row.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Text(
                    text = row.percent,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
            androidx.compose.material3.HorizontalDivider(color = Border)
        }
    }
}

/** "5. 수업방법" 표 전용 렌더링 — 수십 개 선택지 중 실제로 "●" 체크된 방법만 파서에서 이미 걸러져
 *  들어오므로, 그걸 그대로 방법명+설명 목록으로 보여준다. "선진교수법"처럼 묶는 그룹이 있으면
 *  소제목으로 한 번만 보여준다. */
@Composable
private fun SyllabusMethodList(rows: List<SyllabusMethodRow>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        var lastGroup: String? = null
        rows.forEachIndexed { index, row ->
            if (row.group != null && row.group != lastGroup) {
                Text(
                    text = row.group,
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp, bottom = 2.dp)
                )
            }
            lastGroup = row.group

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (row.group != null) 12.dp else 0.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text(text = row.method, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                if (row.description.isNotBlank()) {
                    Text(
                        text = row.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (index != rows.lastIndex) androidx.compose.material3.HorizontalDivider(color = Border)
        }
    }
}

/** "10. 주별 수업계획" 표 전용 렌더링 — 주차별로 소제목을 달고, 그 주에 있는 회차(보통 2개)를
 *  주제 + (내용이 주제와 다를 때만) 부가 설명 + 수업방법 태그로 보여준다. */
@Composable
private fun SyllabusWeeklyPlanList(weeks: List<SyllabusWeek>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        weeks.forEachIndexed { index, week ->
            Text(
                text = "${week.week}주차",
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
                modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp, bottom = 4.dp)
            )
            week.sessions.forEach { session ->
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.topic,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (session.method.isNotBlank()) {
                            Text(
                                text = session.method,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    if (session.content.isNotBlank() && session.content != session.topic) {
                        Text(
                            text = session.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            if (index != weeks.lastIndex) androidx.compose.material3.HorizontalDivider(color = Border)
        }
    }
}

/** "9. 과제" 표 전용 렌더링 — ①~⑤ 중 파서가 이미 빈 자리를 걸러내서 실제 과제만 넘어오므로,
 *  과제명 + (유형/참고자료/제출일자) 메타 정보 한 줄로 보여준다. */
@Composable
private fun SyllabusAssignmentList(assignments: List<SyllabusAssignment>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        assignments.forEachIndexed { index, assignment ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(text = assignment.name, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                val meta = listOfNotNull(
                    assignment.type.takeIf { it.isNotBlank() },
                    assignment.reference.takeIf { it.isNotBlank() },
                    assignment.dueDate.takeIf { it.isNotBlank() }?.let { "제출 $it" }
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (index != assignments.lastIndex) androidx.compose.material3.HorizontalDivider(color = Border)
        }
    }
}

/** "3-1)/3-2) 역량 및 수업목표" 표 전용 렌더링 — 역량명 + 비율을 한 줄에, 수업목표를 그 아래 줄에
 *  보여준다. */
@Composable
private fun SyllabusCompetencyList(rows: List<SyllabusCompetencyRow>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (row.percent.isNotBlank()) {
                        Text(
                            text = row.percent,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                if (row.goal.isNotBlank()) {
                    Text(
                        text = row.goal,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (index != rows.lastIndex) androidx.compose.material3.HorizontalDivider(color = Border)
        }
    }
}
