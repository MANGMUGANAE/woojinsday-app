package com.daejin.woojintoday.ui.screens.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daejin.woojintoday.data.model.Course
import com.daejin.woojintoday.ui.theme.OnPrimary

/**
 * Courses with no parsed weekday/time (e.g. 인터넷강의) don't have anywhere to render in the
 * weekly grid, so they're stacked here underneath it instead — same as Everytime.
 */
@Composable
fun OnlineCourseStack(
    courses: List<Course>,
    colorFor: (Course) -> Color,
    onClick: (Course) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        courses.forEach { course ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colorFor(course).copy(alpha = 0.55f))
                    .clickable { onClick(course) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = OnPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "인터넷강의",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnPrimary.copy(alpha = 0.85f)
                )
            }
        }
    }
}
