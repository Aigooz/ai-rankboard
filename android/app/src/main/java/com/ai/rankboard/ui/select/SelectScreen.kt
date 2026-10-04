package com.ai.rankboard.ui.select

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.EntryDto
import com.ai.rankboard.data.PriceTierPicks
import com.ai.rankboard.data.ScenarioRecommendation
import com.ai.rankboard.data.blendedPriceCny
import com.ai.rankboard.data.isLikelyOpenSourceLicense
import com.ai.rankboard.ui.common.AppCard
import com.ai.rankboard.ui.common.VendorIcon
import java.util.Locale

internal data class ScenarioBoard(
    val slug: String,
    val label: String,
    val icon: ImageVector,
)

internal val SCENARIO_BOARDS = listOf(
    ScenarioBoard("coding", "编程", Icons.Filled.Code),
    ScenarioBoard("agent", "智能体", Icons.Filled.SmartToy),
    ScenarioBoard("arena-text", "写作", Icons.Filled.EditNote),
    ScenarioBoard("livebench-math", "数学", Icons.Filled.Calculate),
    ScenarioBoard("arena-vision", "视觉理解", Icons.Filled.Visibility),
    ScenarioBoard("arena-search", "搜索", Icons.Filled.TravelExplore),
)

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
                        "正在汇总能力、价格与场景数据",
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
                                "混合价 = 3×输入 + 输出 · 数据快照 ${state.snapshotDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        ValuePicksCard(
                            picks = state.valuePicks,
                            isSourceBoard = state.valueIsSourceBoard,
                            onOpenModel = onOpenModel,
                        )
                    }
                    item {
                        ScenarioCard(
                            scenarios = state.scenarios,
                            onOpenModel = onOpenModel,
                        )
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
                    item {
                        Text(
                            "最新模型",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
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
private fun ValuePicksCard(
    picks: List<EntryDto>,
    isSourceBoard: Boolean,
    onOpenModel: (String) -> Unit,
) {
    if (picks.isEmpty()) return
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                    "性价比首选",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                )
                Text(
                    if (isSourceBoard) "来源榜指数" else "能力 ÷ 混合价",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            picks.forEachIndexed { index, entry ->
                SelectionRow(
                    leading = "${index + 1}",
                    entry = entry,
                    badge = if (isSourceBoard) {
                        entry.score?.let { "指数 ${formatOneDecimal(it)}" }
                    } else {
                        valueIndexOfText(entry)
                    },
                    onClick = { onOpenModel(entry.slug) },
                )
            }
        }
    }
}

@Composable
private fun ScenarioCard(
    scenarios: List<ScenarioRecommendation>,
    onOpenModel: (String) -> Unit,
) {
    if (scenarios.isEmpty()) return
    var selectedSlug by remember(scenarios) {
        mutableStateOf(scenarios.first().slug)
    }
    val selected = scenarios.firstOrNull { it.slug == selectedSlug } ?: scenarios.first()

    AppCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "按需求选模型",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${selected.total} 个模型",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(scenarios, key = { it.slug }) { scenario ->
                    val board = SCENARIO_BOARDS.firstOrNull { it.slug == scenario.slug }
                    FilterChip(
                        selected = scenario.slug == selected.slug,
                        onClick = { selectedSlug = scenario.slug },
                        label = { Text(board?.label ?: scenario.slug, maxLines = 1) },
                        leadingIcon = board?.let {
                            {
                                Icon(
                                    it.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        },
                    )
                }
            }

            SelectionRow(
                leading = "首选",
                entry = selected.strengthPick,
                badge = "能力优先",
                onClick = { onOpenModel(selected.strengthPick.slug) },
            )

            val saving = selected.savingRatio
            if (selected.valuePicks.isEmpty()) {
                Text(
                    "能力首选已是该场景的低价优选",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    if (saving != null && saving >= 1.2) "更划算 · 最多省 ${formatOneDecimal(saving)}×"
                    else "能力接近时更省钱",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                selected.valuePicks.forEachIndexed { index, entry ->
                    SelectionRow(
                        leading = "${index + 1}",
                        entry = entry,
                        badge = "省钱替代",
                        onClick = { onOpenModel(entry.slug) },
                    )
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
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "同档最便宜",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "按能力分档",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            picks.forEach { tier ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${tier.label}档 · ${tier.range}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            tierSpreadText(tier),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    tier.models.forEachIndexed { index, entry ->
                        SelectionRow(
                            leading = "${index + 1}",
                            entry = entry,
                            badge = null,
                            compact = true,
                            onClick = { onOpenModel(entry.slug) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionRow(
    leading: String,
    entry: EntryDto,
    badge: String?,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.24f),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (compact) 6.dp else 8.dp),
    ) {
        Text(
            leading,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(if (leading.length > 1) 26.dp else 12.dp),
        )
        VendorIcon(vendor = entry.vendor, size = if (compact) 18.dp else 20.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                entry.displayName,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modelMeta(entry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                formatBlendedPrice(entry),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!badge.isNullOrBlank()) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LatestRow(
    entry: EntryDto,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
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
                    latestMeta(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                entry.score?.let { formatOneDecimal(it) } ?: "-",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun modelMeta(entry: EntryDto): String = buildList {
    add(entry.vendor ?: "未知厂商")
    entry.score?.let { add("能力 ${formatOneDecimal(it)}") }
    if (entry.rank > 0) add("榜内 #${entry.rank}")
    if (isLikelyOpenSourceLicense(entry.license)) add("开源")
}.joinToString(" · ")

private fun latestMeta(entry: EntryDto): String = buildList {
    add(entry.vendor ?: "未知厂商")
    entry.releaseDate?.take(10)?.let { add(it) }
    if (entry.rank > 0) add("综合 #${entry.rank}")
    entry.blendedPriceCny()?.let { add(formatBlendedPriceText(it)) }
}.joinToString(" · ")

private fun valueIndexOfText(entry: EntryDto): String? {
    val score = entry.score ?: return null
    val price = entry.blendedPriceCny() ?: return null
    return "每元 ${formatOneDecimal(score / maxOf(price, 0.5))}"
}

private fun tierSpreadText(tier: PriceTierPicks): String {
    val low = tier.cheapestPrice ?: return "${tier.modelCount} 个模型"
    val high = tier.highestPrice ?: return "${tier.modelCount} 个模型"
    if (low <= 0.0 || high <= low) return "${tier.modelCount} 个模型"
    return "${tier.modelCount} 个 · 价差 ${formatOneDecimal(high / low)}×"
}

private fun formatBlendedPrice(entry: EntryDto): String {
    val price = entry.blendedPriceCny() ?: return "-"
    return formatBlendedPriceText(price)
}

private fun formatBlendedPriceText(price: Double): String = when {
    price <= 0.0 -> "免费"
    price < 1.0 -> "¥%.2f/M".format(Locale.US, price)
    price < 100.0 -> "¥%.1f/M".format(Locale.US, price)
    else -> "¥%.0f/M".format(Locale.US, price)
}

private fun formatOneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

@Composable
private fun EmptyText(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
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
