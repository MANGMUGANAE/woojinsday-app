package com.daejin.woojintoday

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daejin.woojintoday.data.NoticeSection
import com.daejin.woojintoday.data.TermsAgreementStore
import com.daejin.woojintoday.ui.screens.home.HomeScreen
import com.daejin.woojintoday.ui.screens.login.LoginScreen
import com.daejin.woojintoday.ui.screens.splash.SplashDestination
import com.daejin.woojintoday.ui.screens.splash.SplashScreen
import com.daejin.woojintoday.ui.screens.terms.TermsAgreementScreen
import com.daejin.woojintoday.ui.screens.timetable.TimetableScreen
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.WoojinTheme
import com.google.android.gms.ads.MobileAds

private object Routes {
    const val SPLASH = "splash"
    const val TERMS = "terms"
    const val LOGIN = "login"
    const val HOME = "home"
    const val TIMETABLE = "timetable"
}

/** 알림(강의시간 알림/새 공지 알림)을 눌러 앱을 열었을 때 해당 화면으로 바로 이동시키기 위해
 *  Notifier들이 MainActivity로 보내는 Intent에 싣는 값들. */
object DeepLink {
    const val EXTRA_TARGET = "deep_link_target"
    const val TARGET_TIMETABLE = "timetable"
    const val TARGET_NOTICE = "notice"
    const val TARGET_CALENDAR = "calendar"
    const val EXTRA_NOTICE_SECTION = "deep_link_notice_section"
}

class MainActivity : ComponentActivity() {
    // Intent 자체는 Compose가 감지 못 하므로, 여기 담아뒀다가 값이 바뀔 때 리컴포지션이 일어나게 한다.
    private var pendingIntentExtra by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MobileAds.initialize(this)
        pendingIntentExtra = intent
        setContent {
            WoojinTheme {
                WoojinApp(
                    pendingIntent = pendingIntentExtra,
                    onDeepLinkConsumed = { pendingIntentExtra = null }
                )
            }
        }
    }

    // singleTask라 이미 떠있는 상태로 알림을 또 누르면 onCreate가 아니라 여기로 온다.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntentExtra = intent
    }
}

@Composable
private fun WoojinApp(pendingIntent: Intent?, onDeepLinkConsumed: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var pendingRoute by remember { mutableStateOf(Routes.LOGIN) }

    val deepLinkTarget = pendingIntent?.getStringExtra(DeepLink.EXTRA_TARGET)
    val deepLinkNoticeSection = pendingIntent?.getStringExtra(DeepLink.EXTRA_NOTICE_SECTION)
        ?.let { name -> runCatching { NoticeSection.valueOf(name) }.getOrNull() }

    // Home 화면에 이미 와있는 상태(웜 스타트)에서 알림을 누른 경우를 처리한다 — 콜드 스타트(스플래시부터
    // 시작)로 Home에 막 도착한 경우도 결국 이 라우트 값을 거쳐가므로 두 경우 다 여기서 잡힌다.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(deepLinkTarget, currentRoute) {
        if (currentRoute == Routes.HOME && deepLinkTarget == DeepLink.TARGET_TIMETABLE) {
            navController.navigate(Routes.TIMETABLE)
            onDeepLinkConsumed()
        }
    }

    // enableEdgeToEdge() draws content behind the status/navigation bars, so the outer Box paints
    // the app background all the way to the screen edges while the NavHost content itself is
    // inset with safeDrawingPadding — otherwise bottom buttons sit under the gesture nav bar.
    Box(modifier = Modifier.fillMaxSize().background(Background)) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigate = { destination ->
                    val route = when (destination) {
                        SplashDestination.LOGIN -> Routes.LOGIN
                        SplashDestination.TIMETABLE -> Routes.HOME
                    }
                    if (TermsAgreementStore(context).isAgreed()) {
                        navController.navigate(route) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    } else {
                        pendingRoute = route
                        navController.navigate(Routes.TERMS) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Routes.TERMS) {
            TermsAgreementScreen(
                onAgree = {
                    navController.navigate(pendingRoute) {
                        popUpTo(Routes.TERMS) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenTimetable = {
                    navController.navigate(Routes.TIMETABLE)
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                deepLinkNoticeSection = if (deepLinkTarget == DeepLink.TARGET_NOTICE) {
                    deepLinkNoticeSection ?: NoticeSection.ACADEMIC
                } else {
                    null
                },
                onDeepLinkNoticeConsumed = onDeepLinkConsumed,
                deepLinkOpenCalendar = deepLinkTarget == DeepLink.TARGET_CALENDAR,
                onDeepLinkCalendarConsumed = onDeepLinkConsumed
            )
        }
        composable(Routes.TIMETABLE) {
            TimetableScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
    }
}
