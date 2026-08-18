package com.daejin.woojintoday.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daejin.woojintoday.data.model.AcademicEvent
import com.daejin.woojintoday.data.model.UserCalendarEvent
import com.daejin.woojintoday.ui.components.ResponsiveContainer
import com.daejin.woojintoday.ui.components.WheelPicker
import com.daejin.woojintoday.ui.components.wheelRange
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.icons.IconCheck
import com.daejin.woojintoday.ui.icons.IconChevronLeft
import com.daejin.woojintoday.ui.icons.IconChevronRight
import com.daejin.woojintoday.ui.icons.IconPlus
import com.daejin.woojintoday.ui.icons.IconSearch
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.ErrorRed
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextDisabled
import com.daejin.woojintoday.ui.theme.TextPlaceholder
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary
import com.daejin.woojintoday.ui.theme.TimetablePalette
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_TITLE_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
private val DAY_DETAIL_FORMATTER = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val WEEKDAY_LABELS = listOf("일", "월", "화", "수", "목", "금", "토")

private fun colorForEventId(id: String): Color =
    TimetablePalette[(id.hashCode() and 0x7FFFFFFF) % TimetablePalette.size]

@Composable
fun AcademicCalendarDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val viewModel: AcademicCalendarViewModel = viewModel(factory = AcademicCalendarViewModel.Factory(context))
    val today = remember { LocalDate.now() }
    var isExpanded by remember { mutableStateOf(false) }

    var timeEditTarget by remember { mutableStateOf<TimeEditTarget?>(null) }
    var spanDetail by remember { mutableStateOf<EventSpan?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var addBarMode by remember { mutableStateOf(AddBarMode.COLLAPSED) }
    var addBarTitle by remember { mutableStateOf("") }
    var addBarHour by remember { mutableIntStateOf(8) }
    var addBarMinute by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun resetAddBar() {
        addBarMode = AddBarMode.COLLAPSED
        addBarTitle = ""
        addBarHour = 8
        addBarMinute = 0
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    androidx.compose.runtime.LaunchedEffect(viewModel.selectedDate) { resetAddBar() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Background).imePadding()) {
        ResponsiveContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    IconArrowBack(tint = TextPrimary)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .then(
                        if (addBarMode != AddBarMode.COLLAPSED) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures { resetAddBar() }
                            }
                        } else Modifier
                    ),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    val searchResults = remember(searchQuery, viewModel.academicEvents, viewModel.userEvents) {
                        if (searchQuery.isBlank()) {
                            emptyList()
                        } else {
                            val academicMatches = viewModel.academicEvents
                                .filter { it.description.contains(searchQuery, ignoreCase = true) }
                                .map { CalendarSearchResult(it.description, it.beginDate) }
                            val userMatches = viewModel.userEvents
                                .filter { it.title.contains(searchQuery, ignoreCase = true) }
                                .map { CalendarSearchResult(it.title, it.date) }
                            (academicMatches + userMatches).sortedBy { it.date }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .border(1.dp, Border, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconSearch(tint = TextSecondary, size = 16.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "일정 이름으로 검색",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPlaceholder
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                                singleLine = true,
                                cursorBrush = SolidColor(Primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                            )
                        }
                    }

                    if (searchResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Surface)
                        ) {
                            searchResults.forEachIndexed { index, result ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.goToDate(result.date)
                                            searchQuery = ""
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(text = result.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = result.date.format(DAY_DETAIL_FORMATTER),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                if (index != searchResults.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Border)
                                    )
                                }
                            }
                        }
                    } else if (searchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "검색 결과가 없어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    MonthNavRow(
                        yearMonth = viewModel.yearMonth,
                        onPrevious = viewModel::goToPreviousMonth,
                        onNext = viewModel::goToNextMonth
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                var totalDx = 0f
                                var totalDy = 0f
                                detectDragGestures(
                                    onDragStart = { totalDx = 0f; totalDy = 0f },
                                    onDragEnd = {
                                        if (kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy) && kotlin.math.abs(totalDx) > 80f) {
                                            if (totalDx < 0) viewModel.goToNextMonth() else viewModel.goToPreviousMonth()
                                        } else if (kotlin.math.abs(totalDy) > 60f) {
                                            isExpanded = totalDy > 0
                                        }
                                    },
                                    onDragCancel = { totalDx = 0f; totalDy = 0f }
                                ) { change, dragAmount ->
                                    change.consume()
                                    totalDx += dragAmount.x
                                    totalDy += dragAmount.y
                                }
                            }
                    ) {
                        AnimatedContent(targetState = isExpanded, label = "calendarExpand") { expanded ->
                            if (expanded) {
                                ExpandedMonthGrid(
                                    yearMonth = viewModel.yearMonth,
                                    selectedDate = viewModel.selectedDate,
                                    today = today,
                                    academicEvents = viewModel.academicEvents,
                                    userEvents = viewModel.userEvents,
                                    onSelectDate = viewModel::selectDate,
                                    onSpanClick = { span -> spanDetail = span }
                                )
                            } else {
                                MonthGrid(
                                    yearMonth = viewModel.yearMonth,
                                    selectedDate = viewModel.selectedDate,
                                    today = today,
                                    academicEvents = viewModel.academicEvents,
                                    userEvents = viewModel.userEvents,
                                    onSelectDate = viewModel::selectDate
                                )
                            }
                        }
                        DragHandle()
                    }

                    if (viewModel.isLoading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary, modifier = Modifier.size(20.dp))
                        }
                    } else if (viewModel.errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = viewModel.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    AnimatedVisibility(visible = !isExpanded, enter = fadeIn(), exit = fadeOut()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            val (academic, personal) = viewModel.eventsOn(viewModel.selectedDate)
                            DayDetailSection(
                                date = viewModel.selectedDate,
                                academicEvents = academic,
                                userEvents = personal,
                                isAcademicEnabled = viewModel::isAcademicEnabled,
                                academicTimeFor = viewModel::academicTimeFor,
                                onAcademicRowClick = { event -> timeEditTarget = TimeEditTarget.Academic(event) },
                                onAcademicToggleChange = { event, checked ->
                                    if (checked) timeEditTarget = TimeEditTarget.Academic(event)
                                    else viewModel.toggleAcademic(context, event)
                                },
                                onUserRowClick = { event -> timeEditTarget = TimeEditTarget.User(event) },
                                onUserToggleChange = { event, checked ->
                                    if (checked) timeEditTarget = TimeEditTarget.User(event)
                                    else viewModel.toggleUserEventNotification(context, event)
                                },
                                onDeleteUser = { viewModel.deleteUserEvent(context, it) }
                            )
                        }
                    }
                }
            }
        }

        if (addBarMode != AddBarMode.COLLAPSED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { resetAddBar() } }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        ) {
            QuickAddBar(
                date = viewModel.selectedDate,
                mode = addBarMode,
                title = addBarTitle,
                hour = addBarHour,
                minute = addBarMinute,
                onModeChange = { addBarMode = it },
                onTitleChange = { addBarTitle = it },
                onHourChange = { addBarHour = it },
                onMinuteChange = { addBarMinute = it },
                onQuickAdd = { title -> viewModel.addUserEvent(context, viewModel.selectedDate, title) },
                onFullAdd = { title, hour, minute ->
                    viewModel.addUserEvent(context, viewModel.selectedDate, title, hour, minute)
                },
                onCommitted = { resetAddBar() }
            )
        }
        }
        }

        val target = timeEditTarget
        if (target != null) {
            val (title, initialHour, initialMinute) = when (target) {
                is TimeEditTarget.Academic -> {
                    val (h, m) = viewModel.academicTimeFor(target.event)
                    Triple(target.event.description, h, m)
                }
                is TimeEditTarget.User -> Triple(target.event.title, target.event.notifyHour, target.event.notifyMinute)
            }
            EventTimeEditorDialog(
                title = title,
                initialHour = initialHour,
                initialMinute = initialMinute,
                onConfirm = { hour, minute ->
                    when (target) {
                        is TimeEditTarget.Academic -> viewModel.setAcademicEventTime(context, target.event, hour, minute)
                        is TimeEditTarget.User -> viewModel.setUserEventTime(context, target.event, hour, minute)
                    }
                    timeEditTarget = null
                },
                onDismiss = { timeEditTarget = null }
            )
        }

        val detail = spanDetail
        if (detail != null) {
            EventSpanDetailDialog(detail = detail, onDismiss = { spanDetail = null })
        }
    }
}

