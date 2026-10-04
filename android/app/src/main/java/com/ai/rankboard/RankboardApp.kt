package com.ai.rankboard

import android.app.Application
import com.ai.rankboard.data.AppDatabase
import com.ai.rankboard.data.AppUpdateMonitor
import com.ai.rankboard.data.LeaderboardRepository
import com.ai.rankboard.data.LocalSnapshotDataSource
import com.ai.rankboard.data.SettingsStore
import com.ai.rankboard.data.SnapshotStore
import com.ai.rankboard.data.AppUpdateWorker
import com.ai.rankboard.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RankboardApp : Application() {

    val snapshotStore by lazy { SnapshotStore(this) }
    val settingsStore by lazy { SettingsStore(this) }
    val appUpdateMonitor by lazy { AppUpdateMonitor(this) }

    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val repository by lazy {
        LeaderboardRepository(
            local = LocalSnapshotDataSource(snapshotStore),
            db = AppDatabase.build(this),
            snapshotStore = snapshotStore,
            snapshotUrlProvider = {
                settingsStore.settings.value.snapshotUrl.ifBlank { BuildConfig.SNAPSHOT_URL }
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        val settings = settingsStore.settings.value
        if (settings.updateReminders) {
            snapshotStore.scheduleDailyUpdate(
                settings.snapshotUrl.ifBlank { BuildConfig.SNAPSHOT_URL },
                settings.snapshotFrequency.days,
            )
            AppUpdateWorker.schedule(
                this,
                true,
                settings.appUpdateUrl.ifBlank { BuildConfig.APP_UPDATE_URL },
            )
        } else {
            AppUpdateWorker.schedule(this, false, "")
        }

        // 更新状态跨界面共享；设置里切换提醒或更新源时也无需各界面单独重启检查。
        settingsScope.launch {
            settingsStore.settings
                .map { it.updateReminders to it.appUpdateUrl.ifBlank { BuildConfig.APP_UPDATE_URL } }
                .distinctUntilChanged()
                .collect { (enabled, url) ->
                    appUpdateMonitor.start(enabled, url)
                }
        }
    }
}
