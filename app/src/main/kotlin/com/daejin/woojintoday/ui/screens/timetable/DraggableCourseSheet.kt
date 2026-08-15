package com.daejin.woojintoday.ui.screens.timetable

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.daejin.woojintoday.ui.theme.Border
import com.daejin.woojintoday.ui.theme.Surface
import kotlinx.serialization.json.JsonNull.content
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A sheet that overlays its parent, draggable only by its top handle bar — scrolling the
 * content inside (e.g. the course list) never moves the sheet itself. Snaps to one of three
 * anchors on release: fully expanded (top), half the container height, or peeking at the
 * bottom third.
 */
@Composable
fun DraggableCourseSheet(
    containerHeightPx: Float,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(Dp) -> Unit
) {
    val density = LocalDensity.current
    val topAnchor = with(density) { 64.dp.toPx() }
    val halfAnchor = containerHeightPx * 0.5f
    val peekAnchor = containerHeightPx * (2f / 3f)
    val anchors = remember(containerHeightPx) { listOf(topAnchor, halfAnchor, peekAnchor) }

    var offsetY by remember(containerHeightPx) { mutableFloatStateOf(peekAnchor) }

    val draggableState = rememberDraggableState { delta ->
        offsetY = (offsetY + delta).coerceIn(topAnchor, peekAnchor)
    }

    Column(
        modifier = modifier
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .fillMaxWidth()
            // 시트 높이를 항상 화면 전체 높이로 고정해두면, 절반만 올렸을 때도 내부 과목 리스트는
            // (화면 밖으로 내려가 안 보이는 부분까지 포함한) 전체 높이만큼 뷰포트를 갖게 되어 실제
            // 내용이 그 안에 다 들어차 있다고 착각하고 스크롤을 멈춰버린다 — 지금 실제로 화면에 걸쳐있는
            // 만큼(container 아래쪽 끝까지)만 높이로 잡아야 그 안에서 끝까지 스크롤된다.
            .height(with(density) { (containerHeightPx - offsetY).toDp() })
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStopped = {
                        val target = anchors.minByOrNull { abs(it - offsetY) } ?: peekAnchor
                        Animatable(offsetY).animateTo(
                            targetValue = target,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) {
                            offsetY = value
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Border)
            )
        }
        content(
            with(density) {
                120.dp + (offsetY - topAnchor).coerceAtLeast(0f).toDp()
            }
        )
    }
}
