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

        assertFalse(SnapshotStore.isSnapshotFresh(generatedAt, 1, now))
    }

    @Test
    fun `snapshot inside interval is fresh`() {
        val now = Instant.parse("2026-10-05T12:00:00Z")
        val generatedAt = "2026-10-05T01:00:00Z"

        assertTrue(SnapshotStore.isSnapshotFresh(generatedAt, 1, now))
        assertTrue(SnapshotStore.isSnapshotFresh(generatedAt, 7, now))
    }

    @Test
    fun `invalid generated time is not fresh`() {
        val now = Instant.parse("2026-10-05T12:00:00Z")

        assertFalse(SnapshotStore.isSnapshotFresh("", 1, now))
        assertFalse(SnapshotStore.isSnapshotFresh("not-a-date", 1, now))
    }
}
