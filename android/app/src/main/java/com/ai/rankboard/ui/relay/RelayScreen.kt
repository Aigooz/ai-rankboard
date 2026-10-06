package com.ai.rankboard.ui.relay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.RelayModelRank
import com.ai.rankboard.ui.common.scoreColor
import com.ai.rankboard.ui.common.VendorIcon
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayScreen(
    vm: RelayViewModel = viewModel(
        factory = RelayViewModel.factory(LocalContext.current.applicationContext as RankboardApp),
    ),
) {
    val state by vm.state.collectAsState()
    var selectedModel by remember { mutableStateOf<RelayModelRank?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("中转站排名") })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = vm::setUrl,
                    label = { Text("模型广场 / API 地址") },
                    placeholder = { Text("https://example.com") },
                    supportingText = {
                        Text(
                            "支持公开模型广场和 OpenAI 兼容接口；粘贴网页或根地址会自动尝试。",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.relayEndpoints.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            "已保存地址",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 7.dp)
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            state.relayEndpoints.forEach { endpoint ->
                                FilterChip(
                                    selected = state.url == endpoint.url,
                                    onClick = { vm.selectEndpoint(endpoint) },
                                    label = {
                                        Text(
                                            relayEndpointLabel(endpoint.url),
                                            maxLines = 1,
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { vm.removeEndpoint(endpoint) },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "移除 ${relayEndpointLabel(endpoint.url)}",
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = vm::setApiKey,
                    label = { Text("访问密钥（可选）") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = vm::fetch,
                    enabled = !state.loading && state.url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            Modifier
                                .size(18.dp)
                                .padding(end = 8.dp),
                        )
                    } else {
                        Icon(Icons.Filled.Hub, contentDescription = null)
                    }
                    Text(if (state.loading) "排名中" else "生成排名")
                }
            }
            if (state.error.isNotBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            state.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
            state.ranking?.let { ranking ->
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "已排名 ${ranking.matched}/${ranking.total} 个模型",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "其中 ${ranking.inferred} 个为推断匹配 · 快照 ${ranking.snapshotGeneratedAt.take(10)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::setQuery,
                        placeholder = { Text("搜索模型 ID") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RelayFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = state.filter == filter,
                                onClick = { vm.setFilter(filter) },
                                label = { Text(filter.label, maxLines = 1) },
                            )
                        }
                    }
                }
                val visible = state.visibleModels
                if (visible.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
                            Text(
                                "没有匹配的模型",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                } else {
                    items(
                        count = visible.size,
                        key = { index -> visible[index].remoteId },
                    ) { index ->
                        RelayModelRow(
                            model = visible[index],
                            onClick = { selectedModel = visible[index] },
                        )
                    }
                }
            }
        }
    }

    selectedModel?.let { model ->
        RelayModelDetailSheet(
            model = model,
            onDismiss = { selectedModel = null },
        )
    }
}

@Composable
private fun RelayModelRow(
    model: RelayModelRank,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = model.relayRank.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.width(30.dp),
            )
            VendorIcon(vendor = model.vendor, size = 20.dp)
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        model.remoteId,
                        model.vendor,
                        model.matchQuality.label,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                model.priceHint()?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = model.score?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    color = scoreColor(model.score),
                )
                Text(
                    text = model.globalRank?.let { "#$it" } ?: "无榜单",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelayModelDetailSheet(
    model: RelayModelRank,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VendorIcon(vendor = model.vendor, size = 34.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = model.remoteId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = model.score?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = scoreColor(model.score),
                )
            }

            DetailItem("厂商", model.vendor ?: "未知")
            DetailItem("匹配状态", model.matchQuality.label)
            DetailItem("全球排名", model.globalRank?.let { "#$it" } ?: "未上榜")
            DetailItem("分组", model.groupName ?: "默认")
            DetailItem(
                "分组倍率",
                model.rateMultiplier
                    ?.let { "${String.format(Locale.US, "%.2f", it)}x" }
                    ?: "-",
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )

            Text(
                "价格对比",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            DetailItem("中转站实付价", model.actualPrice?.let { formatPrice(it) } ?: "未提供")
            DetailItem("官方参考价", model.officialPrice?.let { formatPrice(it) } ?: "未提供")
            DetailItem("实付 / 官方", priceComparison(model))
            Text(
                "价格为输入/输出价格，按每百万 tokens 展示；分组倍率由中转站提供。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

private fun priceComparison(model: RelayModelRank): String {
    val multiplier = model.priceMultiplier
    if (multiplier != null) {
        return String.format(Locale.US, "%.0f%% 官方价（%.2fx）", multiplier * 100, multiplier)
    }

    val actualCurrency = model.actualPrice?.currency
    val officialCurrency = model.officialPrice?.currency
    if (actualCurrency != null && officialCurrency != null && actualCurrency != officialCurrency) {
        return "币种不同，暂不折算"
    }
    return "价格数据不足"
}

private fun relayEndpointLabel(url: String): String {
    return url
        .removePrefix("https://")
        .removePrefix("http://")
        .trim('/')
        .ifBlank { url }
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun RelayModelRank.priceHint(): String? {
    val parts = listOfNotNull(
        groupName,
        rateMultiplier?.let { String.format(Locale.US, "%.2fx", it) },
    )
    return parts.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun formatPrice(price: com.ai.rankboard.data.RelayPrice): String {
    val symbol = when (price.currency?.uppercase()) {
        "USD" -> "$"
        "CNY", "RMB" -> "¥"
        else -> price.currency?.let { "$it " } ?: ""
    }
    return "${symbol}${formatPerMillion(price.input)}/${formatPerMillion(price.output)} /M"
}

private fun formatPerMillion(value: Double?): String {
    if (value == null) return "-"
    return String.format(Locale.US, "%.2f", value * 1_000_000.0)
}
