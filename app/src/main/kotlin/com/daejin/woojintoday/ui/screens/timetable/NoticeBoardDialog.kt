package com.daejin.woojintoday.ui.screens.timetable

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daejin.woojintoday.data.CredentialStore
import com.daejin.woojintoday.data.NoticeSection
import com.daejin.woojintoday.data.NoticeWatchStore
import com.daejin.woojintoday.data.model.Notice
import com.daejin.woojintoday.data.network.LectureTimetableNoticeClient
import com.daejin.woojintoday.data.network.MileageEligibility
import com.daejin.woojintoday.data.network.NoticeClient
import com.daejin.woojintoday.schedule.NoticeWatchScheduler
import com.daejin.woojintoday.ui.components.ResponsiveContainer
import com.daejin.woojintoday.ui.icons.IconArrowBack
import com.daejin.woojintoday.ui.screens.login.NoticeViewModel
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary

/** 공지사항 다이얼로그 — "학사공지"(공개), "종합강의시간표"(로그인 세션 필요), "마일리지"(공개, 대상자
 *  명단 xlsx 대조 가능) 세 게시판을 아코디언 섹션으로 보여준다. 섹션마다 "글 올라오면 알려드려요"
 *  토글이 있어 켜두면 매일 저녁 6시에 새 글이 있는지 검사해 알림을 준다. 글을 누르면 같은 상세
 *  WebView로 전환된다(뒤로가기 한 번에 목록으로). */
@Composable
fun NoticeBoardDialog(onDismiss: () -> Unit, initialSection: NoticeSection? = null) {
    val context = LocalContext.current
    val lectureTimetableViewModel: LectureTimetableNoticeViewModel =
        viewModel(factory = LectureTimetableNoticeViewModel.Factory(context))
    val noticeViewModel: NoticeViewModel = viewModel()
    val mileageViewModel: MileageScholarshipViewModel =
        viewModel(factory = MileageScholarshipViewModel.Factory(context))
    val credentialStore = remember { CredentialStore(context) }
    val noticeWatchStore = remember { NoticeWatchStore(context) }

    var selectedPath by remember { mutableStateOf<String?>(null) }
    // 종합강의시간표 글은 www.daejin.ac.kr 로그인 세션이 있어야 상세보기가 열린다 — 학사공지/마일리지
    // 글에서 왔는지 구분해뒀다가 그때만 WebView에서 로그인 폼을 한 번 더 태운다.
    var selectedRequiresLogin by remember { mutableStateOf(false) }
    // 시스템/제스처 뒤로가기는 Dialog의 onDismissRequest로 바로 꽂혀서 상세보기 중에도 전체가
    // 닫혀버렸다 — 상단 뒤로가기 버튼과 똑같이 상세보기 중엔 목록으로만 한 단계 돌아가게 가로챈다.
    BackHandler(enabled = selectedPath != null) {
        selectedPath = null
    }

    // 딥링크로 특정 섹션(알림이 온 섹션)이 지정돼 들어왔으면 그 섹션이 선택된 채로 시작한다.
    var selectedSection by remember { mutableStateOf(initialSection ?: NoticeSection.ACADEMIC) }

    // 섹션별로 따로 켜고 끄던 걸 그만두고 하나로 통합 — 셋 다 켜져 있을 때만 "켜짐"으로 보여준다.
    var allNoticesWatch by remember {
        mutableStateOf(
            noticeWatchStore.isEnabled(NoticeSection.ACADEMIC) &&
                noticeWatchStore.isEnabled(NoticeSection.LECTURE_TIMETABLE) &&
                noticeWatchStore.isEnabled(NoticeSection.MILEAGE)
        )
    }

    fun toggleWatch(section: NoticeSection, enabled: Boolean, currentDetailPaths: List<String>) {
        noticeWatchStore.setEnabled(section, enabled)
        if (enabled) {
            // 토글을 막 켠 시점의 목록을 기준선으로 저장 — 다음 검사부터는 이 기준 이후에 새로
            // 생긴 글만 알림으로 알려준다(기존 글들을 전부 "새 글"로 오해하지 않도록).
            noticeWatchStore.saveLastSeenDetailPaths(section, currentDetailPaths.toSet())
            NoticeWatchScheduler.scheduleNext(context)
        } else if (!noticeWatchStore.anyEnabled()) {
            NoticeWatchScheduler.cancel(context)
        }
    }

    fun toggleAllWatch(enabled: Boolean) {
        allNoticesWatch = enabled
        toggleWatch(NoticeSection.ACADEMIC, enabled, noticeViewModel.notices.map { it.detailPath })
        toggleWatch(NoticeSection.LECTURE_TIMETABLE, enabled, lectureTimetableViewModel.notices.map { it.detailPath })
        toggleWatch(NoticeSection.MILEAGE, enabled, mileageViewModel.items.map { it.notice.detailPath })
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
                ) {
                    // 알림 토글 + 탭 스위처는 항상 고정, 그 아래 목록만 내부 스크롤된다.
                    NoticeSectionWatchRow(watchEnabled = allNoticesWatch, onToggleWatch = ::toggleAllWatch)
                    Spacer(modifier = Modifier.height(12.dp))
                    NoticeSectionSwitcher(selected = selectedSection, onSelect = { selectedSection = it })
                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedContent(
                        targetState = selectedSection,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = "noticeSectionContent"
                    ) { section ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (section) {
                                NoticeSection.ACADEMIC -> NoticeListColumn(
                                    notices = noticeViewModel.notices.take(5),
                                    isLoading = noticeViewModel.isLoading,
                                    errorMessage = noticeViewModel.errorMessage,
                                    onSelect = { path -> selectedPath = path; selectedRequiresLogin = false }
                                )
                                NoticeSection.LECTURE_TIMETABLE -> NoticeListColumn(
                                    notices = lectureTimetableViewModel.notices,
                                    isLoading = lectureTimetableViewModel.isLoading,
                                    errorMessage = lectureTimetableViewModel.errorMessage,
                                    onSelect = { path -> selectedPath = path; selectedRequiresLogin = true }
                                )
                                NoticeSection.MILEAGE -> MileageNoticeListColumn(
                                    items = mileageViewModel.items,
                                    isLoading = mileageViewModel.isLoading,
                                    errorMessage = mileageViewModel.errorMessage,
                                    onSelect = { path -> selectedPath = path; selectedRequiresLogin = false },
                                    onCheckClick = { item -> mileageViewModel.checkEligibility(item) }
                                )
                            }

                            // 화면 하단에서 스크롤이 끝나버리면 답답해 보여서, 항목 하나 분량만큼 여백을
                            // 더 둬서 스크롤할 여지를 넉넉하게 남긴다.
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            } else {
                NoticeDetailWebView(
                    url = NoticeClient.BASE_URL + selectedPath,
                    requiresLogin = selectedRequiresLogin,
                    studentNo = credentialStore.studentNo(),
                    password = credentialStore.password(),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                )
            }
        }
        }
        }
    }

    val checkingTitle = mileageViewModel.checkingNoticeTitle
    if (checkingTitle != null) {
        EligibilityResultDialog(
            noticeTitle = checkingTitle,
            result = mileageViewModel.eligibilityResult,
            onDismiss = { mileageViewModel.dismissEligibilityResult() }
        )
    }
}

