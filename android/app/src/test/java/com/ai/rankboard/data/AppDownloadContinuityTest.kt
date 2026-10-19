package com.ai.rankboard.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source guard: downloading is Android-context-bound; prevent reintroducing the old speed abort. */
class AppDownloadContinuityTest {
    @Test fun lowSpeedIsOnlyProgressInformationNotAnAbortCondition() {
        val relative = "src/main/java/com/ai/rankboard/data/AppUpdater.kt"
        val source = (File(relative).takeIf { it.isFile } ?: File("app/$relative")).readText()
        assertFalse(source.contains("SlowDownloadException"))
        assertFalse(source.contains("下载源过慢"))
        assertFalse(source.contains("bytesPerSecond <"))
        assertTrue(source.contains(".readTimeout(60, TimeUnit.SECONDS)"))
        assertTrue(source.contains("onProgress(DownloadProgress(bytesRead, totalBytes, bytesPerSecond))"))
        assertTrue(source.contains("APK 下载不完整"))
        assertTrue(source.contains("APK SHA-256 校验失败"))
    }
}
