package com.ai.rankboard.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ai.rankboard.data.EntryDto
import com.ai.rankboard.data.Periods
import com.ai.rankboard.ui.common.AppCard
import com.ai.rankboard.ui.common.MetricTile
import com.ai.rankboard.ui.common.RankBadge
import com.ai.rankboard.ui.common.ScoreBar
import com.ai.rankboard.ui.common.SearchField
import com.ai.rankboard.ui.common.SectionHeader
import com.ai.rankboard.ui.common.SourceBadge
import com.ai.rankboard.ui.common.VendorIcon
import com.ai.rankboard.ui.common.scoreColor
import java.util.Locale

private val SORT_OPTIONS = listOf(
    "rank" to "按排名",
    "score" to "按得分",
    "updated" to "按更新时间",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenModel: (String) -> Unit,
    defaultTab: String = "overall",
    compactList: Boolean = false,
    showOverview: Boolean = true,
    vm: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            LocalContext.current.applicationContext as RankboardApp,
            defaultTab,
        ),
    ),
) {
    val state by vm.state.collectAsState()
    val favoriteSlugs by vm.favoriteSlugs.collectAsState(initial = emptyList())
    var filterSheetOpen by remember { mutableStateOf(false) }
    val selectedBoard = state.boardsForTab.firstOrNull { it.slug == state.selectedBoard }
        ?: state.allBoardsForTab.firstOrNull { it.slug == state.selectedBoard }
    val selectedPeriod = state.periodOptions.firstOrNull { it.id == state.period }
    val summaryBoardName = when {
        state.tab in Periods.supported -> selectedPeriod?.label ?: "时间榜"
        else -> selectedBoard?.name ?: "选择榜单"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 6.dp, bottom = 10.dp),
            ) {
                SearchField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    placeholder = "搜索模型或厂商",
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    items(HOME_TABS, key = { it.dimension }) { tab ->
                        val selected = state.tab == tab.dimension
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.48f),
                            modifier = Modifier.clickable { vm.selectTab(tab.dimension) },
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                FilterSummaryBar(
                    boardName = summaryBoardName,
                    activeCount = state.activeFilterCount(),
                    snapshotDate = state.snapshotInfo?.generatedAt?.take(10).orEmpty(),
                    onClick = { filterSheetOpen = true },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.refreshFromUser() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading && state.entries.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !state.loading && state.entries.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.offline) "无网络且无本地缓存，请检查后端连接" else "暂无数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.offline) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                                ) {
                                    Text(
                                        "离线数据 · 下拉可刷新",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                        if (showOverview) {
                            item {
                                LeaderboardOverview(
                                    state = state,
                                    onOpenModel = onOpenModel,
                                    onSelectSource = vm::setSourceFilter,
                                    scoreMin = state.scoreMin,
                                    scoreMax = state.scoreMax,
                                )
                            }
                        }
                        items(state.entries, key = { it.slug }) { entry ->
                            ModelRow(
                                entry = entry,
                                isFavorite = entry.slug in favoriteSlugs.toSet(),
                                scoreMin = state.scoreMin,
                                scoreMax = state.scoreMax,
                                compact = compactList,
                                onClick = { onOpenModel(entry.slug) },
                                onToggleFavorite = {
                                    vm.toggleFavorite(entry.slug, entry.displayName)
                                },
                            )
                        }
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    state.loadingMore -> CircularProgressIndicator(Modifier.width(28.dp))
                                    state.entries.size < state.total -> {
                                        LaunchedEffect(
                                            state.entries.size,
                                            state.selectedBoard,
                                            state.query,
                                            state.sort,
                                        ) {
                                            vm.loadMore()
                                        }
                                    }
                                    else -> Text(
                                        "已加载全部 ${state.total} 个模型",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (filterSheetOpen) {
        ModalBottomSheet(onDismissRequest = { filterSheetOpen = false }) {
            FilterSheet(
                state = state,
                sort = state.sort,
                onSelectSort = vm::setSort,
                onSelectBoard = vm::selectBoard,
                onSelectPeriod = vm::setPeriod,
                onSelectSource = vm::setSourceFilter,
                onSelectVendor = vm::setVendorFilter,
                onSelectLicense = vm::setLicenseFilter,
                onSelectParams = vm::setParamsFilter,
                onClear = vm::clearValueFilters,
            )
        }
    }
}

@Composable
private fun LeaderboardOverview(
    state: HomeUiState,
    onOpenModel: (String) -> Unit,
    onSelectSource: (String?) -> Unit,
    scoreMin: Double?,
    scoreMax: Double?,
) {
    val topModels = state.entries.take(3)
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                title = "榜单速览",
                trailing = {
                    if (state.offline) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                "离线",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                },
            )
            if (topModels.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    topModels.forEachIndexed { index, entry ->
                        TopModelCard(
                            entry = entry,
                            accent = when (index) {
                                0 -> Color(0xFFD97706)
                                1 -> Color(0xFF64748B)
                                else -> Color(0xFFB45309)
                            },
                            onClick = { onOpenModel(entry.slug) },
                            modifier = Modifier.weight(1f),
                            scoreMin = scoreMin,
                            scoreMax = scoreMax,
                        )
                    }
                }
            }
            if (state.tab !in Periods.supported) {
                DataSourceStrip(
                    sources = state.allBoardsForTab
                        .groupingBy { it.sourceId }
                        .eachCount()
                        .toList(),
                    selectedSource = state.sourceFilter,
                    onSelectSource = onSelectSource,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricTile(
                    label = "模型",
                    value = state.total.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "榜单",
                    value = state.allBoardsForTab.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "更新",
                    value = state.snapshotInfo?.generatedAt?.take(10).orEmpty().ifBlank { "-" },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TopModelCard(
    entry: EntryDto,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scoreMin: Double?,
    scoreMax: Double?,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.16f)),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "#${entry.rank}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            VendorIcon(vendor = entry.vendor, size = 26.dp)
            Text(
                text = parseModelStrength(entry.displayName).first,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = entry.score?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = scoreColor(entry.score, scoreMin, scoreMax),
            )
            ScoreBar(
                score = entry.score,
                minScore = scoreMin,
                maxScore = scoreMax,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DataSourceStrip(
    sources: List<Pair<String, Int>>,
    selectedSource: String?,
    onSelectSource: (String?) -> Unit,
) {
    if (sources.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = "数据来源")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(
                sources.sortedWith(
                    compareByDescending<Pair<String, Int>> { it.first == "modelsage" }.thenBy { it.first },
                ),
                key = { it.first },
            ) { (sourceId, count) ->
                SourceBadge(
                    sourceId = sourceId,
                    count = count,
                    selected = selectedSource == sourceId || sources.size == 1,
                    onClick = if (sources.size == 1) null else {
                        { onSelectSource(if (selectedSource == sourceId) null else sourceId) }
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterSummaryBar(
    boardName: String,
    activeCount: Int,
    snapshotDate: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = if (activeCount == 0) boardName else "$boardName · $activeCount 项筛选",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (snapshotDate.isNotBlank()) {
                Text(
                    text = "更新 $snapshotDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FilterSheet(
    state: HomeUiState,
    sort: String,
    onSelectSort: (String) -> Unit,
    onSelectBoard: (String) -> Unit,
    onSelectPeriod: (String) -> Unit,
    onSelectSource: (String?) -> Unit,
    onSelectVendor: (String) -> Unit,
    onSelectLicense: (String) -> Unit,
    onSelectParams: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "筛选",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) {
                Text("清除筛选")
            }
        }
        FilterSection("排序") {
            items(SORT_OPTIONS, key = { it.first }) { (key, label) ->
                FilterChip(
                    selected = sort == key,
                    onClick = { onSelectSort(key) },
                    label = { Text(label, maxLines = 1) },
                )
            }
        }
        if (state.tab in Periods.supported) {
            FilterSection("时间区间") {
                items(state.periodOptions, key = { it.id }) { option ->
                    FilterChip(
                        selected = state.period == option.id,
                        onClick = { onSelectPeriod(option.id) },
                        label = { Text("${option.label} · ${option.count}", maxLines = 1) },
                    )
                }
            }
        }
        FilterSection("榜单") {
            if (state.tab !in Periods.supported) {
                items(state.boardsForTab, key = { it.slug }) { board ->
                    FilterChip(
                        selected = state.selectedBoard == board.slug,
                        onClick = { onSelectBoard(board.slug) },
                        label = { Text(board.name, maxLines = 1) },
                    )
                }
            }
        }
        if (state.tab !in Periods.supported &&
            state.allBoardsForTab.map { it.sourceId }.distinct().size > 1
        ) {
            FilterSection("来源") {
                item {
                    FilterChip(
                        selected = state.sourceFilter == null,
                        onClick = { onSelectSource(null) },
                        label = { Text("全部") },
                    )
                }
                items(
                    state.allBoardsForTab.map { it.sourceId }.distinct(),
                    key = { it },
                ) { sourceId ->
                    FilterChip(
                        selected = state.sourceFilter == sourceId,
                        onClick = {
                            onSelectSource(if (state.sourceFilter == sourceId) null else sourceId)
                        },
                        label = { Text(sourceLabel(sourceId), maxLines = 1) },
                    )
                }
            }
        }
        if (state.vendorOptions.isNotEmpty()) {
            FilterSection("厂商") {
                item {
                    FilterChip(
                        selected = state.vendorFilter.isEmpty(),
                        onClick = { state.vendorFilter.forEach(onSelectVendor) },
                        label = { Text("全部") },
                    )
                }
                items(state.vendorOptions, key = { it }) { vendor ->
                    FilterChip(
                        selected = vendor in state.vendorFilter,
                        onClick = { onSelectVendor(vendor) },
                        label = { Text(vendor, maxLines = 1) },
                    )
                }
            }
        }
        FilterSection("许可") {
            item {
                FilterChip(
                    selected = state.licenseFilter.isEmpty(),
                    onClick = { state.licenseFilter.forEach(onSelectLicense) },
                    label = { Text("全部") },
                )
            }
            item {
                FilterChip(
                    selected = "open" in state.licenseFilter,
                    onClick = { onSelectLicense("open") },
                    label = { Text("开源") },
                )
            }
            item {
                FilterChip(
                    selected = "proprietary" in state.licenseFilter,
                    onClick = { onSelectLicense("proprietary") },
                    label = { Text("商业") },
                )
            }
        }
        FilterSection("参数") {
            item {
                FilterChip(
                    selected = state.paramsFilter.isEmpty(),
                    onClick = { state.paramsFilter.forEach(onSelectParams) },
                    label = { Text("全部") },
                )
            }
            item {
                FilterChip(
                    selected = "small" in state.paramsFilter,
                    onClick = { onSelectParams("small") },
                    label = { Text("≤10B") },
                )
            }
            item {
                FilterChip(
                    selected = "medium" in state.paramsFilter,
                    onClick = { onSelectParams("medium") },
                    label = { Text("10-100B") },
                )
            }
            item {
                FilterChip(
                    selected = "large" in state.paramsFilter,
                    onClick = { onSelectParams("large") },
                    label = { Text(">100B") },
                )
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

private fun sourceLabel(sourceId: String): String = when (sourceId) {
    "livebench" -> "LiveBench"
    "swebench" -> "SWE-bench"
    else -> "ModelSage"
}

private fun HomeUiState.activeFilterCount(): Int =
    listOfNotNull(sourceFilter).size + vendorFilter.size + licenseFilter.size + paramsFilter.size

@Composable
private fun ModelRow(
    entry: EntryDto,
    isFavorite: Boolean,
    scoreMin: Double?,
    scoreMax: Double?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    compact: Boolean = false,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = if (compact) 10.dp else 12.dp,
                    vertical = if (compact) 8.dp else 11.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            RankBadge(
                rank = entry.rank,
                width = if (compact) 30.dp else 34.dp,
            )
            VendorIcon(vendor = entry.vendor, size = if (compact) 23.dp else 26.dp)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = parseModelStrength(entry.displayName).first,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    parseModelStrength(entry.displayName).second?.let { strength ->
                        Spacer(Modifier.width(5.dp))
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = strength,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Text(
                    text = listOfNotNull(
                        entry.vendor,
                        entry.paramsB?.let { "${formatParams(it)}B" },
                        entry.releaseDate?.take(10),
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.score?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                    style = if (compact) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontFamily = FontFamily.Monospace,
                    color = scoreColor(entry.score, scoreMin, scoreMax),
                )
                ScoreBar(
                    score = entry.score,
                    minScore = scoreMin,
                    maxScore = scoreMax,
                    modifier = Modifier
                        .width(if (compact) 44.dp else 54.dp)
                        .padding(top = 3.dp),
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun formatParams(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun parseModelStrength(displayName: String): Pair<String, String?> {
    val match = Regex("\\s*\\((xhigh|high|medium|low|max|non-reasoning|reasoning)( with fallback)?\\)$")
        .find(displayName) ?: return displayName to null
    val strength = match.groupValues[1].replaceFirstChar { it.uppercase() } +
        if (match.groupValues[2].isNotBlank()) " FB" else ""
    return displayName.removeRange(match.range) to strength
}
