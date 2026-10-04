package com.ai.rankboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePromptPolicyTest {
    @Test
    fun showsOnlyNewerUndismissedUpdate() {
        val policy = AppUpdatePromptPolicy()
        val info = AppUpdateInfo(versionCode = 55)

        assertTrue(policy.shouldShow(info, currentVersionCode = 54))
        assertFalse(policy.shouldShow(info, currentVersionCode = 55))
        assertFalse(policy.shouldShow(info, currentVersionCode = 56))
    }

    @Test
    fun dismissedVersionDoesNotShowAgain() {
        val policy = AppUpdatePromptPolicy()
        val info = AppUpdateInfo(versionCode = 55)
        policy.dismiss(55)

        assertFalse(policy.shouldShow(info, currentVersionCode = 54))

        val newerInfo = AppUpdateInfo(versionCode = 56)
        assertTrue(policy.shouldShow(newerInfo, currentVersionCode = 54))
    }

    @Test
    fun nullUpdateNeverShows() {
        val policy = AppUpdatePromptPolicy()

        assertFalse(policy.shouldShow(null, currentVersionCode = 54))
        assertEquals(0, AppUpdateInfo().versionCode)
    }
}
