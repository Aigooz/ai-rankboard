package com.ai.rankboard.ui.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompareTableTest {
    @Test
    fun `bestScoreIndex picks highest and first on tie`() {
        assertEquals(1, bestScoreIndex(listOf(70.0, 88.5, 12.0)))
        assertEquals(0, bestScoreIndex(listOf(90.0, 90.0)))
    }

    @Test
    fun `bestScoreIndex returns null when all missing`() {
        assertNull(bestScoreIndex(listOf(null, null)))
        assertNull(bestScoreIndex(emptyList()))
    }

    @Test
    fun `bestRankIndex picks smallest positive rank`() {
        assertEquals(2, bestRankIndex(listOf(0, 3, 1)))
        assertNull(bestRankIndex(listOf(0, null)))
    }

    @Test
    fun `bestPriceIndex ignores missing and zero prices`() {
        assertEquals(2, bestPriceIndex(listOf(null, 0.0, 1.2, 3.4)))
        assertNull(bestPriceIndex(listOf(null, 0.0)))
    }

    @Test
    fun `formatDelta keeps explicit sign and one decimal`() {
        assertEquals("+2.3", formatDelta(2.34))
        assertEquals("-1.8", formatDelta(-1.76))
        assertEquals("+0.0", formatDelta(0.0))
    }
}
