package com.ai.rankboard.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.BoardDto
import com.ai.rankboard.data.ModelDetailDto
import com.ai.rankboard.data.ModelDetailResponse
import com.ai.rankboard.data.ScoreDto
import com.ai.rankboard.ui.common.AppCard
import com.ai.rankboard.ui.common.SearchField
import com.ai.rankboard.ui.common.SectionHeader
import com.ai.rankboard.ui.common.VendorIcon
import com.ai.rankboard.ui.common.scoreColor

@Composable
fun CompareScreen(
    onBack: () -> Unit,
    onOpenModel: (String) -> Unit,
    vm: CompareViewModel = viewModel(
        factory = CompareViewModel.factory(LocalContext.current.applicationContext as RankboardApp),
    ),
) {
    val state by vm.state.collectAsState()
    val visibleBoards = remember(state.boards, state.selectedDimension) {
        state.boards.filter { state.selectedDimension == "all" || it.dimension == state.selectedDimension }
    }
    val dimensions = remember(state.boards) {
        listOf("all") + state.boards.map { it.dimension }.distinct()
    }
    val selectedBoard = visibleBoards.firstOrNull { it.slug == state.selectedBoardSlug }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CompareTopBar(
                selectedCount = state.selectedSlugs.size,
                onBack = onBack,
                onOpenPicker = { vm.setPickerOpen(true) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SelectorStrip(
                title = "能力",
                options = dimensions,
                selected = state.selectedDimension,
                label = { dimensionLabel(it) },
                onSelect = vm::selectDimension,
            )
            SelectorStrip(
                title = "榜单",
                options = visibleBoards.map { it.slug },
                selected = state.selectedBoardSlug,
                label = { slug -> visibleBoards.firstOrNull { it.slug == slug }?.name.orEmpty() },
                onSelect = vm::selectBoard,
            )
            SelectionBar(
                models = state.models,
                canAdd = state.selectedSlugs.size < CompareViewModel.MAX_MODELS,
                onRemove = vm::toggleSelection,
                onAdd = { vm.setPickerOpen(true) },
            )

            when {
                state.loading -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.models.size < 2 -> {
                    EmptyCompare(
                        modelCount = state.models.size,
                        onOpenPicker = { vm.setPickerOpen(true) },
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> {
                    CompareContent(
                        models = state.models,
                        boards = visibleBoards,
                        selectedBoard = selectedBoard,
                        onOpenModel = onOpenModel,
                    )
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
            onToggleFavorite = vm::toggleFavorite,
            onDismiss = { vm.setPickerOpen(false) },
        )
    }
}

@Composable
private fun CompareTopBar(
    selectedCount: Int,
    onBack: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "模型对比",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$selectedCount/${CompareViewModel.MAX_MODELS} 已选",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenPicker) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text("选择")
            }
        }
    }
}

@Composable
private fun SelectorStrip(
    title: String,
    options: List<String>,
    selected: String,
    label: (String) -> String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        items(options, key = { it }) { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.48f)
                },
                modifier = Modifier.clickable { onSelect(option) },
            ) {
                Text(
                    text = if (option == "all") "$title · 全部" else label(option).ifBlank { option },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    models: List<ModelDetailResponse>,
    canAdd: Boolean,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 5.dp),
    ) {
        items(models, key = { it.model.slug }) { response ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(start = 7.dp),
                ) {
                    VendorIcon(vendor = response.model.vendor, size = 18.dp)
                    Text(
                        text = parseModelStrength(response.model.displayName).first,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 112.dp),
                    )
                    IconButton(
                        onClick = { onRemove(response.model.slug) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "移除 ${response.model.displayName}",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
        if (canAdd) {
            item(key = "add") {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable(onClick = onAdd),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "添加",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCompare(
    modelCount: Int,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                Icons.Filled.Assessment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = if (modelCount == 0) "选择 2-4 个模型" else "再选择一个模型",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "默认带入收藏的模型，也可以按厂商或搜索添加；上次的选择会自动保留。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpenPicker) {
                Text(if (modelCount == 0) "开始选择" else "继续选择")
            }
        }
    }
}

@Composable
private fun CompareContent(
    models: List<ModelDetailResponse>,
    boards: List<BoardDto>,
    selectedBoard: BoardDto?,
    onOpenModel: (String) -> Unit,
) {
    val selectedBoardSlug = selectedBoard?.slug.orEmpty()
    val entries = models.map { it to it.scores.firstOrNull { s -> s.boardSlug == selectedBoardSlug } }
    val scoredEntries = entries.filter { it.second?.score != null }
    val leader = scoredEntries.maxByOrNull { it.second!!.score!! }
        ?: entries.filter { (it.second?.rank ?: 0) > 0 }.minByOrNull { it.second!!.rank }
    val runnerUpScore = scoredEntries
        .filter { it.first.model.slug != leader?.first?.model?.slug }
        .maxOfOrNull { it.second!!.score!! }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(key = "leader") {
            LeaderCard(
                leader = leader?.first,
                score = leader?.second,
                runnerUpScore = runnerUpScore,
                board = selectedBoard,
            )
        }
        item(key = "radar") {
            CompareRadarSection(models)
        }
        item(key = "table") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "对比总表")
                CompareTableCard(
                    models = models,
                    selectedBoard = selectedBoard,
                    boards = boards,
                    onOpenModel = onOpenModel,
                )
            }
        }
        item(key = "note") {
            Text(
                text = "价格为每百万 tokens 成本；底色高亮为该行最优：分数取最高、价格取最低、票选榜取名次最前。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun CompareRadarSection(models: List<ModelDetailResponse>) {
    if (selectCompareAxes(models).size < 3) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = "能力雷达")
        CompareRadarChart(models)
    }
}

