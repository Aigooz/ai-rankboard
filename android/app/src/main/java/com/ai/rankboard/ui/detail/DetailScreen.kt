package com.ai.rankboard.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.ui.common.scoreColor
import com.ai.rankboard.ui.common.openUrl
import com.ai.rankboard.ui.common.VendorIcon
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    slug: String,
    onBack: () -> Unit,
    vm: DetailViewModel = viewModel(
        key = slug,
        factory = DetailViewModel.factory(LocalContext.current.applicationContext as RankboardApp, slug),
    ),
) {
    val state by vm.state.collectAsState()
    val favoriteSlugs by vm.favoriteSlugs.collectAsState(initial = emptyList())
    val isFavorite = slug in favoriteSlugs.toSet()
    var inputTokens by remember { mutableStateOf("1") }
    var outputTokens by remember { mutableStateOf("1") }
    var calculatorOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.model?.displayName ?: "模型详情", maxLines = 1)
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
                            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
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
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            VendorIcon(vendor = model.vendor, size = 28.dp)
                            Column {
                                Text(
                                    model.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                )
                                Text(
                                    model.vendor ?: "-",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val specs = buildList {
                            model.paramsB?.let { add("参数规模 ${formatNum(it)}B") }
                            model.license?.let { add(it) }
                            model.contextWindow?.let { add("上下文 $it") }
                        }
                        if (specs.isNotEmpty()) {
                            Text(
                                specs.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            model.releaseDate?.takeIf { it.isNotBlank() }?.let { releaseDate ->
                                StatChip(
                                    label = "发布",
                                    value = releaseDate.take(10),
                                )
                            }
                            val sourceUrl = model.sourceUrl
                            if (!sourceUrl.isNullOrBlank()) {
                                TextButton(
                                    onClick = { openUrl(context, sourceUrl) },
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
                Text(
                    "各榜单成绩与参考价格",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 5.dp, start = 2.dp),
                )
            }
            item {
                val priced = state.scores.firstOrNull { it.priceIn != null || it.priceOut != null }
                if (priced != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp)) {
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
                            val input = inputTokens.toDoubleOrNull()
                            val output = outputTokens.toDoubleOrNull()
                            val inputCost = priced.priceIn?.let { (it * (input ?: 0.0) / 1_000_000.0) }
                            val outputCost = priced.priceOut?.let { (it * (output ?: 0.0) / 1_000_000.0) }
                            val symbol = if (priced.currency == "USD") "$" else "¥"
                            val cost = listOfNotNull(inputCost, outputCost).sum()
                            Text(
                                text = if (inputCost == null && outputCost == null) {
                                    "该模型暂无价格字段"
                                } else {
                                    "约 $symbol${String.format(Locale.US, "%.4f", cost)}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (calculatorOpen) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    OutlinedTextField(
                                        value = inputTokens,
                                        onValueChange = { inputTokens = it },
                                        label = { Text("输入 token") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = outputTokens,
                                        onValueChange = { outputTokens = it },
                                        label = { Text("输出 token") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            items(state.scores.size) { i ->
                val s = state.scores[i]
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.boardName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val priceText = buildString {
                                val symbol = if (s.currency == "USD") "$" else "¥"
                                s.priceIn?.let { append(symbol + String.format(Locale.US, "%.2f", it)) }
                                s.priceOut?.let {
                                    if (isNotEmpty()) append(" → ")
                                    append(symbol + String.format(Locale.US, "%.2f", it))
                                }
                                if (isNotEmpty()) append(" /1M")
                            }
                            Text(
                                text = listOfNotNull(
                                    "#${s.rank}",
                                    priceText.takeIf { it.isNotEmpty() },
                                    s.fetchedAt.take(10),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = s.score?.let {
                                String.format(Locale.US, "%.1f", it) +
                                    if (s.scoreCi != null) " ±${s.scoreCi}" else ""
                            } ?: "-",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            color = scoreColor(s.score),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
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
