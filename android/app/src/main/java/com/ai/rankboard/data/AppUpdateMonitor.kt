package com.ai.rankboard.data

import android.content.Context
import com.ai.rankboard.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 防止同一次更新被用户关闭后又被轮询重新弹出来。 */
internal class AppUpdatePromptPolicy {
    private val dismissedVersionCodes = mutableSetOf<Int>()

    @Synchronized
    fun shouldShow(info: AppUpdateInfo?, currentVersionCode: Int): Boolean {
        return info != null &&
            info.versionCode > currentVersionCode &&
            info.versionCode !in dismissedVersionCodes
    }

    @Synchronized
    fun dismiss(versionCode: Int) {
        dismissedVersionCodes.add(versionCode)
    }
}

class AppUpdateMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val promptPolicy = AppUpdatePromptPolicy()
    private val checkMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    private val _pendingUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val pendingUpdate: StateFlow<AppUpdateInfo?> = _pendingUpdate.asStateFlow()

    fun start(enabled: Boolean, url: String) {
        pollingJob?.cancel()
        pollingJob = null

        if (!enabled || url.isBlank()) {
            clearPending()
            return
        }

        pollingJob = scope.launch {
            while (isActive) {
                checkNow(enabled, url)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    suspend fun checkNow(enabled: Boolean, url: String): AppUpdateResult? {
        if (!enabled || url.isBlank()) {
            clearPending()
            return null
        }

        return checkMutex.withLock {
            val result = AppUpdater.checkUpdate(appContext, url)
            if (result.status == AppUpdateStatus.AVAILABLE) {
                setAvailable(result.info)
            } else if (result.status == AppUpdateStatus.UP_TO_DATE) {
                _pendingUpdate.value = null
            }
            result
        }
    }

    fun setAvailable(info: AppUpdateInfo?) {
        if (!promptPolicy.shouldShow(info, BuildConfig.VERSION_CODE)) return
        _pendingUpdate.value = info
    }

    fun dismiss(info: AppUpdateInfo?) {
        info?.let { promptPolicy.dismiss(it.versionCode) }
        clearPending()
    }

    fun clearPending() {
        _pendingUpdate.value = null
    }

    companion object {
        const val POLL_INTERVAL_MS = 5L * 60L * 1_000L
    }
}
