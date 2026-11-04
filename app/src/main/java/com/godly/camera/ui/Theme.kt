package com.godly.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GodlyColors {
    val Bg = Color(0xFF05070D)
    val Blue = Color(0xFF3D8BFF)
    val Orange = Color(0xFFFF8A1F)
    val Red = Color(0xFFFF2D3F)
    val Glass = Color(0x44101830)
    val GlassBorder = Color(0x44FFFFFF)
    val Dim = Color(0xAAFFFFFF)
}

@Composable
fun GodlyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GodlyColors.Blue,
            background = GodlyColors.Bg,
            surface = Color(0xFF0B1020),
            onSurface = Color.White,
        ),
        content = content,
    )
}

/** "Liquid glass" look without real blur (blur is expensive on a Galaxy A51): translucent fill + light border. */
fun Modifier.glass(radius: Dp = 20.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(GodlyColors.Glass).border(1.dp, GodlyColors.GlassBorder, shape)
}
