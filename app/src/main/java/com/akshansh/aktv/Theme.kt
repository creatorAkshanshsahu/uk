package com.akshansh.aktv

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF08080D)
val CardBg = Color(0xFF1B1B26)
val Accent = Color(0xFF8B6CFF)
val TextDim = Color(0xFFB4B4C4)

@Composable
fun AktvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = CardBg),
        content = content,
    )
}
