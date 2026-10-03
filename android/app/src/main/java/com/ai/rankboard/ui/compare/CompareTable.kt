package com.ai.rankboard.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.rankboard.data.BoardDto
import com.ai.rankboard.data.ModelDetailResponse
import com.ai.rankboard.data.ScoreDto
import com.ai.rankboard.ui.common.AppCard
import com.ai.rankboard.ui.common.VendorIcon
import com.ai.rankboard.ui.common.scoreColor
import java.util.Locale

internal val MODEL_ACCENTS = listOf(
    Color(0xFF6366F1),
    Color(0xFF0EA5E9),
    Color(0xFFF97316),
    Color(0xFF10B981),
)

internal fun modelAccent(index: Int): Color = MODEL_ACCENTS[index % MODEL_ACCENTS.size]

// 指标列固定宽度，模型列随数量增加时整表可横向滚动。
private val LABEL_WIDTH = 78.dp
private val CELL_WIDTH = 66.dp

internal data class CompareCell(
    val text: String,
    val color: Color = Color.Unspecified,
    val mono: Boolean = false,
    val best: Boolean = false,
)

/**
 * 规格表式的对照视图：一行一个指标、一列一个模型，
 * 取代逐模型堆叠卡片，让差距一眼可见。
 */
@Composable
internal fun CompareTableCard(
    models: List<ModelDetailResponse>,
    selectedBoard: BoardDto?,
    boards: List<BoardDto>,
    onOpenModel: (String) -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            CompareTableHeader(models, onOpenModel)
            val boardSlug = selectedBoard?.slug.orEmpty()
            val boardScores = models.map { it.scores.firstOrNull { s -> s.boardSlug == boardSlug } }

            GroupLabel(selectedBoard?.name?.let { "当前榜单 · $it" } ?: "当前榜单")
            MetricRow("得分", models.indices.map { index ->
                val score = boardScores[index]
                CompareCell(
                    text = score?.score?.let(::formatScore) ?: "—",
                    color = scoreColor(score?.score, score?.scoreMin, score?.scoreMax),
                    mono = true,
                    best = index == bestScoreIndex(boardScores.map { it?.score }),
                )
            })
            MetricRow("排名", models.indices.map { index ->
                CompareCell(
                    text = formatRank(boardScores[index]?.rank),
                    mono = true,
                )
            })
            MetricRow("与最优差", models.indices.map { index ->
                val value = boardScores[index]?.score
                val best = boardScores.mapNotNull { it?.score }.maxOrNull()
                CompareCell(
                    text = when {
                        value == null || best == null -> "—"
                        value == best -> "最优"
                        else -> formatDelta(value - best)
                    },
                    mono = value != null && value != best,
                    best = value != null && value == best,
                )
            })
            val priceInBestIndex = bestPriceIndex(boardScores.map { it?.priceIn })
            val priceOutBestIndex = bestPriceIndex(boardScores.map { it?.priceOut })
            MetricRow("输入价/M", boardScores.mapIndexed { index, score ->
                CompareCell(
                    text = formatPrice(score?.priceIn, score?.currency),
                    mono = true,
                    best = index == priceInBestIndex,
                )
            })
            MetricRow("输出价/M", boardScores.mapIndexed { index, score ->
                CompareCell(
                    text = formatPrice(score?.priceOut, score?.currency),
                    mono = true,
                    best = index == priceOutBestIndex,
                )
            })

            GroupLabel("基本信息")
            MetricRow("推理档位", models.map {
                CompareCell(text = parseModelStrength(it.model.displayName).second ?: "—")
            })
            MetricRow("参数量", models.map {
                CompareCell(text = it.model.paramsB?.let { params -> "${formatNum(params)}B" } ?: "—")
            })
            MetricRow("上下文", models.map {
                CompareCell(text = it.model.contextWindow?.takeIf { ctx -> ctx.isNotBlank() } ?: "—")
            })
            MetricRow("发布时间", models.map {
                CompareCell(text = it.model.releaseDate?.take(10)?.takeIf { date -> date.isNotBlank() } ?: "—")
            })
            MetricRow("许可", models.map {
                CompareCell(text = it.model.license?.takeIf { license -> license.isNotBlank() } ?: "—")
            })

            GroupLabel("全维度成绩")
            boards.forEach { board ->
                BoardScoreRow(board, models)
            }
        }
    }
}

