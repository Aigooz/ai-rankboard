package com.ai.rankboard.ui.compare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.rankboard.data.ModelDetailResponse
import com.ai.rankboard.data.ScoreDto
import com.ai.rankboard.ui.common.normalizedScoreRatio
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 多模型叠加雷达：每个模型一条折线，轴为双方共有的能力维度，
 * 数值沿用详情页的"榜内归一化百分位"，保证跨模型可比。
 */
@Composable
internal fun CompareRadarChart(
    models: List<ModelDetailResponse>,
    modifier: Modifier = Modifier,
) {
    val dimensions = remember(models) { selectCompareAxes(models) }
    if (dimensions.size < 3) return
    val series = remember(models, dimensions) {
        models.map { model -> modelAxisRatios(model, dimensions) }
    }

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant
    val gridFill = MaterialTheme.colorScheme.surfaceContainerLowest
    val primary = MaterialTheme.colorScheme.primary
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = onSurfaceVariant,
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
        fontWeight = FontWeight.Medium,
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
                        "按各维度榜内百分位归一",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                    )
                }
                Text(
                    "${dimensions.size} 维",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .padding(top = 2.dp),
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val labelSpace = 44.dp.toPx()
                val radius = min(size.width, size.height) / 2f - labelSpace
                val angleStep = (2.0 * Math.PI / dimensions.size).toFloat()
                val startAngle = (-Math.PI / 2).toFloat()

                fun point(index: Int, ratio: Float): Offset {
                    val angle = startAngle + index * angleStep
                    return Offset(
                        center.x + cos(angle) * radius * ratio,
                        center.y + sin(angle) * radius * ratio,
                    )
                }

                fun polygon(ratios: List<Float>): Path {
                    val path = Path()
                    ratios.forEachIndexed { index, ratio ->
                        val target = point(index, ratio)
                        if (index == 0) path.moveTo(target.x, target.y) else path.lineTo(target.x, target.y)
                    }
                    path.close()
                    return path
                }

                listOf(0.25f, 0.5f, 0.75f, 1f).forEachIndexed { index, level ->
                    val ring = polygon(List(dimensions.size) { level })
                    if (index != 3) drawPath(ring, gridFill.copy(alpha = 0.42f))
                    drawPath(
                        ring,
                        if (index == 3) outline else outline.copy(alpha = 0.52f),
                        style = Stroke(1.dp.toPx()),
                    )
                }
                repeat(dimensions.size) { index ->
                    drawLine(
                        color = outline.copy(alpha = 0.52f),
                        start = center,
                        end = point(index, 1f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                series.forEachIndexed { modelIndex, ratios ->
                    val accent = modelAccent(modelIndex)
                    drawPath(polygon(ratios), accent.copy(alpha = 0.10f))
                    drawPath(polygon(ratios), accent, style = Stroke(2.dp.toPx()))
                }

                val labelRadius = radius + 16.dp.toPx()
                dimensions.forEachIndexed { index, dimension ->
                    val angle = startAngle + index * angleStep
                    val anchor = Offset(
                        center.x + cos(angle) * labelRadius,
                        center.y + sin(angle) * labelRadius,
                    )
                    val label = textMeasurer.measure(
                        AnnotatedString(dimensionLabel(dimension)),
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    drawText(
                        label,
                        color = onSurfaceVariant,
                        topLeft = Offset(
                            (anchor.x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width),
                            (anchor.y - label.size.height / 2f).coerceIn(0f, size.height - label.size.height),
                        ),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                models.forEachIndexed { index, response ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(modelAccent(index), CircleShape),
                        )
                        Text(
                            text = parseModelStrength(response.model.displayName).first,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private val AXIS_PRIORITY = listOf(
    "overall", "coding", "writing", "multimodal",
    "agent", "math", "analysis", "search", "speed", "value",
)

/** 取所有模型可排名次（有分数或名次）的维度，按常用维度优先，最多 6 轴。 */
internal fun selectCompareAxes(models: List<ModelDetailResponse>): List<String> {
    val rankable = models.flatMap { it.scores }.filter { it.score != null || it.rank > 0 }
    return rankable.groupBy { it.dimension }.keys
        .sortedWith(
            compareByDescending<String> { AXIS_PRIORITY.indexOf(it) >= 0 }
                .thenBy { AXIS_PRIORITY.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE },
        )
        .take(6)
}

/** 模型在每条轴上的归一化比例；该维度无成绩时贴近圆心。 */
internal fun modelAxisRatios(model: ModelDetailResponse, dimensions: List<String>): List<Float> =
    dimensions.map { dimension ->
        model.scores.filter { it.dimension == dimension }
            .mapNotNull { compareRadarRatio(it) }
            .maxOrNull()
            ?.coerceIn(0.12f, 1f)
            ?: 0.12f
    }

internal fun compareRadarRatio(score: ScoreDto): Float? {
    normalizedScoreRatio(score.score, score.scoreMin, score.scoreMax)?.let { return it }
    val rank = score.rank.takeIf { it > 0 } ?: return null
    val count = score.entryCount.takeIf { it >= rank } ?: return null
    return (count - rank + 1).toFloat() / count
}
