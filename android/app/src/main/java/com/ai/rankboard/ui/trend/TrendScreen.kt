package com.ai.rankboard.ui.trend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.NewsArticleDto
import com.ai.rankboard.data.PriceDistributionRow
import com.ai.rankboard.data.UsageEntryDto
import com.ai.rankboard.ui.common.AppCard
import com.ai.rankboard.ui.common.VendorIcon
import com.ai.rankboard.ui.common.openUrl
import com.ai.rankboard.ui.compare.parseModelStrength

private const val ARTICLES_BASE_URL = "https://artificialanalysis.ai"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendScreen(
    onOpenModel: (String) -> Unit,
    onBack: () -> Unit,
    vm: TrendViewModel = viewModel(
        factory = TrendViewModel.factory(
            LocalContext.current.applicationContext as RankboardApp,
        ),
    ),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("趋势") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.usage == null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "暂无用量数据，下拉刷新榜单后重试",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                val usage = state.usage!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item(key = "header") {
                        AppCard(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "网关真实用量榜 · ${usage.weekLabel}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                                Text(
                                    "本周共消耗 ${usage.totalTokens} token · 平台总量环比 ${usage.platformWow}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "仅统计 OpenRouter 聚合网关，不代表全球调用量；数据来源 ModelSage。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    val maxShare = usage.entries.maxOfOrNull { it.share } ?: 1.0
                    items(usage.entries.size, key = { usage.entries[it].position }) { index ->
                        UsageRow(
                            entry = usage.entries[index],
                            maxShare = maxShare,
                            onClick = {
                                vm.slugFor(usage.entries[index].name)?.let(onOpenModel)
                            },
                        )
                    }
                    val articles = state.news?.articles.orEmpty()
                    if (articles.isNotEmpty()) {
                        item(key = "news-title") {
                            Text(
                                "行业资讯 · Artificial Analysis",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        items(articles.size, key = { "news-$it" }) { index ->
                            NewsRow(
                                article = articles[index],
                                onClick = { openUrl(context, ARTICLES_BASE_URL + articles[index].url) },
                            )
                        }
                        item(key = "news-note") {
                            Text(
                                "英文内容来自 Artificial Analysis，点击跳转原文。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    if (state.distribution.isNotEmpty()) {
                        item(key = "dist-title") {
                            Text(
                                "价格分布 · ${state.snapshotDate}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        val maxCount = state.distribution.maxOf { it.count }
                        items(state.distribution.size, key = { "dist-$it" }) { index ->
                            DistributionRow(
                                row = state.distribution[index],
                                maxCount = maxCount,
                            )
                        }
                        item(key = "dist-note") {
                            Text(
                                "按混合价（3×输入 + 输出）口径统计，基于综合榜 ${state.distributionTotal} 个模型。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageRow(
    entry: UsageEntryDto,
    maxShare: Double,
    onClick: () -> Unit,
) {
    val wowColor = when {
        entry.wow == null -> MaterialTheme.colorScheme.onSurfaceVariant
        entry.wow.startsWith("+") -> Color(0xFF16A34A)
        entry.wow.startsWith("-") -> Color(0xFFDC2626)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    AppCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = "${entry.position}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(18.dp),
            )
            VendorIcon(vendor = vendorFor(entry.name), size = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = parseModelStrength(entry.name).first,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(4.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth((entry.share / maxOf(maxShare, 0.1)).toFloat().coerceIn(0.03f, 1f))
                            .fillMaxHeight()
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.tokens,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = "占比 ${entry.share}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = entry.wow.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = wowColor,
                modifier = Modifier.width(52.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun NewsRow(article: NewsArticleDto, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull("Artificial Analysis", article.publishedAt).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun DistributionRow(row: PriceDistributionRow, maxCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
            )
            Box(
                Modifier
                    .fillMaxWidth(row.count.toFloat() / maxOf(maxCount, 1))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), RoundedCornerShape(4.dp)),
            )
        }
        Text(
            text = "${row.count}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
        )
    }
}

/** 用量榜的模型名与快照厂商粗略归组，便于显示图标。 */
internal fun vendorFor(name: String): String? = when {
    name.contains("DeepSeek", true) -> "DeepSeek"
    name.contains("GLM", true) -> "智谱AI"
    name.contains("GPT", true) -> "OpenAI"
    name.contains("Gemini", true) -> "Google"
    name.contains("MiMo", true) -> "小米"
    name.contains("Claude", true) -> "Anthropic"
    name.contains("Kimi", true) || name.contains("Moonshot", true) -> "Moonshot/Kimi"
    name.contains("Hy3", true) || name.contains("Hunyuan", true) -> "腾讯混元"
    name.contains("Qwen", true) || name.contains("QwQ", true) -> "阿里百炼"
    else -> null
}
