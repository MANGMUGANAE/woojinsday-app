package com.daejin.woojintoday.ui.screens.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import com.daejin.woojintoday.data.model.AcademicPeriod
import com.daejin.woojintoday.ui.icons.IconChevronDown
import com.daejin.woojintoday.ui.theme.Surface
import com.daejin.woojintoday.ui.theme.TextPrimary
import com.daejin.woojintoday.ui.theme.TextSecondary

private const val HISTORY_LENGTH = 30
private const val VISIBLE_ITEM_COUNT = 5

@Composable
fun PeriodFilter(
    year: Int,
    semester: Int,
    onPeriodSelected: (year: Int, semester: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // Newest first: capped above at "next semester" from today, unbounded going back.
    val periods = remember {
        val current = AcademicPeriod.current()
        val upperBound = AcademicPeriod.next(current.first, current.second)
        buildList {
            add(upperBound)
            var cursor = upperBound
            repeat(HISTORY_LENGTH) {
                cursor = AcademicPeriod.previous(cursor.first, cursor.second)
                add(cursor)
            }
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Surface)
            .clickable { expanded = true }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${year}년 ${semester}학기",
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary
        )
        Row(modifier = Modifier.padding(start = 6.dp)) {
            IconChevronDown(tint = TextSecondary)
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.heightIn(max = 48.dp * VISIBLE_ITEM_COUNT)
    ) {
        periods.forEach { (y, s) ->
            DropdownMenuItem(
                text = { Text("${y}년 ${s}학기") },
                onClick = {
                    expanded = false
                    onPeriodSelected(y, s)
                }
            )
        }
    }
}
