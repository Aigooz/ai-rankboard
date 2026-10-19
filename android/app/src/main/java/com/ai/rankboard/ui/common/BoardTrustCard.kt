package com.ai.rankboard.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.rankboard.data.BoardDto
import com.ai.rankboard.data.CROSS_BOARD_WARNING
import com.ai.rankboard.data.boardCompactStatus
import com.ai.rankboard.data.boardHealthLines
import com.ai.rankboard.data.boardSourceLabel
import com.ai.rankboard.data.metricExplanation
import com.ai.rankboard.data.sourceHttpUrl

@Composable
fun CrossBoardNotice() {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("跨榜分数不可直接比较 ⓘ", style = MaterialTheme.typography.labelSmall) }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("如何理解不同榜单") },
        text = { Text(CROSS_BOARD_WARNING) },
        confirmButton = { TextButton(onClick = { open = false }) { Text("知道了") } },
    )
}

/** Provenance stays one compact row; methodology and diagnostics are opt-in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardTrustCard(board: BoardDto?, generatedAt: String?) {
    var expanded by remember(board?.slug) { mutableStateOf(false) }
    val context = LocalContext.current
    val url = sourceHttpUrl(board?.url) ?: sourceHttpUrl(board?.source?.url)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { url?.let { openUrl(context, it) } }, enabled = url != null) {
            Text(boardSourceLabel(board) + if (url != null) " ↗" else "",
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 112.dp).padding(end = 2.dp))
        }
        val status = boardCompactStatus(board)
        Text(status, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
            color = if (status.contains("失败") || status.contains("过期")) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = { expanded = true }) { Text("详情", style = MaterialTheme.typography.labelSmall) }
    }
    if (expanded) ModalBottomSheet(onDismissRequest = { expanded = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(board?.name ?: "榜单说明", style = MaterialTheme.typography.titleMedium)
            Text(CROSS_BOARD_WARNING, style = MaterialTheme.typography.bodySmall)
            Text("快照生成：${generatedAt?.takeIf { it.isNotBlank() } ?: "未提供"}（文件导出时间）",
                style = MaterialTheme.typography.bodySmall)
            if (board != null) {
                if (url != null) TextButton(onClick = { openUrl(context, url) }) { Text("查看原始来源 · ${boardSourceLabel(board)} ↗") }
                else Text("来源链接未提供或无效", style = MaterialTheme.typography.bodySmall)
                Text(board.metric?.description?.takeIf { it.isNotBlank() } ?: metricExplanation(board.scoreType),
                    style = MaterialTheme.typography.bodySmall)
                board.metric?.let { metric ->
                    Text("指标：${metric.name ?: board.scoreType ?: "未提供"} · 单位：${metric.unit ?: "未提供"} · " +
                        when (metric.higherIsBetter) { true -> "越高越好"; false -> "越低越好"; null -> "方向未提供" },
                        style = MaterialTheme.typography.bodySmall)
                }
                val evaluation = board.evaluation
                Text("上游评测版本：${evaluation?.version?.takeIf { it.isNotBlank() } ?: board.release?.takeIf { it.isNotBlank() } ?: "未提供"}" +
                    if (evaluation?.versionSource == "registry") "（来源配置记录）" else "",
                    style = MaterialTheme.typography.bodySmall)
                Text("上游更新时间：${evaluation?.upstreamUpdatedAt?.takeIf { it.isNotBlank() } ?: "未提供，不能用抓取时间代替"}",
                    style = MaterialTheme.typography.bodySmall)
                boardHealthLines(board).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
