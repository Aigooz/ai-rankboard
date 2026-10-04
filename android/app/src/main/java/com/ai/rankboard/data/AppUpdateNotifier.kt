package com.ai.rankboard.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ai.rankboard.R
import java.io.File

class AppUpdateNotifier(private val context: Context) {

    fun notifyReady(info: AppUpdateInfo?) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
        }

        val versionName = info?.versionName?.takeIf { it.isNotBlank() }
            ?: info?.versionCode?.toString()
            ?: context.getString(R.string.app_update_fallback_version)
        val apkFile = File(context.cacheDir, AppUpdater.APK_FILE_PATH)
        val installIntent = AppUpdater.installIntent(context, apkFile)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_update_title))
            .setContentText(context.getString(R.string.app_update_message, versionName))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        listOfNotNull(
                            context.getString(R.string.app_update_message, versionName),
                            info?.notes?.takeIf { it.isNotBlank() },
                            info?.sizeBytes?.takeIf { it > 0 }?.let {
                                "安装包 ${AppUpdater.formatSize(it)}"
                            },
                        ).joinToString(" "),
                    ),
            )
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 1002
        private const val REQUEST_CODE = 1002
    }
}
