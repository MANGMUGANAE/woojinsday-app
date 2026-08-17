package com.daejin.woojintoday.ui.icons

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.daejin.woojintoday.ui.theme.AccentBlue
import com.daejin.woojintoday.ui.theme.AccentBlueDeep

/**
 * Flat, single-color, stroke-based vector icons. No emoji, no gradients —
 * every icon is drawn as scalable geometry so it stays crisp at any size.
 */

@Composable
fun IconIdCard(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 22.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.08f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        val cardTop = h * 0.22f
        val cardHeight = h * 0.58f
        val cardRect = androidx.compose.ui.geometry.Rect(
            offset = Offset(w * 0.06f, cardTop),
            size = Size(w * 0.88f, cardHeight)
        )
        drawRoundRect(
            color = tint,
            topLeft = cardRect.topLeft,
            size = cardRect.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f),
            style = stroke
        )
        val avatarCenter = Offset(w * 0.28f, cardTop + cardHeight * 0.5f)
        drawCircle(color = tint, radius = w * 0.09f, center = avatarCenter, style = stroke)
        val lineStartX = w * 0.5f
        val lineEndX = w * 0.82f
        drawLine(
            color = tint,
            start = Offset(lineStartX, cardTop + cardHeight * 0.36f),
            end = Offset(lineEndX, cardTop + cardHeight * 0.36f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(lineStartX, cardTop + cardHeight * 0.64f),
            end = Offset(lineEndX - w * 0.14f, cardTop + cardHeight * 0.64f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconLock(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 22.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.09f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        val bodyTop = h * 0.46f
        val bodyRect = androidx.compose.ui.geometry.Rect(
            offset = Offset(w * 0.2f, bodyTop),
            size = Size(w * 0.6f, h * 0.42f)
        )
        drawRoundRect(
            color = tint,
            topLeft = bodyRect.topLeft,
            size = bodyRect.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f),
            style = stroke
        )
        val shackleRadius = w * 0.2f
        val shackleCenter = Offset(w * 0.5f, bodyTop)
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(shackleCenter.x - shackleRadius, shackleCenter.y - shackleRadius * 2),
            size = Size(shackleRadius * 2, shackleRadius * 2),
            style = stroke
        )
        drawCircle(color = tint, radius = w * 0.045f, center = Offset(w * 0.5f, bodyTop + bodyRect.size.height * 0.45f))
    }
}

@Composable
fun IconEye(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 22.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.09f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w * 0.5f, h * 0.5f)
        drawArc(
            color = tint,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.06f, h * 0.14f),
            size = Size(w * 0.88f, h * 0.72f),
            style = stroke
        )
        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.06f, h * 0.14f),
            size = Size(w * 0.88f, h * 0.72f),
            style = stroke
        )
        drawCircle(color = tint, radius = w * 0.12f, center = center, style = stroke)
    }
}

