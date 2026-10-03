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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import com.ai.rankboard.data.EntryDto
import com.ai.rankboard.ui.common.ScoreBar
import com.ai.rankboard.ui.common.VendorIcon
import com.ai.rankboard.ui.common.scoreColor
import java.util.Locale

private val INPUT_VOLUME_OPTIONS = listOf(0.1, 0.5, 1.0, 5.0, 10.0, 50.0)
private val OUTPUT_VOLUME_OPTIONS = listOf(0.1, 0.5, 1.0, 3.0, 10.0, 30.0)
private val SCORE_OPTIONS = listOf(60.0, 70.0, 80.0, 90.0)

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
                        "正在读取模型成本数据",
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
                    item {
                        InputCard(
                            state = state,
                            onInput = vm::setCostInput,
                            onBoard = vm::setBoard,
                        )
                    }
                    item {
                        RecommendedCard(
                            state = state,
                            onOpenModel = onOpenModel,
                        )
                    }
                    item {
                        SectionTitle("费用排行")
                    }
                    val visibleModels = state.estimates
                        .filter { it.score >= state.costInput.minScore }
                        .filter { estimate ->
                            when (state.costInput.openSource) {
                                1 -> estimate.entry.isLikelyOpenSource()
                                2 -> !estimate.entry.isLikelyOpenSource()
                                else -> true
                            }
                        }
                        .sortedBy { it.monthlyCost }
                        .take(60)
                    if (visibleModels.isEmpty()) {
                        item {
                            EmptyText("没有满足能力门槛且有价格数据的模型")
                        }
                    } else {
                        items(
                            items = visibleModels,
                            key = { it.entry.slug },
                        ) { estimate ->
                            CostRow(
                                estimate = estimate,
                                onOpenModel = onOpenModel,
                            )
                        }
                    }

                    item {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 6.dp),
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
private fun InputCard(
    state: SelectUiState,
    onInput: (CostInput) -> Unit,
    onBoard: (String) -> Unit,
) {
    val input = state.costInput
    Surface(
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "成本预估",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    state.snapshotDate.ifBlank { "本地快照" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "能力榜单",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.abilityBoards, key = { it.slug }) { board ->
                    FilterChip(
                        selected = board.slug == state.selectedBoard,
                        onClick = { onBoard(board.slug) },
                        label = {
                            Text(
                                board.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            OptionRowDouble(
                label = "月输入 (M tokens)",
                selected = input.monthlyInputMillions,
                options = INPUT_VOLUME_OPTIONS,
                format = ::formatMillions,
                onSelect = { onInput(input.copy(monthlyInputMillions = it)) },
            )
            OptionRowDouble(
                label = "月输出 (M tokens)",
                selected = input.monthlyOutputMillions,
                options = OUTPUT_VOLUME_OPTIONS,
                format = ::formatMillions,
                onSelect = { onInput(input.copy(monthlyOutputMillions = it)) },
            )

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "缓存命中",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(input.cacheHitRate * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value = input.cacheHitRate,
                    onValueChange = { onInput(input.copy(cacheHitRate = it)) },
                    valueRange = 0f..0.90f,
                    steps = 5,
                )
            }

            OptionRowDouble(
                label = "能力门槛",
                selected = input.minScore,
                options = SCORE_OPTIONS,
                format = { "≥${it.toInt()}" },
                onSelect = { onInput(input.copy(minScore = it)) },
            )
            OptionRowInt(
                label = "类型",
                selected = input.openSource,
                options = listOf(0, 1, 2),
                format = {
                    when (it) {
                        1 -> "开源"
                        2 -> "闭源"
                        else -> "全部"
                    }
                },
                onSelect = { onInput(input.copy(openSource = it)) },
            )
        }
    }
}

@Composable
private fun RecommendedCard(
    state: SelectUiState,
    onOpenModel: (String) -> Unit,
) {
    val best = state.recommended
    Surface(
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = best != null) {
                    best?.let { onOpenModel(it.entry.slug) }
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.Savings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "最省钱可用方案",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            if (best == null) {
                Text(
                    "没有符合门槛且有价格的模型",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VendorIcon(vendor = best.entry.vendor, size = 28.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            best.entry.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                best.entry.vendor,
                                best.entry.releaseDate?.take(10),
                                "能力 ${String.format(Locale.US, "%.1f", best.score)}",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "约${formatCost(best.monthlyCost)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "每月",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ScoreBar(
                    score = best.score,
                    minScore = 0.0,
                    maxScore = 100.0,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CostPill("输入", best.inputCost, Modifier.weight(1f))
                    CostPill("输出", best.outputCost, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CostPill(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatCost(value),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CostRow(
    estimate: CostEstimate,
    onOpenModel: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenModel(estimate.entry.slug) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VendorIcon(vendor = estimate.entry.vendor, size = 22.dp)
            Spacer(Modifier.width(7.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    estimate.entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        estimate.entry.vendor,
                        estimate.entry.releaseDate?.take(10),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ScoreBar(
                    score = estimate.score,
                    minScore = 0.0,
                    maxScore = 100.0,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatCost(estimate.monthlyCost),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${String.format(Locale.US, "%.0f", estimate.score)} 分",
                    style = MaterialTheme.typography.labelSmall,
                    color = scoreColor(estimate.score, 0.0, 100.0),
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
                color = scoreColor(entry.score),
            )
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Int,
    options: List<Int>,
    format: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    OptionRowInt(
        label = label,
        selected = selected,
        options = options,
        format = format,
        onSelect = onSelect,
    )
}

@Composable
private fun OptionRowInt(
    label: String,
    selected: Int,
    options: List<Int>,
    format: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(format(option)) },
                )
            }
        }
    }
}

@Composable
private fun OptionRowDouble(
    label: String,
    selected: Double,
    options: List<Double>,
    format: (Double) -> String,
    onSelect: (Double) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(format(option)) },
                )
            }
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

private fun formatCost(value: Double): String = when {
    value >= 10_000.0 -> "¥${String.format(Locale.US, "%.2f万", value / 10_000.0)}"
    value >= 1.0 -> "¥${String.format(Locale.US, "%.0f", value)}"
    else -> "¥${String.format(Locale.US, "%.2f", value)}"
}

private fun formatMillions(value: Double): String =
    if (value == value.toLong().toDouble()) "${value.toLong()}M" else "${value}M"

private fun EntryDto.isLikelyOpenSource(): Boolean {
    val licenseText = license?.lowercase(Locale.ROOT) ?: return false
    return listOf("open", "apache", "mit", "bsd", "gpl", "lgpl", "agpl", "mpl", "cc-by", "community", "research")
        .any { licenseText.contains(it) }
}
