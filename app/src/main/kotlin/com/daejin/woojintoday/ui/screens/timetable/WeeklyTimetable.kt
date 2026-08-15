package com.daejin.woojintoday.ui.screens.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.daejin.woojintoday.data.model.ClassSession
import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.data.model.TimeRangeFilter
import com.daejin.woojintoday.data.model.TimeRegion
import com.daejin.woojintoday.data.model.Weekday
import com.daejin.woojintoday.ui.icons.IconClose
import com.daejin.woojintoday.ui.theme.AccentBlue
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val DRAG_SNAP_MINUTES = 30
private val HANDLE_SIZE = 24.dp
private val HANDLE_HIT_RADIUS = 24.dp

private val DEFAULT_DAYS = listOf(Weekday.MON, Weekday.TUE, Weekday.WED, Weekday.THU, Weekday.FRI)
private const val DEFAULT_START_HOUR = 9
private const val DEFAULT_END_HOUR = 22
private val TIME_COLUMN_WIDTH = 28.dp
private val HOUR_HEIGHT = 56.dp

private enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
/** [segment]은 손댄 사각형 "조각"의 요일 구간 — 요일이 떨어져있는(비연속) 사각형은 segment가 여러 개라
 *  각 조각마다 자기 핸들/바디를 따로 가진다. */
private sealed interface GridHit {
    data class Handle(val regionIndex: Int, val corner: Corner, val segment: IntRange) : GridHit
    data class Body(val regionIndex: Int, val segment: IntRange) : GridHit
}

/** What the long-press-then-drag gesture currently means, decided once at [detectDragGesturesAfterLongPress]'s
 *  onDragStart from a hit-test, then reused for every onDrag call of that same gesture.
 *  [otherDays]는 지금 손대고 있는 조각 말고 그 사각형에 속한 나머지(비연속) 요일들 — 리사이즈/이동
 *  결과에도 그대로 합쳐 넣어야 다른 조각이 사라지지 않는다. */
private sealed interface DragMode {
    data class Create(val index: Int, val startColumn: Int, val startMinutes: Int) : DragMode
    data class Resize(
        val regionIndex: Int,
        val corner: Corner,
        val fixedLeft: Int,
        val fixedRight: Int,
        val fixedTop: Int,
        val fixedBottom: Int,
        val otherDays: Set<Weekday>
    ) : DragMode
    data class Move(
        val regionIndex: Int,
        val dayCount: Int,
        val duration: Int,
        val startColumn: Int,
        val startMinutes: Int,
        val originLeft: Int,
        val originTop: Int,
        val otherDays: Set<Weekday>
    ) : DragMode
}

/** A region's day columns, split into contiguous runs — a region drawn/resized/moved through the
 *  UI is always one contiguous run, but a same-time merge of distant days can produce more than one. */
private fun regionSegments(region: TimeRegion, days: List<Weekday>): List<IntRange> {
    val indices = if (region.days.isEmpty()) {
        return listOf(0..days.lastIndex)
    } else {
        region.days.mapNotNull { d -> days.indexOf(d).takeIf { it >= 0 } }.sorted()
    }
    if (indices.isEmpty()) return emptyList()
    val segments = mutableListOf<IntRange>()
    var start = indices.first()
    var prev = indices.first()
    for (i in indices.drop(1)) {
        if (i == prev + 1) {
            prev = i
        } else {
            segments += start..prev
            start = i
            prev = i
        }
    }
    segments += start..prev
    return segments
}

