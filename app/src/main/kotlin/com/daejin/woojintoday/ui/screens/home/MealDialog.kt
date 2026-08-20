package com.daejin.woojintoday.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daejin.woojintoday.data.network.CafeteriaEntry
import com.daejin.woojintoday.ui.components.ResponsiveContainer
import com.daejin.woojintoday.ui.components.WheelPicker
import com.daejin.woojintoday.ui.components.wheelRange
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.icons.IconChevronDown
import com.daejin.woojintoday.ui.icons.IconChevronLeft
import com.daejin.woojintoday.ui.icons.IconChevronRight
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PAGE_COUNT = 100_000
private const val CENTER_PAGE = PAGE_COUNT / 2
private val DAY_FORMATTER = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

@Composable
fun MealDialog(onDismiss: () -> Unit) {
    val viewModel: CafeteriaViewModel = viewModel()
    val today = remember { LocalDate.now() }
    val pagerState = rememberPagerState(initialPage = CENTER_PAGE, pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }

    val currentDate = today.plusDays((pagerState.currentPage - CENTER_PAGE).toLong())

    if (showDatePicker) {
        MealDatePickerDialog(
            initialDate = currentDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                showDatePicker = false
                val target = CENTER_PAGE + java.time.temporal.ChronoUnit.DAYS.between(today, picked).toInt()
                scope.launch { pagerState.scrollToPage(target) }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Background)) {
        ResponsiveContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    IconArrowBack(tint = TextPrimary)
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(text = "오늘의 학식", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) {
                        IconChevronLeft(tint = TextPrimary, size = 18.dp)
                    }
                    Text(
                        text = currentDate.format(DAY_FORMATTER),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    IconButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }) {
                        IconChevronRight(tint = TextPrimary, size = 18.dp)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        text = "날짜 직접 선택",
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
                val date = today.plusDays((page - CENTER_PAGE).toLong())
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    // 카드 자체를 한 줄만큼 낮게 잡아서, 카드 밖(카드 아래)에 항상 그만큼의 여백이
                    // 남도록 한다 — 카드 안에서 끝까지 스크롤해도 화면 하단에 바로 붙지 않게.
                    val cardMaxHeight = (maxHeight - MealEntryRowHeight).coerceAtLeast(140.dp)
                    Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        MealDayCard(
                            date = date,
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = cardMaxHeight)
                        )
                        Spacer(modifier = Modifier.height(MealEntryRowHeight))
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun MealDayCard(date: LocalDate, viewModel: CafeteriaViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(date) { viewModel.ensureLoaded(date) }
    val day = viewModel.dayFor(date)
    val loading = viewModel.isLoading(date)
    val error = viewModel.errorFor(date)

    when {
        loading -> {
            Box(
                modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
        }
        error != null -> {
            Box(
                modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Surface).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = error, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        day != null && day.breakfast.isEmpty() && day.lunch.isEmpty() && day.dinner.isEmpty() -> {
            Box(
                modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Surface).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "이 날은 등록된 학식 정보가 없어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        day != null -> {
            // 가게이름(entry.corner) 배지 너비가 줄마다 제각각이라 그 뒤 메뉴/가격 시작점이
            // 흔들렸다 — 이 카드(하루치) 안의 모든 줄이 같은 시작점을 쓰도록 폭을 공유한다.
            val cornerWidthState = remember(date) { mutableIntStateOf(0) }
            Column(
                modifier = modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                MealTypeSection(label = "아침", entries = day.breakfast, cornerWidthState = cornerWidthState, initiallyExpanded = false)
                Spacer(modifier = Modifier.height(16.dp))
                MealTypeSection(label = "점심", entries = day.lunch, cornerWidthState = cornerWidthState, initiallyExpanded = true)
                Spacer(modifier = Modifier.height(16.dp))
                MealTypeSection(label = "저녁", entries = day.dinner, cornerWidthState = cornerWidthState, initiallyExpanded = false)
            }
        }
        else -> {
            Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Surface))
        }
    }
}

@Composable
private fun MealTypeSection(
    label: String,
    entries: List<CafeteriaEntry>,
    cornerWidthState: MutableIntState,
    initiallyExpanded: Boolean
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column {
        Row(
            modifier = Modifier.clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(4.dp))
            IconChevronDown(
                tint = TextSecondary,
                size = 14.dp,
                modifier = Modifier.rotate(if (expanded) 180f else 0f)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                if (entries.isEmpty()) {
                    Text(
                        text = "등록된 메뉴가 없어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        entries.forEach { entry -> MealEntryRow(entry, cornerWidthState) }
                    }
                }
            }
        }
    }
}

/** 자기 자신(및 형제 [MealEntryRow]들)이 측정된 폭 중 가장 큰 값을 [widthState]에 반영하고,
 *  항상 그 값을 자기 폭으로 쓴다 — 내용은 그대로 왼쪽에 붙고 나머지는 빈 여백이 되어, 서로 다른
 *  Row에 있는 형제들끼리도 결국 같은 폭(=같은 시작점)으로 수렴한다. */
private fun Modifier.uniformStartWidth(widthState: MutableIntState): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    if (placeable.width > widthState.value) widthState.value = placeable.width
    val width = maxOf(placeable.width, widthState.value)
    layout(width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}

// [MealEntryRow] 한 줄 정도의 높이 — 하루치 메뉴 목록 맨 아래 여백에 쓴다.
private val MealEntryRowHeight = 56.dp

@Composable
private fun MealEntryRow(entry: CafeteriaEntry, cornerWidthState: MutableIntState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.uniformStartWidth(cornerWidthState),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Primary)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(text = entry.corner, style = MaterialTheme.typography.bodySmall, color = OnPrimary)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.menu, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            if (entry.price != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = entry.price, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun MealDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    var year by remember { mutableIntStateOf(initialDate.year) }
    var month by remember { mutableIntStateOf(initialDate.monthValue) }
    var day by remember { mutableIntStateOf(initialDate.dayOfMonth) }

    val years = remember { wheelRange(initialDate.year - 1, initialDate.year + 1) }
    val months = remember { wheelRange(1, 12) }
    val daysInMonth = remember(year, month) { java.time.YearMonth.of(year, month).lengthOfMonth() }
    val days = remember(daysInMonth) { wheelRange(1, daysInMonth) }

    LaunchedEffect(daysInMonth) {
        if (day > daysInMonth) day = daysInMonth
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(20.dp)
        ) {
            Text(text = "날짜 선택", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WheelPicker(
                    values = years,
                    selected = year,
                    onSelectedChange = { year = it },
                    modifier = Modifier.weight(1.2f),
                    format = { "${it}년" }
                )
                WheelPicker(
                    values = months,
                    selected = month,
                    onSelectedChange = { month = it },
                    modifier = Modifier.weight(1f),
                    format = { "${it}월" }
                )
                WheelPicker(
                    values = days,
                    selected = day,
                    onSelectedChange = { day = it },
                    modifier = Modifier.weight(1f),
                    format = { "${it}일" }
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
                    onClick = { onConfirm(LocalDate.of(year, month, day)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                ) {
                    Text("확인")
                }
            }
        }
    }
}
