package com.kumalatova.lmsadmin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Тёмная тема, приглушённые тона. Оранжевый значок как в плагине на сайте. */
object LmsColors {
    val Bg = Color(0xFF171B22)
    val Row = Color(0xFF212833)
    val RowActive = Color(0xFF2C3646)
    val Line = Color(0xFF232933)
    val Text = Color(0xFFE3E6EB)
    val Muted = Color(0xFF8B93A1)
    val Dim = Color(0xFF5D6675)
    val Badge = Color(0xFFC98A4B)
    val BadgeText = Color(0xFF1D140A)
}

@Composable
fun LmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = LmsColors.Bg,
            surface = LmsColors.Bg,
            onBackground = LmsColors.Text,
            onSurface = LmsColors.Text,
            primary = LmsColors.Badge,
            onPrimary = LmsColors.BadgeText,
        ),
        content = content,
    )
}
