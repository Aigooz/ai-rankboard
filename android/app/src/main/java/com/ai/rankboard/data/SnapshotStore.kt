package com.ai.rankboard.data

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.ai.rankboard.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.selects.select
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

enum class SnapshotUpdateStatus { UPDATED, UP_TO_DATE, SKIPPED, ERROR }

data class SnapshotUpdateResult(
    val status: SnapshotUpdateStatus,
    val message: String,
    val addedModels: List<String> = emptyList(),
)

data class SnapshotInfo(
    val generatedAt: String,
    val sourceId: String,
    val sourceName: String,
    val isDownloaded: Boolean,
    val verified: Boolean,
)

data class SnapshotCheckState(
    val checking: Boolean = false,
    val checkedAt: Long = 0L,
    val result: SnapshotUpdateResult? = null,
)

class SnapshotStore(private val context: Context) {
    private val refreshMutex = Mutex()
    private val prefs = context.getSharedPreferences("snapshot_checks", Context.MODE_PRIVATE)
    private val _checkState = MutableStateFlow(SnapshotCheckState(checkedAt = prefs.getLong("checked_at", 0L)))
    val checkState: StateFlow<SnapshotCheckState> = _checkState.asStateFlow()
    private val gson = Gson()
    // 连接超时压到 6 秒：被墙的地址应尽快失败并切换镜像，而不是让用户等 45 秒。
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val hashClient = client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _progress = MutableStateFlow<SnapshotSyncProgress?>(null)
    val progress: StateFlow<SnapshotSyncProgress?> = _progress.asStateFlow()

    private var verified = true

    private val _snapshot = MutableStateFlow(initialSnapshot())
    val snapshot: Snapshot get() = _snapshot.value
    val snapshotFlow: StateFlow<Snapshot> = _snapshot.asStateFlow()

    val isDownloaded: Boolean get() = jsonFile.exists() && hashFile.exists()

    private val jsonFile: File
        get() = File(context.filesDir, SNAPSHOT_FILE_NAME)

    private val hashFile: File
        get() = File(context.filesDir, SNAPSHOT_HASH_FILE_NAME)

    fun info(): SnapshotInfo {
        val snapshot = _snapshot.value
        return SnapshotInfo(
            generatedAt = snapshot.generatedAt,
            sourceId = snapshot.source?.id.orEmpty(),
            sourceName = snapshot.source?.name.orEmpty(),
            isDownloaded = isDownloaded,
            verified = verified,
        )
    }

    fun updateFromBytes(data: Snapshot) {
        _snapshot.value = data
    }

    suspend fun refresh(url: String): SnapshotUpdateResult {
        if (url.isBlank()) {
            return SnapshotUpdateResult(SnapshotUpdateStatus.SKIPPED, "未配置远端快照地址")
        }

        return refreshMutex.withLock {
            _checkState.value = _checkState.value.copy(checking = true)
            _progress.value = SnapshotSyncProgress(SnapshotSyncPhase.CONNECTING)
            try {
                val startedAt = System.currentTimeMillis()
                val result = withContext(Dispatchers.IO) {
                    refreshRemote(url, startedAt)
                }
                val checkedAt = System.currentTimeMillis()
                val recordedCheckedAt = if (shouldRecordCheckTime(result.status)) {
                    prefs.edit().putLong("checked_at", checkedAt).apply()
                    checkedAt
                } else {
                    prefs.getLong("checked_at", 0L)
                }
                _checkState.value = SnapshotCheckState(checkedAt = recordedCheckedAt, result = result)
                result
            } finally {
                _progress.value = null
                _checkState.value = _checkState.value.copy(checking = false)
            }
        }
    }

