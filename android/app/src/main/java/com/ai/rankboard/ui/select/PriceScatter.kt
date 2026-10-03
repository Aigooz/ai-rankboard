package com.ai.rankboard.ui.select

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.rankboard.data.EntryDto
import com.ai.rankboard.data.blendedPriceCny
import com.ai.rankboard.ui.common.AppCard
import com.ai.rankboard.ui.common.VendorIcon
import java.util.Locale
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max

internal data class ScatterPoint(
    val entry: EntryDto,
    val price: Double,
    val score: Double,
    val nx: Float,
    val ny: Float,
)

/** 能力-价格全景散点：横轴为混合价（对数刻度），纵轴为能力分，点按圆点查看模型。 */
@Composable
internal fun PriceScatterCard(
    entries: List<EntryDto>,
    onOpenModel: (String) -> Unit,
) {
    val points = remember(entries) { computeScatterPoints(entries) }
    if (points.size < 5) return
    var selected by remember(points) { mutableStateOf<ScatterPoint?>(null) }

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = onSurfaceVariant,
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
    )

    AppCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "能力-价格全景",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "横轴为混合价（对数）· 点按圆点查看模型",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                    )
                }
                Text(
                    "${points.size} 个模型",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant,
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            selected = nearestPoint(points, offset.x / size.width, offset.y / size.height)
                        }
                    },
            ) {
                val left = 36.dp.toPx()
                val bottom = 22.dp.toPx()
                val top = 4.dp.toPx()
                val right = 8.dp.toPx()
                val plotW = size.width - left - right
                val plotH = size.height - top - bottom

                val minLog = log10(max(0.01, points.minOf { it.price }))
                val maxLog = log10(max(minLog + 0.01, points.maxOf { it.price }))

                // 价格十进网格：0.1 / 1 / 10 / 100
                for (decade in -1..2) {
                    val value = log10(10.0.pow(decade))
                    if (value < minLog || value > maxLog) continue
                    val nx = ((value - minLog) / (maxLog - minLog)).toFloat()
                    val x = left + nx * plotW
                    drawLine(outlineColor.copy(alpha = 0.45f), Offset(x, top), Offset(x, top + plotH), 1.dp.toPx())
                    val text = when (decade) {
                        -1 -> "¥0.1"
                        0 -> "¥1"
                        1 -> "¥10"
                        else -> "¥100"
                    }
                    val label = textMeasurer.measure(AnnotatedString(text), labelStyle)
                    drawText(
                        label,
                        color = onSurfaceVariant,
                        topLeft = Offset(x - label.size.width / 2f, size.height - bottom + 4.dp.toPx()),
                    )
                }

                val scoreMin = floor(points.minOf { it.score } / 10) * 10
                val scoreMax = max(floor(points.maxOf { it.score } / 10) * 10 + 10, scoreMin + 10)
                for (v in listOf(scoreMin, (scoreMin + scoreMax) / 2, scoreMax)) {
                    val ny = (1f - ((v - scoreMin) / (scoreMax - scoreMin)).toFloat()).coerceIn(0f, 1f)
                    val y = top + ny * plotH
                    drawLine(outlineColor.copy(alpha = 0.30f), Offset(left, y), Offset(left + plotW, y), 1.dp.toPx())
                    val label = textMeasurer.measure(
                        AnnotatedString("%.0f".format(Locale.US, v)),
                        labelStyle,
                    )
                    drawText(label, color = onSurfaceVariant, topLeft = Offset(2.dp.toPx(), y - label.size.height / 2f))
                }

                points.forEachIndexed { index, point ->
                    val center = Offset(left + point.nx * plotW, top + point.ny * plotH)
                    drawCircle(pointColor(point.score, scoreMin, scoreMax), 3.6.dp.toPx(), center)
                }
                selected?.let { hit ->
                    val center = Offset(left + hit.nx * plotW, top + hit.ny * plotH)
                    drawCircle(Color.White, 5.5.dp.toPx(), center)
                    drawCircle(pointColor(hit.score, scoreMin, scoreMax), 5.5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                }
            }

            selected?.let { hit ->
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenModel(hit.entry.slug) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    ) {
                        VendorIcon(vendor = hit.entry.vendor, size = 22.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                hit.entry.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "能力 %.1f · 混合价 %s/M".format(
                                    Locale.US,
                                    hit.score,
                                    formatBlended(hit.price),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatBlended(price: Double): String = when {
    price <= 0.0 -> "免费"
    price < 1.0 -> "¥%.2f".format(Locale.US, price)
    else -> "¥%.1f".format(Locale.US, price)
}

private fun Double.pow(n: Int): Double = Math.pow(this, n.toDouble())

/** 与 scoreColor 相同的红-黄-绿渐变，纯函数版供 Canvas 绘制使用。 */
internal fun pointColor(score: Double, minScore: Double, maxScore: Double): Color {
    val ratio = if (maxScore <= minScore) {
        0.5f
    } else {
        ((score - minScore) / (maxScore - minScore)).toFloat().coerceIn(0f, 1f)
    }
    val low = Color(0xFFEF4444)
    val mid = Color(0xFFF59E0B)
    val high = Color(0xFF22C55E)
    return if (ratio < 0.5f) lerp(low, mid, ratio * 2f) else lerp(mid, high, (ratio - 0.5f) * 2f)
}

internal fun computeScatterPoints(entries: List<EntryDto>): List<ScatterPoint> {
    val priced = entries.mapNotNull { entry ->
        val price = entry.blendedPriceCny() ?: return@mapNotNull null
        val score = entry.score ?: return@mapNotNull null
        Triple(entry, max(price, 0.01), score)
    }
    if (priced.isEmpty()) return emptyList()

    val minLog = log10(priced.minOf { it.second })
    val maxLog = log10(max(minLog + 0.01, priced.maxOf { it.second }))
    val scoreMin = priced.minOf { it.third }
    val scoreMax = max(scoreMin + 0.01, priced.maxOf { it.third })

    return priced.map { (entry, price, score) ->
        ScatterPoint(
            entry = entry,
            price = price,
            score = score,
            nx = (((log10(price) - minLog) / (maxLog - minLog)).toFloat()).coerceIn(0.02f, 0.98f),
            ny = (1f - ((score - scoreMin) / (scoreMax - scoreMin)).toFloat()).coerceIn(0.02f, 0.98f),
        )
    }
}

/** 命中半径为归一化画布的 7%。 */
internal fun nearestPoint(points: List<ScatterPoint>, nx: Float, ny: Float): ScatterPoint? =
    points.minByOrNull { hypot(it.nx - nx, it.ny - ny) }
        ?.takeIf { hypot(it.nx - nx, it.ny - ny) < 0.07f }
