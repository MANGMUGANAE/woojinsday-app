package com.daejin.woojintoday.ui.screens.home

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daejin.woojintoday.data.network.GraduationRequirement
import com.daejin.woojintoday.data.network.RawCell
import com.daejin.woojintoday.data.network.RawLine
import com.daejin.woojintoday.data.network.RawRect
import com.daejin.woojintoday.data.network.TranscriptCourseRow
import com.daejin.woojintoday.data.network.TranscriptDetail
import com.daejin.woojintoday.ui.components.ResponsiveContainer
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary

/** "졸업학점" 카드 — dreams2 이수구분표(AI 시간표 생성 때 이미 이수한 과목을 뺄 때 쓰는 것과 같은
 *  리포트)를 이수구분(교필/교선/일선/전필/전선/전기/교직)별로 묶어, 들은 과목과 리포트에 같이
 *  실려오는 졸업사정 기준(기준학점 대비 취득학점, 교양영역별 기준)까지 보여준다. */
@Composable
fun GraduationCreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val viewModel: GraduationCreditsViewModel = viewModel(factory = GraduationCreditsViewModel.Factory(context))
    val detail = viewModel.detail

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Background)) {
        ResponsiveContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    IconArrowBack(tint = TextPrimary)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    viewModel.isLoading -> GraduationMessageBox { CircularProgressIndicator(color = Primary) }
                    viewModel.errorMessage != null && detail == null ->
                        GraduationMessageBox {
                            Text(viewModel.errorMessage.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    detail != null -> {
                        GraduationTotalCard(detail = detail, onShowRaw = { viewModel.openRawTable() })
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            viewModel.rowsByCategory.forEach { (category, categoryRows) ->
                                val requirement = detail.requirements.find { it.label == category }
                                GraduationCategorySection(
                                    category = category,
                                    requirement = requirement,
                                    rows = categoryRows,
                                    areaRequirements = if (category == "교선") detail.areaRequirements else emptyList()
                                )
                            }
                        }
                    }
                }

                // 화면 맨 끝에서 바로 스크롤이 끊기지 않게 여백을 더 둔다.
                Spacer(modifier = Modifier.height(140.dp))
            }
        }
        }
        }
    }

    if (viewModel.showRawTable && detail != null) {
        RawTranscriptDialog(
            rawCells = detail.rawCells,
            rawLines = detail.rawLines,
            rawRects = detail.rawRects,
            onDismiss = { viewModel.dismissRawTable() }
        )
    }
}

@Composable
private fun GraduationMessageBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun GraduationTotalCard(detail: TranscriptDetail, onShowRaw: () -> Unit) {
    val totalReq = detail.requirements.find { it.label == "졸업학점" }
    val gpaReq = detail.requirements.find { it.label == "졸업평점평균" }
    val earned = totalReq?.earnedValue ?: formatCredits(detail.rows.sumOf { it.credit })
    val required = totalReq?.requiredValue

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "총 이수학점", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Primary)) { append(earned) }
                        withStyle(SpanStyle(color = TextSecondary)) { append(if (required != null) "/${required}학점" else "학점") }
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                if (gpaReq?.earnedValue != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = TextSecondary)) { append("졸업평점 ") }
                            withStyle(SpanStyle(color = Primary)) { append(gpaReq.earnedValue) }
                            withStyle(SpanStyle(color = TextSecondary)) { append("/${gpaReq.requiredValue ?: "-"}") }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
//            Text(
//                text = "이수구분표 원본",
//                style = MaterialTheme.typography.labelMedium,
//                color = Primary,
//                modifier = Modifier
//                    .clip(RoundedCornerShape(50))
//                    .background(Background)
//                    .clickable(onClick = onShowRaw)
//                    .padding(horizontal = 14.dp, vertical = 10.dp)
//            )
        }
    }
}

/** 이수구분 하나 — 헤더(구분명/기준 대비 취득학점/과목수)를 누르면 실제 들은 과목 목록이 펼쳐진다.
 *  과목이 하나도 없는 구분도 그대로 보여준다(펼쳐도 안내 문구만 나옴). 기준학점 정보가 없는
 *  구분(일선/교직 — 리포트의 졸업사정 표에 아예 없는 항목)은 "기준" 없이 취득학점만 보여준다.
 *  교선(교양선택)은 교양영역(1~9, A/B/C, 실용/외국어/심화)별 기준·취득도 같이 보여준다. */
