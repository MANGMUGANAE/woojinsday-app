package com.daejin.woojintoday.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val ITEM_HEIGHT = 40.dp
private const val VISIBLE_COUNT = 5

/** A compact scrollable/snapping value wheel — the standard modern-app date/time input pattern. */
@Composable
fun WheelPicker(
    values: List<Int>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() }
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { ITEM_HEIGHT.toPx() }
    val initialIndex = values.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val currentSelected = rememberUpdatedState(selected)

    LaunchedEffect(listState, values) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .map { (index, offset) ->
                val centered = if (offset > itemHeightPx / 2) index + 1 else index
                values.getOrNull(centered.coerceIn(values.indices)) ?: values.first()
            }
            .distinctUntilChanged()
            .collect { value -> if (value != currentSelected.value) onSelectedChange(value) }
    }

    Box(modifier = modifier.height(ITEM_HEIGHT * VISIBLE_COUNT)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(ITEM_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface)
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = ITEM_HEIGHT * (VISIBLE_COUNT / 2)),
            modifier = Modifier.fillMaxSize()
        ) {
            items(values, key = { it }) { value ->
                Box(
                    modifier = Modifier.fillMaxWidth().height(ITEM_HEIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    val isSelected = value == selected
                    Text(
                        text = format(value),
                        style = if (isSelected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) TextPrimary else TextSecondary
                    )
                }
            }
        }
    }
}

fun wheelRange(start: Int, endInclusive: Int, step: Int = 1): List<Int> =
    (start..endInclusive step step).toList()
