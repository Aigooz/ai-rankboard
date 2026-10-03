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
                if (parsed.schemaVersion !in 1..2 || parsed.boards.isEmpty()) {
                    throw IllegalStateException("unsupported snapshot schema ${parsed.schemaVersion}")
                }

                jsonFile.writeBytes(jsonBytes)
                hashFile.writeText("$expectedHash  $SNAPSHOT_FILE_NAME\n", Charsets.US_ASCII)
                verified = true
                _snapshot.value = parsed
                SnapshotUpdateResult(SnapshotUpdateStatus.UPDATED, "榜单数据已更新")
            } catch (exc: Exception) {
                SnapshotUpdateResult(SnapshotUpdateStatus.ERROR, exc.message ?: "快照更新失败")
            }
        }
    }

    private fun httpBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AI-Rankboard/0.2")
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

    fun scheduleDailyUpdate(url: String) {
        if (url.isBlank()) return
        val request = PeriodicWorkRequestBuilder<SnapshotUpdateWorker>(1, TimeUnit.DAYS)
            .setInputData(androidx.work.Data.Builder().putString(SnapshotUpdateWorker.KEY_URL, url).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val SNAPSHOT_FILE_NAME = "leaderboards.json"
        const val SNAPSHOT_HASH_FILE_NAME = "leaderboards.json.sha256"
        const val ASSET_SNAPSHOT_FILE = "leaderboards.json"
        const val ASSET_SNAPSHOT_HASH = "leaderboards.json.sha256"
        const val DAILY_UPDATE_WORK = "snapshot-daily-update"
    }
}
