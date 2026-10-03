package com.ai.rankboard.ui.favorites

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.ModelDetailResponse
import com.ai.rankboard.data.ScoreDto
import com.ai.rankboard.ui.common.VendorIcon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(onOpenModel: (String) -> Unit, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as RankboardApp
    val favorites by app.repository.favorites().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // slug -> 详情，缓存避免每次列表变化都全量重查；下榜模型查不到时展示占位文案。
    val details = remember { mutableStateMapOf<String, ModelDetailResponse?>() }
    LaunchedEffect(favorites) {
        favorites.forEach { favorite ->
            if (favorite.modelSlug !in details) {
                details[favorite.modelSlug] = app.repository.modelDetail(favorite.modelSlug)
            }
        }
        val keep = favorites.map { it.modelSlug }.toSet()
        details.keys.toList().forEach { slug ->
            if (slug !in keep) details.remove(slug)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的收藏") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "还没有收藏，去模型详情页点亮星标吧",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(favorites, key = { it.modelSlug }) { fav ->
                val detail = details[fav.modelSlug]
                FavoriteRow(
                    displayName = fav.displayName,
                    vendor = detail?.model?.vendor,
                    summary = detail?.scores?.let { favoriteScoreSummary(it) },
                    offBoard = detail == null,
                    savedAtText = dateFormat.format(Date(fav.savedAt)),
                    onClick = { onOpenModel(fav.modelSlug) },
                    onRemove = {
                        scope.launch {
                            app.repository.setFavorite(fav.modelSlug, fav.displayName, false)
                        }
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    displayName: String,
    vendor: String?,
    summary: String?,
    offBoard: Boolean,
    savedAtText: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VendorIcon(vendor = vendor, size = 26.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        offBoard -> "模型暂不在当前榜单数据中"
                        else -> listOfNotNull(vendor, summary).joinToString(" · ").ifBlank { "暂无榜单成绩" }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "取消收藏 $displayName",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    savedAtText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 优先取综合榜成绩，否则取名次最靠前的成绩，用于收藏列表的一行摘要。 */
internal fun favoriteScoreSummary(scores: List<ScoreDto>): String? {
    val rankable = scores.filter { it.score != null || it.rank > 0 }
    val picked = rankable.firstOrNull { it.dimension == "overall" }
        ?: rankable.minByOrNull { it.rank.takeIf { rank -> rank > 0 } ?: Int.MAX_VALUE }
        ?: return null
    val rankText = picked.rank.takeIf { it > 0 }?.let { "#$it" } ?: "未上榜"
    val scoreText = picked.score?.let { String.format(Locale.US, "%.1f", it) } ?: return rankText
    return "$rankText · $scoreText"
}