@Composable
private fun LeaderCard(
    leader: ModelDetailResponse?,
    score: ScoreDto?,
    runnerUpScore: Double?,
    board: BoardDto?,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (leader == null || score == null) {
                Text(
                    text = "当前榜单缺少所选模型的数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                VendorIcon(vendor = leader.model.vendor, size = 28.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "当前领先 · ${board?.name.orEmpty()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = parseModelStrength(leader.model.displayName).first,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val delta = runnerUpScore?.let { runnerUp -> score.score?.minus(runnerUp) }
                    if (delta != null && delta > 0) {
                        Text(
                            text = "领先第 2 名 ${formatDelta(delta)} 分",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (score.score != null) {
                        Text(
                            text = formatScore(score.score),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = scoreColor(score.score, score.scoreMin, score.scoreMax),
                        )
                        Text(
                            text = formatRank(score.rank),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "#${score.rank}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "票选排名",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    query: String,
    tab: PickerTab,
    candidates: List<ModelDetailDto>,
    favoriteModels: List<ModelDetailDto>,
    selectedSlugs: List<String>,
    maxCount: Int,
    onQueryChange: (String) -> Unit,
    onTabChange: (PickerTab) -> Unit,
    onToggle: (String) -> Unit,
    onToggleFavorite: (ModelDetailDto) -> Unit,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "选择模型",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${selectedSlugs.size}/$maxCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onDismiss) {
                    Text("完成")
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(PickerTab.entries.toList(), key = { it.name }) { item ->
                    val selected = tab == item
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { onTabChange(item) },
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            SearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "搜索模型或厂商",
            )
            if (tab == PickerTab.Favorites && favoriteModels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有收藏模型，在模型详情页点亮星标即可加入这里",
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
                        text = "没有匹配的模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        count = groups.size,
                        key = { index -> "vendor:${groups[index].vendor}" },
                    ) { index ->
                        ModelGroupSection(
                            group = groups[index],
                            selectedSlugs = selectedSlugs,
                            favoriteSlugs = favoriteSlugs,
                            maxCount = maxCount,
                            onToggle = onToggle,
                            onToggleFavorite = onToggleFavorite,
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
    onToggleFavorite: (ModelDetailDto) -> Unit,
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
                text = group.vendor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = group.models.size.toString(),
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
                onToggleFavorite = { onToggleFavorite(model) },
            )
        }
    }
}

@Composable
private fun ModelPickerRow(
    model: ModelDetailDto,
    selected: Boolean,
    favorite: Boolean,
    disabled: Boolean,
    onToggle: () -> Unit,
    onToggleFavorite: () -> Unit,
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
        ) {
            VendorIcon(vendor = model.vendor, size = 20.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = parseModelStrength(model.displayName).first,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        model.vendor,
                        model.paramsB?.let { params -> "${formatNum(params)}B" },
                        parseModelStrength(model.displayName).second,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (favorite) "取消收藏" else "收藏",
                    tint = if (favorite) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(17.dp),
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
