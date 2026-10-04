package com.ai.rankboard.ui.select

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.FilterChip
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
import com.ai.rankboard.data.blendedPriceCny
import com.ai.rankboard.ui.common.AppCard
import com.ai.rankboard.ui.common.VendorIcon
import java.util.Locale
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max

/** 散点支持的质量轴，键与榜单 slug 对应。 */
internal val SCATTER_METRICS = listOf(
    "overall" to "综合智能",
    "coding" to "代码能力",
    "agent" to "Agent 能力",
    "arena-text" to "Arena 口碑",
    "speed" to "响应速度",
)

data class ScatterModel(
    val slug: String,
    val displayName: String,
    val vendor: String?,
    val priceCny: Double?,
    val scores: Map<String, Double>,
    val contextWindow: String? = null,
    val license: String? = null,
    val releaseDate: String? = null,
)

internal data class ScatterPoint(
    val model: ScatterModel,
    val price: Double,
    val score: Double,
    val sizeNorm: Float,
    val nx: Float,
    val ny: Float,
)

private val VENDOR_PALETTE = listOf(
    Color(0xFFF97316),
    Color(0xFFEAB308),
    Color(0xFF22C55E),
    Color(0xFF0EA5E9),
    Color(0xFF6366F1),
    Color(0xFFA855F7),
    Color(0xFFEC4899),
    Color(0xFF14B8A6),
    Color(0xFFF43F5E),
    Color(0xFF84CC16),
    Color(0xFF64748B),
    Color(0xFF8B5CF6),
)

internal fun assignVendorColors(models: List<ScatterModel>): Map<String, Color> =
    models.mapNotNull { it.vendor?.takeIf { vendor -> vendor.isNotBlank() } }
        .distinct()
        .sorted()
        .mapIndexed { index, vendor -> vendor to VENDOR_PALETTE[index % VENDOR_PALETTE.size] }
        .toMap()

/** 能力-价格全景散点：横轴混合价（对数），纵轴所选能力，气泡大小=响应速度，颜色按厂商。 */
@Composable
internal fun PriceScatterCard(
    models: List<ScatterModel>,
    onOpenModel: (String) -> Unit,
) {
    if (models.isEmpty()) return
    var metric by remember { mutableStateOf(SCATTER_METRICS.first().first) }
    val points = remember(models, metric) { computeScatterPoints(models, metric) }
    val vendorColors = remember(models) { assignVendorColors(models) }
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
                        "气泡越大响应越快 · 左上角性价比越高 · 点按查看模型",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SCATTER_METRICS, key = { it.first }) { (key, label) ->
                    FilterChip(
                        selected = metric == key,
                        onClick = {
                            metric = key
                            selected = null
                        },
                        label = { Text(label, maxLines = 1) },
                    )
                }
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

                for (decade in -1..3) {
                    val value = log10(10.0.pow(decade))
                    if (value < minLog || value > maxLog) continue
                    val nx = ((value - minLog) / (maxLog - minLog)).toFloat()
                    val x = left + nx * plotW
                    drawLine(outlineColor.copy(alpha = 0.45f), Offset(x, top), Offset(x, top + plotH), 1.dp.toPx())
                    val text = when (decade) {
                        -1 -> "¥0.1"
                        0 -> "¥1"
                        1 -> "¥10"
                        2 -> "¥100"
                        else -> "¥1000"
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

                points.forEach { point ->
                    val center = Offset(left + point.nx * plotW, top + point.ny * plotH)
                    val radius = (2.4f + 3.4f * point.sizeNorm).dp.toPx()
                    drawCircle(vendorColors[point.model.vendor] ?: Color(0xFF9E9E9E), radius, center)
                }
                selected?.let { hit ->
                    val center = Offset(left + hit.nx * plotW, top + hit.ny * plotH)
                    val radius = (2.4f + 3.4f * hit.sizeNorm).dp.toPx() + 2.dp.toPx()
                    drawCircle(Color.White, radius, center)
                    drawCircle(
                        vendorColors[hit.model.vendor] ?: Color(0xFF9E9E9E),
                        radius,
                        center,
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }

            selected?.let { hit ->
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenModel(hit.model.slug) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    ) {
                        VendorIcon(vendor = hit.model.vendor, size = 22.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                hit.model.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "%s %.1f · 混合价 %s/M".format(
                                    Locale.US,
                                    SCATTER_METRICS.firstOrNull { it.first == metric }?.second.orEmpty(),
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

            val legendVendors = vendorColors.entries.sortedBy { it.key }.take(10)
            if (legendVendors.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(legendVendors) { (vendor, color) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(color, CircleShape),
                            )
                            Text(
                                text = vendor,
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun formatBlended(price: Double): String = when {
    price <= 0.0 -> "免费"
    price < 1.0 -> "¥%.2f".format(Locale.US, price)
    else -> "¥%.1f".format(Locale.US, price)
}

private fun Double.pow(n: Int): Double = Math.pow(this, n.toDouble())

internal fun computeScatterPoints(models: List<ScatterModel>, metric: String): List<ScatterPoint> {
    val priced = models.mapNotNull { model ->
        val price = model.priceCny ?: return@mapNotNull null
        val score = model.scores[metric] ?: return@mapNotNull null
        model to Pair(max(price, 0.01), score)
    }
    if (priced.isEmpty()) return emptyList()

    val speeds = models.mapNotNull { it.scores["speed"] }
    val speedMin = speeds.minOrNull() ?: 0.0
    val speedMax = max(speedMin + 0.01, speeds.maxOrNull() ?: 0.0)

    val minLog = log10(priced.minOf { it.second.first })
    val maxLog = log10(max(minLog + 0.01, priced.maxOf { it.second.first }))
    val scoreMin = priced.minOf { it.second.second }
    val scoreMax = max(scoreMin + 0.01, priced.maxOf { it.second.second })

    return priced.map { (model, pair) ->
        val (price, score) = pair
        val speed = model.scores["speed"]
        ScatterPoint(
            model = model,
            price = price,
            score = score,
            sizeNorm = speed?.let { value ->
                ((value - speedMin) / (speedMax - speedMin)).toFloat().coerceIn(0f, 1f)
            } ?: 0.3f,
            nx = (((log10(price) - minLog) / (maxLog - minLog)).toFloat()).coerceIn(0.02f, 0.98f),
            ny = (1f - ((score - scoreMin) / (scoreMax - scoreMin)).toFloat()).coerceIn(0.02f, 0.98f),
        )
    }
}

/** 命中半径为归一化画布的 8%。 */
internal fun nearestPoint(points: List<ScatterPoint>, nx: Float, ny: Float): ScatterPoint? =
    points.minByOrNull { hypot(it.nx - nx, it.ny - ny) }
        ?.takeIf { hypot(it.nx - nx, it.ny - ny) < 0.08f }
