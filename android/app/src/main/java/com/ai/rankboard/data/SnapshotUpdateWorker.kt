package com.ai.rankboard.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ai.rankboard.BuildConfig
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.SnapshotUpdateStatus

class SnapshotUpdateWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as? RankboardApp ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: BuildConfig.SNAPSHOT_URL
        return when (app.snapshotStore.refresh(url).status) {
            SnapshotUpdateStatus.UPDATED -> Result.success()
            SnapshotUpdateStatus.ERROR -> Result.retry()
            SnapshotUpdateStatus.UP_TO_DATE,
            SnapshotUpdateStatus.SKIPPED -> Result.success()
        }
    }

    companion object {
        const val KEY_URL = "url"
    }
}
