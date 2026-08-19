package com.daejin.woojintoday.ui.screens.home

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daejin.woojintoday.data.model.Notice
import com.daejin.woojintoday.data.network.MileageEligibility
import com.daejin.woojintoday.data.network.MileageHistoryEntry
import com.daejin.woojintoday.data.network.MileageScholarshipClient
import com.daejin.woojintoday.data.network.MileageSummary
import com.daejin.woojintoday.ui.components.ResponsiveContainer
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.screens.timetable.MileageNoticeItem
import com.daejin.woojintoday.ui.screens.timetable.MileageScholarshipViewModel
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary

/** "마일리지" 카드 — T-WIN(together.daejin.ac.kr)의 내 마일리지 점수/순위/전체 top5, 지급내역,
 *  그리고 공지사항 다이얼로그의 "마일리지" 섹션과 동일한 대상자 확인 기능("새 글 알림 받기"만 제외)을
 *  한 화면에 모아 보여준다. */
@Composable
fun MileageStatusDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val statusViewModel: MileageStatusViewModel = viewModel(factory = MileageStatusViewModel.Factory(context))
    val mileageViewModel: MileageScholarshipViewModel =
        viewModel(factory = MileageScholarshipViewModel.Factory(context))

    var selectedPath by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedPath != null) {
        selectedPath = null
    }

    var selectedSection by remember { mutableStateOf(MileageSection.STATUS) }

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
                IconButton(
                    onClick = {
                        if (selectedPath != null) selectedPath = null else onDismiss()
                    }
                ) {
                    IconArrowBack(tint = TextPrimary)
                }
            }

            if (selectedPath == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MileageSectionSwitcher(selected = selectedSection, onSelect = { selectedSection = it })
                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedContent(targetState = selectedSection, label = "mileageSectionContent") { section ->
                        when (section) {
                            MileageSection.STATUS -> Column {
                                MileageSummarySection(
                                    summary = statusViewModel.summary,
                                    isLoading = statusViewModel.isLoading,
                                    errorMessage = statusViewModel.errorMessage
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                MileageHistorySection(
                                    entries = statusViewModel.history,
                                    isLoading = statusViewModel.isLoading,
                                    errorMessage = statusViewModel.errorMessage
                                )
                            }
                            MileageSection.SCHOLARSHIP -> {
                                // "대상자 확인" 버튼이 뜨는(=xlsx 명단이 첨부된) 글만 추려서 보여준다 —
                                // 시행 안내처럼 버튼이 안 뜨는 글은 이 화면에서는 굳이 안 보여줘도 된다.
                                MileageNoticeListColumn(
                                    items = mileageViewModel.items.filter { it.attachment != null },
                                    isLoading = mileageViewModel.isLoading,
                                    errorMessage = mileageViewModel.errorMessage,
                                    onSelect = { path -> selectedPath = path },
                                    onCheckClick = { item -> mileageViewModel.checkEligibility(item) }
                                )
                            }
                        }
                    }

                    // 화면 맨 끝에서 바로 스크롤이 끊기지 않게 여백을 더 둔다.
                    Spacer(modifier = Modifier.height(72.dp))
                }
            } else {
                MileageNoticeDetailWebView(
                    url = MileageScholarshipClient.BASE_URL + selectedPath,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                )
            }
        }
        }
        }
    }

    val checkingTitle = mileageViewModel.checkingNoticeTitle
    if (checkingTitle != null) {
        MileageEligibilityResultDialog(
            noticeTitle = checkingTitle,
            result = mileageViewModel.eligibilityResult,
            onDismiss = { mileageViewModel.dismissEligibilityResult() }
        )
    }
}

/** 순위를 한눈에 딱 들어오게 큰 숫자로 먼저 보여주고, TOP5는 막대그래프로 내가 그 사이 어디쯤인지
 *  바로 감이 오게 한다(1위 점수를 기준으로 상대 길이를 계산). */
