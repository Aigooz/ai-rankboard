package com.ai.rankboard

import android.app.Application
import com.ai.rankboard.data.AppDatabase
import com.ai.rankboard.data.LeaderboardRepository
import com.ai.rankboard.data.LocalSnapshotDataSource
import com.ai.rankboard.data.SnapshotStore
import com.ai.rankboard.BuildConfig

class RankboardApp : Application() {

    val snapshotStore by lazy { SnapshotStore(this) }

    val repository by lazy {
        LeaderboardRepository(
            local = LocalSnapshotDataSource(snapshotStore),
            db = AppDatabase.build(this),
            snapshotStore = snapshotStore,
        )
    }

    override fun onCreate() {
        super.onCreate()
        snapshotStore.scheduleDailyUpdate(BuildConfig.SNAPSHOT_URL)
    }
}
