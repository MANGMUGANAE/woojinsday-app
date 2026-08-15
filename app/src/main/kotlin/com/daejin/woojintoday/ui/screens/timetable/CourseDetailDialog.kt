package com.daejin.woojintoday.ui.screens.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.daejin.woojintoday.data.SyllabusIndexStore
import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary

@Composable
fun CourseDetailDialog(
    course: Course,
    year: Int,
    semester: Int,
    onDismiss: () -> Unit,
    onShowSyllabus: () -> Unit
) {
    // 강의계획서 인덱스를 한 번이라도 만들어둔 적이 있으면(전공 조회에서 나온 과목이면) 그때 같이
    // 알아낸 학과명을 재사용한다 — 못 찾으면(아직 인덱스가 없거나 전공 과목이 아니면) 그냥 안 보여준다.
    val context = LocalContext.current
    val departmentName = remember(course.courseKey, year, semester) {
        SyllabusIndexStore(context).getDepartments(year, semester)?.get(course.courseKey)
    }
    // 교선 영역도 같은 방식으로(별도로 구축해둔 가벼운 인덱스에서) 재사용한다 — 교선이 아닌 과목이거나
    // 아직 인덱스가 없으면 null이라 그냥 이수구분을 그대로 보여준다.
    val genEdArea = remember(course.courseKey, year, semester) {
        SyllabusIndexStore(context).getGenEdAreas(year, semester)?.get(course.courseKey)
    }
    val categoryText = if (course.category == "교선" && genEdArea != null) "교선($genEdArea)" else course.category

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(20.dp)
        ) {
            Text(text = course.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${course.code}-${course.section}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            DetailRow("교수", course.professor)
            DetailRow("이수구분", categoryText)
            if (departmentName != null) DetailRow("학과", departmentName)
            DetailRow("학점", course.credit.toString())
            DetailRow("학년", course.grade)
            DetailRow("시간", course.timeText.ifBlank { "시간 미정" })
            DetailRow("강의실", course.room)
            DetailRow("담은인원", "${course.waitlistApplicants}/${course.capacity}명")
            if (course.note.isNotBlank()) DetailRow("비고", course.note)

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Text("닫기", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onShowSyllabus,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("강의계획서 보기", style = MaterialTheme.typography.labelLarge, color = Primary)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