@Composable
fun IconEyeOff(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 22.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.09f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        drawArc(
            color = tint,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.06f, h * 0.14f),
            size = Size(w * 0.88f, h * 0.72f),
            style = stroke
        )
        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.06f, h * 0.14f),
            size = Size(w * 0.88f, h * 0.72f),
            style = stroke
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.16f, h * 0.16f),
            end = Offset(w * 0.84f, h * 0.84f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconAlertCircle(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 18.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.1f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w * 0.5f, h * 0.5f)
        drawCircle(color = tint, radius = w * 0.42f, center = center, style = stroke)
        drawLine(
            color = tint,
            start = Offset(center.x, h * 0.32f),
            end = Offset(center.x, h * 0.56f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
        drawCircle(color = tint, radius = stroke.width * 0.55f, center = Offset(center.x, h * 0.68f))
    }
}

@Composable
fun IconClose(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 14.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.16f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        drawLine(color = tint, start = Offset(w * 0.22f, h * 0.22f), end = Offset(w * 0.78f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.78f, h * 0.22f), end = Offset(w * 0.22f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
fun IconMenu(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 22.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.1f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        val xStart = w * 0.18f
        val xEnd = w * 0.82f
        listOf(0.28f, 0.5f, 0.72f).forEach { fraction ->
            drawLine(
                color = tint,
                start = Offset(xStart, h * fraction),
                end = Offset(xEnd, h * fraction),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun IconArrowBack(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 20.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.12f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height
        drawLine(color = tint, start = Offset(w * 0.75f, h * 0.5f), end = Offset(w * 0.25f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.48f, h * 0.24f), end = Offset(w * 0.25f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.48f, h * 0.76f), end = Offset(w * 0.25f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
fun IconSearch(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 20.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.09f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        val radius = w * 0.32f
        val center = Offset(w * 0.42f, h * 0.42f)
        drawCircle(color = tint, radius = radius, center = center, style = stroke)
        drawLine(
            color = tint,
            start = Offset(center.x + radius * 0.75f, center.y + radius * 0.75f),
            end = Offset(w * 0.88f, h * 0.88f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconChevronDown(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 16.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.14f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        drawLine(color = tint, start = Offset(w * 0.2f, h * 0.35f), end = Offset(w * 0.5f, h * 0.65f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.5f, h * 0.65f), end = Offset(w * 0.8f, h * 0.35f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
fun IconChevronLeft(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 16.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.14f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        drawLine(color = tint, start = Offset(w * 0.65f, h * 0.2f), end = Offset(w * 0.35f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.35f, h * 0.5f), end = Offset(w * 0.65f, h * 0.8f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
fun IconChevronRight(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 16.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.14f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        drawLine(color = tint, start = Offset(w * 0.35f, h * 0.2f), end = Offset(w * 0.65f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.65f, h * 0.5f), end = Offset(w * 0.35f, h * 0.8f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
fun IconPlus(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 18.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.12f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        drawLine(color = tint, start = Offset(w * 0.5f, h * 0.18f), end = Offset(w * 0.5f, h * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.18f, h * 0.5f), end = Offset(w * 0.82f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
fun IconCheck(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 16.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.14f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.2f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.74f)
            lineTo(w * 0.82f, h * 0.28f)
        }
        drawPath(path, color = tint, style = stroke)
    }
}

@Composable
fun IconCalendar(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.08f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height
        val bodyTop = h * 0.24f
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.1f, bodyTop),
            size = Size(w * 0.8f, h * 0.66f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f),
            style = stroke
        )
        drawLine(color = tint, start = Offset(w * 0.1f, h * 0.42f), end = Offset(w * 0.9f, h * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.32f, h * 0.14f), end = Offset(w * 0.32f, h * 0.32f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.68f, h * 0.14f), end = Offset(w * 0.68f, h * 0.32f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawCircle(color = tint, radius = w * 0.045f, center = Offset(w * 0.34f, h * 0.64f))
        drawCircle(color = tint, radius = w * 0.045f, center = Offset(w * 0.5f, h * 0.64f))
        drawCircle(color = tint, radius = w * 0.045f, center = Offset(w * 0.66f, h * 0.64f))
    }
}

/** Three ascending bars, podium-style (2nd / 1st / 3rd) — reads as "ranking", not a trophy emoji. */
@Composable
fun IconRankBars(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barWidth = w * 0.2f
        val corner = androidx.compose.ui.geometry.CornerRadius(w * 0.05f, w * 0.05f)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.1f, h * 0.44f),
            size = Size(barWidth, h * 0.42f),
            cornerRadius = corner
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.4f, h * 0.18f),
            size = Size(barWidth, h * 0.68f),
            cornerRadius = corner
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.7f, h * 0.58f),
            size = Size(barWidth, h * 0.28f),
            cornerRadius = corner
        )
    }
}

@Composable
fun IconBell(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.08f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.28f, h * 0.62f)
            cubicTo(w * 0.28f, h * 0.34f, w * 0.34f, h * 0.16f, w * 0.5f, h * 0.16f)
            cubicTo(w * 0.66f, h * 0.16f, w * 0.72f, h * 0.34f, w * 0.72f, h * 0.62f)
            lineTo(w * 0.84f, h * 0.74f)
            lineTo(w * 0.16f, h * 0.74f)
            close()
        }
        drawPath(path, color = tint, style = stroke)
        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.38f, h * 0.78f),
            size = Size(w * 0.24f, h * 0.2f),
            style = stroke
        )
    }
}

/** A solid plate with a fork and spoon crossed on top — reads as "meal/dining". */
@Composable
fun IconMeal(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w * 0.5f, h * 0.5f)
        val utensilColor = Color(0xFF2C2C2F)
        val utensilStroke = Stroke(width = w * 0.06f, cap = StrokeCap.Round)

        // Plate: solid disc in the icon's tint color
        drawCircle(color = tint, radius = w * 0.42f, center = center)

        // Fork: handle plus three short tines
        val forkX = w * 0.4f
        drawLine(utensilColor, Offset(forkX, h * 0.3f), Offset(forkX, h * 0.72f), utensilStroke.width, StrokeCap.Round)
        val tineTop = h * 0.28f
        val tineBottom = h * 0.42f
        listOf(-0.05f, 0f, 0.05f).forEach { dx ->
            drawLine(
                utensilColor,
                Offset(forkX + dx * w, tineTop),
                Offset(forkX + dx * w, tineBottom),
                utensilStroke.width * 0.6f,
                StrokeCap.Round
            )
        }

        // Spoon: oval head plus a handle
        val spoonX = w * 0.6f
        drawLine(utensilColor, Offset(spoonX, h * 0.44f), Offset(spoonX, h * 0.72f), utensilStroke.width, StrokeCap.Round)
        drawOval(
            color = utensilColor,
            topLeft = Offset(spoonX - w * 0.08f, h * 0.24f),
            size = Size(w * 0.16f, h * 0.22f)
        )
    }
}

/** Floppy-disk save glyph: envelope with a clipped corner, a shutter tab, and two label lines. */
@Composable
fun IconSave(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 22.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.09f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height
        val bodyPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.2f, h * 0.16f)
            lineTo(w * 0.68f, h * 0.16f)
            lineTo(w * 0.84f, h * 0.32f)
            lineTo(w * 0.84f, h * 0.84f)
            lineTo(w * 0.2f, h * 0.84f)
            close()
        }
        drawPath(bodyPath, color = tint, style = stroke)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.36f, h * 0.16f),
            size = Size(w * 0.3f, h * 0.18f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f, w * 0.03f),
            style = stroke
        )
        drawLine(color = tint, start = Offset(w * 0.32f, h * 0.6f), end = Offset(w * 0.72f, h * 0.6f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.32f, h * 0.72f), end = Offset(w * 0.6f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

/** Bulleted list: three rows, each a small dot with a line — reads as "list", not a hamburger menu. */
@Composable
fun IconList(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 22.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.1f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        listOf(0.26f, 0.5f, 0.74f).forEach { fraction ->
            drawCircle(color = tint, radius = w * 0.045f, center = Offset(w * 0.16f, h * fraction))
            drawLine(
                color = tint,
                start = Offset(w * 0.32f, h * fraction),
                end = Offset(w * 0.86f, h * fraction),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Door frame (open on the right) with an arrow stepping out through the gap. */
@Composable
fun IconLogout(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 20.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.1f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height
        val doorPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.6f, h * 0.16f)
            lineTo(w * 0.2f, h * 0.16f)
            lineTo(w * 0.2f, h * 0.84f)
            lineTo(w * 0.6f, h * 0.84f)
        }
        drawPath(doorPath, color = tint, style = stroke)
        drawLine(color = tint, start = Offset(w * 0.38f, h * 0.5f), end = Offset(w * 0.86f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.65f, h * 0.3f), end = Offset(w * 0.86f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.65f, h * 0.7f), end = Offset(w * 0.86f, h * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

/** Three horizontal sliders of varying knob position — reads as "filter/tune", not a list. */
@Composable
fun IconFilter(tint: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 20.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.1f, cap = StrokeCap.Round)
        val w = this.size.width
        val h = this.size.height
        val knobX = listOf(0.36f, 0.64f, 0.44f)
        listOf(0.22f, 0.5f, 0.78f).forEachIndexed { index, yFraction ->
            drawLine(
                color = tint,
                start = Offset(w * 0.14f, h * yFraction),
                end = Offset(w * 0.86f, h * yFraction),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
            drawCircle(color = tint, radius = w * 0.12f, center = Offset(w * knobX[index], h * yFraction))
        }
    }
}

/** 4갈래 반짝임(sparkle) — Gemini류 "AI" 버튼에 흔히 쓰는 모양. 파란 그라데이션 고정이라 다른
 *  아이콘들과 달리 tint 파라미터를 받지 않는다("AI" 자체를 나타내는 고정 마크라는 의미). */
@Composable
fun IconSparkle(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 20.dp) {
    // 제미나이 아이콘처럼 단색으로 가만히 있지 않고, 계속 살짝 커졌다 작아지며 은은하게
    // 반짝이는 느낌을 준다 — 크기(scale)와 투명도를 같은 사이클로 움직이고, 그 안의 그라디언트
    // 각도도 천천히 돌려서 색이 계속 흐르는 것처럼 보이게 한다.
    val infiniteTransition = rememberInfiniteTransition(label = "sparkleShimmer")
    val twinkle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleTwinkle"
    )
    val shimmerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkleShimmerAngle"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w * 0.5f, h * 0.5f)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.02f)
            quadraticTo(center.x, center.y, w * 0.98f, h * 0.5f)
            quadraticTo(center.x, center.y, w * 0.5f, h * 0.98f)
            quadraticTo(center.x, center.y, w * 0.02f, h * 0.5f)
            quadraticTo(center.x, center.y, w * 0.5f, h * 0.02f)
            close()
        }

        val angleRad = Math.toRadians(shimmerAngle.toDouble())
        val radius = kotlin.math.hypot(w, h) / 2f
        val gradientStart = Offset(
            center.x - (kotlin.math.cos(angleRad) * radius).toFloat(),
            center.y - (kotlin.math.sin(angleRad) * radius).toFloat()
        )
        val gradientEnd = Offset(
            center.x + (kotlin.math.cos(angleRad) * radius).toFloat(),
            center.y + (kotlin.math.sin(angleRad) * radius).toFloat()
        )

        scale(scale = 0.88f + 0.12f * twinkle, pivot = center) {
            drawPath(
                path,
                brush = Brush.linearGradient(
                    colors = listOf(AccentBlue, Color.White, AccentBlueDeep),
                    start = gradientStart,
                    end = gradientEnd
                ),
                alpha = 0.7f + 0.3f * twinkle
            )
        }
    }
}

/** Daejin University mascot mark, used until 우진 provides the final wordmark/logo. */
@Composable
fun AppMark(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 72.dp) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = com.daejin.woojintoday.R.drawable.daejin_mascot),
        contentDescription = null,
        modifier = modifier.height(height),
        contentScale = androidx.compose.ui.layout.ContentScale.FillHeight
    )
}
