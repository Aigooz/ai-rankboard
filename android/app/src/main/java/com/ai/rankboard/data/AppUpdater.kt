package com.ai.rankboard.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.Keep
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.ai.rankboard.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Keep
data class AppUpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val sha256: String = "",
    val notes: String = "",
    val sizeBytes: Long = 0L,
)

enum class AppUpdateStatus {
    AVAILABLE,
    UP_TO_DATE,
    DOWNLOADED,
    ERROR,
}

data class AppUpdateResult(
    val status: AppUpdateStatus,
    val message: String,
    val info: AppUpdateInfo? = null,
)

object AppUpdater {
    private const val APK_FILE_NAME = "update.apk"
    const val APK_FILE_PATH = "app_update/update.apk"

    fun canInstall(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun installPermissionIntent(context: Context): Intent {
        return Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun updateAndInstall(context: Context, url: String): AppUpdateResult {
        val prepared = prepareUpdate(context, url)
        if (prepared.status == AppUpdateStatus.DOWNLOADED) {
            context.startActivity(installIntent(context, apkFile(context)))
        }
        return prepared
    }

    suspend fun checkUpdate(context: Context, url: String): AppUpdateResult {
        if (url.isBlank()) {
            return AppUpdateResult(AppUpdateStatus.ERROR, "未配置应用更新地址")
        }
        if (!url.startsWith("https://", ignoreCase = true)) {
            return AppUpdateResult(AppUpdateStatus.ERROR, "应用更新地址必须使用 HTTPS")
        }

        return withContext(Dispatchers.IO) {
            try {
                val info = parseManifest(httpBytes(url)) ?: throw IllegalStateException("更新清单解析失败")
                if (info.versionCode <= BuildConfig.VERSION_CODE) {
                    AppUpdateResult(
                        AppUpdateStatus.UP_TO_DATE,
                        "应用已是最新版本 v${BuildConfig.VERSION_NAME}",
                    )
                } else if (info.apkUrl.isBlank() || !info.apkUrl.startsWith("https://", true)) {
                    throw IllegalStateException("更新清单缺少有效的 APK 地址")
                } else if (info.sha256.isBlank()) {
                    throw IllegalStateException("更新清单缺少 APK SHA-256")
                } else {
                    val sizeBytes = if (info.sizeBytes > 0) info.sizeBytes else remoteFileSize(info.apkUrl)
                    AppUpdateResult(
                        AppUpdateStatus.AVAILABLE,
                        "发现新版本 v${info.versionName.ifBlank { info.versionCode.toString() }}",
                        info.copy(sizeBytes = sizeBytes),
                    )
                }
            } catch (exc: Exception) {
                AppUpdateResult(AppUpdateStatus.ERROR, exc.message ?: "应用更新检查失败")
            }
        }
    }

    suspend fun downloadAndInstall(context: Context, info: AppUpdateInfo): AppUpdateResult {
        if (info.versionCode <= BuildConfig.VERSION_CODE) {
            return AppUpdateResult(
                AppUpdateStatus.UP_TO_DATE,
                "应用已是最新版本 v${BuildConfig.VERSION_NAME}",
            )
        }
        if (info.apkUrl.isBlank() || !info.apkUrl.startsWith("https://", ignoreCase = true)) {
            return AppUpdateResult(AppUpdateStatus.ERROR, "更新清单缺少有效的 APK 地址")
        }
        if (info.sha256.isBlank()) {
            return AppUpdateResult(AppUpdateStatus.ERROR, "更新清单缺少 APK SHA-256")
        }

        return withContext(Dispatchers.IO) {
            try {
                val apkFile = apkFile(context)
                apkFile.delete()
                val bytes = httpBytes(info.apkUrl)
                if (!sha256(bytes).equals(info.sha256, ignoreCase = true)) {
                    throw IllegalStateException("APK SHA-256 校验失败")
                }
                apkFile.writeBytes(bytes)

                val archiveInfo = context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    0,
                )
                val archiveVersionCode = archiveInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
                if (
                    archiveInfo == null ||
                    archiveInfo.packageName != context.packageName ||
                    archiveVersionCode <= BuildConfig.VERSION_CODE
                ) {
                    throw IllegalStateException("APK 包名或版本不匹配")
                }

                context.startActivity(installIntent(context, apkFile))
                AppUpdateResult(
                    AppUpdateStatus.DOWNLOADED,
                    "已下载 v${info.versionName.ifBlank { info.versionCode.toString() }}，请确认安装",
                    info,
                )
            } catch (exc: Exception) {
                AppUpdateResult(AppUpdateStatus.ERROR, exc.message ?: "应用更新失败")
            }
        }
    }

    suspend fun prepareUpdate(context: Context, url: String): AppUpdateResult {
        if (url.isBlank()) {
            return AppUpdateResult(AppUpdateStatus.ERROR, "未配置应用更新地址")
        }
        if (!url.startsWith("https://", ignoreCase = true)) {
            return AppUpdateResult(AppUpdateStatus.ERROR, "应用更新地址必须使用 HTTPS")
        }

        return withContext(Dispatchers.IO) {
            try {
                val info = parseManifest(httpBytes(url))
                    ?: throw IllegalStateException("更新清单解析失败")
                if (info.versionCode <= BuildConfig.VERSION_CODE) {
                    return@withContext AppUpdateResult(
                        AppUpdateStatus.UP_TO_DATE,
                        "应用已是最新版本 v${BuildConfig.VERSION_NAME}",
                    )
                }
                if (info.apkUrl.isBlank()) {
                    throw IllegalStateException("更新清单缺少 APK 地址")
                }
                if (!info.apkUrl.startsWith("https://", ignoreCase = true)) {
                    throw IllegalStateException("APK 地址必须使用 HTTPS")
                }
                if (info.sha256.isBlank()) {
                    throw IllegalStateException("更新清单缺少 APK SHA-256")
                }

                downloadAndInstall(context, info)
            } catch (exc: Exception) {
                AppUpdateResult(AppUpdateStatus.ERROR, exc.message ?: "应用更新失败")
            }
        }
    }

    fun installIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    private fun apkFile(context: Context): File {
        val dir = File(context.cacheDir, "app_update")
        dir.mkdirs()
        return File(dir, APK_FILE_NAME)
    }

    private fun parseManifest(bytes: ByteArray): AppUpdateInfo? {
        return runCatching {
            Gson().fromJson(bytes.decodeToString(), AppUpdateInfo::class.java)
        }.getOrNull()
    }

    private fun httpBytes(url: String): ByteArray {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AI-Rankboard-Updater/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $url")
            }
            return response.body?.bytes() ?: throw IllegalStateException("空响应：$url")
        }
    }

    private fun remoteFileSize(url: String): Long {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", "AI-Rankboard-Updater/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            return if (response.isSuccessful) {
                response.header("Content-Length")?.toLongOrNull() ?: 0L
            } else {
                0L
            }
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "未知大小"
        if (bytes < 1024L * 1024L) return "%.0f KB".format(bytes / 1024.0)
        if (bytes < 1024L * 1024L * 1024L) return "%.1f MB".format(bytes / (1024.0 * 1024.0))
        return "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
