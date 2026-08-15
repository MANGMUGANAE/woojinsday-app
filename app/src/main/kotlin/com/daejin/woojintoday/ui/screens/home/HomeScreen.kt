package com.daejin.woojintoday.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.daejin.woojintoday.R
import com.daejin.woojintoday.data.CredentialStore
import com.daejin.woojintoday.data.SessionStore
import com.daejin.woojintoday.ui.components.BannerAdView
import com.daejin.woojintoday.ui.icons.IconCalendar
import com.daejin.woojintoday.ui.icons.IconMeal
import com.daejin.woojintoday.ui.icons.IconRankBars
import com.daejin.woojintoday.ui.screens.timetable.NoticeBoardDialog
import com.daejin.woojintoday.ui.theme.AccentBlue
import com.daejin.woojintoday.ui.theme.AccentBlueDeep
import com.daejin.woojintoday.ui.theme.AccentPurple
import com.daejin.woojintoday.ui.theme.AccentPurpleDeep
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.ErrorRed
import com.daejin.woojintoday.ui.theme.OnPrimary
import com.daejin.woojintoday.ui.theme.Primary
import com.daejin.woojintoday.ui.theme.PrimaryPressed
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(onOpenTimetable: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    var showNoticeDialog by remember { mutableStateOf(false) }
    var showRankDialog by remember { mutableStateOf(false) }
    var showLogoutMenu by remember { mutableStateOf(false) }
    var showFeatureListDialog by remember { mutableStateOf(false) }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var showMealDialog by remember { mutableStateOf(false) }
    var showCampusWalkDialog by remember { mutableStateOf(false) }
    var showMileageStatusDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    val greeting = remember { greetingLabel() }

    val onFeatureClick: (HomeFeature) -> Unit = { feature ->
        when (feature.id) {
            "woojin_calendar" -> showCalendarDialog = true
            "notice_board" -> showNoticeDialog = true
            "campus_walk" -> showCampusWalkDialog = true
            "mileage_status" -> showMileageStatusDialog = true
        }
    }

    if (showNoticeDialog) {
        NoticeBoardDialog(onDismiss = { showNoticeDialog = false })
    }
    if (showMileageStatusDialog) {
        MileageStatusDialog(onDismiss = { showMileageStatusDialog = false })
    }
    if (showMealDialog) {
        MealDialog(onDismiss = { showMealDialog = false })
    }
    if (showProfileDialog) {
        MyProfileDialog(onDismiss = { showProfileDialog = false })
    }
    if (showTermsDialog) {
        TermsHistoryDialog(onDismiss = { showTermsDialog = false })
    }
    if (showRankDialog) {
        RankDialog(onDismiss = { showRankDialog = false })
    }
    if (showFeatureListDialog) {
        FeatureListDialog(
            features = HomeFeatures,
            onDismiss = { showFeatureListDialog = false },
            onFeatureClick = onFeatureClick
        )
    }
    if (showCalendarDialog) {
        AcademicCalendarDialog(onDismiss = { showCalendarDialog = false })
    }
    if (showCampusWalkDialog) {
        CampusWalkDialog(onDismiss = { showCampusWalkDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Surface)
                        .clickable { showLogoutMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.daejin_mascot),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                if (showLogoutMenu) {
                    val density = LocalDensity.current
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = with(density) { IntOffset(0, 58.dp.roundToPx()) },
                        onDismissRequest = { showLogoutMenu = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Surface)
                        ) {
                            Text(
                                text = "내 정보",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                modifier = Modifier
                                    .clickable {
                                        showLogoutMenu = false
                                        showProfileDialog = true
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Border)
                            )
                            Text(
                                text = "이용약관",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                modifier = Modifier
                                    .clickable {
                                        showLogoutMenu = false
                                        showTermsDialog = true
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Border)
                            )
                            Text(
                                text = "로그아웃",
                                style = MaterialTheme.typography.labelMedium,
                                color = ErrorRed,
                                modifier = Modifier
                                    .clickable {
                                        showLogoutMenu = false
                                        SessionStore(context).clear()
                                        CredentialStore(context).clear()
                                        onLogout()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = todayLabel(), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = greeting, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "오늘은 무얼\n같이 해볼까요?",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(24.dp))

        HeroCard(onClick = onOpenTimetable)

        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SubCard(
                modifier = Modifier.weight(1f),
                colors = listOf(AccentBlue, AccentBlueDeep),
                decor = RankCardDecor,
                icon = { tint -> IconRankBars(tint = tint, size = 30.dp) },
                title = "나는 학과에서\n몇등일까요?",
                subtitle = "두근두근",
                onClick = { showRankDialog = true }
            )
            SubCard(
                modifier = Modifier.weight(1f),
                colors = listOf(AccentPurple, AccentPurpleDeep),
                decor = NoticeCardDecor,
                icon = { tint -> IconMeal(tint = tint, size = 30.dp) },
                title = "오늘의 메뉴는\n무엇일까요?",
                subtitle = "학식을 조회할 수 있어요!",
                onClick = { showMealDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        FeatureCarouselSection(
            features = HomeFeatures,
            onShowAll = { showFeatureListDialog = true },
            onFeatureClick = onFeatureClick
        )

        Spacer(modifier = Modifier.height(24.dp))
        }

        BannerAdView()
    }
}

private fun todayLabel(): String =
    SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(Date())

private val MorningGreetings = listOf(
    "좋은 아침이에요!",
    "상쾌한 아침이에요!",
    "오늘도 활기차게 시작해봐요!",
    "아침밥은 챙겨 드셨어요?",
    "오늘 하루도 힘차게 시작해봐요!"
)

private val AfternoonGreetings = listOf(
    "점심 맛있게 드세요!",
    "밥은 잘 챙겨 드셨어요?",
    "든든하게 챙겨 드세요!",
    "오후도 화이팅이에요!",
    "맛있는 점심 되세요!"
)

private val EveningGreetings = listOf(
    "오늘 하루도 고생 많으셨어요",
    "저녁 맛있게 드세요!",
    "오늘 하루도 수고했어요",
    "하루 마무리 잘 하고 계신가요?",
    "오늘도 애쓰셨어요!"
)

private val NightGreetings = listOf(
    "안녕히 주무세요!",
    "오늘 하루도 정말 고생했어요",
    "좋은 꿈 꾸세요!",
    "푹 쉬고 내일 만나요",
    "이제 쉴 시간이에요, 잘 자요!"
)

private fun greetingLabel(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val pool = when (hour) {
        in 5..10 -> MorningGreetings
        in 11..16 -> AfternoonGreetings
        in 17..21 -> EveningGreetings
        else -> NightGreetings
    }
    return pool.random()
}

/** A single soft decorative circle floating inside a card. */
private data class DecorCircle(
    val size: Dp,
    val offsetX: Dp,
    val offsetY: Dp = 0.dp,
    val alpha: Float,
    val alignment: Alignment = Alignment.CenterEnd
)

// Distinct per-card constellations so the three cards don't read as visual copies of each other.
private val HeroCardDecor = listOf(
    DecorCircle(size = 210.dp, offsetX = 64.dp, offsetY = (-58).dp, alpha = 0.12f, alignment = Alignment.TopEnd),
    DecorCircle(size = 84.dp, offsetX = 8.dp, offsetY = 26.dp, alpha = 0.16f, alignment = Alignment.BottomEnd)
)
private val RankCardDecor = listOf(
    DecorCircle(size = 128.dp, offsetX = 38.dp, offsetY = (-46).dp, alpha = 0.12f, alignment = Alignment.TopEnd),
    DecorCircle(size = 48.dp, offsetX = 4.dp, offsetY = 18.dp, alpha = 0.18f, alignment = Alignment.BottomEnd)
)
private val NoticeCardDecor = listOf(
    DecorCircle(size = 96.dp, offsetX = 42.dp, offsetY = 10.dp, alpha = 0.10f, alignment = Alignment.CenterEnd),
    DecorCircle(size = 58.dp, offsetX = (-8).dp, offsetY = (-22).dp, alpha = 0.16f, alignment = Alignment.TopEnd),
    DecorCircle(size = 26.dp, offsetX = (-18).dp, offsetY = 14.dp, alpha = 0.22f, alignment = Alignment.BottomEnd)
)

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CardDecor(circles: List<DecorCircle>) {
    circles.forEach { circle ->
        Box(
            modifier = Modifier
                .size(circle.size)
                .align(circle.alignment)
                .offset(x = circle.offsetX, y = circle.offsetY)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = circle.alpha))
        )
    }
}

@Composable
private fun HeroCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Primary, PrimaryPressed)))
            .clickable(onClick = onClick)
    ) {
        CardDecor(HeroCardDecor)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(text = "추천", style = MaterialTheme.typography.bodySmall, color = OnPrimary)
            }
            Spacer(modifier = Modifier.height(14.dp))
            IconCalendar(tint = OnPrimary, size = 40.dp)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "시간표를 짜볼까요?", style = MaterialTheme.typography.titleLarge, color = OnPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "나만의 시간표를 만들고 저장해보세요",
                style = MaterialTheme.typography.bodySmall,
                color = OnPrimary.copy(alpha = 0.78f)
            )
        }
    }
}