@Composable
private fun EventSpanDetailDialog(detail: EventSpan, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(detail.color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = detail.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detail.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (detail.startDate == detail.endDate) {
                    detail.startDate.format(DAY_DETAIL_FORMATTER)
                } else {
                    "${detail.startDate.format(DAY_DETAIL_FORMATTER)} ~ ${detail.endDate.format(DAY_DETAIL_FORMATTER)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Primary
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Text("확인")
            }
        }
    }
}

private sealed class TimeEditTarget {
    data class Academic(val event: AcademicEvent) : TimeEditTarget()
    data class User(val event: UserCalendarEvent) : TimeEditTarget()
}

@Composable
private fun EventTimeEditorDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    val hours = remember { wheelRange(0, 23) }
    val minutes = remember { wheelRange(0, 59) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(20.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "알림받을 시간을 설정하세요", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WheelPicker(
                    values = hours,
                    selected = hour,
                    onSelectedChange = { hour = it },
                    modifier = Modifier.weight(1f),
                    format = { "%02d시".format(it) }
                )
                WheelPicker(
                    values = minutes,
                    selected = minute,
                    onSelectedChange = { minute = it },
                    modifier = Modifier.weight(1f),
                    format = { "%02d분".format(it) }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Text("취소")
                }
                Button(
                    onClick = { onConfirm(hour, minute) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                ) {
                    Text("저장")
                }
            }
        }
    }
}

