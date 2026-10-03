package com.ai.rankboard.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ai.rankboard.BuildConfig
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.SnapshotUpdateStatus
import com.ai.rankboard.data.UpdateNotifier

class SnapshotUpdateWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as? RankboardApp ?: return Result.failure()
        val url = inputData.getString(KEY_URL)?.takeIf { it.isNotBlank() }
            ?: app.settingsStore.settings.value.snapshotUrl
            ?: BuildConfig.SNAPSHOT_URL
        val store = app.snapshotStore
        return when (store.refresh(url).status) {
            SnapshotUpdateStatus.UPDATED -> {
                UpdateNotifier(context).notifyUpdated(store.snapshot.generatedAt)
                Result.success()
            }
            SnapshotUpdateStatus.ERROR -> Result.retry()
            SnapshotUpdateStatus.UP_TO_DATE,
            SnapshotUpdateStatus.SKIPPED -> Result.success()
        }
    }

    companion object {
        const val KEY_URL = "url"
    }
}
