package com.ai.rankboard.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.rankboard.data.AppUpdateInfo
import com.ai.rankboard.data.DownloadProgress
import com.ai.rankboard.data.AppUpdater

@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    installing: Boolean,
    progress: DownloadProgress? = null,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "v${info.versionName.ifBlank { info.versionCode.toString() }} · ${AppUpdater.formatSize(info.sizeBytes)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (info.notes.isNotBlank()) {
                Text(info.notes, style = MaterialTheme.typography.bodyMedium)
                }
                if (installing) {
                    val completed = progress?.fraction ?: 0f
                    LinearProgressIndicator(
                        progress = { completed },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${(completed * 100).toInt()}% · " +
                            "${AppUpdater.formatSize(progress?.bytesRead ?: 0L)} / " +
                            AppUpdater.formatSize(progress?.totalBytes ?: info.sizeBytes) +
                            " · ${formatSpeed(progress?.bytesPerSecond ?: 0L)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (message.isNotBlank()) {
                    Text(
                        message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !installing,
            ) {
                Text(if (installing) "下载中..." else "下载并安装")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !installing,
            ) {
                Text("稍后")
            }
        },
    )
}

private fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "准备中"
    return "${AppUpdater.formatSize(bytesPerSecond)}/s"
}
