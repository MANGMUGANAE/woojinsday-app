package com.daejin.woojintoday.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.daejin.woojintoday.ui.theme.Surface

/** Sits in the empty space below the login button — the university's looping promo video. */
@Composable
fun LoginPromoVideoSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { LoginVideoPlayerHolder.player(context) }

    AndroidView(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Surface),
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        }
    )
}
