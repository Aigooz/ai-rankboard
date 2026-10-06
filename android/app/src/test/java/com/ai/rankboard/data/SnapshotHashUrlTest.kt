package com.ai.rankboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotHashUrlTest {
    @Test
    fun `contents api query stays on the checksum url`() {
        assertEquals(
            "https://api.github.com/repos/Aigooz/ai-rankboard/contents/leaderboards.json.sha256?ref=main",
            SnapshotStore.hashUrl(
                "https://api.github.com/repos/Aigooz/ai-rankboard/contents/leaderboards.json?ref=main",
            ),
        )
    }

    @Test
    fun `raw url appends checksum extension`() {
        assertEquals(
            "https://raw.githubusercontent.com/Aigooz/ai-rankboard/main/leaderboards.json.sha256",
            SnapshotStore.hashUrl("https://raw.githubusercontent.com/Aigooz/ai-rankboard/main/leaderboards.json"),
        )
    }
}
