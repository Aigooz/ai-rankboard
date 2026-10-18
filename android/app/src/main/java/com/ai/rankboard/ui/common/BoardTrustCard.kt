package com.ai.rankboard.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.rankboard.data.BoardDto
import com.ai.rankboard.data.CROSS_BOARD_WARNING
import com.ai.rankboard.data.boardHealthLines
import com.ai.rankboard.data.metricExplanation
import com.ai.rankboard.data.sourceHttpUrl

@Composable
fun CrossBoardNotice() {
    Text(CROSS_BOARD_WARNING, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun BoardTrustCard(board: BoardDto?, generatedAt: String?) {
    val context = LocalContext.current
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(board?.name ?: "榜单说明", style = MaterialTheme.typography.titleSmall)
            CrossBoardNotice()
            Text("快照生成：${generatedAt?.takeIf { it.isNotBlank() } ?: "未提供"}（文件导出时间）",
                style = MaterialTheme.typography.bodySmall)
            if (board != null) {
                val url = sourceHttpUrl(board.url) ?: sourceHttpUrl(board.source?.url)
                if (url != null) {
                    TextButton(onClick = { openUrl(context, url) }) {
                        Text("查看原始来源 · ${board.source?.name?.takeIf { it.isNotBlank() } ?: board.sourceId} ↗")
                    }
                } else {
                    Text("来源链接未提供或无效", style = MaterialTheme.typography.bodySmall)
                }
                Text(board.metric?.description?.takeIf { it.isNotBlank() } ?: metricExplanation(board.scoreType),
                    style = MaterialTheme.typography.bodySmall)
                val metric = board.metric
                if (metric != null) {
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
