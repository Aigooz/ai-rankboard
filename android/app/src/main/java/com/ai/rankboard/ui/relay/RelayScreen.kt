package com.ai.rankboard.ui.relay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.RelayMatchQuality
import com.ai.rankboard.data.RelayModelRank
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
                        RelayModelRow(visible[index])
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayModelRow(model: RelayModelRank) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = model.relayRank.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp),
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
                model.priceSummary()?.let { summary ->
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
                    color = if (model.matchQuality == RelayMatchQuality.NONE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
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

private fun RelayModelRank.priceSummary(): String? {
    val parts = listOfNotNull(
        groupName,
        actualPrice?.let { "实付 ${formatPrice(it)}" },
        officialPrice?.let { "官方 ${formatPrice(it)}" },
        rateMultiplier?.let { String.format(Locale.US, "%.2fx", it) },
        priceMultiplier?.let { String.format(Locale.US, "%.0f%% 官方价", it * 100) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
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
