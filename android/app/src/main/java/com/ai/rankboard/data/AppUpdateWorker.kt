package com.ai.rankboard.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ai.rankboard.BuildConfig
import com.ai.rankboard.RankboardApp
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as? RankboardApp ?: return Result.failure()
        val settings = app.settingsStore.settings.value
        if (!settings.appUpdateReminders) return Result.success()
        val url = settings.appUpdateUrl.ifBlank { BuildConfig.APP_UPDATE_URL }
        if (url.isBlank()) return Result.success()

        val result = app.appUpdateMonitor.checkNow(true, url) ?: return Result.success()
        return when (result.status) {
            AppUpdateStatus.AVAILABLE -> {
                if (app.settingsStore.settings.value.appUpdateReminders) {
                    AppUpdateNotifier(context).notifyAvailable(result.info)
                }
                Result.success()
            }
            AppUpdateStatus.DOWNLOADED -> Result.success()
            AppUpdateStatus.UP_TO_DATE -> Result.success()
            AppUpdateStatus.ERROR -> Result.retry()
        }
    }

    companion object {
        const val KEY_URL = "url"
        private const val PERIODIC_INTERVAL_MINUTES = 15L
        private const val PERIODIC_WORK = "app-update-periodic"
        private const val STARTUP_WORK = "app-update-startup"

        fun schedule(context: Context, enabled: Boolean, url: String) {
            val manager = WorkManager.getInstance(context)
            if (!enabled || url.isBlank()) {
                manager.cancelUniqueWork(PERIODIC_WORK)
                manager.cancelUniqueWork(STARTUP_WORK)
                return
            }
            val inputData = workDataOf(KEY_URL to url)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodic = PeriodicWorkRequestBuilder<AppUpdateWorker>(PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setInputData(inputData)
                .setConstraints(constraints)
                .build()
            val startup = OneTimeWorkRequestBuilder<AppUpdateWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .build()
            manager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic,
            )
            manager.enqueueUniqueWork(
                STARTUP_WORK,
                ExistingWorkPolicy.REPLACE,
                startup,
            )
        }
    }
}
