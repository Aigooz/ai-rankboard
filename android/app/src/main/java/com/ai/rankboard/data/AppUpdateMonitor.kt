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
    fun shouldShow(info: AppUpdateInfo?, currentVersionCode: Int, manual: Boolean = false): Boolean {
        return info != null &&
            info.versionCode > currentVersionCode &&
            (manual || info.versionCode !in dismissedVersionCodes)
    }

    @Synchronized
    fun dismiss(versionCode: Int) {
        dismissedVersionCodes.add(versionCode)
    }
}

data class AppUpdateCheckState(
    val checking: Boolean = false,
    val checkedAt: Long = 0L,
    val result: AppUpdateResult? = null,
)

class AppUpdateMonitor internal constructor(
    private val check: suspend (String) -> AppUpdateResult,
    private val currentVersionCode: Int,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(
        check = { url -> AppUpdater.checkUpdate(context.applicationContext, url) },
        currentVersionCode = BuildConfig.VERSION_CODE,
    )

    private val promptPolicy = AppUpdatePromptPolicy()
    private val checkMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    private val _pendingUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val pendingUpdate: StateFlow<AppUpdateInfo?> = _pendingUpdate.asStateFlow()
    private val _state = MutableStateFlow(AppUpdateCheckState())
    val state: StateFlow<AppUpdateCheckState> = _state.asStateFlow()
    @Volatile private var automaticEnabled = true
    @Volatile private var sourceUrl: String? = null

    fun start(enabled: Boolean, url: String) {
        automaticEnabled = enabled
        if (sourceUrl != null && sourceUrl != url) {
            _state.value = AppUpdateCheckState()
            clearPending()
        }
        sourceUrl = url
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

    suspend fun checkNow(enabled: Boolean, url: String, manual: Boolean = false): AppUpdateResult? {
        if (!enabled && !manual) return null

        return checkMutex.withLock {
            _state.value = _state.value.copy(checking = true)
            try {
                val result = check(url)
                if (!manual && sourceUrl != null && sourceUrl != url) return@withLock result
                _state.value = AppUpdateCheckState(checkedAt = now(), result = result)
                if (result.status == AppUpdateStatus.AVAILABLE && (manual || automaticEnabled)) {
                    setAvailable(result.info, manual)
                } else if (result.status == AppUpdateStatus.UP_TO_DATE) {
                    clearPending()
                }
                result
            } finally {
                _state.value = _state.value.copy(checking = false)
            }
        }
    }

    fun setAvailable(info: AppUpdateInfo?, manual: Boolean = false) {
        if (!promptPolicy.shouldShow(info, currentVersionCode, manual)) return
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
