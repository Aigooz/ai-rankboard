package com.ai.rankboard.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateMonitorTest {
    private val info = AppUpdateInfo(versionCode = 67, versionName = "0.20.17")
    private val available = AppUpdateResult(AppUpdateStatus.AVAILABLE, "new version", info)

    @Test
    fun manualCheckWorksWithRemindersDisabled() = runBlocking {
        var calls = 0
        val monitor = AppUpdateMonitor({ calls++; available }, 66) { 123L }
        monitor.start(false, "")

        assertNull(monitor.checkNow(false, "official"))
        assertEquals(0, calls)
        assertEquals(available, monitor.checkNow(false, "official", manual = true))
        assertEquals(info, monitor.pendingUpdate.value)
        assertEquals(123L, monitor.state.value.checkedAt)
        assertFalse(monitor.state.value.checking)
    }

    @Test
    fun manualCheckReopensDismissedVersionButAutomaticCheckDoesNot() = runBlocking {
        val monitor = AppUpdateMonitor({ available }, 66)
        monitor.checkNow(true, "official")
        monitor.dismiss(info)
        monitor.checkNow(true, "official")
        assertNull(monitor.pendingUpdate.value)

        monitor.checkNow(false, "official", manual = true)
        assertEquals(info, monitor.pendingUpdate.value)
    }

    @Test
    fun latestVersionClearsOldPrompt() = runBlocking {
        var result = available
        val monitor = AppUpdateMonitor({ result }, 66)
        monitor.checkNow(true, "official")
        result = AppUpdateResult(AppUpdateStatus.UP_TO_DATE, "latest")
        monitor.checkNow(true, "official")
        assertNull(monitor.pendingUpdate.value)
        assertEquals(result, monitor.state.value.result)
    }

    @Test
    fun networkErrorIsVisibleAndRetryCanSucceed() = runBlocking {
        var result = AppUpdateResult(AppUpdateStatus.ERROR, "offline")
        val monitor = AppUpdateMonitor({ result }, 66)
        monitor.checkNow(true, "official")
        assertEquals(AppUpdateStatus.ERROR, monitor.state.value.result?.status)
        assertFalse(monitor.state.value.checking)
        assertNull(monitor.pendingUpdate.value)
        result = available
        monitor.checkNow(true, "official")
        assertEquals(info, monitor.pendingUpdate.value)
    }

    @Test
    fun disablingRemindersDuringCheckPreventsLateAutomaticPrompt() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<AppUpdateResult>()
        val monitor = AppUpdateMonitor({ started.complete(Unit); response.await() }, 66)
        val request = async { monitor.checkNow(true, "official") }
        started.await()
        assertTrue(monitor.state.value.checking)
        monitor.start(false, "official")
        response.complete(available)
        request.await()
        assertNull(monitor.pendingUpdate.value)
        assertEquals(available, monitor.state.value.result)
    }

    @Test
    fun cancelledCheckReleasesBusyStateForRetry() = runBlocking {
        var cancel = true
        val monitor = AppUpdateMonitor({
            if (cancel) throw CancellationException("cancelled")
            available
        }, 66)
        try {
            monitor.checkNow(true, "official")
        } catch (_: CancellationException) {
            // A cancelled foreground check must not leave settings permanently disabled.
        }
        assertFalse(monitor.state.value.checking)
        cancel = false
        monitor.checkNow(true, "official")
        assertEquals(info, monitor.pendingUpdate.value)
    }

    @Test
    fun changingUpdateSourceDiscardsOldResult() = runBlocking {
        val monitor = AppUpdateMonitor({ available }, 66)
        monitor.start(false, "old-source")
        monitor.checkNow(false, "old-source", manual = true)
        assertEquals(info, monitor.pendingUpdate.value)
        monitor.start(false, "new-source")
        assertNull(monitor.pendingUpdate.value)
        assertNull(monitor.state.value.result)
        monitor.checkNow(true, "old-source")
        assertNull(monitor.state.value.result)
    }
}