data class HomeFeature(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageRes: Int,
    val imageOnLeft: Boolean = false,
    // imageOnLeft=false일 때만 쓰는 캐릭터 이미지 크기/오프셋 — 카드 오른쪽에 살짝 걸치듯 배치하는
    // 연출이라 이미지마다(캐릭터가 캔버스 안에서 차지하는 비중이 달라) 따로 맞춰줘야 한다.
    val imageSize: Dp = 1299.dp,
    val imageOffsetX: Dp = 120.dp,
    // true면 위 크기/오프셋 대신, 카드 오른쪽 안에 딱 맞게(높이 기준) 들어간다 — 크기를 잘못 예측할
    // 걱정 없이 항상 카드 안에서 오른쪽에 온전히 보이게 하고 싶을 때 쓴다.
    val imageFitInCard: Boolean = false,
    // imageOnLeft=true일 때만 쓰는 이미지 높이 비율 — 1f면 카드 높이에 꽉 차게, 줄이면 위아래 여백이
    // 그만큼 생긴다(이미지마다 원본 여백/비중이 달라 따로 맞춰줘야 한다).
    val imageOnLeftHeightFraction: Float = 1f,
    // imageOnLeft=true일 때만 쓰는 가로/세로 오프셋 — 기본은 카드 왼쪽 끝에 딱 붙지만, 이미지마다
    // 살짝 밀어야 균형이 맞는 경우가 있어 따로 조절한다(세로는 기본 -10dp 위로 당겨진 위치 기준 추가값).
    val imageOnLeftOffsetX: Dp = 0.dp,
    val imageOnLeftOffsetY: Dp = 0.dp
)

