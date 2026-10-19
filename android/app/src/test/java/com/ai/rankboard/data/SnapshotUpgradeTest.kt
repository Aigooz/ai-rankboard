package com.ai.rankboard.data

import org.junit.Assert.*
import org.junit.Test

class SnapshotUpgradeTest {
    @Test fun apkUpgradeInvalidatesEvenFreshChecksumValidDownloadedSnapshot() {
        assertTrue(SnapshotStore.shouldDiscardDownloadedSnapshot(70, 71))
        assertTrue(SnapshotStore.shouldDiscardDownloadedSnapshot(71, 72))
        // Legacy installs predate the preference key: missing record defaults to zero.
        assertTrue(SnapshotStore.shouldDiscardDownloadedSnapshot(0, 71))
    }
    @Test fun sameVersionRetainsDownloadedDataAndDoesNotResetOnEveryLaunch() {
        assertFalse(SnapshotStore.shouldDiscardDownloadedSnapshot(71, 71))
        assertFalse(SnapshotStore.shouldDiscardDownloadedSnapshot(72, 71))
        // Successful startup/download records current code, making the next launch stable.
        val current = 72
        var recorded = 71
        assertTrue(SnapshotStore.shouldDiscardDownloadedSnapshot(recorded, current))
        recorded = current
        assertFalse(SnapshotStore.shouldDiscardDownloadedSnapshot(recorded, current))
    }
}