    private suspend fun refreshRemote(url: String, startedAt: Long): SnapshotUpdateResult {
        val candidates = snapshotSourceCandidates(url, prefs.getString(PREFERRED_HOST_KEY, null))
        if (candidates.isEmpty()) {
            return SnapshotUpdateResult(SnapshotUpdateStatus.SKIPPED, "未配置远端快照地址")
        }
        var lastError: Exception? = null
        try {
            // 先只取 84 字节的校验文件：绝大多数检查的结果是"没有变化"，
            // 没必要每次都下载整份 2.7 MB 快照。
            // Hash probes run together so one slow or blocked endpoint does not delay all
            // of the faster mirrors behind it.
            val hashProbe = probeAuthoritativeHash(candidates, startedAt)
                ?: throw IllegalStateException("所有数据源均未返回有效校验值")
            val authoritativeHash = hashProbe.hash
            val currentHash = currentHash()
            if (shouldSkipSnapshotDownload(authoritativeHash, currentHash)) {
                return SnapshotUpdateResult(
                    SnapshotUpdateStatus.UP_TO_DATE,
                    "已是最新，无需下载（${elapsedSeconds(startedAt)} 秒）",
                )
            }

            // 先下载刚刚成功返回校验值的入口，避免校验阶段选中了快速镜像，
            // 下载阶段却又回到一个较慢的官方地址。
            val downloadCandidates = listOf(hashProbe.source) +
                candidates.filterNot { it.url == hashProbe.source.url }
            // 每个入口都必须匹配同一个可信校验值，响应异常时可以安全切换到下一个源。
            for (candidate in downloadCandidates) {
                try {
                    val jsonBytes = httpBytes(
                        withCacheBust(candidate.url, startedAt),
                        candidate.label,
                        onProgress = { received, total ->
                        _progress.value = SnapshotSyncProgress(
                            SnapshotSyncPhase.DOWNLOADING, candidate.label, received, total,
                        )
                        },
                    )
                    // Every mirror must match the same trusted hash. Never accept a
                    // response merely because its self-calculated hash is consistent.
                    if (!verify(jsonBytes, authoritativeHash)) {
                        throw IllegalStateException("snapshot checksum mismatch")
                    }
                    _progress.value = SnapshotSyncProgress(SnapshotSyncPhase.PARSING, candidate.label)
                    val result = applySnapshot(jsonBytes, authoritativeHash)
                    rememberPreferredHost(candidate)
                    return result.copy(message = "${result.message}（${elapsedSeconds(startedAt)} 秒）")
                } catch (exc: CancellationException) {
                    throw exc
                } catch (exc: Exception) {
                    lastError = exc
                }
            }
            throw lastError ?: IllegalStateException("所有数据源均不可用")
        } catch (exc: CancellationException) {
            throw exc
        } catch (exc: Exception) {
            android.util.Log.w("SnapshotStore", "Snapshot check failed", exc)
            val tried = candidates.size
            return SnapshotUpdateResult(
                SnapshotUpdateStatus.ERROR,
                "同步失败（已尝试 $tried 个数据源，${elapsedSeconds(startedAt)} 秒）。已保留现有榜单，可稍后重试。",
            )
        }
    }

    private data class SnapshotHashProbe(
        val hash: String,
        val source: SnapshotSource,
    )

    /**
     * Probe checksum endpoints concurrently. Give the official raw endpoint a short
     * priority window so a stale CDN mirror cannot make the app roll back to older
     * data, then fall back to the first healthy mirror when GitHub is unavailable.
     */
    private suspend fun probeAuthoritativeHash(
        candidates: List<SnapshotSource>,
        startedAt: Long,
    ): SnapshotHashProbe? = supervisorScope {
        val probes = candidates.map { candidate ->
            candidate to async(Dispatchers.IO) {
                try {
                    _progress.value = SnapshotSyncProgress(SnapshotSyncPhase.CONNECTING, candidate.label)
                    val text = httpBytes(
                        withCacheBust(snapshotHashUrl(candidate.url), startedAt),
                        candidate.label,
                        httpClient = hashClient,
                    ).decodeToString()
                    val hash = parseHash(text).takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("校验文件为空")
                    Result.success(hash)
                } catch (exc: CancellationException) {
                    throw exc
                } catch (exc: Exception) {
                    Result.failure<String>(exc)
                }
            }
        }
        val official = probes.firstOrNull { it.first.host == "raw.githubusercontent.com" }
        if (official != null) {
            val officialHash = withTimeoutOrNull(2_000L) {
                official.second.await().getOrNull()
            }
            if (isSha256(officialHash.orEmpty())) {
                probes.filter { it.second != official.second }.forEach { it.second.cancel() }
                return@supervisorScope SnapshotHashProbe(officialHash.orEmpty(), official.first)
            }
            // A timeout cancels the await, not necessarily the underlying request.
            // Stop the slow official probe before waiting for mirror results.
            official.second.cancel()
        }

        val pending = probes
            .filter { it.second != official?.second }
            .map { it.second }
            .toMutableSet()
        while (pending.isNotEmpty()) {
            val completed = withTimeoutOrNull(12_000L) {
                select<Triple<SnapshotSource, Deferred<Result<String>>, Result<String>>> {
                    probes.filter { it.second in pending }.forEach { (source, deferred) ->
                        deferred.onAwait { Triple(source, deferred, it) }
                    }
                }
            }
            if (completed == null) break
            pending.remove(completed.second)
            completed.third.getOrNull()?.takeIf(::isSha256)?.let { hash ->
                pending.forEach { it.cancel() }
                return@supervisorScope SnapshotHashProbe(hash, completed.first)
            }
        }
        null
    }

