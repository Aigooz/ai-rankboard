package com.ai.rankboard.ui.compare

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Color
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
import com.ai.rankboard.ui.common.ScoreBar
import com.ai.rankboard.ui.common.SearchField
import com.ai.rankboard.ui.common.SectionHeader
import com.ai.rankboard.ui.common.SourceBadge
import com.ai.rankboard.ui.common.VendorIcon
import com.ai.rankboard.ui.common.scoreColor
import java.util.Locale

private val MODEL_ACCENTS = listOf(
    Color(0xFF6366F1),
    Color(0xFF0EA5E9),
    Color(0xFFF97316),
    Color(0xFF10B981),
)

@Composable
fun CompareScreen(
    onBack: () -> Unit,
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
                text = "已收藏模型会自动进入对比，也可以按厂商分类添加。",
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
) {
    val selectedBoardSlug = selectedBoard?.slug.orEmpty()
    val leader = models.maxByOrNull { response ->
        response.scores.firstOrNull { it.boardSlug == selectedBoardSlug }?.score
            ?: Double.NEGATIVE_INFINITY
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(key = "leader") {
            LeaderCard(leader = leader, board = selectedBoard)
        }
        items(models, key = { it.model.slug }) { response ->
            val score = response.scores.firstOrNull { it.boardSlug == selectedBoardSlug }
            ModelCompareCard(
                response = response,
                score = score,
                isLeader = leader?.model?.slug == response.model.slug && score?.score != null,
            )
        }
        item(key = "matrix-header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "全维度矩阵")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    models.forEachIndexed { index, response ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.widthIn(max = 82.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(MODEL_ACCENTS[index % MODEL_ACCENTS.size], CircleShape),
                            )
                            Text(
                                text = parseModelStrength(response.model.displayName).first,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        items(boards, key = { "matrix:${it.slug}" }) { board ->
            BoardComparisonCard(board = board, models = models)
        }
        item(key = "note") {
            Text(
                text = "价格为每百万 token 成本；颜色按当前榜单的分数区间计算。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun LeaderCard(
    leader: ModelDetailResponse?,
    board: BoardDto?,
) {
    val score = leader?.scores?.firstOrNull { it.boardSlug == board?.slug.orEmpty() }
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
                }
                Column(horizontalAlignment = Alignment.End) {
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
                }
            }
        }
    }
}

@Composable
private fun ModelCompareCard(
    response: ModelDetailResponse,
    score: ScoreDto?,
    isLeader: Boolean,
) {
    val model = response.model
    val strength = parseModelStrength(model.displayName).second
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VendorIcon(vendor = model.vendor, size = 30.dp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = parseModelStrength(model.displayName).first,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        strength?.let { value ->
                            Spacer(Modifier.width(5.dp))
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                            ) {
                                Text(
                                    text = value,
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
                            model.vendor,
                            model.paramsB?.let { "${formatNum(it)}B" },
                            model.releaseDate?.take(10)?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (isLeader) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                    },
                ) {
                    Text(
                        text = if (isLeader) "领先" else formatRank(score?.rank ?: 0),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLeader) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }

            ScoreBar(
                score = score?.score,
                minScore = score?.scoreMin,
                maxScore = score?.scoreMax,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatCell(
                    label = "得分",
                    value = formatScore(score?.score),
                    color = scoreColor(score?.score, score?.scoreMin, score?.scoreMax),
                    modifier = Modifier.weight(1.15f),
                )
                StatCell(
                    label = "输入价/M",
                    value = formatPrice(score?.priceIn, score?.currency),
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "输出价/M",
                    value = formatPrice(score?.priceOut, score?.currency),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = listOfNotNull(
                    model.license?.let { "许可 $it" },
                    model.contextWindow?.let { "上下文 $it" },
                    score?.fetchedAt?.take(10)?.let { "数据 $it" },
                ).joinToString(" · ").ifBlank { "暂无扩展信息" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BoardComparisonCard(
    board: BoardDto,
    models: List<ModelDetailResponse>,
) {
    val rows = models.mapIndexed { index, response ->
        ComparisonRow(
            response = response,
            accent = MODEL_ACCENTS[index % MODEL_ACCENTS.size],
            score = response.scores.firstOrNull { it.boardSlug == board.slug },
        )
    }
    val bestScore = rows.mapNotNull { it.score?.score }.maxOrNull()
    val bestRank = rows.mapNotNull { it.score?.takeIf { item -> item.score == null }?.rank }
        .filter { it > 0 }.minOrNull()

    AppCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = board.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SourceBadge(sourceId = board.sourceId)
            }
            if (rows.all { it.score == null }) {
                Text(
                    text = "所选模型均未进入该榜单",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                rows.forEach { row ->
                    val isBest = row.score?.score?.let { bestScore != null && it == bestScore } ?: false
                    val rankBest = row.score?.score == null && row.score?.rank?.takeIf { it > 0 } == bestRank
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isBest || rankBest) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            Color.Transparent
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(row.accent, CircleShape),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = parseModelStrength(row.response.model.displayName).first,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isBest || rankBest) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${formatRank(row.score?.rank ?: 0)} · ${sourceLabel(board.sourceId)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            ScoreBar(
                                score = row.score?.score,
                                minScore = row.score?.scoreMin,
                                maxScore = row.score?.scoreMax,
                                modifier = Modifier.width(42.dp),
                            )
                            Text(
                                text = formatScore(row.score?.score),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = scoreColor(row.score?.score, row.score?.scoreMin, row.score?.scoreMax),
                                maxLines = 1,
                                modifier = Modifier.width(46.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ComparisonRow(
    val response: ModelDetailResponse,
    val accent: Color,
    val score: ScoreDto?,
)

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
                        text = "还没有收藏模型，去榜单页点亮星标吧",
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
                        model.paramsB?.let { "${formatNum(it)}B" },
                        parseModelStrength(model.displayName).second,
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

private fun dimensionLabel(dimension: String): String = when (dimension) {
    "overall" -> "综合"
    "coding" -> "代码"
    "writing" -> "写作"
    "multimodal" -> "多模态"
    "agent" -> "智能体"
    "search" -> "搜索"
    "speed" -> "速度"
    "value" -> "性价比"
    "math" -> "数学"
    "analysis" -> "数据分析"
    else -> dimension.replaceFirstChar { it.uppercase(Locale.US) }
}

private fun sourceLabel(sourceId: String): String = when (sourceId.lowercase(Locale.US)) {
    "modelsage" -> "ModelSage"
    "livebench" -> "LiveBench"
    "swebench", "swe-bench" -> "SWE-bench"
    "arena", "lmarena" -> "Arena"
    else -> sourceId
}

private fun parseModelStrength(displayName: String): Pair<String, String?> {
    val match = Regex("\\s*\\((xhigh|high|medium|low|max|non-reasoning|reasoning)( with fallback)?\\)$")
        .find(displayName) ?: return displayName to null
    val strength = match.groupValues[1].replaceFirstChar { it.uppercase() } +
        if (match.groupValues[2].isNotBlank()) " FB" else ""
    return displayName.removeRange(match.range) to strength
}

private fun formatScore(value: Double?): String {
    if (value == null) return "未上榜"
    return if (value >= 1000) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

private fun formatRank(rank: Int?): String {
    return if (rank != null && rank > 0) "#$rank" else "未上榜"
}

private fun formatPrice(value: Double?, currency: String?): String {
    if (value == null) return "-"
    val symbol = if (currency.equals("USD", ignoreCase = true)) "$" else "¥"
    return "$symbol${String.format(Locale.US, "%.2f", value)}"
}

private fun formatNum(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
