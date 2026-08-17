package com.daejin.woojintoday.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daejin.woojintoday.data.network.AdvisorCounselStatus
import com.daejin.woojintoday.data.network.CounselDetail
import com.daejin.woojintoday.data.network.CounselHistoryEntry
import com.daejin.woojintoday.data.network.ProfessorSearchResult
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private enum class ProfessorCounselSection { STATUS, APPLY }

/** "지도교수 상담" 카드 — 내 지도교수님 성함, 졸업까지 채워야 하는 지도교수 상담 인정 횟수(학년·학기별),
 *  그리고 실제 온라인 상담 신청까지 한 화면에서 처리한다. 다른 정보 조회 다이얼로그들과 같은
 *  뒤로가기 화살표 + 스크롤 구조. */
@Composable
fun ProfessorCounselDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ProfessorCounselViewModel = viewModel(factory = ProfessorCounselViewModel.Factory(context))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
        ) {
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
                AdvisorNameCard(
                    name = viewModel.advisorName,
                    isLoading = viewModel.isLoadingAdvisor,
                    errorMessage = viewModel.advisorErrorMessage
                )

                Spacer(modifier = Modifier.height(28.dp))

                var selectedSection by remember { mutableStateOf(ProfessorCounselSection.STATUS) }
                // 신청이 성공하면 어차피 이력이 새로고침되니, 그 결과를 바로 볼 수 있게 현황
                // 섹션으로 자동으로 넘어간다.
                LaunchedEffect(viewModel.applySucceeded) {
                    if (viewModel.applySucceeded) selectedSection = ProfessorCounselSection.STATUS
                }
                CounselSectionSwitcher(selected = selectedSection, onSelect = { selectedSection = it })
                Spacer(modifier = Modifier.height(16.dp))

                when (selectedSection) {
                    ProfessorCounselSection.STATUS -> CounselStatusCard(
                        status = viewModel.counselStatus,
                        isLoading = viewModel.isLoadingCounsel,
                        errorMessage = viewModel.counselErrorMessage,
                        history = viewModel.counselHistory,
                        isLoadingHistory = viewModel.isLoadingHistory,
                        historyErrorMessage = viewModel.historyErrorMessage,
                        onHistoryEntryClick = { entry -> viewModel.openHistoryDetail(entry.cnsKeyId) }
                    )
                    ProfessorCounselSection.APPLY -> Column {
                        ProfessorPickerSection(viewModel)
                        Spacer(modifier = Modifier.height(14.dp))
                        ProfessorCounselApplyForm(viewModel)
                    }
                }

                // 맨 아래 카드가 화면 하단에 딱 붙지 않도록, 기능 카드 하나 정도의 세로폭만큼
                // 항상 여유 스크롤 공간을 남겨둔다(모아보기 화면과 동일한 기준).
                Spacer(modifier = Modifier.height(140.dp))
            }
        }
    }

    if (viewModel.showHistoryDetail) {
        CounselHistoryDetailDialog(
            detail = viewModel.selectedHistoryDetail,
            isLoading = viewModel.isLoadingHistoryDetail,
            errorMessage = viewModel.historyDetailErrorMessage,
            onDismiss = { viewModel.dismissHistoryDetail() }
        )
    }
}

