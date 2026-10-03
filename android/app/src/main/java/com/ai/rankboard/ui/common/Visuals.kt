package com.ai.rankboard.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

data class SourceVisual(
    val label: String,
    val shortLabel: String,
    val color: Color,
)

fun sourceVisual(sourceId: String): SourceVisual {
    val id = sourceId.lowercase(Locale.ROOT)
    return when {
        id == "modelsage" -> SourceVisual("ModelSage", "MS", Color(0xFF6366F1))
        id == "livebench" -> SourceVisual("LiveBench", "LB", Color(0xFF14B8A6))
        id == "swebench" || id == "swe-bench" -> SourceVisual("SWE-bench", "SB", Color(0xFF2563EB))
        id == "artificialanalysis" || id == "artificial-analysis" ->
            SourceVisual("Artificial Analysis", "AA", Color(0xFF7C3AED))
        id == "vals" || id == "vals.ai" -> SourceVisual("Vals", "VS", Color(0xFF1B5E20))
        id == "arena" || id == "lmarena" -> SourceVisual("Arena", "AR", Color(0xFF111827))
        id == "openrouter" -> SourceVisual("OpenRouter", "OR", Color(0xFF0EA5E9))
        else -> SourceVisual(sourceId.uppercase(Locale.ROOT), "DS", Color(0xFF64748B))
    }
}

fun boardSourceId(boardSlug: String, fallback: String = "modelsage"): String = when {
    boardSlug.startsWith("livebench") -> "livebench"
    boardSlug.startsWith("swe-bench") -> "swebench"
    boardSlug.startsWith("arena") -> "arena"
    else -> fallback
}

@Composable
fun SourceBadge(
    sourceId: String,
    count: Int? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val visual = remember(sourceId) { sourceVisual(sourceId) }
    val container = if (selected) {
        visual.color.copy(alpha = 0.13f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
    }
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = container,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, visual.color.copy(alpha = 0.5f))
        } else {
            null
        },
        modifier = modifier.clickable(enabled = onClick != null) { onClick?.invoke() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(18.dp)
                    .background(visual.color, CircleShape),
            ) {
                Text(
                    visual.shortLabel,
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                text = buildString {
                    append(visual.label)
                    if (count != null) append(" · $count")
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ScoreBar(
    score: Double?,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 6.dp,
) {
    val ratio = (((score ?: 0.0).coerceIn(0.0, 100.0)) / 100.0).toFloat()
    val color = scoreColor(score)
    Box(
        modifier = modifier
            .height(height)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(percent = 50)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(ratio)
                .background(color, RoundedCornerShape(percent = 50)),
        )
    }
}
