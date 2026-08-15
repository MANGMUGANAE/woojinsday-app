package com.daejin.woojintoday.ui.screens.splash

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daejin.woojintoday.ui.icons.AppMark
import com.daejin.woojintoday.ui.screens.login.LoginVideoPlayerHolder
import com.daejin.woojintoday.ui.theme.Background
import com.daejin.woojintoday.ui.theme.Primary

@Composable
fun SplashScreen(onNavigate: (SplashDestination) -> Unit) {
    val context = LocalContext.current
    val viewModel: SplashViewModel = viewModel(factory = SplashViewModel.Factory(context))

    LaunchedEffect(Unit) { LoginVideoPlayerHolder.prepare(context) }

    // 우진이 캘린더 알림에 쓰이는 알림 권한만 가볍게 요청 — 거부해도 로그인/진행은 막지 않는다.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.begin()
    }

    LaunchedEffect(viewModel.destination) {
        viewModel.destination?.let(onNavigate)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppMark(height = 120.dp)
            Box(modifier = Modifier.height(28.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Primary,
                strokeWidth = 2.dp
            )
        }
    }
}
