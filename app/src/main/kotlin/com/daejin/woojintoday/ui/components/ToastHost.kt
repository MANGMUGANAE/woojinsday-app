package com.daejin.woojintoday.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TOAST_DURATION_MS = 2400L
private const val DISMISS_SWIPE_THRESHOLD_PX = 120f

/** Holds the single active toast message. Call [show] from anywhere; the [ToastHost] renders it. */
class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    private var token = 0

    fun show(text: String) {
        token++
        message = text
    }

    fun dismiss() {
        message = null
    }
}

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }

/**
 * The app-wide toast style: a compact, rounded pill sized to its text (never full-width),
 * anchored top-center, that slides/fades in and out — swipe sideways or tap to dismiss early.
 * This replaces Material Snackbars as the standard notification pattern going forward.
 */
@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val message = state.message

    LaunchedEffect(message) {
        if (message != null) {
            delay(TOAST_DURATION_MS)
            state.dismiss()
        }
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            if (message != null) {
                ToastBubble(text = message, onDismiss = state::dismiss)
            }
        }
    }
}

@Composable
private fun ToastBubble(text: String, onDismiss: () -> Unit) {
    var offsetX by remember(text) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .padding(top = 48.dp)
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .clip(RoundedCornerShape(50))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(50))
            .clickable(onClick = onDismiss)
            .pointerInput(text) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(offsetX) > DISMISS_SWIPE_THRESHOLD_PX) onDismiss() else offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount
                }
            }
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
