package com.ai.rankboard.ui.select

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Savings
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
import com.ai.rankboard.data.EntryDto
import com.ai.rankboard.data.PriceTierPicks
import com.ai.rankboard.data.blendedPriceCny
import com.ai.rankboard.ui.common.VendorIcon
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectScreen(
    onOpenModel: (String) -> Unit,
    onBack: () -> Unit,
    vm: SelectViewModel = viewModel(
        factory = SelectViewModel.factory(
            LocalContext.current.applicationContext as RankboardApp,
        ),
    ),
) {
    val state by vm.state.collectAsState()
    var tableSort by remember { mutableStateOf(ModelTableSort.INTELLIGENCE) }
    var tableOpenness by remember { mutableStateOf(0) }
    val tableRows = remember(state.scatterModels, tableSort, tableOpenness) {
        filterAndSortModels(state.scatterModels, tableSort, tableOpenness)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智能选型") },
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(
                        "正在读取模型数据",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            state.error.isNotBlank() -> {
                Text(
                    state.error,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(20.dp),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.snapshotDate.isNotBlank()) {
                        item {
                            Text(
                                "按综合能力与混合单价比价 · 数据快照 ${state.snapshotDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        TierPicksCard(
                            picks = state.tierPicks,
                            onOpenModel = onOpenModel,
                        )
                    }
                    item {
                        PriceScatterCard(
                            models = state.scatterModels,
                            onOpenModel = onOpenModel,
                        )
                    }
                    item(key = "table-title") {
                        SectionTitle("全部模型")
                    }
                    item(key = "table-controls") {
                        ModelTableControls(
                            sort = tableSort,
                            openness = tableOpenness,
                            count = tableRows.size,
                            onSort = { tableSort = it },
                            onOpenness = { tableOpenness = it },
                        )
                    }
                    items(tableRows, key = { "table-" + it.slug }) { model ->
                        ModelTableRow(
                            model = model,
                            onOpenModel = onOpenModel,
                        )
                    }
                    item {
                        SectionTitle("最新模型")
                    }
                    if (state.latest.isEmpty()) {
                        item {
                            EmptyText("暂无发布时间数据")
                        }
                    } else {
                        items(
                            items = state.latest,
                            key = { "latest-" + it.slug },
                        ) { entry ->
                            LatestRow(
                                entry = entry,
                                onClick = { onOpenModel(entry.slug) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TierPicksCard(
    picks: List<PriceTierPicks>,
    onOpenModel: (String) -> Unit,
) {
    if (picks.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Savings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "同档最便宜",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                )
                Text(
                    "混合价 = 3×输入 + 输出",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            picks.forEach { tier ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${tier.label}档 · ${tier.range}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    tier.models.forEachIndexed { index, entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenModel(entry.slug) }
                                .padding(vertical = 2.dp),
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(12.dp),
                            )
                            VendorIcon(vendor = entry.vendor, size = 20.dp)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Text(
                                    entry.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        entry.vendor,
                                        entry.score?.let { "能力 ${String.format(Locale.US, "%.1f", it)}" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                formatBlendedPrice(entry),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatBlendedPrice(entry: EntryDto): String {
    val price = entry.blendedPriceCny() ?: return "-"
    return when {
        price <= 0.0 -> "免费"
        price < 1.0 -> "¥%.2f/M".format(Locale.US, price)
        price < 100.0 -> "¥%.1f/M".format(Locale.US, price)
        else -> "¥%.0f/M".format(Locale.US, price)
    }
}

@Composable
private fun LatestRow(
    entry: EntryDto,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VendorIcon(vendor = entry.vendor, size = 22.dp)
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(entry.vendor, entry.releaseDate?.take(10)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                entry.score?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EmptyText(text: String) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
}