@Composable
private fun MileageSummarySection(summary: MileageSummary?, isLoading: Boolean, errorMessage: String?) {
    when {
        isLoading -> MileageLoadingOrEmptyBox { CircularProgressIndicator(color = Primary) }
        errorMessage != null && summary == null -> MileageLoadingOrEmptyBox {
            Text(text = errorMessage, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        summary != null -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .padding(20.dp)
            ) {
                Text(text = "나의 마일리지 순위", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${summary.myRank}",
                        style = MaterialTheme.typography.displaySmall,
                        color = Primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "위",
                        style = MaterialTheme.typography.titleMedium,
                        color = Primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "나의 마일리지", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            text = "${summary.myScore}점",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                }

                if (summary.top5.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "전체 TOP 5", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    val maxScore = summary.top5.max().toFloat()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        summary.top5.forEachIndexed { index, score ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${index + 1}위",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.width(28.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Background)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = (score / maxScore).coerceIn(0f, 1f))
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Primary)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "$score",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.width(36.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
        else -> MileageLoadingOrEmptyBox {
            Text(text = "마일리지 정보가 없어요", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

private enum class MileageSection(val displayName: String) {
    STATUS("마일리지 현황"),
    SCHOLARSHIP("장학 대상자 확인")
}

/** 공지사항/지도교수 상담 다이얼로그와 같은 방식의 가로 세그먼트 스위처 — 알약(pill) 모양
 *  배경이 고른 쪽으로 스르륵 미끄러진다. */
@Composable
private fun MileageSectionSwitcher(selected: MileageSection, onSelect: (MileageSection) -> Unit) {
    val targetFraction = if (selected == MileageSection.STATUS) 0f else 1f
    val fraction by animateFloatAsState(targetValue = targetFraction, label = "mileageSectionIndicator")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / 2
        Box(
            modifier = Modifier
                .offset(x = tabWidth * fraction)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(Primary)
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            MileageSectionTab(
                label = MileageSection.STATUS.displayName,
                selected = selected == MileageSection.STATUS,
                onClick = { onSelect(MileageSection.STATUS) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MileageSectionTab(
                label = MileageSection.SCHOLARSHIP.displayName,
                selected = selected == MileageSection.SCHOLARSHIP,
                onClick = { onSelect(MileageSection.SCHOLARSHIP) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun MileageSectionTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val textColor by animateColorAsState(targetValue = if (selected) OnPrimary else TextSecondary, label = "mileageSectionTabText")
    Box(modifier = modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MileageHistorySection(entries: List<MileageHistoryEntry>, isLoading: Boolean, errorMessage: String?) {
    when {
        isLoading -> MileageLoadingOrEmptyBox { CircularProgressIndicator(color = Primary) }
        errorMessage != null && entries.isEmpty() -> MileageLoadingOrEmptyBox {
            Text(text = errorMessage, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        entries.isEmpty() -> MileageLoadingOrEmptyBox {
            Text(text = "지급 내역이 없어요", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        entries.size <= 5 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { entry -> MileageHistoryRow(entry) }
        }
        else -> {
            // 한 번에 최대 5개까지만 보여주고, 그 이상은 옆으로 넘겨서 보게 페이지를 나눈다.
            val chunks = remember(entries) { entries.chunked(5) }
            val pagerState = rememberPagerState(pageCount = { chunks.size })
            AutoHeightHorizontalPager(pagerState = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunks[page].forEach { entry -> MileageHistoryRow(entry) }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            MileagePagerDots(
                pageCount = chunks.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MileagePagerDots(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (selected) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (selected) Primary else Border)
            )
        }
    }
}

/** HorizontalPager는 스스로 "지금 보이는 페이지 내용에 맞춰 높이를 줄였다 늘렸다" 하지 못한다 —
 *  안 보이는 곳에서 지금 페이지와 똑같은 내용을 높이 제한 없이 한 번 더 그려서 실제로 필요한
 *  높이를 재고, 진짜 보여줄 Pager는 그 높이로 딱 맞춰 그린다(지도교수 상담 다이얼로그와 동일한
 *  패턴). */
@Composable
private fun AutoHeightHorizontalPager(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    pageContent: @Composable (page: Int) -> Unit
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val measuredHeight = subcompose("measure") { pageContent(pagerState.currentPage) }
            .maxOf { it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)).height }

        val pagerPlaceables = subcompose("pager") {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page -> pageContent(page) }
        }.map { it.measure(constraints.copy(minHeight = measuredHeight, maxHeight = measuredHeight)) }

        layout(constraints.maxWidth, measuredHeight) {
            pagerPlaceables.forEach { it.place(0, 0) }
        }
    }
}

@Composable
private fun MileageHistoryRow(entry: MileageHistoryEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.activityName,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${entry.category} · ${entry.date}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "+${entry.points}", style = MaterialTheme.typography.labelMedium, color = Primary)
    }
}

@Composable
private fun MileageLoadingOrEmptyBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

// ---- 아래는 NoticeBoardDialog.kt의 "마일리지" 섹션과 동일한 UI("새 글 알림 받기" 토글만 제외) ----

@Composable
private fun MileageNoticeListColumn(
    items: List<MileageNoticeItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onSelect: (String) -> Unit,
    onCheckClick: (MileageNoticeItem) -> Unit
) {
    when {
        isLoading -> MileageLoadingOrEmptyBox { CircularProgressIndicator(color = Primary) }
        errorMessage != null && items.isEmpty() -> MileageLoadingOrEmptyBox {
            Text(text = errorMessage, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                MileageNoticeRow(
                    item = item,
                    onClick = { onSelect(item.notice.detailPath) },
                    onCheckClick = { onCheckClick(item) }
                )
            }
        }
    }
}

private fun mileageRowContainerModifier(onClick: () -> Unit): Modifier = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(10.dp))
    .background(Surface)
    .clickable(onClick = onClick)
    .padding(12.dp)

@Composable
private fun MileageNoticeRowContent(notice: Notice, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = notice.title,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (notice.isNew) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("new", style = MaterialTheme.typography.bodySmall, color = OnPrimary)
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${notice.writer} · ${notice.date} · 조회 ${notice.views}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MileageNoticeRow(item: MileageNoticeItem, onClick: () -> Unit, onCheckClick: () -> Unit) {
    Row(modifier = mileageRowContainerModifier(onClick), verticalAlignment = Alignment.CenterVertically) {
        MileageNoticeRowContent(notice = item.notice, modifier = Modifier.weight(1f))
        if (item.attachment != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "대상자 확인",
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, Primary, RoundedCornerShape(50))
                    .clickable(onClick = onCheckClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun MileageEligibilityResultDialog(
    noticeTitle: String,
    result: MileageEligibility?,
    onDismiss: () -> Unit
) {
    var showSheetValues by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = noticeTitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            when (result) {
                null -> {
                    CircularProgressIndicator(color = Primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "명단 확인 중...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
                is MileageEligibility.Found -> {
                    Text(
                        text = "마일리지 장학 대상자입니다!",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
                is MileageEligibility.NotFound -> {
                    Text(
                        text = "아쉽게도 대상자 명단에 없어요..",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
                is MileageEligibility.Error -> {
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MileageDialogPillButton(text = "확인", filled = true, onClick = onDismiss)
                if (result is MileageEligibility.NotFound) {
                    MileageDialogPillButton(text = "명단보기", filled = false, onClick = { showSheetValues = true })
                }
            }
        }
    }

    if (showSheetValues && result is MileageEligibility.NotFound) {
        // 명단 모달을 닫을 때 뒤에 남아있던 "대상자 명단에 없어요" 결과 모달도 같이 닫는다 —
        // 굳이 확인을 한 번 더 누를 필요 없이 명단보기→닫기 한 번으로 전부 닫히게.
        MileageSheetValuesDialog(
            values = result.values,
            onDismiss = {
                showSheetValues = false
                onDismiss()
            }
        )
    }
}

@Composable
private fun MileageSheetValuesDialog(values: List<String>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, values) {
        if (query.isBlank()) values else values.filter { it.contains(query) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(20.dp)
        ) {
            Text(
                text = "명단 (${values.size}명)",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("학번 검색") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Primary
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "검색 결과가 없어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { value ->
                        val highlighted = query.isNotBlank() && value.contains(query)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (highlighted) Primary else Background)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (highlighted) OnPrimary else TextPrimary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            MileageDialogPillButton(text = "닫기", filled = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun MileageDialogPillButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (filled) Modifier.background(Primary) else Modifier.border(1.dp, Primary, RoundedCornerShape(50))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) OnPrimary else Primary
        )
    }
}

/** 마일리지 게시판(189번)은 로그인 없이도 상세보기가 열려서(실측 확인됨), NoticeBoardDialog의
 *  종합강의시간표용 재로그인 분기 없이 그냥 바로 연다. */
@Composable
private fun MileageNoticeDetailWebView(url: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
                        val cookie = CookieManager.getInstance().getCookie(downloadUrl)
                        val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                            addRequestHeader("Cookie", cookie)
                            addRequestHeader("User-Agent", userAgent)
                            setMimeType(mimeType)
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                        }
                        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                        Toast.makeText(context, "다운로드를 시작합니다: $fileName", Toast.LENGTH_SHORT).show()
                    }
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            }
        )
    }
}
