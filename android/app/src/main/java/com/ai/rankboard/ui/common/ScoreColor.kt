package com.ai.rankboard.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val LowScore = Color(0xFFEF4444)
private val MidScore = Color(0xFFF59E0B)
private val HighScore = Color(0xFF22C55E)

@Composable
fun scoreColor(score: Double?): Color {
    if (score == null || score.isNaN()) return MaterialTheme.colorScheme.onSurfaceVariant
    val ratio = (score.coerceIn(0.0, 100.0) / 100.0).toFloat()
    return if (ratio < 0.5f) {
        lerp(LowScore, MidScore, ratio * 2f)
    } else {
        lerp(MidScore, HighScore, (ratio - 0.5f) * 2f)
    }
}