@Composable
private fun AdvisorNameCard(name: String?, isLoading: Boolean, errorMessage: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(24.dp)
    ) {
        when {
            isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "지도교수님을 찾아보고 있어요...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            name != null -> Column {
                Text(text = "나의 지도교수님", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Primary)) { append(name) }
                        withStyle(SpanStyle(color = TextPrimary)) { append(" 교수님") }
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            errorMessage != null -> Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            else -> Text(
                text = "지도교수님 정보가 없어요",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

/** "지도교수 상담 현황"/"지도교수 상담 신청" 두 섹션을 오가는 세그먼트 컨트롤 — 평범한 버튼 두 개가
 *  아니라, 알약 모양 트랙 안에서 고른 쪽만 통째로 채워지는 한 덩어리로 보이게 해서 "지금 이 섹션이
 *  선택돼 있다"는 상태가 바로 읽히게 한다. */
@Composable
private fun CounselSectionSwitcher(selected: ProfessorCounselSection, onSelect: (ProfessorCounselSection) -> Unit) {
    val targetFraction = if (selected == ProfessorCounselSection.STATUS) 0f else 1f
    val fraction by animateFloatAsState(targetValue = targetFraction, label = "sectionSwitcherIndicator")

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
            CounselSectionTab(
                label = "지도교수 상담 현황",
                selected = selected == ProfessorCounselSection.STATUS,
                onClick = { onSelect(ProfessorCounselSection.STATUS) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            CounselSectionTab(
                label = "지도교수 상담 신청",
                selected = selected == ProfessorCounselSection.APPLY,
                onClick = { onSelect(ProfessorCounselSection.APPLY) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun CounselSectionTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val textColor by animateColorAsState(targetValue = if (selected) OnPrimary else TextSecondary, label = "sectionTabText")
    Box(
        modifier = modifier
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CounselStatusCard(
    status: AdvisorCounselStatus?,
    isLoading: Boolean,
    errorMessage: String?,
    history: List<CounselHistoryEntry>,
    isLoadingHistory: Boolean,
    historyErrorMessage: String?,
    onHistoryEntryClick: (CounselHistoryEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(20.dp)
    ) {
        when {
            isLoading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                CircularProgressIndicator(color = Primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "상담 현황을 불러오고 있어요...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            status != null -> {
                Text(text = "지금까지의 상담 인정 횟수", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "${status.totalCount}", style = MaterialTheme.typography.displaySmall, color = Primary)
                    Text(
                        text = "회 / ${status.goalCount}회",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
                Spacer(modifier = Modifier.height(16.dp))

                CounselGradeTable(status)
            }
            errorMessage != null -> Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            else -> Text(
                text = "상담 현황 정보가 없어요",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
        Spacer(modifier = Modifier.height(16.dp))

        CounselHistorySection(
            history = history,
            isLoading = isLoadingHistory,
            errorMessage = historyErrorMessage,
            onEntryClick = onHistoryEntryClick
        )
    }
}

/** 상담 이력 — 세로로 쭉 늘어놓지 않고, 카드 한 장에 2건씩 담아 옆으로 스와이프해서 보게 한다
 *  (서버에서 다 받아오는 건 여전히 5건 단위 페이지 병렬 조회, 화면에 보여주는 묶음 크기만 다르다). */
@Composable
private fun CounselHistorySection(
    history: List<CounselHistoryEntry>,
    isLoading: Boolean,
    errorMessage: String?,
    onEntryClick: (CounselHistoryEntry) -> Unit
) {
    when {
        isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = Primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "상담 이력을 불러오고 있어요...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        errorMessage != null && history.isEmpty() -> Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        history.isEmpty() -> Text(
            text = "상담 이력이 없어요",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        else -> {
            val chunks = remember(history) { history.chunked(2) }
            val pagerState = rememberPagerState(pageCount = { chunks.size })
            AutoHeightHorizontalPager(pagerState = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunks[page].forEach { entry ->
                        CounselHistoryRow(entry = entry, onClick = { onEntryClick(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CounselHistoryRow(entry: CounselHistoryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.date, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${entry.method} · ${entry.status}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Text(text = "상세보기", style = MaterialTheme.typography.labelSmall, color = Primary)
    }
}

@Composable
private fun CounselHistoryDetailDialog(
    detail: CounselDetail?,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(24.dp)
        ) {
            Text(text = "상세보기", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary)
                }
                errorMessage != null -> Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                detail != null -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CounselDetailField(label = "신청일시", value = detail.date)
                    CounselDetailField(label = "상태", value = detail.status)
                    CounselDetailField(label = "신청 내용", value = detail.content)
                    // 교수가 아직 답변을 안 남겼으면(신청 직후, 학생 취소 등) 이 두 필드가 응답에
                    // 아예 없다 — 있을 때만 보여준다.
                    if (!detail.answerDate.isNullOrBlank()) {
                        CounselDetailField(label = "답변일시", value = detail.answerDate)
                    }
                    if (!detail.answerContent.isNullOrBlank()) {
                        CounselDetailField(label = "답변 내용", value = detail.answerContent)
                    }
                }
                else -> Text(
                    text = "정보를 찾을 수 없어요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Primary)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "확인", style = MaterialTheme.typography.labelLarge, color = OnPrimary)
            }
        }
    }
}

@Composable
private fun CounselDetailField(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

@Composable
private fun CounselGradeTable(status: AdvisorCounselStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            CounselTableCell(text = "학기", weight = 1f, isHeader = true)
            (1..4).forEach { grade ->
                CounselTableCell(text = "${grade}학년", weight = 1f, isHeader = true)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
        // 1·2학년은 학기당 2회, 3·4학년은 학기당 1회가 최소 기준 — "3/2"처럼 실제/기준을 나란히
        // 보여줘서 채웠는지 한눈에 보이게 한다.
        (1..2).forEach { term ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CounselTableCell(text = "${term}학기", weight = 1f)
                (1..4).forEach { grade ->
                    val count = status.countsByTermGrade[term to grade] ?: 0
                    val required = requiredCountFor(grade)
                    CounselCountCell(count = count, required = required, weight = 1f)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CounselTableCell(text = "합계", weight = 1f)
            (1..4).forEach { grade ->
                val total = (status.countsByTermGrade[1 to grade] ?: 0) + (status.countsByTermGrade[2 to grade] ?: 0)
                CounselTableCell(text = "${total}회", weight = 1f, emphasize = true)
            }
        }
    }
}

private fun requiredCountFor(grade: Int): Int = if (grade <= 2) 2 else 1

/** "3/2"처럼 실제 상담 횟수만 기준 충족 시 주황으로 강조하고, "/기준"은 항상 흰색으로 둔다.
 *  상담 기록이 없는 학기·학년도 "-" 대신 "0/기준"으로 명시해 기준이 항상 같이 보이게 한다. */
@Composable
private fun RowScope.CounselCountCell(count: Int, required: Int, weight: Float) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = if (count >= required) Primary else TextPrimary)) { append("$count") }
            withStyle(SpanStyle(color = TextPrimary)) { append("/$required") }
        },
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(weight)
    )
}

@Composable
private fun RowScope.CounselTableCell(text: String, weight: Float, isHeader: Boolean = false, emphasize: Boolean = false) {
    Text(
        text = text,
        style = if (isHeader) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        color = when {
            isHeader -> TextSecondary
            emphasize -> Primary
            else -> TextPrimary
        },
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(weight)
    )
}

/** 상담교수 선택 — 기본은 내 지도교수, "변경"을 누르면 이름 검색으로 다른 교수도 고를 수 있다.
 *  검색 결과에도 지도교수가 항상 맨 위에 "(지도교수)" 표시와 함께 고정으로 보인다. */
@Composable
private fun ProfessorPickerSection(viewModel: ProfessorCounselViewModel) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) viewModel.updateSearchQuery(viewModel.professorSearchQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(20.dp)
    ) {
        Text(text = "상담교수", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val selected = viewModel.selectedProfessor
            Text(
                text = when {
                    selected == null -> "상담교수를 선택해 주세요"
                    selected.staffNo == viewModel.advisorStaffNo -> "${selected.name} (지도교수)"
                    else -> selected.name
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "닫기" else "변경",
                style = MaterialTheme.typography.labelMedium,
                color = Primary,
                modifier = Modifier.clickable { expanded = !expanded }
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = viewModel.professorSearchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("교수님 이름으로 검색") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = professorFieldColors()
            )
            Spacer(modifier = Modifier.height(10.dp))

            val advisor = viewModel.advisorStaffNo?.let { staffNo ->
                ProfessorSearchResult(staffNo, viewModel.advisorName ?: "지도교수", "")
            }
            val displayed = remember(viewModel.professorSearchResults, advisor) {
                listOfNotNull(advisor) + viewModel.professorSearchResults.filter { it.staffNo != advisor?.staffNo }
            }

            when {
                viewModel.isSearchingProfessors -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary, modifier = Modifier.size(20.dp))
                }
                viewModel.professorSearchError != null -> Text(
                    text = viewModel.professorSearchError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                displayed.isEmpty() -> Text(
                    text = "검색 결과가 없어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    displayed.forEach { professor ->
                        val isAdvisor = professor.staffNo == advisor?.staffNo
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Background)
                                .clickable {
                                    viewModel.selectProfessor(professor)
                                    expanded = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isAdvisor) "${professor.name} (지도교수)" else professor.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isAdvisor) Primary else TextPrimary
                                )
                                if (professor.department.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = professor.department,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
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

private const val APPLY_STEP_COUNT = 3
private val EMAIL_DOMAIN_SUGGESTIONS = listOf(
    "daejin.ac.kr", "gmail.com", "naver.com", "hanmail.net", "nate.com", "kakao.com", "outlook.com"
)

/** 전화번호 → 이메일 → 상담내용, 카드 3장을 옆으로 넘기며 채우는 구성 — 스와이프로도, 아래
 *  이전/다음 버튼으로도 넘길 수 있다. 높이는 고정하지 않고 지금 보이는 카드 내용에 맞춰 자연스럽게
 *  줄었다 늘었다 한다(상담내용 칸은 줄이 늘어나면 카드도 같이 커진다). */
@Composable
private fun ProfessorCounselApplyForm(viewModel: ProfessorCounselViewModel) {
    val pagerState = rememberPagerState(pageCount = { APPLY_STEP_COUNT })
    val scope = rememberCoroutineScope()
    val goToPage: (Int) -> Unit = { page -> scope.launch { pagerState.animateScrollToPage(page) } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .padding(20.dp)
    ) {
        AutoHeightHorizontalPager(pagerState = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            when (page) {
                0 -> ApplyPhoneStep(viewModel)
                1 -> ApplyEmailStep(viewModel)
                else -> ApplyContentStep(viewModel)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (pagerState.currentPage > 0) {
                OutlinedButton(
                    onClick = { goToPage(pagerState.currentPage - 1) },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Text("이전")
                }
            }
            if (pagerState.currentPage < APPLY_STEP_COUNT - 1) {
                val stepValid = if (pagerState.currentPage == 0) viewModel.isPhoneStepValid else viewModel.isEmailStepValid
                Button(
                    onClick = { goToPage(pagerState.currentPage + 1) },
                    modifier = Modifier.weight(1f),
                    enabled = stepValid,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = OnPrimary,
                        disabledContainerColor = Border,
                        disabledContentColor = TextSecondary
                    )
                ) {
                    Text("다음")
                }
            } else {
                val submitEnabled = viewModel.canSubmit && !viewModel.isSubmittingApply
                Button(
                    onClick = { viewModel.requestSubmit() },
                    modifier = Modifier.weight(1f),
                    enabled = submitEnabled,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = OnPrimary,
                        disabledContainerColor = Border,
                        disabledContentColor = TextSecondary
                    )
                ) {
                    if (viewModel.isSubmittingApply) {
                        CircularProgressIndicator(color = OnPrimary, modifier = Modifier.size(18.dp))
                    } else {
                        Text("상담 신청하기")
                    }
                }
            }
        }

        if (viewModel.showSubmitConfirm) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelSubmitConfirm() },
                title = { Text("상담 신청할까요?") },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmSubmit() }) { Text("신청할게요") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelSubmitConfirm() }) { Text("취소") }
                }
            )
        }

        val resultMessage = viewModel.applyResultMessage
        if (resultMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = resultMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (viewModel.applySucceeded) Primary else TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** HorizontalPager는 스스로 "지금 보이는 페이지 내용에 맞춰 높이를 줄였다 늘렸다" 하지 못한다 —
 *  안 보이는 곳에서 지금 페이지와 똑같은 내용을 높이 제한 없이 한 번 더 그려서 실제로 필요한
 *  높이를 재고, 진짜 보여줄 Pager는 그 높이로 딱 맞춰 그린다. 페이지를 넘기거나 내용이 늘어나
 *  다시 측정되면 그 즉시 다시 반영된다. */
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
private fun ApplyPhoneStep(viewModel: ProfessorCounselViewModel) {
    Column {
        Text(text = "전화번호를 입력해주세요", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        val phone2Focus = remember { FocusRequester() }
        val phone3Focus = remember { FocusRequester() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = viewModel.applyPhonePart1,
                onValueChange = { new ->
                    viewModel.updateApplyPhonePart1(new)
                    if (viewModel.applyPhonePart1.length == 3) phone2Focus.requestFocus()
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("010") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = professorFieldColors()
            )
            OutlinedTextField(
                value = viewModel.applyPhonePart2,
                onValueChange = { new ->
                    viewModel.updateApplyPhonePart2(new)
                    if (viewModel.applyPhonePart2.length == 4) phone3Focus.requestFocus()
                },
                modifier = Modifier.weight(1f).focusRequester(phone2Focus),
                placeholder = { Text("1234") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = professorFieldColors()
            )
            OutlinedTextField(
                value = viewModel.applyPhonePart3,
                onValueChange = viewModel::updateApplyPhonePart3,
                modifier = Modifier.weight(1f).focusRequester(phone3Focus),
                placeholder = { Text("5678") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = professorFieldColors()
            )
        }
    }
}

/** "@"를 입력하는 순간부터, 아직 입력 안 한 도메인 뒷부분을 흔한 이메일 도메인들로 미리 채워
 *  후보로 보여준다 — 그 중 하나를 누르면 바로 완성된 이메일로 채워진다. */
@Composable
private fun ApplyEmailStep(viewModel: ProfessorCounselViewModel) {
    Column {
        Text(text = "이메일을 입력해주세요", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))

        val atIndex = viewModel.applyEmail.indexOf('@')
        val local = if (atIndex >= 0) viewModel.applyEmail.substring(0, atIndex) else null
        val domainTyped = if (atIndex >= 0) viewModel.applyEmail.substring(atIndex + 1) else ""
        // "@" 입력 직후엔 아직 아무것도 안 걸러진 전체 목록이라 의미가 없다 — 도메인 글자를
        // 최소 한 글자는 입력해야(예: "g" -> gmail.com) 그때부터 후보를 보여준다. 이미 정확히
        // 후보 중 하나와 같아지면(직접 다 쳤거나 후보를 눌러 채운 경우) 더 보여줄 게 없으니 닫는다.
        val suggestions = if (!local.isNullOrBlank() && domainTyped.isNotEmpty()) {
            EMAIL_DOMAIN_SUGGESTIONS.filter {
                it.startsWith(domainTyped, ignoreCase = true) && !it.equals(domainTyped, ignoreCase = true)
            }
        } else {
            emptyList()
        }

        // 입력칸 바로 아래에 붙는 드롭다운으로 띄운다 — Popup이라 레이아웃 높이에 영향을 주지 않고
        // 다른 요소를 아래로 밀어내지 않는다.
        Box {
            OutlinedTextField(
                value = viewModel.applyEmail,
                onValueChange = viewModel::updateApplyEmail,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("example@daejin.ac.kr") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = professorFieldColors()
            )
            DropdownMenu(
                expanded = suggestions.isNotEmpty(),
                onDismissRequest = {},
                properties = PopupProperties(focusable = false),
                containerColor = Surface
            ) {
                suggestions.forEach { domain ->
                    DropdownMenuItem(
                        text = {
                            Text(text = "$local@$domain", color = TextPrimary)
                        },
                        onClick = { viewModel.updateApplyEmail("$local@$domain") }
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplyContentStep(viewModel: ProfessorCounselViewModel) {
    Column {
        Text(text = "어떤 이야기를 나누고 싶어요?", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = viewModel.applyContent,
            onValueChange = viewModel::updateApplyContent,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            placeholder = { Text("10자 이상 적어주세요") },
            shape = RoundedCornerShape(16.dp),
            colors = professorFieldColors()
        )
    }
}

@Composable
private fun professorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Primary
)
