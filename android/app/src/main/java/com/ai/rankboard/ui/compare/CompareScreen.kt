package com.ai.rankboard.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.ModelDetailResponse
import com.ai.rankboard.ui.common.VendorIcon
import java.util.Locale

private val LABEL_WIDTH = 62.dp
private val COLUMN_WIDTH = 104.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    onBack: () -> Unit,
    vm: CompareViewModel = viewModel(
        factory = CompareViewModel.factory(LocalContext.current.applicationContext as RankboardApp),
    ),
) {
    val state by vm.state.collectAsState()
    val tableScroll = rememberScrollState()
    val board = state.boards.firstOrNull { it.slug == state.selectedBoardSlug }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型对比") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            BenchmarkBar(
                boardName = board?.name ?: "选择榜单",
                sourceName = board?.sourceId.orEmpty(),
                selectedCount = state.selectedSlugs.size,
                onClick = { vm.setBoardPickerOpen(true) },
            )
            SelectionRow(
                models = state.models,
                canAdd = state.selectedSlugs.size < CompareViewModel.MAX_MODELS,
                onRemove = vm::toggleSelection,
                onAdd = { vm.setPickerOpen(true) },
            )
            when {
                state.loading -> {
                    Box(
                        Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.models.isEmpty() -> {
                    Box(
                        Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "选择 2-4 个模型",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "默认使用收藏的模型，也可以搜索添加",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { vm.setPickerOpen(true) }) {
                                Text("选择模型")
                            }
                        }
                    }
                }
                state.models.size < 2 -> {
                    Box(
                        Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "再添加一个模型即可开始对比",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        item {
                            SummaryCard(
                                models = state.models,
                                boardSlug = state.selectedBoardSlug,
                            )
                        }
                        item {
                            CompareHeader(
                                models = state.models,
                                scrollModifier = Modifier.horizontalScroll(tableScroll),
                            )
                        }
                        item {
                            MetricRow(
                                label = "得分",
                                scrollModifier = Modifier.horizontalScroll(tableScroll),
                                values = state.models.map { response ->
                                    val score = response.scores.firstOrNull { it.boardSlug == state.selectedBoardSlug }
                                    CompareMetric(
                                        text = score?.score?.let { String.format(Locale.US, "%.1f", it) } ?: "未上榜",
                                        best = score?.score != null &&
                                            score.score == state.models.mapNotNull { model ->
                                                model.scores.firstOrNull { entry -> entry.boardSlug == state.selectedBoardSlug }?.score
                                            }.maxOrNull(),
                                    )
                                },
                            )
                        }
                        item {
                            MetricRow(
                                label = "排名",
                                scrollModifier = Modifier.horizontalScroll(tableScroll),
                                values = state.models.map { response ->
                                    val score = response.scores.firstOrNull { it.boardSlug == state.selectedBoardSlug }
                                    CompareMetric(
                                        text = score?.rank?.takeIf { it > 0 }?.let { "#$it" } ?: "未上榜",
                                        best = score?.rank != null && score.rank > 0 &&
                                            score.rank == state.models.mapNotNull { model ->
                                                model.scores.firstOrNull { entry -> entry.boardSlug == state.selectedBoardSlug }?.rank
                                            }.filter { it > 0 }.minOrNull(),
                                    )
                                },
                            )
                        }
                        item {
                            PriceRow(
                                models = state.models,
                                boardSlug = state.selectedBoardSlug,
                                scrollModifier = Modifier.horizontalScroll(tableScroll),
                            )
                        }
                        item {
                            MetricRow(
                                label = "参数",
                                scrollModifier = Modifier.horizontalScroll(tableScroll),
                                values = state.models.map { response ->
                                    CompareMetric(
                                        response.model.paramsB?.let { "${formatNum(it)}B" } ?: "-",
                                    )
                                },
                            )
                        }
                        item {
                            MetricRow(
                                label = "上下文",
                                scrollModifier = Modifier.horizontalScroll(tableScroll),
                                values = state.models.map { response ->
                                    CompareMetric(response.model.contextWindow ?: "-")
                                },
                            )
                        }
                        item {
                            MetricRow(
                                label = "许可",
                                scrollModifier = Modifier.horizontalScroll(tableScroll),
                                values = state.models.map { response ->
                                    CompareMetric(response.model.license ?: "-")
                                },
                            )
                        }
                        item {
                            MetricRow(
                                label = "更新",
                                scrollModifier = Modifier.horizontalScroll(tableScroll),
                                values = state.models.map { response ->
                                    val fetchedAt = response.scores.firstOrNull { it.boardSlug == state.selectedBoardSlug }?.fetchedAt
                                    CompareMetric(fetchedAt?.take(10) ?: "-")
                                },
                            )
                        }
                        item {
                            Text(
                                "得分与排名取当前所选榜单；价格按每百万 token 显示。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.pickerOpen) {
        ModelPickerSheet(
            query = state.query,
            tab = state.pickerTab,
            candidates = state.candidates,
            favoriteModels = state.favoriteModels,
            selectedSlugs = state.selectedSlugs,
            maxCount = CompareViewModel.MAX_MODELS,
            onQueryChange = vm::setQuery,
            onTabChange = vm::setPickerTab,
            onToggle = vm::toggleSelection,
            onDismiss = { vm.setPickerOpen(false) },
        )
    }
    if (state.boardPickerOpen) {
        BoardPickerSheet(
            boards = state.boards,
            selectedSlug = state.selectedBoardSlug,
            onSelect = vm::selectBoard,
            onDismiss = { vm.setBoardPickerOpen(false) },
        )
    }
}

@Composable
private fun BenchmarkBar(
    boardName: String,
    sourceName: String,
    selectedCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Assessment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
            Text(
                boardName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                sourceLabel(sourceName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                "$selectedCount/${CompareViewModel.MAX_MODELS}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SelectionRow(
    models: List<ModelDetailResponse>,
    canAdd: Boolean,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        models.forEach { response ->
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(start = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        response.model.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 118.dp),
                    )
                    IconButton(
                        onClick = { onRemove(response.model.slug) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "移除 ${response.model.displayName}",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        if (canAdd) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable(onClick = onAdd),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        "添加",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    models: List<ModelDetailResponse>,
    boardSlug: String,
) {
    val scores = models.mapNotNull { response ->
        response.scores.firstOrNull { it.boardSlug == boardSlug }?.let { score -> response to score }
    }
    val winner = scores.maxByOrNull { it.second.score ?: Double.NEGATIVE_INFINITY }
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (winner == null) {
                Text(
                    "当前榜单缺少所选模型的数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                VendorIcon(vendor = winner.first.model.vendor, size = 20.dp)
                Column {
                    Text(
                        "当前领先",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        winner.first.model.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    winner.second.score?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CompareHeader(
    models: List<ModelDetailResponse>,
    scrollModifier: Modifier,
) {
    Row(
        modifier = scrollModifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.width(LABEL_WIDTH))
        models.forEach { response ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.width(COLUMN_WIDTH),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VendorIcon(vendor = response.model.vendor, size = 20.dp)
                    Text(
                        response.model.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    values: List<CompareMetric>,
    scrollModifier: Modifier,
) {
    Row(
        modifier = scrollModifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.width(LABEL_WIDTH),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        values.forEach { value ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (value.best) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
                },
                modifier = Modifier.width(COLUMN_WIDTH),
            ) {
                Text(
                    value.text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (value.best) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (value.best) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 5.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PriceRow(
    models: List<ModelDetailResponse>,
    boardSlug: String,
    scrollModifier: Modifier,
) {
    val inputPrices = models.mapNotNull { response ->
        response.scores.firstOrNull { it.boardSlug == boardSlug }?.priceIn
    }
    val outputPrices = models.mapNotNull { response ->
        response.scores.firstOrNull { it.boardSlug == boardSlug }?.priceOut
    }
    val minInput = inputPrices.minOrNull()
    val minOutput = outputPrices.minOrNull()
    MetricRow(
        label = "输入价",
        scrollModifier = scrollModifier,
        values = models.map { response ->
            val price = response.scores.firstOrNull { it.boardSlug == boardSlug }?.priceIn
            CompareMetric(
                text = price?.let { value ->
                    currencySymbol(response.scores.first { it.boardSlug == boardSlug }.currency) +
                        String.format(Locale.US, "%.2f", value)
                } ?: "-",
                best = price != null && minInput != null && price == minInput,
            )
        },
    )
    MetricRow(
        label = "输出价",
        scrollModifier = scrollModifier,
        values = models.map { response ->
            val price = response.scores.firstOrNull { it.boardSlug == boardSlug }?.priceOut
            CompareMetric(
                text = price?.let { value ->
                    currencySymbol(response.scores.first { it.boardSlug == boardSlug }.currency) +
                        String.format(Locale.US, "%.2f", value)
                } ?: "-",
                best = price != null && minOutput != null && price == minOutput,
            )
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ModelPickerSheet(
    query: String,
    tab: PickerTab,
    candidates: List<com.ai.rankboard.data.ModelDetailDto>,
    favoriteModels: List<com.ai.rankboard.data.ModelDetailDto>,
    selectedSlugs: List<String>,
    maxCount: Int,
    onQueryChange: (String) -> Unit,
    onTabChange: (PickerTab) -> Unit,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val favoriteSlugs = favoriteModels.map { it.slug }.toSet()
    val visibleModels = when (tab) {
        PickerTab.Favorites -> candidates.filter { it.slug in favoriteSlugs }
        PickerTab.Vendors -> candidates
    }
    val groups = remember(visibleModels) { ModelPickerGroups.group(visibleModels) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "选择模型",
                    style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${selectedSlugs.size}/$maxCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
                TextButton(onClick = onDismiss) {
                    Text("完成")
                }
            }
            TabRow(selectedTabIndex = tab.ordinal) {
                PickerTab.entries.forEach { item ->
                    Tab(
                        selected = tab == item,
                        onClick = { onTabChange(item) },
                        text = { Text(item.label) },
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索模型或厂商") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清空搜索",
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (tab == PickerTab.Favorites && favoriteModels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "还没有收藏模型，去榜单页点亮星标吧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "没有匹配的模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    count = groups.size,
                    key = { index -> "vendor:${groups[index].vendor}" },
                ) { index ->
                    val group = groups[index]
                    ModelGroupSection(
                        group = group,
                        selectedSlugs = selectedSlugs,
                        favoriteSlugs = favoriteSlugs,
                        maxCount = maxCount,
                        onToggle = onToggle,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun ModelGroupSection(
    group: VendorGroup,
    selectedSlugs: List<String>,
    favoriteSlugs: Set<String>,
    maxCount: Int,
    onToggle: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VendorIcon(vendor = group.vendor, size = 18.dp)
            Text(
                group.vendor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                "${group.models.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        group.models.forEach { model ->
            ModelPickerRow(
                model = model,
                selected = model.slug in selectedSlugs,
                favorite = model.slug in favoriteSlugs,
                disabled = model.slug !in selectedSlugs && selectedSlugs.size >= maxCount,
                onToggle = { onToggle(model.slug) },
            )
        }
    }
}

@Composable
private fun ModelPickerRow(
    model: com.ai.rankboard.data.ModelDetailDto,
    selected: Boolean,
    favorite: Boolean,
    disabled: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            disabled -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !disabled) { onToggle() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            VendorIcon(vendor = model.vendor, size = 20.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    model.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        model.vendor,
                        model.paramsB?.let { "${formatNum(it)}B" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (favorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "已收藏",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已选",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BoardPickerSheet(
    boards: List<com.ai.rankboard.data.BoardDto>,
    selectedSlug: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "选择榜单",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text("完成")
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(boards, key = { it.slug }) { board ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (board.slug == selectedSlug) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(board.slug) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    board.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    sourceLabel(board.sourceId),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (board.slug == selectedSlug) {
                                Text(
                                    "当前",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CompareMetric(
    val text: String,
    val best: Boolean = false,
)

private fun sourceLabel(sourceId: String): String = when (sourceId) {
    "livebench" -> "LiveBench"
    "swebench" -> "SWE-bench"
    else -> "ModelSage"
}

private fun formatNum(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun currencySymbol(currency: String): String =
    if (currency.equals("USD", ignoreCase = true)) "$" else "¥"
