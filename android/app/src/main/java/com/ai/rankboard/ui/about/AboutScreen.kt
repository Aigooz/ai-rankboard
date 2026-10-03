package com.ai.rankboard.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dataset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.rankboard.BuildConfig
import com.ai.rankboard.data.SnapshotSourceDto
import com.ai.rankboard.R
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.ui.common.openUrl

private const val GITHUB_URL = "https://github.com/Aigooz/ai-rankboard"
private const val ISSUES_URL = "https://github.com/Aigooz/ai-rankboard/issues"
private const val SOURCE_URL = "https://modelsage.cn/"
private val FALLBACK_SOURCES = listOf(
    SnapshotSourceDto("modelsage", "ModelSage", SOURCE_URL),
    SnapshotSourceDto("livebench", "LiveBench", "https://livebench.ai/leaderboard"),
    SnapshotSourceDto("swebench", "SWE-bench", "https://www.swebench.com"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as RankboardApp
    val context = LocalContext.current
    val snapshot by app.snapshotStore.snapshotFlow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    )
                    Text(
                        "AI 排行榜",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "v${BuildConfig.VERSION_NAME} · 独立离线优先",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                AboutGroup(title = "项目", icon = Icons.Filled.Code) {
                    AboutLinkRow(
                        onClick = { openUrl(context, GITHUB_URL) },
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        label = "GitHub 仓库",
                    )
                    AboutLinkRow(
                        onClick = { openUrl(context, ISSUES_URL) },
                        icon = Icons.Filled.BugReport,
                        label = "反馈问题",
                    )
                }
            }
            item {
                AboutGroup(title = "数据", icon = Icons.Filled.Dataset) {
                    val sources = snapshot.sources.ifEmpty { FALLBACK_SOURCES }
                    Text(
                        "当前接入 ${sources.size} 个数据源\n" +
                            "快照生成：${snapshot.generatedAt.take(19).replace("T", " ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                    sources.forEach { source ->
                        AboutLinkRow(
                            onClick = { openUrl(context, source.url.ifBlank { SOURCE_URL }) },
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            label = source.name.ifBlank { source.id },
                        )
                    }
                }
            }
            item {
                Text(
                    "榜单数据仅用于聚合展示和模型选型参考，应用内置快照可离线使用。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AboutGroup(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            content()
        }
    }
}

@Composable
private fun AboutLinkRow(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