val HomeFeatures = listOf(
    HomeFeature(
        id = "woojin_calendar",
        title = "우진이의 캘린더",
        subtitle = "학교 일정을 알림으로 받아보세요",
        imageRes = R.drawable.woojin_cutout
    ),
    HomeFeature(
        id = "notice_board",
        title = "공지사항을\n알려드릴게요!",
        subtitle = "무슨일이 있었을까요?!",
        imageRes = R.drawable.woojin_notice,
        imageOnLeft = true
    ),
    HomeFeature(
        id = "campus_walk",
        title = "연강이 가능할까요?",
        subtitle = "건물간 도보 시간을 알려드릴께요!",
        imageRes = R.drawable.woojin_map,
        imageFitInCard = true
    ),
    HomeFeature(
        id = "mileage_status",
        title = "마일리지",
        subtitle = "마일리지 관련 정보를\n알려드릴게요!",
        imageRes = R.drawable.woojin_mileage,
        imageOnLeft = true,
        imageOnLeftHeightFraction = 0.6f,
        imageOnLeftOffsetX = 16.dp,
        imageOnLeftOffsetY = 6.dp
    )
)

@Composable
private fun FeatureCarouselSection(
    features: List<HomeFeature>,
    onShowAll: () -> Unit,
    onFeatureClick: (HomeFeature) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "필요한 기능만 모아봤어요!",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Text(
                text = "모아보기",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.clickable(onClick = onShowAll)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        val pagerState = rememberPagerState(pageCount = { features.size })
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            FeatureCard(
                feature = features[page],
                modifier = Modifier.fillMaxWidth(),
                onClick = { onFeatureClick(features[page]) }
            )
        }
    }
}

@Composable
fun FeatureCard(feature: HomeFeature, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier = modifier
            .height(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Surface)
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
    ) {
        if (feature.imageOnLeft) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.58f)
                    .padding(20.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = feature.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
            Image(
                painter = painterResource(id = feature.imageRes),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(fraction = feature.imageOnLeftHeightFraction)
                    .padding(vertical = 8.dp)
                    .offset(x = feature.imageOnLeftOffsetX, y = (-10).dp + feature.imageOnLeftOffsetY),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.58f)
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = feature.title, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = feature.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Image(
                painter = painterResource(id = feature.imageRes),
                contentDescription = null,
                modifier = if (feature.imageFitInCard) {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(100.dp)
                        .padding(20.dp)
                } else {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(feature.imageSize)
                        .offset(x = feature.imageOffsetX, y = (-2).dp)
                },
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun SubCard(
    modifier: Modifier = Modifier,
    colors: List<Color>,
    decor: List<DecorCircle>,
    icon: @Composable (Color) -> Unit,
    title: String,
    subtitle: String,
    badgeCount: Int? = null,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = modifier
            .height(168.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors))
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
    ) {
        CardDecor(decor)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            icon(OnPrimary)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = OnPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = OnPrimary.copy(alpha = 0.78f))
        }

        if (badgeCount != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ErrorRed)
                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = badgeCount.toString(), style = MaterialTheme.typography.bodySmall, color = OnPrimary)
            }
        }
    }
}