@Composable
private fun DragHandle() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Border)
        )
    }
}

@Composable
private fun MonthNavRow(yearMonth: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            IconChevronLeft(tint = TextPrimary, size = 18.dp)
        }
        Text(
            text = yearMonth.format(MONTH_TITLE_FORMATTER),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onNext) {
            IconChevronRight(tint = TextPrimary, size = 18.dp)
        }
    }
}

private fun buildMonthCells(yearMonth: YearMonth): List<LocalDate?> {
    val firstOfMonth = yearMonth.atDay(1)
    val leadingBlanks = firstOfMonth.dayOfWeek.value % 7
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = leadingBlanks + daysInMonth
    val trailingBlanks = (7 - totalCells % 7) % 7
    return buildList {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..daysInMonth) add(yearMonth.atDay(day))
        repeat(trailingBlanks) { add(null) }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        WEEKDAY_LABELS.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    academicEvents: List<AcademicEvent>,
    userEvents: List<UserCalendarEvent>,
    onSelectDate: (LocalDate) -> Unit
) {
    val cells = remember(yearMonth) { buildMonthCells(yearMonth) }
    val eventIdsByDate = remember(academicEvents, userEvents) {
        val map = mutableMapOf<LocalDate, MutableList<String>>()
        academicEvents.forEach { event ->
            generateSequence(event.beginDate) { it.plusDays(1) }
                .takeWhile { !it.isAfter(event.endDate) }
                .forEach { date -> map.getOrPut(date) { mutableListOf() }.add(event.id) }
        }
        userEvents.forEach { event -> map.getOrPut(event.date) { mutableListOf() }.add(event.id) }
        map
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        WeekdayHeaderRow()
        Spacer(modifier = Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier.weight(1f).padding(2.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (date != null) {
                            val isSelected = date == selectedDate
                            val isToday = date == today
                            val ids = eventIdsByDate[date].orEmpty()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Primary else Surface)
                                    .clickable { onSelectDate(date) }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isSelected -> OnPrimary
                                        isToday -> Primary
                                        else -> TextPrimary
                                    },
                                    fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                if (ids.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        ids.forEach { id ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp)
                                                    .clip(RoundedCornerShape(1.5.dp))
                                                    .background(if (isSelected) OnPrimary else colorForEventId(id))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CalendarSearchResult(val title: String, val date: LocalDate)

private data class EventSpan(
    val id: String,
    val title: String,
    val subtitle: String,
    val startCol: Int,
    val endCol: Int,
    val color: Color,
    val startDate: LocalDate,
    val endDate: LocalDate
)

private fun computeWeekSpans(
    week: List<LocalDate?>,
    academicEvents: List<AcademicEvent>,
    userEvents: List<UserCalendarEvent>
): List<EventSpan> {
    val weekDates = week.filterNotNull()
    if (weekDates.isEmpty()) return emptyList()
    val weekStart = weekDates.first()
    val weekEnd = weekDates.last()
    val spans = mutableListOf<EventSpan>()

    academicEvents.forEach { event ->
        if (event.endDate.isBefore(weekStart) || event.beginDate.isAfter(weekEnd)) return@forEach
        val visibleStart = maxOf(event.beginDate, weekStart)
        val visibleEnd = minOf(event.endDate, weekEnd)
        val startCol = week.indexOfFirst { it == visibleStart }
        val endCol = week.indexOfFirst { it == visibleEnd }
        if (startCol == -1 || endCol == -1) return@forEach
        spans.add(
            EventSpan(
                id = event.id,
                title = event.description,
                subtitle = "학사일정",
                startCol = startCol,
                endCol = endCol,
                color = colorForEventId(event.id),
                startDate = event.beginDate,
                endDate = event.endDate
            )
        )
    }
    userEvents.forEach { event ->
        val col = week.indexOfFirst { it == event.date }
        if (col == -1) return@forEach
        spans.add(
            EventSpan(
                id = event.id,
                title = event.title,
                subtitle = "내 일정",
                startCol = col,
                endCol = col,
                color = colorForEventId(event.id),
                startDate = event.date,
                endDate = event.date
            )
        )
    }
    return spans
}

private const val MAX_LANES = 3

private fun assignLanes(spans: List<EventSpan>, maxLanes: Int = MAX_LANES): Pair<Map<EventSpan, Int>, Int> {
    val sorted = spans.sortedWith(compareBy({ it.startCol }, { it.startCol - it.endCol }))
    val laneEndCols = MutableList(maxLanes) { -1 }
    val laneOf = mutableMapOf<EventSpan, Int>()
    var overflow = 0
    sorted.forEach { span ->
        val laneIndex = laneEndCols.indexOfFirst { it < span.startCol }
        if (laneIndex >= 0) {
            laneEndCols[laneIndex] = span.endCol
            laneOf[span] = laneIndex
        } else {
            overflow++
        }
    }
    return laneOf to overflow
}

@Composable
private fun ExpandedMonthGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    academicEvents: List<AcademicEvent>,
    userEvents: List<UserCalendarEvent>,
    onSelectDate: (LocalDate) -> Unit,
    onSpanClick: (EventSpan) -> Unit
) {
    val cells = remember(yearMonth) { buildMonthCells(yearMonth) }

    Column(modifier = Modifier.fillMaxWidth()) {
        WeekdayHeaderRow()
        Spacer(modifier = Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            ExpandedWeekRow(
                week = week,
                selectedDate = selectedDate,
                today = today,
                academicEvents = academicEvents,
                userEvents = userEvents,
                onSelectDate = onSelectDate,
                onSpanClick = onSpanClick
            )
        }
    }
}

@Composable
private fun ExpandedWeekRow(
    week: List<LocalDate?>,
    selectedDate: LocalDate,
    today: LocalDate,
    academicEvents: List<AcademicEvent>,
    userEvents: List<UserCalendarEvent>,
    onSelectDate: (LocalDate) -> Unit,
    onSpanClick: (EventSpan) -> Unit
) {
    val spans = remember(week, academicEvents, userEvents) { computeWeekSpans(week, academicEvents, userEvents) }
    val (laneOf, overflow) = remember(spans) { assignLanes(spans) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { date ->
                Box(modifier = Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
                    if (date != null) {
                        val isSelected = date == selectedDate
                        val isToday = date == today
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Primary else Color.Transparent)
                                .clickable { onSelectDate(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isSelected -> OnPrimary
                                    isToday -> Primary
                                    else -> TextPrimary
                                },
                                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        repeat(MAX_LANES) { lane ->
            Row(modifier = Modifier.fillMaxWidth().height(18.dp)) {
                var col = 0
                while (col < 7) {
                    val span = spans.firstOrNull { laneOf[it] == lane && col in it.startCol..it.endCol }
                    if (span == null) {
                        Spacer(modifier = Modifier.weight(1f))
                        col++
                    } else {
                        val width = span.endCol - span.startCol + 1
                        // 여러 주에 걸치는 일정은 주(week) 단위로 잘려서 그려지므로, 이 줄의
                        // startCol/endCol이 실제 일정의 시작/끝 날짜와 같을 때만 그쪽 모서리를
                        // 둥글게 한다 — 다음 주로 이어지는 중간 지점은 각지게 남겨 "계속됨"을 표현한다.
                        val isTrueStart = week.getOrNull(span.startCol) == span.startDate
                        val isTrueEnd = week.getOrNull(span.endCol) == span.endDate
                        Box(
                            modifier = Modifier
                                .weight(width.toFloat())
                                .fillMaxHeight()
                                .padding(vertical = 1.dp, horizontal = 1.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (isTrueStart) 6.dp else 0.dp,
                                        bottomStart = if (isTrueStart) 6.dp else 0.dp,
                                        topEnd = if (isTrueEnd) 6.dp else 0.dp,
                                        bottomEnd = if (isTrueEnd) 6.dp else 0.dp
                                    )
                                )
                                .background(span.color)
                                .clickable { onSpanClick(span) }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = span.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = OnPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        col += width
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
        if (overflow > 0) {
            Text(
                text = "+${overflow}개 더보기",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun DayDetailSection(
    date: LocalDate,
    academicEvents: List<AcademicEvent>,
    userEvents: List<UserCalendarEvent>,
    isAcademicEnabled: (AcademicEvent) -> Boolean,
    academicTimeFor: (AcademicEvent) -> Pair<Int, Int>,
    onAcademicRowClick: (AcademicEvent) -> Unit,
    onAcademicToggleChange: (AcademicEvent, Boolean) -> Unit,
    onUserRowClick: (UserCalendarEvent) -> Unit,
    onUserToggleChange: (UserCalendarEvent, Boolean) -> Unit,
    onDeleteUser: (UserCalendarEvent) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = date.format(DAY_DETAIL_FORMATTER),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (academicEvents.isEmpty() && userEvents.isEmpty()) {
            Text(
                text = "이 날에는 등록된 일정이 없어요.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        academicEvents.forEach { event ->
            val (hour, minute) = academicTimeFor(event)
            EventRow(
                title = event.description,
                subtitle = "학사일정",
                color = colorForEventId(event.id),
                notifyEnabled = isAcademicEnabled(event),
                notifyHour = hour,
                notifyMinute = minute,
                onRowClick = { onAcademicRowClick(event) },
                onToggleChange = { checked -> onAcademicToggleChange(event, checked) },
                onDelete = null
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        userEvents.forEach { event ->
            EventRow(
                title = event.title,
                subtitle = "내 일정",
                color = colorForEventId(event.id),
                notifyEnabled = event.notifyEnabled,
                notifyHour = event.notifyHour,
                notifyMinute = event.notifyMinute,
                onRowClick = { onUserRowClick(event) },
                onToggleChange = { checked -> onUserToggleChange(event, checked) },
                onDelete = { onDeleteUser(event) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun EventRow(
    title: String,
    subtitle: String,
    color: Color,
    notifyEnabled: Boolean,
    notifyHour: Int,
    notifyMinute: Int,
    onRowClick: () -> Unit,
    onToggleChange: (Boolean) -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable { onRowClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            if (notifyEnabled) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "%02d:%02d 알림".format(notifyHour, notifyMinute),
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Text(text = "삭제", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
            }
        }
        Switch(
            checked = notifyEnabled,
            onCheckedChange = onToggleChange,
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

private enum class AddBarMode { COLLAPSED, QUICK_INPUT, FULL_ADD }

@Composable
private fun RoundIconButton(icon: @Composable (Color) -> Unit, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (enabled) Primary else TextDisabled
    Box(
        modifier = Modifier
            .size(26.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        icon(tint)
    }
}

@Composable
private fun QuickAddBar(
    date: LocalDate,
    mode: AddBarMode,
    title: String,
    hour: Int,
    minute: Int,
    onModeChange: (AddBarMode) -> Unit,
    onTitleChange: (String) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onQuickAdd: (String) -> Unit,
    onFullAdd: (String, Int, Int) -> Unit,
    onCommitted: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val hours = remember { wheelRange(0, 23) }
    val minutes = remember { wheelRange(0, 59) }

    val cornerRadius by animateDpAsState(
        targetValue = if (mode == AddBarMode.COLLAPSED) 999.dp else 20.dp,
        label = "quickAddCorner"
    )

    androidx.compose.runtime.LaunchedEffect(mode) {
        if (mode == AddBarMode.QUICK_INPUT) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = Modifier
            .then(if (mode == AddBarMode.COLLAPSED) Modifier.wrapContentWidth() else Modifier.width(240.dp))
            .animateContentSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(cornerRadius))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        when (mode) {
            AddBarMode.COLLAPSED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = date.format(DAY_DETAIL_FORMATTER),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.clickable { onModeChange(AddBarMode.QUICK_INPUT) }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    RoundIconButton(icon = { tint -> IconPlus(tint = tint) }, onClick = { onModeChange(AddBarMode.FULL_ADD) })
                }
            }
            AddBarMode.QUICK_INPUT -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (title.isEmpty()) {
                            Text(text = "일정 제목", style = MaterialTheme.typography.bodyMedium, color = TextPlaceholder)
                        }
                        BasicTextField(
                            value = title,
                            onValueChange = onTitleChange,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            singleLine = true,
                            cursorBrush = SolidColor(Primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (title.isNotBlank()) {
                                    onQuickAdd(title)
                                    onCommitted()
                                }
                            })
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    RoundIconButton(
                        icon = { tint -> IconCheck(tint = tint) },
                        enabled = title.isNotBlank(),
                        onClick = {
                            if (title.isNotBlank()) {
                                onQuickAdd(title)
                                onCommitted()
                            }
                        }
                    )
                }
            }
            AddBarMode.FULL_ADD -> {
                Text(text = "제목", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (title.isEmpty()) {
                        Text(text = "일정 제목", style = MaterialTheme.typography.bodyMedium, color = TextPlaceholder)
                    }
                    BasicTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        singleLine = true,
                        cursorBrush = SolidColor(Primary)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(text = "알림받을 시간 설정", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WheelPicker(
                        values = hours,
                        selected = hour,
                        onSelectedChange = onHourChange,
                        modifier = Modifier.weight(1f),
                        format = { "%02d시".format(it) }
                    )
                    WheelPicker(
                        values = minutes,
                        selected = minute,
                        onSelectedChange = onMinuteChange,
                        modifier = Modifier.weight(1f),
                        format = { "%02d분".format(it) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCommitted,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("취소")
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onFullAdd(title, hour, minute)
                                onCommitted()
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = OnPrimary,
                            disabledContainerColor = TextDisabled
                        )
                    ) {
                        Text("저장")
                    }
                }
            }
        }
    }
}
