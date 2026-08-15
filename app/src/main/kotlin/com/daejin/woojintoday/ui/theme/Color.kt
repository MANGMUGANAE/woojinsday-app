package com.daejin.woojintoday.ui.theme

import androidx.compose.ui.graphics.Color

// Base
val Background = Color(0xFF0A0A0B)
val Surface = Color(0xFF19191B)
val SurfaceElevated = Color(0xFF222224)
val Border = Color(0xFF2C2C2F)
val BorderFocused = Color(0xFFFF6A1A)

// Primary
val Primary = Color(0xFFFF6A1A)
val PrimaryPressed = Color(0xFFE05C12)
val OnPrimary = Color(0xFFFFFFFF)

// Text
val TextPrimary = Color(0xFFF5F5F6)
val TextSecondary = Color(0xFFA3A3A8)
val TextPlaceholder = Color(0xFF6B6B70)
val TextDisabled = Color(0xFF4A4A4D)

// Status
val ErrorRed = Color(0xFFFF4D4F)

// Home hub cards — subtle two-tone gradients (Apple Music style: barely-there shift, not a
// full-blown gradient), one accent pair per card so hero/sub-cards read as distinct destinations.
val AccentBlue = Color(0xFF4C6FFF)
val AccentBlueDeep = Color(0xFF3A54D9)
val AccentPurple = Color(0xFF8B6FE0)
val AccentPurpleDeep = Color(0xFF6E54C4)

// Timetable course blocks — flat, medium-dark hues that all hold up under white text.
val TimetablePalette = listOf(
    Primary,
    Color(0xFF4C6FFF),
    Color(0xFF2BB6A3),
    Color(0xFF8B6FE0),
    Color(0xFFE0587A),
    Color(0xFF55B172),
    Color(0xFFC99A3A),
    Color(0xFF4FA6D8)
)