    /** 校验通过后写入本地并返回结果；只有内容真的变化才报告更新。 */
    private fun applySnapshot(jsonBytes: ByteArray, expectedHash: String): SnapshotUpdateResult {
        val parsed = parse(jsonBytes) ?: throw IllegalStateException("snapshot parse failed")
        if (parsed.schemaVersion !in 1..3 || parsed.boards.isEmpty()) {
            throw IllegalStateException("unsupported snapshot schema ${parsed.schemaVersion}")
        }

        // 与旧快照对比找出新增模型；旧快照为空（首装）时不提示，避免全部算作新增。
        val previousSlugs = _snapshot.value.models.keys
        val addedModels = if (previousSlugs.isEmpty()) {
            emptyList()
        } else {
            parsed.models.keys
                .minus(previousSlugs)
                .sortedByDescending { slug -> parsed.models[slug]?.releaseDate.orEmpty() }
        }

        writeSnapshotAtomically(jsonBytes, "$expectedHash  $SNAPSHOT_FILE_NAME\n")
        prefs.edit().putInt(SNAPSHOT_APP_VERSION_KEY, BuildConfig.VERSION_CODE).apply()
        verified = true
        _snapshot.value = parsed
        return SnapshotUpdateResult(
            SnapshotUpdateStatus.UPDATED,
            "已同步新快照；各榜抓取状态与上游版本请查看榜单说明",
            addedModels,
        )
    }

    private fun elapsedSeconds(startedAt: Long): String {
        val ms = System.currentTimeMillis() - startedAt
        return String.format(java.util.Locale.US, "%.1f", ms / 1000.0)
    }

    private fun rememberPreferredHost(source: SnapshotSource) {
        if (source.host.isBlank()) return
        prefs.edit().putString(PREFERRED_HOST_KEY, source.host).apply()
    }