@Composable
private fun GraduationCategorySection(
    category: String,
    requirement: GraduationRequirement?,
    rows: List<TranscriptCourseRow>,
    areaRequirements: List<GraduationRequirement>
) {
    var expanded by remember { mutableStateOf(false) }
    val earned = requirement?.earnedValue ?: formatCredits(rows.sumOf { it.credit })
    val required = requirement?.requiredValue

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = category, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Primary)) { append(earned) }
                    withStyle(SpanStyle(color = TextSecondary)) {
                        append(if (required != null) "/${required}학점 · ${rows.size}과목" else "학점 · ${rows.size}과목")
                    }
                },
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary
            )
        }

        if (expanded) {
            if (areaRequirements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "교양영역별 기준/취득학점", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                GraduationAreaChips(areaRequirements)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (rows.isEmpty()) {
                Text(text = "들은 과목이 없어요", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row -> GraduationCourseRow(row) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraduationAreaChips(areaRequirements: List<GraduationRequirement>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        areaRequirements.forEach { area ->
            val earned = area.earnedValue ?: "0"
            val required = area.requiredValue ?: "0"
            // 순수 숫자 영역(1~9)은 "1영역"처럼 보이게, A/B/C·실용·외국어·심화 같은 이름은 그대로 둔다.
            val label = if (area.label.toIntOrNull() != null) "${area.label}영역" else area.label
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Background)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextPrimary)) { append("$label ") }
                        withStyle(SpanStyle(color = Primary)) { append(earned) }
                        withStyle(SpanStyle(color = TextPrimary)) { append("/$required") }
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun GraduationCourseRow(row: TranscriptCourseRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Background)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.courseName,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = listOfNotNull(row.term, row.subArea, "${formatCredits(row.credit)}학점").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = row.grade, style = MaterialTheme.typography.labelMedium, color = Primary)
    }
}

// ---- 이수구분표 원본 — 학교 리포트 뷰어(crownix-viewer.js)와 같은 좌표 변환 공식으로 다시
// 배치해서, 파싱하지 않은 원본 모양 그대로 확대/축소/이동하며 볼 수 있게 한다 ----

/** crownix 뷰어의 `Painter.adjustCoord`를 그대로 옮긴 것 — MML 좌표는 1px ≈ 10.3 단위이고,
 *  기본 여백(margin)이 24.3px 더해진다(뷰어 소스: `Math.floor(mmlCoord / 10.3 + 24.3)`). */
private const val MML_UNIT_SCALE = 10.3f
private const val MML_MARGIN = 24.3f
private const val MML_CANVAS_PADDING = 40f
private const val MML_PT_TO_SP = 4f / 3f // 뷰어가 CSS pt로 그리는 걸 화면 px 기준(96dpi)으로 흉내

private fun adjustCoord(mmlCoord: Int): Float = mmlCoord / MML_UNIT_SCALE + MML_MARGIN
private fun mmlToDp(v: Int): Dp = adjustCoord(v).dp

private fun parseHexColor(hex: String): Color = try {
    Color(AndroidColor.parseColor(hex))
} catch (e: IllegalArgumentException) {
    Color.Black
}

