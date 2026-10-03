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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
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
    onOpenFavorites: () -> Unit,
    onOpenCompare: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(LocalContext.current.applicationContext as RankboardApp),
    ),
) {
    val state by vm.state.collectAsState()
    val favoriteSlugs by vm.favoriteSlugs.collectAsState(initial = emptyList())
    var sortMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("AI 排行榜") },
                    actions = {
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                SORT_OPTIONS.forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label + if (state.sort == key) " ✓" else "") },
                                        onClick = { vm.setSort(key); sortMenuOpen = false },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onOpenCompare) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "模型对比")
                        }
                        IconButton(onClick = onOpenFavorites) {
                            Icon(Icons.Outlined.StarBorder, contentDescription = "收藏")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "设置")
                        }
                    },
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("模糊搜索模型名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
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
                            text = { Text(tab.label) },
                        )
                    }
                }
                state.snapshotInfo?.let { info ->
                    Text(
                        text = buildString {
                            append("数据来源 ")
                            append(info.sourceName.ifBlank { "ModelSage" })
                            append(" · 快照 ")
                            append(info.generatedAt.take(10).ifBlank { "未知" })
                            if (info.isDownloaded) append(" · 远端更新")
                            if (!info.verified) append(" · 校验未通过")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (state.updateMessage.isNotBlank()) {
                    Text(
                        text = state.updateMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                if (state.boardsForTab.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.boardsForTab, key = { it.slug }) { board ->
                            FilterChip(
                                selected = state.selectedBoard == board.slug,
                                onClick = { vm.selectBoard(board.slug) },
                                label = { Text(board.name) },
                            )
                        }
                    }
                }
                if (state.allBoardsForTab.map { it.sourceId }.distinct().size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = state.sourceFilter == null,
                                onClick = { vm.setSourceFilter(null) },
                                label = { Text("全部来源") },
                            )
                        }
                        items(
                            state.allBoardsForTab.map { it.sourceId }.distinct(),
                            key = { it },
                        ) { sourceId ->
                            FilterChip(
                                selected = state.sourceFilter == sourceId,
                                onClick = {
                                    vm.setSourceFilter(if (state.sourceFilter == sourceId) null else sourceId)
                                },
                                label = { Text(sourceLabel(sourceId)) },
                            )
                        }
                    }
                }
                if (state.vendorOptions.isNotEmpty() || state.licenseFilter != null || state.paramsFilter != null) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = state.vendorFilter == null,
                                onClick = { vm.setVendorFilter(null) },
                                label = { Text("厂商") },
                            )
                        }
                        items(state.vendorOptions, key = { it }) { vendor ->
                            FilterChip(
                                selected = state.vendorFilter == vendor,
                                onClick = {
                                    vm.setVendorFilter(if (state.vendorFilter == vendor) null else vendor)
                                },
                                label = { Text(vendor, maxLines = 1) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = state.licenseFilter == "open",
                                onClick = {
                                    vm.setLicenseFilter(if (state.licenseFilter == "open") null else "open")
                                },
                                label = { Text("开源") },
                            )
                        }
                        item {
                            FilterChip(
                                selected = state.licenseFilter == "proprietary",
                                onClick = {
                                    vm.setLicenseFilter(
                                        if (state.licenseFilter == "proprietary") null else "proprietary",
                                    )
                                },
                                label = { Text("商业") },
                            )
                        }
                        item {
                            FilterChip(
                                selected = state.paramsFilter == "small",
                                onClick = {
                                    vm.setParamsFilter(if (state.paramsFilter == "small") null else "small")
                                },
                                label = { Text("≤10B") },
                            )
                        }
                        item {
                            FilterChip(
                                selected = state.paramsFilter == "medium",
                                onClick = {
                                    vm.setParamsFilter(if (state.paramsFilter == "medium") null else "medium")
                                },
                                label = { Text("10-100B") },
                            )
                        }
                        item {
                            FilterChip(
                                selected = state.paramsFilter == "large",
                                onClick = {
                                    vm.setParamsFilter(if (state.paramsFilter == "large") null else "large")
                                },
                                label = { Text(">100B") },
                            )
                        }
                    }
                }
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
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
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
}

private fun sourceLabel(sourceId: String): String = when (sourceId) {
    "livebench" -> "LiveBench"
    "swebench" -> "SWE-bench"
    else -> "ModelSage"
}

@Composable
private fun ModelRow(
    entry: EntryDto,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.rank.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(28.dp),
            )
            VendorIcon(vendor = entry.vendor, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = parseModelStrength(entry.displayName).first,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = entry.vendor ?: "-",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    parseModelStrength(entry.displayName).second?.let { strength ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = strength,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.score?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = entry.fetchedAt.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun parseModelStrength(displayName: String): Pair<String, String?> {
    val match = Regex("\\s*\\((xhigh|high|medium|low|max|non-reasoning|reasoning)( with fallback)?\\)$")
        .find(displayName) ?: return displayName to null
    val strength = match.groupValues[1] + if (match.groupValues[2].isNotBlank()) " + fallback" else ""
    return displayName.removeRange(match.range) to strength
}
