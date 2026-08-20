package com.daejin.woojintoday.ui.screens.home

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
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
    var selectedCategory by remember { mutableStateOf<String?>(null) }

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
            ) {
                when {
                    viewModel.isLoading -> GraduationSkeleton()
                    viewModel.errorMessage != null && detail == null ->
                        GraduationMessageBox {
                            Text(viewModel.errorMessage.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    detail != null -> {
                        // 주전공은 전필+전선 과목을 합친 것 — 기준학점은 복수전공/부전공 여부나
                        // 학번마다 달라져서 하나로 특정할 수 없으므로 아예 표시하지 않는다(취득만).
                        val majorRows = remember(detail) {
                            detail.rows.filter { it.category == "전필" || it.category == "전선" }
                        }
                        val majorEarnedText = remember(majorRows) { formatCredits(majorRows.sumOf { it.credit }) }

                        // 복수전공은 이수구분표엔 없고, 과목이 "복필"/"복선"으로 따로 들어오는 걸
                        // 모아서 만든다 — 취득학점은 그 과목들 학점 합, 기준학점은 졸업사정
                        // 기준표의 "복전" 항목 값을 그대로 쓴다.
                        val minorRows = remember(detail) {
                            detail.rows.filter { it.category == "복필" || it.category == "복선" }
                        }
                        val minorEarnedText = remember(minorRows) { formatCredits(minorRows.sumOf { it.credit }) }
                        val minorRequiredText = detail.requirements.find { it.label == "복전" }?.requiredValue

                        // 총 이수학점 게이지는 고정, 그 아래 이수구분 카드/교양영역 표만 내부
                        // 스크롤된다 — 게이지와 카드 사이 간격은 스크롤 밖(고정 영역)에 있어서
                        // 스크롤해도 그대로 유지된다.
                        GraduationCreditGauge(
                            detail = detail,
                            majorEarnedText = majorEarnedText,
                            minorEarnedText = minorEarnedText,
                            minorRequiredText = minorRequiredText,
                            onSelect = { category -> selectedCategory = category }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            GraduationCategoryScrollRow(
                                rowsByCategory = viewModel.rowsByCategory,
                                requirements = detail.requirements,
                                majorRows = majorRows,
                                majorEarnedText = majorEarnedText,
                                minorRows = minorRows,
                                minorEarnedText = minorEarnedText,
                                minorRequiredText = minorRequiredText,
                                onSelect = { category -> selectedCategory = category }
                            )

                            if (detail.areaRequirements.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                GraduationAreaTable(areaRequirements = detail.areaRequirements)
                            }

                            if (detail.notes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                GraduationNotesSection(notes = detail.notes)
                            }

                            // 화면 맨 끝에서 바로 스크롤이 끊기지 않게, 카드 하나 정도의 여백을 더 둔다.
                            Spacer(modifier = Modifier.height(GraduationCategoryCardHeight))
                        }
                    }
                }
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

    val category = selectedCategory
    if (category != null && detail != null) {
        when (category) {
            "주전공" -> GraduationCategoryDetailDialog(
                category = category,
                requirement = GraduationRequirement(
                    label = category,
                    requiredValue = null,
                    earnedValue = formatCredits(detail.rows.filter { it.category == "전필" || it.category == "전선" }.sumOf { it.credit })
                ),
                rows = detail.rows.filter { it.category == "전필" || it.category == "전선" },
                accentColor = MinorColor,
                onDismiss = { selectedCategory = null }
            )
            "복수전공" -> GraduationCategoryDetailDialog(
                category = category,
                requirement = GraduationRequirement(
                    label = category,
                    requiredValue = detail.requirements.find { it.label == "복전" }?.requiredValue,
                    earnedValue = formatCredits(detail.rows.filter { it.category == "복필" || it.category == "복선" }.sumOf { it.credit })
                ),
                rows = detail.rows.filter { it.category == "복필" || it.category == "복선" },
                accentColor = MinorColor,
                onDismiss = { selectedCategory = null }
            )
            else -> GraduationCategoryDetailDialog(
                category = category,
                requirement = detail.requirements.find { it.label == category },
                rows = viewModel.rowsByCategory.find { it.first == category }?.second.orEmpty(),
                onDismiss = { selectedCategory = null }
            )
        }
    }
}

@Composable
private fun GraduationMessageBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

/** 데이터가 아직 없을 때, 실제 화면(게이지 카드/이수구분 카드 줄/영역별 표)과 같은 모양의 뼈대를
 *  숨쉬듯 깜빡이는 채로 먼저 보여준다. */
@Composable
private fun GraduationSkeleton() {
    val transition = rememberInfiniteTransition(label = "graduationSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "graduationSkeletonAlpha"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SkeletonBlock(width = 70.dp, height = 12.dp, alpha = alpha)
            Spacer(modifier = Modifier.height(14.dp))
            SkeletonBlock(width = 150.dp, height = 150.dp, alpha = alpha, shape = CircleShape)
            Spacer(modifier = Modifier.height(14.dp))
            SkeletonBlock(width = 120.dp, height = 14.dp, alpha = alpha)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) {
                Column(
                    modifier = Modifier
                        .width(GraduationCategoryCardWidth)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .padding(16.dp)
                ) {
                    SkeletonBlock(width = 40.dp, height = 14.dp, alpha = alpha)
                    Spacer(modifier = Modifier.height(10.dp))
                    SkeletonBlock(width = 70.dp, height = 18.dp, alpha = alpha)
                    Spacer(modifier = Modifier.height(4.dp))
                    SkeletonBlock(width = 50.dp, height = 12.dp, alpha = alpha)
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .padding(16.dp)
        ) {
            SkeletonBlock(width = 100.dp, height = 14.dp, alpha = alpha)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(6) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SkeletonBlock(width = 32.dp, height = 10.dp, alpha = alpha)
                        Spacer(modifier = Modifier.height(6.dp))
                        SkeletonBlock(width = 28.dp, height = 12.dp, alpha = alpha)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(width: Dp, height: Dp, alpha: Float, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .alpha(alpha)
            .clip(shape)
            .background(Background)
    )
}

/** 총 이수학점(주황, 가운데)을 중심으로 왼쪽엔 주전공(전필+전선 합, 주황), 오른쪽엔 복수전공
 *  (복필+복선 합, 파랑) 취득학점을 작은 원형 게이지로 나란히 붙여서 보여준다 — 양옆 링은 가운데
 *  링의 60% 크기. 주전공은 기준학점이 복수전공/부전공 여부·학번마다 달라서 표시하지 않고 취득만
 *  보여준다. 졸업평점은 세 링 아래, 가운데 정렬로 놓는다. */
@Composable
private fun GraduationCreditGauge(
    detail: TranscriptDetail,
    majorEarnedText: String,
    minorEarnedText: String,
    minorRequiredText: String?,
    onSelect: (String) -> Unit
) {
    val totalReq = detail.requirements.find { it.label == "졸업학점" }
    val gpaReq = detail.requirements.find { it.label == "졸업평점평균" }
    val earnedText = totalReq?.earnedValue ?: formatCredits(detail.rows.sumOf { it.credit })
    val requiredText = totalReq?.requiredValue
    val fraction = remember(earnedText, requiredText) {
        val earnedValue = earnedText.toDoubleOrNull() ?: 0.0
        val requiredValue = requiredText?.toDoubleOrNull()
        if (requiredValue != null && requiredValue > 0) (earnedValue / requiredValue).toFloat().coerceIn(0f, 1f) else 0f
    }
    val minorFraction = remember(minorEarnedText, minorRequiredText) {
        val earnedValue = minorEarnedText.toDoubleOrNull() ?: 0.0
        val requiredValue = minorRequiredText?.toDoubleOrNull()
        if (requiredValue != null && requiredValue > 0) (earnedValue / requiredValue).toFloat().coerceIn(0f, 1f) else 0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "총 이수학점", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(14.dp))
        // Row는 자식을 순서대로 배치하면서 남는 폭만 뒤쪽 자식에게 넘기는 방식이라, 세 원의
        // dp 합이 카드 폭을 넘는 화면(좁은 폰)에서는 맨 뒤(복수전공)만 남은 자투리 폭으로
        // 찌그러져 작아 보였다 — 양옆 두 원을 weight(1f)로 동일하게 나눠 가지는 폭에 딱 맞게
        // (aspectRatio 1:1) 그려서, 화면 폭이 얼마든 항상 서로 같은 크기·대칭이 되도록 한다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).clickable { onSelect("주전공") }
            ) {
                CreditGaugeRing(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    strokeWidth = MINOR_GAUGE_STROKE,
                    color = MinorColor,
                    fraction = 0f,
                    earnedText = majorEarnedText,
                    requiredText = null,
                    valueStyle = MaterialTheme.typography.titleMedium,
                    captionStyle = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "주전공", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            CreditGaugeRing(
                modifier = Modifier.size(MAJOR_GAUGE_SIZE),
                strokeWidth = MAJOR_GAUGE_STROKE,
                color = Primary,
                fraction = fraction,
                earnedText = earnedText,
                requiredText = requiredText,
                valueStyle = MaterialTheme.typography.titleLarge,
                captionStyle = MaterialTheme.typography.bodySmall
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).clickable { onSelect("복수전공") }
            ) {
                CreditGaugeRing(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    strokeWidth = MINOR_GAUGE_STROKE,
                    color = MinorColor,
                    fraction = minorFraction,
                    earnedText = minorEarnedText,
                    requiredText = minorRequiredText,
                    valueStyle = MaterialTheme.typography.titleMedium,
                    captionStyle = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "복수전공", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        if (gpaReq?.earnedValue != null) {
            Spacer(modifier = Modifier.height(14.dp))
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
}

private val MAJOR_GAUGE_SIZE = 150.dp
private val MAJOR_GAUGE_STROKE = 16.dp
private val MINOR_GAUGE_STROKE = 10.dp

/** 원형 게이지 하나 — 링 자체는 기준학점 대비 취득 비율을 채우고, 안쪽엔 취득/기준 학점을
 *  텍스트로 보여준다. 주전공/부전공 게이지가 같은 모양을 크기만 다르게 재사용한다. */
@Composable
private fun CreditGaugeRing(
    modifier: Modifier,
    strokeWidth: Dp,
    color: Color,
    fraction: Float,
    earnedText: String,
    requiredText: String?,
    valueStyle: TextStyle,
    captionStyle: TextStyle
) {
    // 화면에 들어올 때 0에서 실제 값까지 링이 자연스럽게 채워지도록 애니메이션.
    val animatedFraction = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        animatedFraction.animateTo(fraction, animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val diameter = size.minDimension - strokePx
            val arcTopLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = Background,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (animatedFraction.value > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedFraction.value,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = color)) { append(earnedText) }
                    withStyle(SpanStyle(color = TextSecondary)) { append(if (requiredText != null) "/$requiredText" else "") }
                },
                style = valueStyle
            )
            Text(text = "학점", style = captionStyle, color = TextSecondary)
        }
    }
}

// 부전공(복수전공) 전용 강조색 — 주전공 게이지/카드에 쓰는 Primary(주황)와 구분되도록 파란색을 쓴다.
private val MinorColor = Color(0xFF3B82F6)

// 카드 하나의 너비/높이 — 스크롤 영역 맨 아래 여백(카드 하나 세로폭)에도 그대로 재사용한다.
private val GraduationCategoryCardWidth = 130.dp
private val GraduationCategoryCardHeight = 150.dp

/** 이수구분(교필/교선/일선/전필/전선/전기/교직)마다 카드 하나 — 가로로 쭉 스크롤해서 훑어보고,
 *  카드를 누르면 그 구분에 들은 과목 전체를 모달로 띄워서 보여준다. */
@Composable
private fun GraduationCategoryScrollRow(
    rowsByCategory: List<Pair<String, List<TranscriptCourseRow>>>,
    requirements: List<GraduationRequirement>,
    majorRows: List<TranscriptCourseRow>,
    majorEarnedText: String,
    minorRows: List<TranscriptCourseRow>,
    minorEarnedText: String,
    minorRequiredText: String?,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rowsByCategory.forEach { (category, rows) ->
            val requirement = requirements.find { it.label == category }
            GraduationCategoryCard(
                category = category,
                earned = requirement?.earnedValue ?: formatCredits(rows.sumOf { it.credit }),
                required = requirement?.requiredValue,
                courseCount = rows.size,
                onClick = { onSelect(category) }
            )
        }
        // 주전공/복수전공은 이수구분표에 없는 별도 항목이라 카드 두 개로 맨 뒤에 붙인다.
        GraduationCategoryCard(
            category = "주전공",
            earned = majorEarnedText,
            required = null,
            courseCount = majorRows.size,
            accentColor = MinorColor,
            onClick = { onSelect("주전공") }
        )
        GraduationCategoryCard(
            category = "복수전공",
            earned = minorEarnedText,
            required = minorRequiredText,
            courseCount = minorRows.size,
            accentColor = MinorColor,
            onClick = { onSelect("복수전공") }
        )
    }
}

@Composable
private fun GraduationCategoryCard(
    category: String,
    earned: String,
    required: String?,
    courseCount: Int,
    onClick: () -> Unit,
    accentColor: Color = Primary
) {
    Column(
        modifier = Modifier
            .width(GraduationCategoryCardWidth)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(text = category, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = accentColor)) { append(earned) }
                withStyle(SpanStyle(color = TextSecondary)) { append(if (required != null) "/${required}학점" else "학점") }
            },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "${courseCount}과목", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

/** 카드를 눌렀을 때 뜨는 모달 — 그 이수구분에 들은 과목 전체를 보여준다(기존 아코디언 펼침
 *  내용과 동일). */
@Composable
private fun GraduationCategoryDetailDialog(
    category: String,
    requirement: GraduationRequirement?,
    rows: List<TranscriptCourseRow>,
    onDismiss: () -> Unit,
    accentColor: Color = Primary
) {
    val earned = requirement?.earnedValue ?: formatCredits(rows.sumOf { it.credit })
    val required = requirement?.requiredValue

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = accentColor)) { append(earned) }
                        withStyle(SpanStyle(color = TextSecondary)) {
                            append(if (required != null) "/${required}학점 · ${rows.size}과목" else "학점 · ${rows.size}과목")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (rows.isEmpty()) {
                Text(text = "들은 과목이 없어요", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    rows.forEach { row -> GraduationCourseRow(row) }
                }
            }
        }
    }
}

