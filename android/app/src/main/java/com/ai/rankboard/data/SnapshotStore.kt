package com.ai.rankboard.data

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

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

class SnapshotStore(private val context: Context) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

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

        return withContext(Dispatchers.IO) {
            try {
                val jsonBytes = httpBytes(url)
                val hashBytes = httpBytes("$url.sha256")
                val expectedHash = parseHash(hashBytes.decodeToString())
                if (!verify(jsonBytes, expectedHash)) {
                    throw IllegalStateException("snapshot checksum mismatch")
                }
                if (expectedHash == currentHash()) {
                    return@withContext SnapshotUpdateResult(SnapshotUpdateStatus.UP_TO_DATE, "榜单快照已是最新")
                }

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

                jsonFile.writeBytes(jsonBytes)
                hashFile.writeText("$expectedHash  $SNAPSHOT_FILE_NAME\n", Charsets.US_ASCII)
                verified = true
                _snapshot.value = parsed
                SnapshotUpdateResult(SnapshotUpdateStatus.UPDATED, "榜单数据已更新", addedModels)
            } catch (exc: Exception) {
                SnapshotUpdateResult(SnapshotUpdateStatus.ERROR, exc.message ?: "快照更新失败")
            }
        }
    }

    private fun httpBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AI-Rankboard/0.4")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for $url")
            return response.body?.bytes() ?: throw IllegalStateException("empty response for $url")
        }
    }

    private fun initialSnapshot(): Snapshot {
        readDownloaded()?.let { return it }
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
        content.trim().split(Regex("\\s+")).first().orEmpty()

    fun scheduleDailyUpdate(url: String, intervalDays: Int = 1) {
        if (url.isBlank()) return
        val safeDays = intervalDays.coerceIn(1, 7)
        val request = PeriodicWorkRequestBuilder<SnapshotUpdateWorker>(safeDays.toLong(), TimeUnit.DAYS)
            .setInputData(androidx.work.Data.Builder().putString(SnapshotUpdateWorker.KEY_URL, url).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun setDailyUpdateEnabled(enabled: Boolean, url: String, intervalDays: Int = 1) {
        if (enabled) {
            scheduleDailyUpdate(url)
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
    }
}
