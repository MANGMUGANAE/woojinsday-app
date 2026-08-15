package com.daejin.woojintoday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MobileAds.initialize(this)
        setContent {
            WoojinTheme {
                WoojinApp()
            }
        }
    }
}

@Composable
private fun WoojinApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    var pendingRoute by remember { mutableStateOf(Routes.LOGIN) }

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
                }
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
