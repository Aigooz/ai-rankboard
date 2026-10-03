package com.ai.rankboard.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.ui.common.AppCard
import com.ai.rankboard.ui.common.ScoreBar
import com.ai.rankboard.ui.common.SectionHeader
import com.ai.rankboard.ui.common.SourceBadge
import com.ai.rankboard.ui.common.VendorIcon
import com.ai.rankboard.ui.common.boardSourceId
import com.ai.rankboard.ui.common.openUrl
import com.ai.rankboard.ui.common.scoreColor
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    slug: String,
    onBack: () -> Unit,
    vm: DetailViewModel = viewModel(
        key = slug,
        factory = DetailViewModel.factory(
            LocalContext.current.applicationContext as RankboardApp,
            slug,
        ),
    ),
) {
    val state by vm.state.collectAsState()
    val favoriteSlugs by vm.favoriteSlugs.collectAsState(initial = emptyList())
    val isFavorite = slug in favoriteSlugs.toSet()
    var inputMillions by remember { mutableStateOf("1") }
    var outputMillions by remember { mutableStateOf("1") }
    var calculatorOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.model?.displayName ?: "模型详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = vm::toggleFavorite) {
                        Icon(
                            if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isFavorite) "取消收藏" else "收藏",
                            tint = if (isFavorite) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        val model = state.model
        if (model == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (state.failed) "加载失败，请检查网络" else "加载中…")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            VendorIcon(vendor = model.vendor, size = 46.dp)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    model.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    model.vendor ?: "-",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                        val specs = buildList {
                            model.paramsB?.let { add("参数 ${formatNum(it)}B") }
                            model.license?.let { add(it) }
                            model.contextWindow?.let { add("上下文 $it") }
                            model.releaseDate?.takeIf { it.isNotBlank() }?.let {
                                add("发布 ${it.take(10)}")
                            }
                        }
                        if (specs.isNotEmpty()) {
                            Text(
                                specs.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatChip(
                                label = "上榜",
                                value = "${state.scores.size}",
                            )
                            state.scores.minOfOrNull { it.rank }?.let { bestRank ->
                                StatChip(
                                    label = "最佳",
                                    value = "#$bestRank",
                                )
                            }
                            model.sourceUrl?.takeIf { it.isNotBlank() }?.let {
                                TextButton(
                                    onClick = { openUrl(context, it) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        "官网",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionHeader(title = "能力雷达")
            }
            item {
                ScoreRadarChart(
                    scores = state.scores,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                )
            }
            item {
                SectionHeader(title = "各榜单成绩")
            }
            item {
                val priced = state.scores.firstOrNull { it.priceIn != null || it.priceOut != null }
                if (priced != null) {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { calculatorOpen = !calculatorOpen },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "价格计算器",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    if (calculatorOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (calculatorOpen) "收起价格计算器" else "展开价格计算器",
                                )
                            }
                            val input = inputMillions.toDoubleOrNull()
                            val output = outputMillions.toDoubleOrNull()
                            val inputCost = priced.priceIn?.let { it * (input ?: 0.0) }
                            val outputCost = priced.priceOut?.let { it * (output ?: 0.0) }
                            val symbol = if (priced.currency == "USD") "$" else "¥"
                            val cost = listOfNotNull(inputCost, outputCost).sum()
                            Text(
                                text = if (inputCost == null && outputCost == null) {
                                    "该模型暂无价格字段"
                                } else {
                                    "约 $symbol${String.format(Locale.US, "%.4f", cost)}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                            if (calculatorOpen) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedTextField(
                                        value = inputMillions,
                                        onValueChange = { inputMillions = it },
                                        label = { Text("输入 (M tokens)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = outputMillions,
                                        onValueChange = { outputMillions = it },
                                        label = { Text("输出 (M tokens)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Text(
                                    "1 = 100 万 tokens",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            items(state.scores.size) { i ->
                val s = state.scores[i]
                ScoreCard(score = s)
            }
        }
    }
}

@Composable
private fun ScoreCard(
    score: com.ai.rankboard.data.ScoreDto,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        score.boardName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val priceText = buildString {
                        val symbol = if (score.currency == "USD") "$" else "¥"
                        score.priceIn?.let { append(symbol + String.format(Locale.US, "%.2f", it)) }
                        score.priceOut?.let {
                            if (isNotEmpty()) append(" → ")
                            append(symbol + String.format(Locale.US, "%.2f", it))
                        }
                        if (isNotEmpty()) append(" /1M")
                    }
                    Text(
                        text = listOfNotNull(
                            "#${score.rank}",
                            priceText.takeIf { it.isNotEmpty() },
                            score.fetchedAt.take(10),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Text(
                    text = score.score?.let {
                        String.format(Locale.US, "%.1f", it) +
                            if (score.scoreCi != null) " ±${score.scoreCi}" else ""
                    } ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = scoreColor(score.score, score.scoreMin, score.scoreMax),
                )
            }
            ScoreBar(
                score = score.score,
                minScore = score.scoreMin,
                maxScore = score.scoreMax,
                modifier = Modifier.fillMaxWidth(),
            )
            SourceBadge(
                sourceId = boardSourceId(score.boardSlug),
                selected = true,
            )
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun formatNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