@Composable
fun WeeklyTimetable(
    courses: List<Course>,
    colorFor: (Course) -> Color,
    onCourseClick: (Course) -> Unit,
    onCourseRemove: (Course) -> Unit,
    onGridTap: () -> Unit,
    previewCourse: Course?,
    previewColor: Color,
    regions: List<TimeRegion>,
    onRegionUpdate: (index: Int, region: TimeRegion) -> Unit,
    onRegionCreate: (TimeRegion) -> Int,
    onRegionFinalize: () -> Unit,
    showCurrentTimeIndicator: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sessionEntries = remember(courses) {
        courses.flatMap { course -> course.sessions.map { it to course } }
    }
    val days = remember(sessionEntries) {
        val extraDays = sessionEntries.map { it.first.day }
            .filter { it == Weekday.SAT || it == Weekday.SUN }
            .distinct()
            .sortedBy { it.ordinal }
        DEFAULT_DAYS + extraDays
    }
    val hourRange = remember(sessionEntries) {
        if (sessionEntries.isEmpty()) {
            DEFAULT_START_HOUR..DEFAULT_END_HOUR
        } else {
            val minHour = minOf(DEFAULT_START_HOUR, sessionEntries.minOf { it.first.startMinutes } / 60)
            val maxHour = maxOf(DEFAULT_END_HOUR, ceil(sessionEntries.maxOf { it.first.endMinutes } / 60.0).toInt())
            minHour..maxHour
        }
    }
    val hourCount = hourRange.last - hourRange.first

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(TIME_COLUMN_WIDTH))
            days.forEach { day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = day.label.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            val density = LocalDensity.current
            val gridWidth = maxWidth - TIME_COLUMN_WIDTH
            val dayWidth = gridWidth / days.size
            val dayWidthPx = with(density) { dayWidth.toPx() }
            val hourHeightPx = with(density) { HOUR_HEIGHT.toPx() }
            val handleHitRadiusPx = with(density) { HANDLE_HIT_RADIUS.toPx() }
            val gridHeightPx = hourHeightPx * hourCount
            // 필터 사각형을 꾹 눌러 조절하기 시작하는 시간을 시스템 기본 롱프레스(보통 500ms)보다
            // 살짝 앞당긴다 — 제스처 방식(detectDragGesturesAfterLongPress) 자체는 그대로 두고
            // 이 그리드 범위에서만 임계값을 낮춘다.
            val baseViewConfiguration = LocalViewConfiguration.current
            val fastLongPressViewConfiguration = remember(baseViewConfiguration) {
                object : ViewConfiguration by baseViewConfiguration {
                    override val longPressTimeoutMillis: Long = 220L
                }
            }
            // 요일만 고르거나 시간을 "전체"로 선택하면 내부적으로 0:00~24:00 풀레인지가 저장되는데,
            // 그리드는 hourRange(예: 9시~18시)만 보여주므로 화면 좌표는 항상 그 범위로 clamp해야
            // 핸들이 화면 밖으로 계산되지 않는다(드래그 사각형 자체는 drawBehind에서 이미 clamp됨).
            fun clampY(y: Float) = y.coerceIn(0f, gridHeightPx)

            fun yToMinutes(y: Float): Int {
                val raw = hourRange.first * 60 + ((y / hourHeightPx) * 60).toInt()
                return ((raw / DRAG_SNAP_MINUTES) * DRAG_SNAP_MINUTES).coerceIn(hourRange.first * 60, hourRange.last * 60)
            }
            fun xToColumn(x: Float): Int = (x / dayWidthPx).toInt().coerceIn(0, days.lastIndex)
            fun xToBoundary(x: Float): Int = (x / dayWidthPx).roundToInt().coerceIn(0, days.size)

            val onRegionUpdateLatest by rememberUpdatedState(onRegionUpdate)
            val onRegionCreateLatest by rememberUpdatedState(onRegionCreate)
            val onRegionFinalizeLatest by rememberUpdatedState(onRegionFinalize)
            val onGridTapLatest by rememberUpdatedState(onGridTap)
            val regionsLatest by rememberUpdatedState(regions)

            fun hitTest(pos: Offset): GridHit? {
                val currentRegions = regionsLatest
                for (regionIndex in currentRegions.indices) {
                    val region = currentRegions[regionIndex]
                    val topY = clampY(hourHeightPx * (region.timeRange.startMinutes - hourRange.first * 60) / 60f)
                    val bottomY = clampY(hourHeightPx * (region.timeRange.endMinutes - hourRange.first * 60) / 60f)
                    for (segment in regionSegments(region, days)) {
                        val leftX = segment.first * dayWidthPx
                        val rightX = (segment.last + 1) * dayWidthPx
                        val corners = listOf(
                            Corner.TOP_LEFT to Offset(leftX, topY),
                            Corner.TOP_RIGHT to Offset(rightX, topY),
                            Corner.BOTTOM_LEFT to Offset(leftX, bottomY),
                            Corner.BOTTOM_RIGHT to Offset(rightX, bottomY)
                        )
                        for ((corner, cornerPos) in corners) {
                            if ((pos - cornerPos).getDistance() <= handleHitRadiusPx) {
                                return GridHit.Handle(regionIndex, corner, segment)
                            }
                        }
                    }
                }
                for (regionIndex in currentRegions.indices) {
                    val region = currentRegions[regionIndex]
                    val topY = hourHeightPx * (region.timeRange.startMinutes - hourRange.first * 60) / 60f
                    val bottomY = hourHeightPx * (region.timeRange.endMinutes - hourRange.first * 60) / 60f
                    if (pos.y < topY || pos.y > bottomY) continue
                    for (segment in regionSegments(region, days)) {
                        val leftX = segment.first * dayWidthPx
                        val rightX = (segment.last + 1) * dayWidthPx
                        if (pos.x in leftX..rightX) return GridHit.Body(regionIndex, segment)
                    }
                }
                return null
            }

            Box {
            Row(modifier = Modifier.height(HOUR_HEIGHT * hourCount)) {
                Column(modifier = Modifier.width(TIME_COLUMN_WIDTH)) {
                    repeat(hourCount) { i ->
                        Box(modifier = Modifier.height(HOUR_HEIGHT)) {
                            Text(
                                text = "${hourRange.first + i}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    CompositionLocalProvider(LocalViewConfiguration provides fastLongPressViewConfiguration) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 빈 그리드를 그냥 탭하면(꾹 누르지 않고) 미리보기 중인 과목을 취소한다.
                            // 과목 블록 위 탭은 그 블록의 clickable이 이벤트를 소비하므로 여기까지 오지 않는다.
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onGridTapLatest() })
                            }
                            .pointerInput(hourRange, days) {
                                var mode: DragMode? = null
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val column = xToColumn(offset.x)
                                        val minutes = yToMinutes(offset.y)
                                        mode = when (val hit = hitTest(offset)) {
                                            is GridHit.Handle -> {
                                                val region = regionsLatest.getOrNull(hit.regionIndex)
                                                if (region != null) {
                                                    val segment = hit.segment
                                                    val segmentDays = segment.map { days[it] }.toSet()
                                                    DragMode.Resize(
                                                        regionIndex = hit.regionIndex,
                                                        corner = hit.corner,
                                                        fixedLeft = segment.first,
                                                        fixedRight = segment.last + 1,
                                                        fixedTop = region.timeRange.startMinutes,
                                                        fixedBottom = region.timeRange.endMinutes,
                                                        otherDays = region.days - segmentDays
                                                    )
                                                } else null
                                            }
                                            is GridHit.Body -> {
                                                val region = regionsLatest.getOrNull(hit.regionIndex)
                                                if (region != null) {
                                                    val segment = hit.segment
                                                    val segmentDays = segment.map { days[it] }.toSet()
                                                    DragMode.Move(
                                                        regionIndex = hit.regionIndex,
                                                        dayCount = segment.last - segment.first + 1,
                                                        duration = region.timeRange.endMinutes - region.timeRange.startMinutes,
                                                        startColumn = column,
                                                        startMinutes = minutes,
                                                        originLeft = segment.first,
                                                        originTop = region.timeRange.startMinutes,
                                                        otherDays = region.days - segmentDays
                                                    )
                                                } else null
                                            }
                                            null -> {
                                                val newIndex = onRegionCreateLatest(
                                                    TimeRegion(
                                                        setOf(days[column]),
                                                        TimeRangeFilter(minutes, (minutes + DRAG_SNAP_MINUTES).coerceAtMost(hourRange.last * 60))
                                                    )
                                                )
                                                DragMode.Create(newIndex, column, minutes)
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val column = xToColumn(change.position.x)
                                        val boundary = xToBoundary(change.position.x)
                                        val minutes = yToMinutes(change.position.y)
                                        when (val m = mode) {
                                            is DragMode.Create -> {
                                                val lo = minOf(m.startColumn, column)
                                                val hi = maxOf(m.startColumn, column)
                                                val tLo = minOf(m.startMinutes, minutes)
                                                val tHi = (maxOf(m.startMinutes, minutes) + DRAG_SNAP_MINUTES).coerceAtMost(hourRange.last * 60)
                                                onRegionUpdateLatest(m.index, TimeRegion((lo..hi).map { days[it] }.toSet(), TimeRangeFilter(tLo, tHi)))
                                            }
                                            is DragMode.Resize -> {
                                                // otherDays: 비연속 사각형에서 지금 안 잡은 나머지 요일 조각 —
                                                // 리사이즈 결과에도 그대로 합쳐 넣어야 그 조각이 사라지지 않는다.
                                                val newRegion = when (m.corner) {
                                                    Corner.TOP_LEFT -> TimeRegion(
                                                        m.otherDays + (boundary.coerceAtMost(m.fixedRight - 1) until m.fixedRight).map { days[it] }.toSet(),
                                                        TimeRangeFilter(minutes.coerceAtMost(m.fixedBottom - DRAG_SNAP_MINUTES), m.fixedBottom)
                                                    )
                                                    Corner.TOP_RIGHT -> TimeRegion(
                                                        m.otherDays + (m.fixedLeft until boundary.coerceAtLeast(m.fixedLeft + 1)).map { days[it] }.toSet(),
                                                        TimeRangeFilter(minutes.coerceAtMost(m.fixedBottom - DRAG_SNAP_MINUTES), m.fixedBottom)
                                                    )
                                                    Corner.BOTTOM_LEFT -> TimeRegion(
                                                        m.otherDays + (boundary.coerceAtMost(m.fixedRight - 1) until m.fixedRight).map { days[it] }.toSet(),
                                                        TimeRangeFilter(m.fixedTop, minutes.coerceAtLeast(m.fixedTop + DRAG_SNAP_MINUTES))
                                                    )
                                                    Corner.BOTTOM_RIGHT -> TimeRegion(
                                                        m.otherDays + (m.fixedLeft until boundary.coerceAtLeast(m.fixedLeft + 1)).map { days[it] }.toSet(),
                                                        TimeRangeFilter(m.fixedTop, minutes.coerceAtLeast(m.fixedTop + DRAG_SNAP_MINUTES))
                                                    )
                                                }
                                                onRegionUpdateLatest(m.regionIndex, newRegion)
                                            }
                                            is DragMode.Move -> {
                                                val deltaCol = column - m.startColumn
                                                val deltaMinutes = ((minutes - m.startMinutes) / DRAG_SNAP_MINUTES) * DRAG_SNAP_MINUTES
                                                val newLeft = (m.originLeft + deltaCol).coerceIn(0, days.size - m.dayCount)
                                                // duration이 그리드에 보이는 시간 범위보다 클 수 있다(요일만 고르거나
                                                // 시간을 "전체"로 선택하면 0:00~24:00 풀레인지로 저장됨) — 그 경우
                                                // hourRange.last*60 - duration이 hourRange.first*60보다 작아져
                                                // coerceIn(lo, hi)에서 lo > hi가 되어 크래시가 났다.
                                                val topLowBound = hourRange.first * 60
                                                val topHighBound = (hourRange.last * 60 - m.duration).coerceAtLeast(topLowBound)
                                                val newTop = (m.originTop + deltaMinutes).coerceIn(topLowBound, topHighBound)
                                                // otherDays: 비연속 사각형에서 지금 안 잡은 나머지 요일 조각 — 이동
                                                // 결과에도 그대로 합쳐 넣어야 그 조각이 사라지지 않는다.
                                                onRegionUpdateLatest(
                                                    m.regionIndex,
                                                    TimeRegion(
                                                        m.otherDays + (newLeft until newLeft + m.dayCount).map { days[it] }.toSet(),
                                                        TimeRangeFilter(newTop, newTop + m.duration)
                                                    )
                                                )
                                            }
                                            null -> Unit
                                        }
                                    },
                                    onDragEnd = {
                                        mode = null
                                        onRegionFinalizeLatest()
                                    },
                                    onDragCancel = { mode = null }
                                )
                            }
                            .drawBehind {
                                val stroke = Stroke(width = 1f)
                                for (i in 0..hourCount) {
                                    val y = HOUR_HEIGHT.toPx() * i
                                    drawLine(Border, Offset(0f, y), Offset(size.width, y), stroke.width)
                                }
                                for (i in 0..days.size) {
                                    val x = dayWidth.toPx() * i
                                    drawLine(Border, Offset(x, 0f), Offset(x, size.height), stroke.width)
                                }
                                val hourStartMinutes = hourRange.first * 60
                                regions.forEach { region ->
                                    val top = (HOUR_HEIGHT.toPx() * (region.timeRange.startMinutes - hourStartMinutes) / 60f).coerceIn(0f, size.height)
                                    val bottom = (HOUR_HEIGHT.toPx() * (region.timeRange.endMinutes - hourStartMinutes) / 60f).coerceIn(0f, size.height)
                                    if (bottom <= top) return@forEach
                                    regionSegments(region, days).forEach { segment ->
                                        val left = (segment.first * dayWidthPx).coerceIn(0f, size.width)
                                        val right = ((segment.last + 1) * dayWidthPx).coerceIn(0f, size.width)
                                        if (right > left) {
                                            drawRect(
                                                color = AccentBlue,
                                                topLeft = Offset(left, top),
                                                size = Size(right - left, bottom - top),
                                                style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f)))
                                            )
                                        }
                                    }
                                }
                            }
                    ) {
                        sessionEntries.forEach { (session, course) ->
                            val dayIndex = days.indexOf(session.day)
                            if (dayIndex < 0) return@forEach
                            CourseBlock(
                                session = session,
                                course = course,
                                color = colorFor(course),
                                hourRangeStartMinutes = hourRange.first * 60,
                                dayIndex = dayIndex,
                                dayWidth = dayWidth,
                                onClick = { onCourseClick(course) },
                                onRemove = { onCourseRemove(course) }
                            )
                        }

                        // 리스트에서 한 번 탭해 "미리보기" 중인 과목 — 점선+투명, 겹쳐도 그냥 보여준다.
                        previewCourse?.sessions?.forEach { session ->
                            val dayIndex = days.indexOf(session.day)
                            if (dayIndex < 0) return@forEach
                            CourseBlockPreview(
                                session = session,
                                course = previewCourse,
                                color = previewColor,
                                hourRangeStartMinutes = hourRange.first * 60,
                                dayIndex = dayIndex,
                                dayWidth = dayWidth
                            )
                        }
                    }
                    }

                    // 리사이즈 핸들은 순수 시각 표시만 — 실제 제스처는 위 그리드 Box 하나에서 좌표 기준으로
                    // 처리한다(핸들 스스로 위치를 옮기면서 자기 좌표계가 흔들리는 문제를 피하기 위함).
                    regions.forEach { region ->
                        val topY = clampY(hourHeightPx * (region.timeRange.startMinutes - hourRange.first * 60) / 60f)
                        val bottomY = clampY(hourHeightPx * (region.timeRange.endMinutes - hourRange.first * 60) / 60f)
                        // 요일이 떨어져있는(비연속) 사각형은 조각마다 자기 모서리 핸들을 따로 가진다.
                        regionSegments(region, days).forEach { segment ->
                            val leftX = segment.first * dayWidthPx
                            val rightX = (segment.last + 1) * dayWidthPx

                            RegionHandleDot(xPx = leftX, yPx = topY)
                            RegionHandleDot(xPx = rightX, yPx = topY)
                            RegionHandleDot(xPx = leftX, yPx = bottomY)
                            RegionHandleDot(xPx = rightX, yPx = bottomY)
                        }
                    }
                }
            }
            if (showCurrentTimeIndicator) {
                val timeColumnWidthPx = with(density) { TIME_COLUMN_WIDTH.toPx() }
                CurrentTimeIndicator(hourRange = hourRange, hourHeightPx = hourHeightPx, timeColumnWidthPx = timeColumnWidthPx)
            }
            }
        }
    }
}

