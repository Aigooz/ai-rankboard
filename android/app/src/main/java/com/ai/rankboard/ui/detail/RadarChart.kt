package com.ai.rankboard.ui.detail

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.rankboard.data.ScoreDto
import com.ai.rankboard.ui.common.boardSourceId
import com.ai.rankboard.ui.common.normalizedScoreRatio
import com.ai.rankboard.ui.common.sourceVisual
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun ScoreRadarChart(
    scores: List<ScoreDto>,
    modifier: Modifier = Modifier,
) {
    val activeScores = scores.filter { it.score != null }
    if (activeScores.size < 3) return

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val pointColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelTextSize = with(LocalDensity.current) { 12.sp.toPx() }
    val labelPaint = Paint().apply {
        isAntiAlias = true
        color = labelColor.toArgb()
        textSize = labelTextSize
        textAlign = Paint.Align.CENTER
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.66f
        val angleStep = (2 * Math.PI / activeScores.size).toFloat()
        val startAngle = (-Math.PI / 2).toFloat()

        fun point(index: Int, ratio: Float): Offset {
            val angle = startAngle + index * angleStep
            return Offset(
                center.x + cos(angle) * radius * ratio,
                center.y + sin(angle) * radius * ratio,
            )
        }

        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { level ->
            val gridPath = Path()
            activeScores.indices.forEach { index ->
                val target = point(index, level)
                if (index == 0) gridPath.moveTo(target.x, target.y) else gridPath.lineTo(target.x, target.y)
            }
            gridPath.close()
            drawPath(gridPath, gridColor, style = Stroke(1.dp.toPx()))
        }

        activeScores.indices.forEach { index ->
            val outer = point(index, 1f)
            drawLine(
                color = gridColor,
                start = center,
                end = outer,
                strokeWidth = 1.dp.toPx(),
            )
        }

        val dataPath = Path()
        activeScores.forEachIndexed { index, score ->
            val ratio = normalizedScoreRatio(score.score, score.scoreMin, score.scoreMax) ?: 0.5f
            val target = point(index, ratio)
            if (index == 0) dataPath.moveTo(target.x, target.y) else dataPath.lineTo(target.x, target.y)
        }
        dataPath.close()
        drawPath(dataPath, fillColor)
        drawPath(dataPath, lineColor, style = Stroke(2.dp.toPx()))

        activeScores.forEachIndexed { index, score ->
            val ratio = normalizedScoreRatio(score.score, score.scoreMin, score.scoreMax) ?: 0.5f
            val target = point(index, ratio)
            drawCircle(pointColor, 3.dp.toPx(), target)
        }

        activeScores.forEachIndexed { index, score ->
            val angle = startAngle + index * angleStep
            val labelRadius = radius + 16.dp.toPx()
            val target = Offset(
                center.x + cos(angle) * labelRadius,
                center.y + sin(angle) * labelRadius,
            )
            drawContext.canvas.nativeCanvas.drawText(
                axisLabel(score),
                target.x,
                target.y + labelPaint.textSize / 3f,
                labelPaint,
            )
        }
    }
}

private fun axisLabel(score: ScoreDto): String {
    val source = sourceVisual(boardSourceId(score.boardSlug)).shortLabel
    return when (score.dimension) {
        "overall" -> "综合 $source"
        "coding" -> "代码 $source"
        "writing" -> "写作 $source"
        "multimodal" -> "多模态 $source"
        "agent" -> "智能体 $source"
        "search" -> "搜索 $source"
        "speed" -> "速度 $source"
        "value" -> "性价比 $source"
        "math" -> "数学 $source"
        "analysis" -> "分析 $source"
        else -> score.boardName
    }
}
