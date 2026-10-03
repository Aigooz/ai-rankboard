package com.ai.rankboard.ui.home

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.EntryDto
import com.ai.rankboard.ui.common.VendorIcon
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
    vm: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(LocalContext.current.applicationContext as RankboardApp),
    ),
) {
    val state by vm.state.collectAsState()
    val favoriteSlugs by vm.favoriteSlugs.collectAsState(initial = emptyList())
    var filterSheetOpen by remember { mutableStateOf(false) }
    val selectedBoard = state.boardsForTab.firstOrNull { it.slug == state.selectedBoard }
        ?: state.allBoardsForTab.firstOrNull { it.slug == state.selectedBoard }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("AI 排行榜") },
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("模糊搜索模型名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "清空")
                            }
                        }
                    },
                )
                ScrollableTabRow(selectedTabIndex = HOME_TABS.indexOfFirst { it.dimension == state.tab }) {
                    HOME_TABS.forEach { tab ->
                        Tab(
                            selected = state.tab == tab.dimension,
                            onClick = { vm.selectTab(tab.dimension) },
                            text = {
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
                FilterSummaryBar(
                    boardName = selectedBoard?.name ?: "选择榜单",
                    activeCount = state.activeFilterCount(),
                    snapshotDate = state.snapshotInfo?.generatedAt?.take(10).orEmpty(),
                    onClick = { filterSheetOpen = true },
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    Column {
                        if (state.offline) {
                            Text(
                                "离线数据，下拉可刷新",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            item {
                                LeaderboardOverview(
                                    state = state,
                                    onOpenModel = onOpenModel,
                                )
                            }
                            items(state.entries, key = { it.slug }) { entry ->
                                ModelRow(
                                    entry = entry,
                                    isFavorite = entry.slug in favoriteSlugs.toSet(),
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
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when {
                                        state.loadingMore -> CircularProgressIndicator(Modifier.width(28.dp))
                                        state.entries.size < state.total -> {
                                            LaunchedEffect(state.entries.size, state.selectedBoard, state.query, state.sort) {
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
    }

    if (filterSheetOpen) {
        ModalBottomSheet(onDismissRequest = { filterSheetOpen = false }) {
            FilterSheet(
                state = state,
                sort = state.sort,
                onSelectSort = vm::setSort,
                onSelectBoard = {
                    vm.selectBoard(it)
                },
                onSelectSource = { sourceId ->
                    vm.setSourceFilter(sourceId)
                },
                onSelectVendor = { vendor ->
                    vm.setVendorFilter(vendor)
                },
                onSelectLicense = { license ->
                    vm.setLicenseFilter(license)
                },
                onSelectParams = { params ->
                    vm.setParamsFilter(params)
                },
                onClear = {
                    vm.setVendorFilter(null)
                    vm.setLicenseFilter(null)
                    vm.setParamsFilter(null)
                },
            )
        }
    }
}

@Composable
private fun LeaderboardOverview(
    state: HomeUiState,
    onOpenModel: (String) -> Unit,
) {
    val topModels = state.entries.take(3)
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "榜单速览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (state.offline) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            "离线",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (topModels.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    topModels.forEachIndexed { index, entry ->
                        TopModelCard(
                            entry = entry,
                            rankColor = when (entry.rank) {
                                1 -> MaterialTheme.colorScheme.tertiary
                                2 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            onClick = { onOpenModel(entry.slug) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverviewMetric(
                    label = "模型",
                    value = state.total.toString(),
                    modifier = Modifier.weight(1f),
                )
                OverviewMetric(
                    label = "榜单",
                    value = state.allBoardsForTab.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                OverviewMetric(
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
    rankColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "#${entry.rank}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = rankColor,
            )
            VendorIcon(vendor = entry.vendor, size = 21.dp)
            Text(
                parseModelStrength(entry.displayName).first,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                entry.score?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterSummaryBar(
    boardName: String,
    activeCount: Int,
    snapshotDate: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Tune,
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
                    snapshotDate,
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
    onSelectSource: (String?) -> Unit,
    onSelectVendor: (String?) -> Unit,
    onSelectLicense: (String?) -> Unit,
    onSelectParams: (String?) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "筛选",
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
        FilterSection("榜单") {
            items(state.boardsForTab, key = { it.slug }) { board ->
                FilterChip(
                    selected = state.selectedBoard == board.slug,
                    onClick = { onSelectBoard(board.slug) },
                    label = { Text(board.name, maxLines = 1) },
                )
            }
        }
        if (state.allBoardsForTab.map { it.sourceId }.distinct().size > 1) {
            FilterSection("来源") {
                item {
                    FilterChip(
                        selected = state.sourceFilter == null,
                        onClick = { onSelectSource(null) },
                        label = { Text("全部") },
                    )
                }
                items(state.allBoardsForTab.map { it.sourceId }.distinct(), key = { it }) { sourceId ->
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
                        selected = state.vendorFilter == null,
                        onClick = { onSelectVendor(null) },
                        label = { Text("全部") },
                    )
                }
                items(state.vendorOptions, key = { it }) { vendor ->
                    FilterChip(
                        selected = state.vendorFilter == vendor,
                        onClick = {
                            onSelectVendor(if (state.vendorFilter == vendor) null else vendor)
                        },
                        label = { Text(vendor, maxLines = 1) },
                    )
                }
            }
        }
        FilterSection("许可") {
            item {
                FilterChip(
                    selected = state.licenseFilter == null,
                    onClick = { onSelectLicense(null) },
                    label = { Text("全部") },
                )
            }
            item {
                FilterChip(
                    selected = state.licenseFilter == "open",
                    onClick = { onSelectLicense(if (state.licenseFilter == "open") null else "open") },
                    label = { Text("开源") },
                )
            }
            item {
                FilterChip(
                    selected = state.licenseFilter == "proprietary",
                    onClick = { onSelectLicense(if (state.licenseFilter == "proprietary") null else "proprietary") },
                    label = { Text("商业") },
                )
            }
        }
        FilterSection("参数") {
            item {
                FilterChip(
                    selected = state.paramsFilter == null,
                    onClick = { onSelectParams(null) },
                    label = { Text("全部") },
                )
            }
            item {
                FilterChip(
                    selected = state.paramsFilter == "small",
                    onClick = { onSelectParams(if (state.paramsFilter == "small") null else "small") },
                    label = { Text("≤10B") },
                )
            }
            item {
                FilterChip(
                    selected = state.paramsFilter == "medium",
                    onClick = { onSelectParams(if (state.paramsFilter == "medium") null else "medium") },
                    label = { Text("10-100B") },
                )
            }
            item {
                FilterChip(
                    selected = state.paramsFilter == "large",
                    onClick = { onSelectParams(if (state.paramsFilter == "large") null else "large") },
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
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 3.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}

private fun sourceLabel(sourceId: String): String = when (sourceId) {
    "livebench" -> "LiveBench"
    "swebench" -> "SWE-bench"
    else -> "ModelSage"
}

private fun HomeUiState.activeFilterCount(): Int = listOf<String?>(
    sourceFilter,
    vendorFilter,
    licenseFilter,
    paramsFilter,
).count { it != null }

@Composable
private fun ModelRow(
    entry: EntryDto,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.rank.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(21.dp),
            )
            VendorIcon(vendor = entry.vendor, size = 20.dp)
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = parseModelStrength(entry.displayName).first,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    parseModelStrength(entry.displayName).second?.let { strength ->
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            Text(
                                text = strength,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Text(
                    text = listOfNotNull(
                        entry.vendor,
                        entry.paramsB?.let { "${formatParams(it)}B" },
                        entry.fetchedAt.take(10),
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.score?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    val strength = match.groupValues[1] + if (match.groupValues[2].isNotBlank()) " + fallback" else ""
    return displayName.removeRange(match.range) to strength
}