    private suspend fun httpBytes(
        url: String,
        sourceLabel: String = "",
        onProgress: ((Long, Long) -> Unit)? = null,
        httpClient: OkHttpClient = client,
    ): ByteArray = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.raw")
            .header("User-Agent", "AI-Rankboard/${BuildConfig.VERSION_NAME}")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, exc: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(exc)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}")
                        val body = it.body ?: throw IllegalStateException("空响应")
                        val bytes = if (onProgress == null) {
                            body.bytes()
                        } else {
                            // 透明 gzip 解压后 contentLength 常为 -1，因此按累计字节展示进度。
                            val total = body.contentLength()
                            val buffer = ByteArray(64 * 1024)
                            val out = java.io.ByteArrayOutputStream(
                                total.coerceAtLeast(0L).toInt().coerceAtMost(8 * 1024 * 1024),
                            )
                            var received = 0L
                            body.byteStream().use { input ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    if (read > 0) {
                                        out.write(buffer, 0, read)
                                        received += read
                                        onProgress(received, total)
                                    }
                                }
                            }
                            out.toByteArray()
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    }
                } catch (exc: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(exc)
                }
            }
        })
    }

    private fun initialSnapshot(): Snapshot {
        val discardDownloaded = shouldDiscardDownloadedSnapshot(
            prefs.getInt(SNAPSHOT_APP_VERSION_KEY, 0), BuildConfig.VERSION_CODE,
        )
        if (discardDownloaded) {
            // A checksum-valid old cache can still hide boards/features bundled with the new APK.
            // Ignore it for this launch even if filesystem deletion unexpectedly fails.
            jsonFile.delete()
            hashFile.delete()
        }
        if (!discardDownloaded || (!jsonFile.exists() && !hashFile.exists())) {
            prefs.edit().putInt(SNAPSHOT_APP_VERSION_KEY, BuildConfig.VERSION_CODE).apply()
        }
        if (!discardDownloaded) readDownloaded()?.let { return it }
        val bytes = context.assets.open(ASSET_SNAPSHOT_FILE).use { it.readBytes() }
        val expectedHash = runCatching {
            parseHash(context.assets.open(ASSET_SNAPSHOT_HASH).use { it.readBytes().decodeToString() })
        }.getOrNull()
        verified = expectedHash == null || verify(bytes, expectedHash)
        return parse(bytes) ?: Snapshot()
    }

    private fun readDownloaded(): Snapshot? {
        if (!jsonFile.exists() || !hashFile.exists()) return null
        return runCatching {
            val bytes = jsonFile.readBytes()
            if (!verify(bytes, parseHash(hashFile.readText(Charsets.US_ASCII)))) {
                jsonFile.delete()
                hashFile.delete()
                verified = false
                null
            } else {
                parse(bytes)
            }
        }.getOrNull().also {
            if (it == null) {
                jsonFile.delete()
                hashFile.delete()
                verified = false
            }
        }
    }

    private fun parse(bytes: ByteArray): Snapshot? = runCatching {
        gson.fromJson(bytes.decodeToString(), Snapshot::class.java)
    }.getOrNull()

    private fun currentHash(): String? = runCatching {
        parseHash(hashFile.readText(Charsets.US_ASCII))
    }.getOrNull()

    private fun verify(bytes: ByteArray, expected: String?): Boolean {
        if (expected.isNullOrBlank()) return false
        return sha256(bytes).equals(expected, ignoreCase = true)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun parseHash(content: String): String =
        content.trim().split(Regex("\\s+")).firstOrNull()
            ?.takeIf { isSha256(it) }
            .orEmpty()

    private fun writeSnapshotAtomically(jsonBytes: ByteArray, hashText: String) {
        val jsonTemp = File.createTempFile("leaderboards-", ".json", context.filesDir)
        val hashTemp = File.createTempFile("leaderboards-", ".sha256", context.filesDir)
        try {
            jsonTemp.writeBytes(jsonBytes)
            hashTemp.writeText(hashText, Charsets.US_ASCII)
            atomicReplace(jsonTemp, jsonFile)
            atomicReplace(hashTemp, hashFile)
        } finally {
            jsonTemp.delete()
            hashTemp.delete()
        }
    }

    private fun atomicReplace(source: File, target: File) {
        runCatching {
            Files.move(
                source.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.recoverCatching {
            Files.move(
                source.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrThrow()
    }

    fun scheduleDailyUpdate(url: String, intervalHours: Int = 6) {
        if (url.isBlank()) return
        val safeHours = intervalHours.coerceIn(6, 7 * 24)
        val request = PeriodicWorkRequestBuilder<SnapshotUpdateWorker>(safeHours.toLong(), TimeUnit.HOURS)
            .setInputData(androidx.work.Data.Builder().putString(SnapshotUpdateWorker.KEY_URL, url).build())
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.MINUTES,
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    suspend fun refreshIfDue(url: String, intervalHours: Int = 6): SnapshotUpdateResult {
        if (!isCheckDue(prefs.getLong("checked_at", 0L), intervalHours)) {
            return SnapshotUpdateResult(SnapshotUpdateStatus.SKIPPED, "榜单快照未到检查周期")
        }
        return refresh(url)
    }

    fun setDailyUpdateEnabled(enabled: Boolean, url: String, intervalHours: Int = 6) {
        if (enabled) {
            scheduleDailyUpdate(url, intervalHours)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(DAILY_UPDATE_WORK)
        }
    }

    companion object {
        const val SNAPSHOT_FILE_NAME = "leaderboards.json"
        const val SNAPSHOT_HASH_FILE_NAME = "leaderboards.json.sha256"
        const val ASSET_SNAPSHOT_FILE = "leaderboards.json"
        const val ASSET_SNAPSHOT_HASH = "leaderboards.json.sha256"
        const val DAILY_UPDATE_WORK = "snapshot-daily-update"
        const val SNAPSHOT_APP_VERSION_KEY = "snapshot_app_version"
        private const val PREFERRED_HOST_KEY = "preferred_host"

        fun shouldDiscardDownloadedSnapshot(recordedVersionCode: Int, currentVersionCode: Int): Boolean =
            recordedVersionCode < currentVersionCode

        fun isSnapshotFresh(
            generatedAt: String?,
            intervalHours: Int,
            now: Instant = Instant.now(),
        ): Boolean {
            val generated = parseInstant(generatedAt) ?: return false
            return Duration.between(generated, now).toHours() < intervalHours
        }

        fun isCheckDue(
            checkedAtMillis: Long,
            intervalHours: Int,
            nowMillis: Long = System.currentTimeMillis(),
        ): Boolean {
            if (checkedAtMillis <= 0L) return true
            return nowMillis - checkedAtMillis >= intervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
        }

        fun shouldRecordCheckTime(status: SnapshotUpdateStatus): Boolean =
            status == SnapshotUpdateStatus.UPDATED || status == SnapshotUpdateStatus.UP_TO_DATE

        fun hashUrl(url: String): String = snapshotHashUrl(url)

        private fun parseInstant(value: String?): Instant? {
            val text = value?.trim().orEmpty()
            if (text.isBlank()) return null
            return runCatching { Instant.parse(text) }
                .recoverCatching {
                    runCatching { OffsetDateTime.parse(text).toInstant() }
                        .getOrElse {
                            LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)
                        }
                }
                .getOrNull()
        }
    }
}
