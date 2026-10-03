package com.ai.rankboard.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreColorTest {
    @Test
    fun normalizedScoreUsesBoardRangeInsteadOfAbsoluteScale() {
        assertEquals(0f, normalizedScoreRatio(900.0, 900.0, 4000.0))
        assertEquals(1f, normalizedScoreRatio(4000.0, 900.0, 4000.0))
        assertEquals(0.5f, normalizedScoreRatio(2450.0, 900.0, 4000.0))
    }
}
