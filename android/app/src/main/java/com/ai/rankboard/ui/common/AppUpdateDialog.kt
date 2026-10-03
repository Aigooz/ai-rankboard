package com.ai.rankboard.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.rankboard.data.AppUpdateInfo
import com.ai.rankboard.data.AppUpdater

@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    installing: Boolean,
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
                Text(if (installing) "下载中" else "下载并安装")
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
