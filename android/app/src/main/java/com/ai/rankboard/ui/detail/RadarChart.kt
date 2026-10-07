package com.ai.rankboard.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.rankboard.data.ScoreDto
import com.ai.rankboard.ui.common.boardSourceId
import com.ai.rankboard.ui.common.normalizedScoreRatio
import com.ai.rankboard.ui.common.sourceVisual
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ScoreRadarChart(
    scores: List<ScoreDto>,
    modifier: Modifier = Modifier,
) {
    val radarScores = remember(scores) { selectRadarScores(scores) }
    if (radarScores.size < 3) return

    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant
    val gridFill = MaterialTheme.colorScheme.surfaceContainerLowest
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = onSurfaceVariant,
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
    val valueStyle = TextStyle(
        color = onSurface,
        fontSize = MaterialTheme.typography.labelLarge.fontSize,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "能力雷达",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "榜单内归一化百分位",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${radarScores.size} 维",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                    )
                    Text(
                        "均值 ${averagePercent(radarScores)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = onSurfaceVariant,
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(top = 2.dp),
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val labelSpace = 56.dp.toPx()
                val radius = min(size.width, size.height) / 2f - labelSpace
                val angleStep = (2.0 * Math.PI / radarScores.size).toFloat()
                val startAngle = (-Math.PI / 2).toFloat()

                fun point(index: Int, ratio: Float): Offset {
                    val angle = startAngle + index * angleStep
                    return Offset(
                        center.x + cos(angle) * radius * ratio,
                        center.y + sin(angle) * radius * ratio,
                    )
                }

                fun polygon(level: Float): Path {
                    val path = Path()
                    repeat(radarScores.size) { index ->
                        val target = point(index, level)
                        if (index == 0) path.moveTo(target.x, target.y) else path.lineTo(target.x, target.y)
                    }
                    path.close()
                    return path
                }

                listOf(0.25f, 0.5f, 0.75f, 1f).forEachIndexed { index, level ->
                    val ring = polygon(level)
                    if (index != 3) drawPath(ring, gridFill.copy(alpha = 0.42f))
                    drawPath(
                        ring,
                        if (index == 3) outline else outline.copy(alpha = 0.52f),
                        style = Stroke(1.dp.toPx()),
                    )
                }

                repeat(radarScores.size) { index ->
                    drawLine(
                        color = outline.copy(alpha = 0.52f),
                        start = center,
                        end = point(index, 1f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val dataPath = Path()
                radarScores.forEachIndexed { index, score ->
                    val target = point(index, (radarRatio(score) ?: 0f).coerceIn(0.12f, 1f))
                    if (index == 0) dataPath.moveTo(target.x, target.y) else dataPath.lineTo(target.x, target.y)
                }
                dataPath.close()
                drawPath(
                    dataPath,
                    Brush.verticalGradient(
                        colors = listOf(primary.copy(alpha = 0.32f), primary.copy(alpha = 0.08f)),
                        startY = center.y - radius,
                        endY = center.y + radius,
                    ),
                )
                drawPath(dataPath, primary, style = Stroke(2.2.dp.toPx()))

                radarScores.forEachIndexed { index, score ->
                    val target = point(index, (radarRatio(score) ?: 0f).coerceIn(0.12f, 1f))
                    drawCircle(Color.White, 5.dp.toPx(), target)
                    drawCircle(primary, 3.4.dp.toPx(), target)
                }

                val labelRadius = radius + 20.dp.toPx()
                radarScores.forEachIndexed { index, score ->
                    val angle = startAngle + index * angleStep
                    val anchor = Offset(
                        center.x + cos(angle) * labelRadius,
                        center.y + sin(angle) * labelRadius,
                    )
                    val label = textMeasurer.measure(
                        AnnotatedString(axisLabel(score)),
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val value = textMeasurer.measure(
                        AnnotatedString(axisValue(score)),
                        style = valueStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val gap = 3.dp.toPx()
                    val blockHeight = label.size.height + gap + value.size.height
                    val labelTop = (anchor.y - blockHeight / 2f)
                        .coerceIn(0f, size.height - blockHeight)
                    val labelLeft = (anchor.x - label.size.width / 2f)
                        .coerceIn(0f, size.width - label.size.width)
                    val valueLeft = (anchor.x - value.size.width / 2f)
                        .coerceIn(0f, size.width - value.size.width)

                    drawText(
                        label,
                        color = onSurfaceVariant,
                        topLeft = Offset(labelLeft, labelTop),
                    )
                    drawText(
                        value,
                        color = onSurface,
                        topLeft = Offset(valueLeft, labelTop + label.size.height + gap),
                    )
                }
            }

            val sourceLabels = radarScores
                .map { boardSourceId(it.boardSlug) }
                .distinct()
                .map { sourceVisual(it).label }
            Text(
                text = "来源 · ${sourceLabels.joinToString(" · ")}",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

internal fun selectRadarScores(scores: List<ScoreDto>): List<ScoreDto> {
    val priority = listOf(
        "overall", "coding", "writing", "multimodal",
        "agent", "math", "analysis", "search", "speed", "value",
    )
    return scores
        .filter { it.score != null }
        .groupBy { it.dimension }
        .map { (_, values) ->
            values.maxByOrNull { score -> radarRatio(score) ?: -1.0f }
        }
        .filterNotNull()
        .sortedWith(
            compareByDescending<ScoreDto> { priority.indexOf(it.dimension) >= 0 }
                .thenBy { priority.indexOf(it.dimension).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                .thenByDescending { score -> radarRatio(score) ?: 0.0f },
        )
        .take(6)
}

internal fun radarRatio(score: ScoreDto): Float? {
    normalizedScoreRatio(score.score, score.scoreMin, score.scoreMax)?.let { return it }
    val rank = score.rank.takeIf { it > 0 } ?: return null
    val count = score.entryCount.takeIf { it >= rank } ?: return null
    return (count - rank + 1).toFloat() / count
}

private fun radarPercent(score: ScoreDto): Int =
    radarRatio(score)?.times(100)?.roundToInt() ?: 0

private fun averagePercent(scores: List<ScoreDto>): Int {
    val values = scores.mapNotNull(::radarRatio)
    if (values.isEmpty()) return 0
    return (values.average() * 100).roundToInt()
}

private fun axisLabel(score: ScoreDto): String = when (score.dimension) {
    "overall" -> "综合"
    "coding" -> "代码"
    "writing" -> "写作"
    "multimodal" -> "多模态"
    "agent" -> "智能体"
    "search" -> "搜索"
    "speed" -> "速度"
    "value" -> "性价比"
    "math" -> "数学"
    "analysis" -> "分析"
    "reasoning" -> "推理"
    "instruction" -> "指令遵循"
    else -> score.boardName.substringBefore(" ")
}

private fun axisValue(score: ScoreDto): String {
    val rank = score.rank.takeIf { it > 0 }?.let { "#$it" } ?: "-"
    return "${radarPercent(score)}% $rank"
}
