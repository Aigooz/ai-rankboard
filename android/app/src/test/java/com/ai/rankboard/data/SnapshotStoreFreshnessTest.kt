package com.ai.rankboard.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SnapshotStoreFreshnessTest {
    @Test
    fun `snapshot older than interval is not fresh`() {
        val now = Instant.parse("2026-10-05T12:00:00Z")
        val generatedAt = "2026-10-04T11:00:00Z"

        assertFalse(SnapshotStore.isSnapshotFresh(generatedAt, 24, now))
    }

    @Test
    fun `snapshot inside interval is fresh`() {
        val now = Instant.parse("2026-10-05T12:00:00Z")
        val generatedAt = "2026-10-05T01:00:00Z"

        assertTrue(SnapshotStore.isSnapshotFresh(generatedAt, 24, now))
        assertTrue(SnapshotStore.isSnapshotFresh(generatedAt, 168, now))
    }

    @Test
    fun `invalid generated time is not fresh`() {
        val now = Instant.parse("2026-10-05T12:00:00Z")

        assertFalse(SnapshotStore.isSnapshotFresh("", 24, now))
        assertFalse(SnapshotStore.isSnapshotFresh("not-a-date", 24, now))
    }

    @Test
    fun `check due uses last check time rather than snapshot timestamp`() {
        val hour = 60L * 60L * 1000L

        assertTrue(SnapshotStore.isCheckDue(0L, 6, nowMillis = 1_000L))
        assertFalse(SnapshotStore.isCheckDue(1_000L, 6, nowMillis = 1_000L + 5 * hour))
        assertTrue(SnapshotStore.isCheckDue(1_000L, 6, nowMillis = 1_000L + 6 * hour))
    }

    @Test
    fun `failed sync does not postpone the next retry`() {
        assertTrue(SnapshotStore.shouldRecordCheckTime(SnapshotUpdateStatus.UPDATED))
        assertTrue(SnapshotStore.shouldRecordCheckTime(SnapshotUpdateStatus.UP_TO_DATE))
        assertFalse(SnapshotStore.shouldRecordCheckTime(SnapshotUpdateStatus.ERROR))
        assertFalse(SnapshotStore.shouldRecordCheckTime(SnapshotUpdateStatus.SKIPPED))
    }
}
