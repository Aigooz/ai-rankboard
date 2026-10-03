package com.ai.rankboard.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val LowScore = Color(0xFFEF4444)
private val MidScore = Color(0xFFF59E0B)
private val HighScore = Color(0xFF22C55E)

@Composable
fun scoreColor(
    score: Double?,
    minScore: Double? = 0.0,
    maxScore: Double? = 100.0,
): Color {
    if (score == null || score.isNaN()) return MaterialTheme.colorScheme.onSurfaceVariant
    val ratio = normalizedScoreRatio(score, minScore, maxScore) ?: return MaterialTheme.colorScheme.onSurfaceVariant
    return if (ratio < 0.5f) {
        lerp(LowScore, MidScore, ratio * 2f)
    } else {
        lerp(MidScore, HighScore, (ratio - 0.5f) * 2f)
    }
}

fun normalizedScoreRatio(
    score: Double?,
    minScore: Double?,
    maxScore: Double?,
): Float? {
    if (score == null || score.isNaN() || minScore == null || maxScore == null) return null
    if (maxScore <= minScore) return 0.5f
    return ((score - minScore) / (maxScore - minScore)).coerceIn(0.0, 1.0).toFloat()
}