/** "강의시간 알림" 토글이 켜져있을 때만 그려지는 현재 시각 표시 — 시간 라벨 오른쪽에 우측을 향하는
 *  작은 빨간 삼각형과, 거기서부터 그리드 오른쪽 끝까지 이어지는 얇은 빨간 점선. 표시 중인 시간
 *  범위([hourRange])를 벗어난 시각이면(아침 일찍/밤늦게) 그리지 않는다. */
@Composable
private fun CurrentTimeIndicator(hourRange: IntRange, hourHeightPx: Float, timeColumnWidthPx: Float) {
    var nowMinutes by remember { mutableStateOf(currentMinutesOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMinutes = currentMinutesOfDay()
        }
    }

    val rangeStartMinutes = hourRange.first * 60
    val rangeEndMinutes = hourRange.last * 60
    if (nowMinutes < rangeStartMinutes || nowMinutes > rangeEndMinutes) return

    val y = hourHeightPx * (nowMinutes - rangeStartMinutes) / 60f
    Canvas(modifier = Modifier.fillMaxSize()) {
        val triangleSize = 6.dp.toPx()
        val trianglePath = Path().apply {
            moveTo(timeColumnWidthPx - triangleSize, y - triangleSize)
            lineTo(timeColumnWidthPx, y)
            lineTo(timeColumnWidthPx - triangleSize, y + triangleSize)
            close()
        }
        drawPath(trianglePath, color = Color.Red)
        drawLine(
            color = Color.Red,
            start = Offset(timeColumnWidthPx, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )
    }
}

private fun currentMinutesOfDay(): Int {
    val now = java.time.LocalTime.now()
    return now.hour * 60 + now.minute
}

/** Purely visual corner marker — no gesture attached (the grid handles all interaction). */
@Composable
private fun RegionHandleDot(xPx: Float, yPx: Float) {
    val density = LocalDensity.current
    val handlePx = with(density) { HANDLE_SIZE.toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset((xPx - handlePx / 2).roundToInt(), (yPx - handlePx / 2).roundToInt())
            }
            .size(HANDLE_SIZE)
            .clip(CircleShape)
            .background(AccentBlue)
    )
}

