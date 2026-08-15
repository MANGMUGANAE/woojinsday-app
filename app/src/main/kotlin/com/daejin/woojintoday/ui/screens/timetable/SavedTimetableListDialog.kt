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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.data.model.SavedTimetable
import com.daejin.woojintoday.ui.icons.IconChevronDown
import com.daejin.woojintoday.ui.icons.IconClose
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.ErrorRed
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.SurfaceElevated
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedTimetableListDialog(
    timetables: List<SavedTimetable>,
    onDeleteTimetable: (String) -> Unit,
    onDeleteCourse: (String, String) -> Unit,
    onViewAsTimetable: (SavedTimetable) -> Unit,
    onDismiss: () -> Unit
) {
    var localTimetables by remember { mutableStateOf(timetables) }
    var expandedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("저장한 시간표", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                IconButton(onClick = onDismiss) {
                    IconClose(tint = TextPrimary, size = 18.dp)
                }
            }

            if (localTimetables.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("저장된 시간표가 없습니다.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(localTimetables, key = { it.id }) { timetable ->
                        SavedTimetableCard(
                            timetable = timetable,
                            expanded = timetable.id in expandedIds,
                            onToggleExpand = {
                                expandedIds = if (timetable.id in expandedIds) {
                                    expandedIds - timetable.id
                                } else {
                                    expandedIds + timetable.id
                                }
                            },
                            onViewAsTimetable = {
                                onViewAsTimetable(timetable)
                                onDismiss()
                            },
                            onDeleteTimetable = {
                                onDeleteTimetable(timetable.id)
                                localTimetables = localTimetables.filterNot { it.id == timetable.id }
                            },
                            onDeleteCourse = { courseKey ->
                                onDeleteCourse(timetable.id, courseKey)
                                localTimetables = localTimetables.map {
                                    if (it.id == timetable.id) {
                                        it.copy(courses = it.courses.filterNot { c -> c.courseKey == courseKey })
                                    } else {
                                        it
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedTimetableCard(
    timetable: SavedTimetable,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onViewAsTimetable: () -> Unit,
    onDeleteTimetable: () -> Unit,
    onDeleteCourse: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(vertical = 16.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeleteButton(onClick = onDeleteTimetable)
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = timetable.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${timetable.year}년 ${timetable.semester}학기 · ${timetable.courses.size}개 과목 · ${formatDate(timetable.savedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            ViewAsTimetableButton(onClick = onViewAsTimetable)
            Spacer(modifier = Modifier.width(8.dp))
            IconChevronDown(tint = TextSecondary)
        }

        if (expanded) {
            HorizontalDivider(color = Border, thickness = 1.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timetable.courses.forEach { course ->
                    SavedCourseRow(
                        course = course,
                        onDelete = { onDeleteCourse(course.courseKey) }
                    )
                }
                if (timetable.courses.isEmpty()) {
                    Text(
                        text = "이 시간표에 남은 과목이 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

/** Rendered indented under its parent card with a distinct background — visually a sub-item. */
@Composable
private fun SavedCourseRow(course: Course, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeleteButton(onClick = onDelete)
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${course.professor} · ${course.credit}학점 · ${course.category}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${course.timeText.ifBlank { "시간 미정" }} · ${course.room}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (course.note.isNotBlank()) {
                Text(
                    text = course.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        IconClose(tint = ErrorRed, size = 14.dp)
    }
}

@Composable
private fun ViewAsTimetableButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text("시간표로 보기", style = MaterialTheme.typography.bodySmall, color = OnPrimary)
    }
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(epochMillis))