/** 학사공지/종합강의시간표/마일리지 세로 아코디언 대신, 지도교수 상담 다이얼로그의 세그먼트
 *  스위처와 같은 방식으로 가로 탭 3개 중 하나를 고르면 그 알약(pill) 모양 배경이 스르륵
 *  옆으로 미끄러지듯 이동한다. */
@Composable
private fun NoticeSectionSwitcher(selected: NoticeSection, onSelect: (NoticeSection) -> Unit) {
    val sections = remember { NoticeSection.entries.toList() }
    val targetIndex = sections.indexOf(selected).toFloat()
    val indexFraction by animateFloatAsState(targetValue = targetIndex, label = "noticeSectionIndicator")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / sections.size
        Box(
            modifier = Modifier
                .offset(x = tabWidth * indexFraction)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(Primary)
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            sections.forEach { section ->
                NoticeSectionTab(
                    label = section.displayName,
                    selected = section == selected,
                    onClick = { onSelect(section) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun NoticeSectionTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val textColor by animateColorAsState(targetValue = if (selected) OnPrimary else TextSecondary, label = "noticeSectionTabText")
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
private fun NoticeSectionWatchRow(watchEnabled: Boolean, onToggleWatch: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "새 글 알림 받기",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Switch(
            checked = watchEnabled,
            onCheckedChange = onToggleWatch,
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

@Composable
private fun NoticeListColumn(
    notices: List<Notice>,
    isLoading: Boolean,
    errorMessage: String?,
    onSelect: (String) -> Unit
) {
    when {
        isLoading -> LoadingOrEmptyBox { CircularProgressIndicator(color = Primary) }
        errorMessage != null && notices.isEmpty() -> LoadingOrEmptyBox {
            Text(text = errorMessage, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            notices.forEach { notice ->
                NoticeRow(notice = notice, onClick = { onSelect(notice.detailPath) })
            }
        }
    }
}

@Composable
private fun MileageNoticeListColumn(
    items: List<MileageNoticeItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onSelect: (String) -> Unit,
    onCheckClick: (MileageNoticeItem) -> Unit
) {
    when {
        isLoading -> LoadingOrEmptyBox { CircularProgressIndicator(color = Primary) }
        errorMessage != null && items.isEmpty() -> LoadingOrEmptyBox {
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

@Composable
private fun LoadingOrEmptyBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

private fun rowContainerModifier(onClick: () -> Unit): Modifier = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(10.dp))
    .background(Surface)
    .clickable(onClick = onClick)
    .padding(12.dp)

@Composable
private fun NoticeRowContent(notice: Notice, modifier: Modifier = Modifier) {
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
private fun NoticeRow(notice: Notice, onClick: () -> Unit) {
    Row(modifier = rowContainerModifier(onClick), verticalAlignment = Alignment.CenterVertically) {
        NoticeRowContent(notice = notice, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MileageNoticeRow(item: MileageNoticeItem, onClick: () -> Unit, onCheckClick: () -> Unit) {
    Row(modifier = rowContainerModifier(onClick), verticalAlignment = Alignment.CenterVertically) {
        NoticeRowContent(notice = item.notice, modifier = Modifier.weight(1f))
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

/** "대상자 확인" 결과 모달 — [result]가 null이면 아직 명단 다운로드/대조 중이라는 뜻. 대상자가
 *  아닐 때만 원본 명단을 직접 눈으로 볼 수 있는 "명단보기" 버튼을 확인 버튼 오른쪽에 보여준다. */
@Composable
private fun EligibilityResultDialog(
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
                DialogPillButton(text = "확인", filled = true, onClick = onDismiss)
                if (result is MileageEligibility.NotFound) {
                    DialogPillButton(text = "명단보기", filled = false, onClick = { showSheetValues = true })
                }
            }
        }
    }

    if (showSheetValues && result is MileageEligibility.NotFound) {
        // 명단 모달을 닫을 때 뒤에 남아있던 "대상자 명단에 없어요" 결과 모달도 같이 닫는다 —
        // 굳이 확인을 한 번 더 누를 필요 없이 명단보기→닫기 한 번으로 전부 닫히게.
        SheetValuesDialog(
            values = result.values,
            onDismiss = {
                showSheetValues = false
                onDismiss()
            }
        )
    }
}

/** 방금 대조에 쓴 xlsx를 다시 받지 않고, 이미 메모리에 있는 셀 값 그대로 보여준다 — 상단 검색창에
 *  학번을 입력하면 일치하는 항목이 강조된다. */
@Composable
private fun SheetValuesDialog(values: List<String>, onDismiss: () -> Unit) {
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
            DialogPillButton(text = "닫기", filled = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun DialogPillButton(text: String, filled: Boolean, onClick: () -> Unit) {
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

/** [requiresLogin]이면(종합강의시간표 글) WebView 자체 쿠키 저장소엔 로그인 세션이 없어 "접근 권한이
 *  없습니다"로 막히므로, 목록 조회 때와 같은 로그인 폼을 이 WebView에서 한 번 더 postUrl로 태워 세션을
 *  받은 뒤에야 실제 글 주소를 연다(WebView는 postUrl 응답의 Set-Cookie를 자기 CookieManager에 자동
 *  저장하므로 그 뒤 loadUrl은 그대로 인증된 상태로 나간다). */
@Composable
private fun NoticeDetailWebView(
    url: String,
    requiresLogin: Boolean,
    studentNo: String?,
    password: String?,
    modifier: Modifier = Modifier
) {
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
                    // 게시글의 첨부파일 링크는 WebView 안에서 그냥 두면 아무 반응이 없다 — 다운로드
                    // 요청을 가로채 세션 쿠키를 그대로 실어 DownloadManager로 넘겨준다.
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
                    if (requiresLogin && studentNo != null && password != null) {
                        var loggedIn = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, finishedUrl: String) {
                                if (!loggedIn) {
                                    loggedIn = true
                                    view.loadUrl(url)
                                }
                            }
                        }
                        postUrl(
                            LectureTimetableNoticeClient.LOGIN_URL,
                            LectureTimetableNoticeClient.loginFormBody(studentNo, password).toByteArray()
                        )
                    } else {
                        webViewClient = WebViewClient()
                        loadUrl(url)
                    }
                }
            }
        )
    }
}
