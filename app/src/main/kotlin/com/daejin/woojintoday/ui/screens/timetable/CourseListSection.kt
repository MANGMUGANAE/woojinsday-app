package com.daejin.woojintoday.ui.screens.timetable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.data.model.CourseFilters
import com.daejin.woojintoday.data.model.Department
import com.daejin.woojintoday.data.model.TimeRangeFilter
import com.daejin.woojintoday.data.model.Weekday
import com.daejin.woojintoday.ui.icons.IconCheck
import com.daejin.woojintoday.ui.icons.IconFilter
import com.daejin.woojintoday.ui.icons.IconSearch
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.BorderFocused
import com.daejin.woojintoday.ui.theme.ErrorRed
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextDisabled
import com.daejin.woojintoday.ui.theme.TextPlaceholder
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary
import androidx.compose.ui.unit.Dp

@Composable
fun CourseListSection(
    query: String,
    onQueryChange: (String) -> Unit,
    courses: List<Course>,
    selectedKeys: Set<String>,
    colorFor: (Course) -> Color,
    onTapCourse: (Course) -> Unit,
    onLongPressCourse: (Course) -> Unit,
    onAddToTimetable: (Course) -> Unit,
    previewCourseKey: String?,
    filters: CourseFilters,
    onDepartmentSelected: (Department?) -> Unit,
    onToggleDay: (Weekday) -> Unit,
    onTimeRangeChange: (TimeRangeFilter?) -> Unit,
    onRemoveRegionAt: (Int) -> Unit,
    onToggleGrade: (String) -> Unit,
    onToggleCategory: (String) -> Unit,
    onToggleGenEdArea: (String) -> Unit,
    onToggleCredit: (String) -> Unit,
    genEdAreaFor: (Course) -> String? = { null },
    extraBottomPadding: Dp = 700.dp,
    modifier: Modifier = Modifier
) {
    var showFilterPanel by remember { mutableStateOf(false) }
    var showDepartmentPicker by remember { mutableStateOf(false) }

    if (showDepartmentPicker) {
        DepartmentPickerDialog(
            currentCode = filters.department?.code,
            onSelect = { department ->
                onDepartmentSelected(department)
                showDepartmentPicker = false
            },
            onDismiss = { showDepartmentPicker = false }
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("과목코드, 교수명, 과목명 검색", color = TextPlaceholder, style = MaterialTheme.typography.bodySmall)
                },
                leadingIcon = { IconSearch(tint = TextSecondary) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    disabledContainerColor = Surface,
                    focusedBorderColor = BorderFocused,
                    unfocusedBorderColor = Border,
                    disabledBorderColor = Border,
                    cursorColor = Primary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedPlaceholderColor = TextPlaceholder,
                    unfocusedPlaceholderColor = TextPlaceholder,
                    disabledTextColor = TextDisabled
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (showFilterPanel) Primary else Surface)
                    .clickable { showFilterPanel = !showFilterPanel },
                contentAlignment = Alignment.Center
            ) {
                IconFilter(tint = if (showFilterPanel) OnPrimary else TextSecondary)
            }
        }

        ActiveFilterChips(
            filters = filters,
            onRemoveDepartment = { onDepartmentSelected(null) },
            onRemoveRegion = onRemoveRegionAt,
            onRemoveGrade = onToggleGrade,
            onRemoveCategory = onToggleCategory,
            onRemoveGenEdArea = onToggleGenEdArea,
            onRemoveCredit = { filters.credits.forEach(onToggleCredit) },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (showFilterPanel) {
            CourseFilterPanel(
                filters = filters,
                onDepartmentClick = { showDepartmentPicker = true },
                onToggleDay = onToggleDay,
                onTimeRangeChange = onTimeRangeChange,
                onToggleGrade = onToggleGrade,
                onToggleCategory = onToggleCategory,
                onToggleGenEdArea = onToggleGenEdArea,
                onToggleCredit = onToggleCredit,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyColumn(
            // fillMaxSize()는 검색창/필터칩/필터패널이 이미 차지한 공간을 빼지 않고 Column에 허용된
            // 전체 높이를 그대로 요구해서, 시트가 절반만 올라와 있을 때 리스트 뷰포트가 실제 보이는
            // 영역보다 커진 채로 "다 들어찼다"고 착각해 스크롤이 일찍 멈췄다 — weight(1f)로 검색창 등을
            // 뺀 "진짜 남은 공간"만 차지하도록 고친다.
            modifier = Modifier.fillMaxWidth().weight(1f),
            // 마지막 항목이 기기 하단 내비게이션 바에 가려지지 않도록 아래쪽에 여유를 넉넉히 둔다.
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = extraBottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(courses, key = { it.courseKey }) { course ->
                CourseRow(
                    course = course,
                    isSelected = course.courseKey in selectedKeys,
                    isPreview = course.courseKey == previewCourseKey,
                    color = colorFor(course),
                    genEdArea = genEdAreaFor(course),
                    onClick = { onTapCourse(course) },
                    onLongClick = { onLongPressCourse(course) },
                    onAddToTimetable = { onAddToTimetable(course) },
                    onShowDetail = { onLongPressCourse(course) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CourseRow(
    course: Course,
    isSelected: Boolean,
    isPreview: Boolean,
    color: Color,
    genEdArea: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAddToTimetable: () -> Unit,
    onShowDetail: () -> Unit
) {
    val categoryText = if (course.category == "교선" && genEdArea != null) "교선($genEdArea)" else course.category
    val filledRatio = if (course.capacity > 0) course.waitlistApplicants.toDouble() / course.capacity else 0.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) color else Border)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "담은인원 ${course.waitlistApplicants}/${course.capacity}명",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                    Text(
                        text = "경쟁률 %.1f:1".format(filledRatio),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${course.professor} · ${course.credit}학점 · $categoryText",
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
            // 미리보기 중일 때만 뜨는 액션 버튼 — 다시 탭하면(row의 onClick) 버블처럼 접히면서 같이 사라진다.
            AnimatedVisibility(
                visible = isPreview,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "시간표에 추가",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Primary)
                            .clickable(onClick = onAddToTimetable)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                    Text(
                        text = "상세보기",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Border, RoundedCornerShape(8.dp))
                            .clickable(onClick = onShowDetail)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isSelected) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Surface)
                    .padding(6.dp)
            ) {
                IconCheck(tint = Primary, size = 14.dp)
            }
        }
    }
}