@Composable
private fun CompareTableHeader(
    models: List<ModelDetailResponse>,
    onOpenModel: (String) -> Unit,
) {
    Row(Modifier.heightIn(min = 70.dp)) {
        Spacer(Modifier.width(LABEL_WIDTH))
        models.forEachIndexed { index, response ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .width(CELL_WIDTH)
                    .clickable { onOpenModel(response.model.slug) }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(modelAccent(index), CircleShape),
                )
                VendorIcon(vendor = response.model.vendor, size = 18.dp)
                Text(
                    text = parseModelStrength(response.model.displayName).first,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BoardScoreRow(board: BoardDto, models: List<ModelDetailResponse>) {
    val scores = models.map { it.scores.firstOrNull { s -> s.boardSlug == board.slug } }
    val bestIndex = bestScoreIndex(scores.map { it?.score })
        ?: bestRankIndex(scores.map { it?.rank })
    Row(Modifier.heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = board.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(LABEL_WIDTH)
                .padding(end = 4.dp),
        )
        models.forEachIndexed { index, _ ->
            val score = scores[index]
            Box(
                Modifier
                    .width(CELL_WIDTH)
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .background(
                        if (index == bestIndex) modelAccent(index).copy(alpha = 0.16f) else Color.Transparent,
                        RoundedCornerShape(7.dp),
                    )
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val rankOnly = score?.score == null && (score?.rank ?: 0) > 0
                    Text(
                        text = when {
                            score?.score != null -> formatScore(score.score)
                            rankOnly -> "#${score!!.rank}"
                            else -> "—"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (index == bestIndex) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (score?.score != null) {
                            scoreColor(score.score, score.scoreMin, score.scoreMax)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                    )
                    Text(
                        text = when {
                            score == null -> ""
                            rankOnly -> score.votes?.let { votes -> "%,d 票".format(votes) } ?: "票选"
                            else -> formatRank(score.rank).takeIf { it != "未上榜" } ?: ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, cells: List<CompareCell>) {
    Row(Modifier.heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(LABEL_WIDTH).padding(end = 4.dp),
        )
        cells.forEachIndexed { index, cell ->
            Box(
                Modifier
                    .width(CELL_WIDTH)
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .background(
                        if (cell.best) modelAccent(index).copy(alpha = 0.16f) else Color.Transparent,
                        RoundedCornerShape(7.dp),
                    )
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cell.text,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = if (cell.mono) FontFamily.Monospace else null,
                    fontWeight = if (cell.best) FontWeight.SemiBold else FontWeight.Normal,
                    color = cell.color,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(title: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 3.dp),
    )
}

internal fun formatScore(value: Double?): String {
    if (value == null) return "未上榜"
    return if (value >= 1000) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

internal fun formatRank(rank: Int?): String {
    return if (rank != null && rank > 0) "#$rank" else "未上榜"
}

internal fun formatPrice(value: Double?, currency: String?): String {
    if (value == null) return "-"
    val symbol = if (currency.equals("USD", ignoreCase = true)) "$" else "¥"
    return "$symbol${String.format(Locale.US, "%.2f", value)}"
}

internal fun formatNum(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

internal fun formatDelta(value: Double): String = String.format(Locale.US, "%+.1f", value)

/** 最高分所在列；全部为空时返回 null。并列时取先出现的一列。 */
internal fun bestScoreIndex(values: List<Double?>): Int? {
    val max = values.filterNotNull().maxOrNull() ?: return null
    return values.indexOfFirst { it == max }
}

/** 名次最靠前（>0）的列，用于只有票选排名的榜单。 */
internal fun bestRankIndex(values: List<Int?>): Int? {
    val min = values.filterNotNull().filter { it > 0 }.minOrNull() ?: return null
    return values.indexOfFirst { it == min }
}

/** 最低价所在列（忽略缺失与 0 价），价格更低视为更优。 */
internal fun bestPriceIndex(values: List<Double?>): Int? {
    val min = values.filterNotNull().filter { it > 0 }.minOrNull() ?: return null
    return values.indexOfFirst { it == min }
}

internal fun dimensionLabel(dimension: String): String = when (dimension) {
    "overall" -> "综合"
    "coding" -> "代码"
    "writing" -> "写作"
    "multimodal" -> "多模态"
    "agent" -> "智能体"
    "search" -> "搜索"
    "speed" -> "速度"
    "value" -> "性价比"
    "math" -> "数学"
    "analysis" -> "数据分析"
    else -> dimension.replaceFirstChar { it.uppercase(Locale.US) }
}

internal fun sourceLabel(sourceId: String): String = when (sourceId.lowercase(Locale.US)) {
    "modelsage" -> "ModelSage"
    "livebench" -> "LiveBench"
    "swebench", "swe-bench" -> "SWE-bench"
    "arena", "lmarena" -> "Arena"
    else -> sourceId
}

internal fun parseModelStrength(displayName: String): Pair<String, String?> {
    val match = Regex("\\s*\\((xhigh|high|medium|low|max|non-reasoning|reasoning)( with fallback)?\\)$")
        .find(displayName) ?: return displayName to null
    val strength = match.groupValues[1].replaceFirstChar { it.uppercase() } +
        if (match.groupValues[2].isNotBlank()) " FB" else ""
    return displayName.removeRange(match.range) to strength
}