@Composable
private fun CourseBlock(
    session: ClassSession,
    course: Course,
    color: Color,
    hourRangeStartMinutes: Int,
    dayIndex: Int,
    dayWidth: Dp,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val top = HOUR_HEIGHT * ((session.startMinutes - hourRangeStartMinutes) / 60f)
    val height = HOUR_HEIGHT * ((session.endMinutes - session.startMinutes) / 60f)

    Box(
        modifier = Modifier
            .offset(x = dayWidth * dayIndex, y = top)
            .width(dayWidth)
            .height(height)
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.bodySmall,
                color = OnPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = course.room,
                style = MaterialTheme.typography.bodySmall,
                color = OnPrimary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(onClick = onRemove)
                .padding(3.dp)
        ) {
            IconClose(tint = OnPrimary, size = 9.dp)
        }
    }
}

/** Ghost preview of where a first-tapped (not yet confirmed) course would land — same content,
 *  same color, but a dashed outline over a near-transparent fill instead of a solid block. */
@Composable
private fun CourseBlockPreview(
    session: ClassSession,
    course: Course,
    color: Color,
    hourRangeStartMinutes: Int,
    dayIndex: Int,
    dayWidth: Dp
) {
    val top = HOUR_HEIGHT * ((session.startMinutes - hourRangeStartMinutes) / 60f)
    val height = HOUR_HEIGHT * ((session.endMinutes - session.startMinutes) / 60f)
    val cornerRadiusPx = with(LocalDensity.current) { 6.dp.toPx() }

    Box(
        modifier = Modifier
            .offset(x = dayWidth * dayIndex, y = top)
            .width(dayWidth)
            .height(height)
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .drawBehind {
                drawRoundRect(
                    color = color,
                    style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = course.room,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
