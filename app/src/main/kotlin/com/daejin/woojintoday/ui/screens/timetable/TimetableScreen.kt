package com.daejin.woojintoday.ui.screens.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daejin.woojintoday.data.model.AcademicPeriod
import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.ui.components.ToastHost
import com.daejin.woojintoday.ui.components.WheelPicker
import com.daejin.woojintoday.ui.components.rememberToastState
import com.daejin.woojintoday.ui.components.wheelRange
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.icons.IconList
import com.daejin.woojintoday.ui.icons.IconSparkle
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.ErrorRed
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextDisabled
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary
import com.daejin.woojintoday.ui.theme.TimetablePalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: TimetableViewModel = viewModel(factory = TimetableViewModel.Factory(context))
    val toastState = rememberToastState()
    var detailCourse by remember { mutableStateOf<Course?>(null) }
    var syllabusCourse by remember { mutableStateOf<Course?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showListDialog by remember { mutableStateOf(false) }
    var showLoadTimetableConfirm by remember { mutableStateOf(false) }
    var showNotificationMinutesDialog by remember { mutableStateOf(false) }
    var showAiTimetableDialog by remember { mutableStateOf(false) }
    var isPreparingAiTimetable by remember { mutableStateOf(false) }
    // AI 아이콘을 누른 시점에 이수구분표를 먼저 조회해 이미 들은 과목명을 뽑아둔다(생성 시 제외용).
    var aiExcludedCourseNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 과목코드-분반 → 학과명 — 캡스톤디자인 자기 학과 판별, 전공과목 중복 이수 판별(학과+이름 일치)에 쓰인다.
    var aiCourseDepartments by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val coroutineScope = rememberCoroutineScope()
    // "AI로 시간표 짜기"는 가장 최신(현재) 학기에서만 지원 — 지난 학기는 이미 수강신청이 끝나
    // 조합을 새로 짤 이유가 없으니 비활성화한다.
    val (currentYear, currentSemester) = remember { AcademicPeriod.current() }
    val isCurrentPeriod = viewModel.year == currentYear && viewModel.semester == currentSemester

    val selectedCourses = viewModel.selectedCourses
    // 색상은 담긴 "순서"(selectedCourseKeys, 담을 때마다 뒤에 추가됨)로 고정한다 — 카탈로그 목록 순서로
    // 색을 뽑으면 다른 과목을 추가할 때 카탈로그상 앞쪽 과목이 끼어들며 기존에 담긴 과목들의 색이 바뀌었다.
    val selectedKeysOrdered = viewModel.selectedCourseKeys.toList()
    val colorFor: (Course) -> androidx.compose.ui.graphics.Color = { course ->
        val index = selectedKeysOrdered.indexOf(course.courseKey)
        TimetablePalette[if (index >= 0) index % TimetablePalette.size else 0]
    }
    val previewCourse = viewModel.previewCourse
    // 미리보기 중인 과목은 확정되는 순간 selectedCourseKeys 맨 뒤에 추가되므로, 받을 색도 같은 규칙으로 미리 계산한다.
    val previewColor: androidx.compose.ui.graphics.Color = TimetablePalette[selectedKeysOrdered.size % TimetablePalette.size]
    val totalCreditsText = creditText(viewModel.totalCredits)

    LaunchedEffect(viewModel.conflictMessage) {
        val message = viewModel.conflictMessage ?: return@LaunchedEffect
        toastState.show(message)
        viewModel.clearConflictMessage()
    }

    detailCourse?.let { course ->
        CourseDetailDialog(
            course = course,
            year = viewModel.year,
            semester = viewModel.semester,
            onDismiss = { detailCourse = null },
            onShowSyllabus = { syllabusCourse = course }
        )
    }

    syllabusCourse?.let { course ->
        SyllabusDialog(
            course = course,
            year = viewModel.year,
            semester = viewModel.semester,
            onDismiss = { syllabusCourse = null }
        )
    }

    if (showLoadTimetableConfirm) {
        LoadMyTimetableConfirmDialog(
            onConfirm = {
                showLoadTimetableConfirm = false
                viewModel.loadMyRegisteredTimetable()
            },
            onDismiss = { showLoadTimetableConfirm = false }
        )
    }

    if (showNotificationMinutesDialog) {
        NotificationMinutesDialog(
            initialMinutes = viewModel.notificationMinutesBefore,
            onConfirm = { minutes ->
                showNotificationMinutesDialog = false
                viewModel.applyNotificationSettings(minutes)
                requestExactAlarmPermissionIfNeeded(context)
                toastState.show(viewModel.firstAlarmTimeText ?: "예약된 수업이 없어서 알람을 걸 수 없어요")
            },
            onDismiss = { showNotificationMinutesDialog = false }
        )
    }

    if (showSaveDialog) {
        SaveTimetableDialog(
            onConfirm = { name ->
                viewModel.saveCurrentTimetable(name)
                showSaveDialog = false
                toastState.show("\"$name\" 시간표를 저장했습니다.")
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showListDialog) {
        SavedTimetableListDialog(
            timetables = viewModel.savedTimetables(),
            onDeleteTimetable = viewModel::deleteSavedTimetable,
            onDeleteCourse = viewModel::removeSavedCourse,
            onViewAsTimetable = { timetable ->
                viewModel.viewSavedTimetable(timetable)
                toastState.show("\"${timetable.name}\" 시간표를 불러왔습니다.")
            },
            onDismiss = { showListDialog = false }
        )
    }

    if (showAiTimetableDialog) {
        AiTimetableDialog(
            courses = viewModel.courses,
            year = viewModel.year,
            semester = viewModel.semester,
            excludedCourseNames = aiExcludedCourseNames,
            studentGrade = viewModel.studentProfile()?.grade,
            studentDepartment = viewModel.studentProfile()?.department,
            courseDepartments = aiCourseDepartments,
            onDismiss = { showAiTimetableDialog = false },
            onApply = { generated ->
                viewModel.applyGeneratedTimetable(generated)
                toastState.show("AI가 짜준 시간표를 적용했어요!")
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val peekHeight = maxHeight / 3

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        IconArrowBack(tint = TextPrimary)
                    }
                    PeriodFilter(
                        year = viewModel.year,
                        semester = viewModel.semester,
                        onPeriodSelected = viewModel::setPeriod
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = totalCreditsText,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                        if (previewCourse != null) {
                            Text(
                                text = " + ${creditText(previewCourse.credit)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = previewColor
                            )
                        }
                        Text(
                            text = "학점",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "저장",
                        style = MaterialTheme.typography.labelLarge,
                        color = Primary,
                        modifier = Modifier
                            .clickable { showSaveDialog = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                    Box {
                        IconButton(onClick = { showListDialog = true }) {
                            IconList(tint = TextPrimary)
                        }
                        val savedCount = viewModel.savedTimetables().size
                        if (savedCount > 0) {
                            Text(
                                text = savedCount.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 6.dp)
                            )
                        }
                    }
                }
            }
            val canLoadMyTimetable = !viewModel.isLoading && !viewModel.isLoadingMyTimetable
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 0.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "내 시간표 불러오기",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (canLoadMyTimetable) OnPrimary else TextDisabled,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(50))
                        .background(if (canLoadMyTimetable) Primary else Background)
                        .clickable(enabled = canLoadMyTimetable) {
                            if (viewModel.selectedCourseKeys.isNotEmpty()) {
                                showLoadTimetableConfirm = true
                            } else {
                                viewModel.loadMyRegisteredTimetable()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "강의시간 알림",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = viewModel.notificationsEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showNotificationMinutesDialog = true
                            } else {
                                viewModel.disableNotifications()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnPrimary,
                            checkedTrackColor = Primary,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = Background,
                            uncheckedBorderColor = Border
                        )
                    )
                }
            }

            val (timedCourses, onlineCourses) = selectedCourses.partition { it.sessions.isNotEmpty() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                WeeklyTimetable(
                    courses = timedCourses,
                    colorFor = colorFor,
                    onCourseClick = { course -> detailCourse = course },
                    onCourseRemove = viewModel::removeCourse,
                    onGridTap = viewModel::cancelPreview,
                    previewCourse = previewCourse?.takeIf { it.sessions.isNotEmpty() },
                    previewColor = previewColor,
                    regions = viewModel.filters.regions,
                    onRegionUpdate = viewModel::setRegionAt,
                    onRegionCreate = viewModel::appendRegion,
                    onRegionFinalize = viewModel::finalizeRegions,
                    showCurrentTimeIndicator = viewModel.notificationsEnabled,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 16.dp)
                        .alpha(if (!isCurrentPeriod) 0.4f else if (viewModel.isLoading || isPreparingAiTimetable) 0.5f else 1f)
                        .clickable(enabled = isCurrentPeriod && !viewModel.isLoading && !isPreparingAiTimetable) {
                            coroutineScope.launch {
                                isPreparingAiTimetable = true
                                aiExcludedCourseNames = viewModel.fetchCompletedCourseNames()
                                aiCourseDepartments = viewModel.ensureCourseDepartments()
                                isPreparingAiTimetable = false
                                showAiTimetableDialog = true
                            }
                        }
                        .padding(4.dp)
                ) {
                    IconSparkle(size = 26.dp)
                }
            }
            if (onlineCourses.isNotEmpty()) {
                OnlineCourseStack(
                    courses = onlineCourses,
                    colorFor = colorFor,
                    onClick = { course -> detailCourse = course },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(peekHeight))
        }

        DraggableCourseSheet(
            containerHeightPx = containerHeightPx,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                viewModel.isLoading -> {
                    CourseListSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                viewModel.errorMessage != null && viewModel.courses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = viewModel.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed
                        )
                    }
                }
                else -> {
                    CourseListSection(
                        query = viewModel.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        courses = viewModel.filteredCourses,
                        selectedKeys = viewModel.selectedCourseKeys,
                        colorFor = colorFor,
                        onTapCourse = viewModel::onCourseRowTap,
                        onLongPressCourse = { course -> detailCourse = course },
                        onAddToTimetable = viewModel::addPreviewedCourse,
                        previewCourseKey = viewModel.previewCourseKey,
                        filters = viewModel.filters,
                        onDepartmentSelected = viewModel::setDepartmentFilter,
                        onToggleDay = viewModel::toggleDayFilter,
                        onTimeRangeChange = viewModel::setTimeRangeFilter,
                        onRemoveRegionAt = viewModel::removeRegionAt,
                        onToggleGrade = viewModel::toggleGradeFilter,
                        onToggleCategory = viewModel::toggleCategoryFilter,
                        onToggleGenEdArea = viewModel::toggleGenEdAreaFilter,
                        onToggleCredit = viewModel::toggleCreditFilter,
                        genEdAreaFor = { course -> viewModel.genEdAreaByCourse[course.courseKey] },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }

        ToastHost(
            state = toastState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

private fun creditText(credit: Double): String =
    if (credit == credit.toInt().toDouble()) "${credit.toInt()}" else "$credit"

/** Android 12+에서는 "정확한 알람" 권한이 기본 거부라, POST_NOTIFICATIONS와 달리 런타임 다이얼로그가
 *  없고 시스템 설정 화면으로 보내는 것만 가능하다 — 이게 없으면 강의시간 알림이 setExactAndAllowWhileIdle
 *  대신 부정확한 예약으로 조용히 대체돼서(LectureAlarmScheduler.arm 참고), 절전모드에서 몇 분~몇 시간씩
 *  밀리거나 사실상 안 오는 것처럼 보인다. "강의시간 알림"을 켤 때 이미 허용돼있지 않으면 바로 그 설정
 *  화면으로 보낸다. */
private fun requestExactAlarmPermissionIfNeeded(context: android.content.Context) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
    val alarmManager = context.getSystemService(android.app.AlarmManager::class.java) ?: return
    if (alarmManager.canScheduleExactAlarms()) return
    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = android.net.Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

@Composable
private fun NotificationMinutesDialog(initialMinutes: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember { mutableStateOf(initialMinutes) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(20.dp)
        ) {
            Text(text = "강의시간 알림", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "수업 시작 몇 분 전에 알려드릴까요?",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            WheelPicker(
                values = wheelRange(1, 60),
                selected = minutes,
                onSelectedChange = { minutes = it },
                modifier = Modifier.fillMaxWidth(),
                format = { "${it}분 전" }
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Text("취소")
                }
                Button(
                    onClick = { onConfirm(minutes) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                ) {
                    Text("확인")
                }
            }
        }
    }
}

@Composable
private fun LoadMyTimetableConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(20.dp)
        ) {
            Text(text = "현재 시간표가 초기화됩니다!", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Text("취소")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                ) {
                    Text("확인")
                }
            }
        }
    }
}