/** 교양영역(1~9, A/B/C, 실용/외국어/심화)별 기준·취득 학점 — 교선 카드 안에 묶지 않고, 화면
 *  맨 아래에 표 형태로 따로 보여준다. */
@Composable
private fun GraduationAreaTable(areaRequirements: List<GraduationRequirement>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(16.dp)
    ) {
        Text(text = "영역별 이수현황", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            areaRequirements.forEachIndexed { index, area ->
                val earned = area.earnedValue ?: "0"
                val required = area.requiredValue ?: "0"
                // 순수 숫자 영역(1~9)은 "1영역"처럼 보이게, A/B/C·실용·외국어·심화 같은 이름은 그대로 둔다.
                val label = if (area.label.toIntOrNull() != null) "${area.label}영역" else area.label
                Column(
                    modifier = Modifier.width(64.dp).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Primary)) { append(earned) }
                            withStyle(SpanStyle(color = TextSecondary)) { append("/$required") }
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (index != areaRequirements.lastIndex) {
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(Background))
                }
            }
        }
    }
}

/** 리포트 하단의 "1. 학생의 졸업 사정 적용 기준년도는..." 같은 번호 매겨진 안내 문구를 그대로
 *  보여준다. */
@Composable
private fun GraduationNotesSection(notes: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        notes.forEach { note ->
            Text(text = note, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun GraduationCourseRow(row: TranscriptCourseRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
        // 성적 폭을 "A+" 기준으로 고정하고 왼쪽 정렬해서, "A"/"P"처럼 짧은 성적도 항상 같은
        // x좌표에서 시작하게 한다(짧은 성적이 오른쪽 끝에 붙어 보이지 않도록).
        Text(
            text = row.grade,
            style = MaterialTheme.typography.labelMedium,
            color = Primary,
            textAlign = TextAlign.Start,
            modifier = Modifier.width(28.dp)
        )
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