@Composable
private fun RawTranscriptDialog(
    rawCells: List<RawCell>,
    rawLines: List<RawLine>,
    rawRects: List<RawRect>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Background)) {
        ResponsiveContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    IconArrowBack(tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "이수구분표 원본", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
            Text(
                text = "손가락으로 확대·축소하고 움직여서 볼 수 있어요",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (rawCells.isEmpty()) {
                GraduationMessageBox {
                    Text("원본 데이터가 없어요", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            } else {
                val maxLe = listOfNotNull(
                    rawCells.maxOfOrNull { it.ri },
                    rawLines.maxOfOrNull { maxOf(it.sx, it.ex) },
                    rawRects.maxOfOrNull { it.ex }
                ).max()
                val maxTo = listOfNotNull(
                    rawCells.maxOfOrNull { it.bo },
                    rawLines.maxOfOrNull { maxOf(it.sy, it.ey) },
                    rawRects.maxOfOrNull { it.ey }
                ).max()
                val canvasWidth = mmlToDp(maxLe) + MML_CANVAS_PADDING.dp
                val canvasHeight = mmlToDp(maxTo) + MML_CANVAS_PADDING.dp

                ZoomablePannable(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    contentWidth = canvasWidth,
                    contentHeight = canvasHeight
                ) {
                    Box(
                        modifier = Modifier
                            .size(canvasWidth, canvasHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface)
                    ) {
                        // RA(배경/테두리)와 LN(구분선)을 원본처럼 텍스트 아래 한 레이어에 먼저 그린다.
                        Canvas(modifier = Modifier.matchParentSize()) {
                            rawRects.forEach { rect ->
                                val left = adjustCoord(rect.sx).dp.toPx()
                                val top = adjustCoord(rect.sy).dp.toPx()
                                val right = adjustCoord(rect.ex).dp.toPx()
                                val bottom = adjustCoord(rect.ey).dp.toPx()
                                rect.fillColor?.let { fc ->
                                    drawRect(
                                        color = parseHexColor(fc),
                                        topLeft = Offset(left, top),
                                        size = Size(right - left, bottom - top)
                                    )
                                }
                                if (rect.lineWidthMml > 0f) {
                                    drawRect(
                                        color = parseHexColor(rect.lineColor ?: "#000000"),
                                        topLeft = Offset(left, top),
                                        size = Size(right - left, bottom - top),
                                        style = Stroke(width = (rect.lineWidthMml / MML_UNIT_SCALE).dp.toPx())
                                    )
                                }
                            }
                            rawLines.forEach { line ->
                                drawLine(
                                    color = parseHexColor(line.color),
                                    start = Offset(adjustCoord(line.sx).dp.toPx(), adjustCoord(line.sy).dp.toPx()),
                                    end = Offset(adjustCoord(line.ex).dp.toPx(), adjustCoord(line.ey).dp.toPx()),
                                    strokeWidth = (line.widthMml / MML_UNIT_SCALE).dp.toPx()
                                )
                            }
                        }

                        rawCells.forEach { cell ->
                            val left = adjustCoord(cell.le)
                            val top = adjustCoord(cell.to) - 1
                            val right = adjustCoord(cell.ri)
                            val bottom = adjustCoord(cell.bo) + 1
                            val boxAlign = when (cell.ha) {
                                1 -> Alignment.Center
                                2 -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            }
                            val textAlign = when (cell.ha) {
                                1 -> TextAlign.Center
                                2 -> TextAlign.End
                                else -> TextAlign.Start
                            }
                            Box(
                                modifier = Modifier
                                    .offset(x = left.dp, y = top.dp)
                                    .size((right - left).coerceAtLeast(1f).dp, (bottom - top).coerceAtLeast(1f).dp),
                                contentAlignment = boxAlign
                            ) {
                                Text(
                                    text = cell.text,
                                    fontSize = (cell.fontSizePt * MML_PT_TO_SP).sp,
                                    fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                                    color = parseHexColor(cell.color),
                                    textAlign = textAlign,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth()
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

/** 핀치로 확대/축소하고, 그 상태로 드래그해서 이동할 수 있게 하는 뷰포트 — [contentWidth]가 화면
 *  폭보다 훨씬 넓은 원본 표를 위한 것이라, 처음엔 화면 폭에 맞춰 축소해서 전체가 한눈에 들어오게
 *  시작하고, 거기서부터 최대 4배까지 확대할 수 있다. */
@Composable
private fun ZoomablePannable(
    modifier: Modifier = Modifier,
    contentWidth: Dp,
    contentHeight: Dp,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val fitScale = remember(maxWidth, contentWidth) { (maxWidth / contentWidth).coerceAtMost(1f) }
        var scale by remember(fitScale) { mutableFloatStateOf(fitScale) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(fitScale, 4f)
                        offset += pan
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .size(contentWidth, contentHeight)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                        transformOrigin = TransformOrigin(0f, 0f)
                    )
            ) {
                content()
            }
        }
    }
}

private fun formatCredits(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
