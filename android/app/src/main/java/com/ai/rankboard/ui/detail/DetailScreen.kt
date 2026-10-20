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
import com.ai.rankboard.ui.common.VendorIcon
import com.ai.rankboard.ui.common.openUrl
import com.ai.rankboard.ui.common.scoreColor
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    slug: String,
    onBack: () -> Unit,
    onOpenModel: (String) -> Unit,
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
    var inputMillions by remember(slug) { mutableStateOf("1") }
    var outputMillions by remember(slug) { mutableStateOf("1") }
    var calculatorOpen by remember(slug) { mutableStateOf(false) }
    var radarOpen by remember(slug) { mutableStateOf(false) }
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
        val representativePricing = remember(state.scores) { representativePricedScore(state.scores) }
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
                                )
                                Text(
                                    model.vendor?.takeIf { it.isNotBlank() } ?: "厂商未获取",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                        listOf(
                            "参数" to (model.paramsB?.let { "${formatNum(it)} B" } ?: "未获取"),
                            "上下文" to (model.contextWindow?.takeIf { it.isNotBlank() } ?: "未获取"),
                            "许可" to (model.license?.takeIf { it.isNotBlank() } ?: "未获取"),
                            "发布日期" to (model.releaseDate?.takeIf { it.isNotBlank() } ?: "未获取"),
                            "价格" to (representativePricing?.let(::formatModelPrice) ?: "未获取"),
                        ).forEach { (label, value) ->
                            Text("$label：$value", style = MaterialTheme.typography.bodyMedium)
                        }
                        representativePricing?.let { price ->
                            Text("价格来源：${price.boardName}；抓取 ${price.fetchedAt.takeIf { it.isNotBlank() } ?: "未获取"}。价格非实时且未换汇。", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatChip(
                                label = "上榜",
                                value = "${state.scores.size}",
                            )
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
                                        "模型来源记录",
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
                com.ai.rankboard.ui.common.CrossBoardNotice()
                SectionHeader(title = "各榜单真实成绩")
                if (state.scores.isEmpty()) Text("快照中暂无该模型的榜单成绩；未上榜不等于零分。")
            }
            items(state.scores.size) { index -> ScoreCard(state.scores[index], state.generatedAt) }
            item {
                val priced = representativePricing
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
                            Text(
                                text = estimatePrice(priced, inputMillions, outputMillions),
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
                                    "1 = 100 万 tokens；${priced.boardName}。${formatModelPrice(priced)}。非实时账单，不含缓存/阶梯/税费。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { radarOpen = !radarOpen }) { Text(if (radarOpen) "收起相对位置雷达" else "展开相对位置雷达（非统一能力分数）") }
                if (radarOpen) {
                    Text("各轴仅为各榜内min-max相对位置，不是百分位或统一能力总分；不同评测条件不可直接横比。", style = MaterialTheme.typography.bodySmall)
                    ScoreRadarChart(state.scores, Modifier.fillMaxWidth().height(280.dp))
                }
            }
            item {
                SectionHeader(title = "同厂商模型")
                if (state.relatedModels.isEmpty()) Text(if (model.vendor.isNullOrBlank()) "厂商未获取，无法匹配同厂商模型。" else "快照中暂无其他同厂商模型。")
            }
            items(state.relatedModels.size) { index ->
                val related = state.relatedModels[index]
                TextButton(onClick = { onOpenModel(related.slug) }, modifier = Modifier.fillMaxWidth()) {
                    Text(related.displayName, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ScoreCard(
    score: com.ai.rankboard.data.ScoreDto,
    generatedAt: String?,
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
                    )
                    Text(
                        text = listOfNotNull(
                            detailRankLabel(score.rank),
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
                    } ?: "未获取",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = scoreColor(score.score, score.scoreMin, score.scoreMax),
                )
            }
            Text("来源：${com.ai.rankboard.data.boardSourceLabel(score.board)}", style = MaterialTheme.typography.bodySmall)
            Text("成绩抓取：${score.fetchedAt.takeIf { it.isNotBlank() } ?: "未获取"}", style = MaterialTheme.typography.bodySmall)
            Text(com.ai.rankboard.data.metricExplanation(score.scoreType), style = MaterialTheme.typography.bodySmall)
            com.ai.rankboard.ui.common.BoardTrustCard(score.board, generatedAt = generatedAt)
            ScoreBar(
                score = score.score,
                minScore = score.scoreMin,
                maxScore = score.scoreMax,
                modifier = Modifier.fillMaxWidth(),
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
