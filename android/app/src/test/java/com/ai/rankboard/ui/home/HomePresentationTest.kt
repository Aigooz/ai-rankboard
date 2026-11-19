package com.ai.rankboard.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomePresentationTest {
    @Test
    fun `overview keeps original board ranks after filtering`() {
        assertEquals(listOf("#3", "#9", "#13"), listOf(3, 9, 13).map(::overviewRankLabel))
        assertEquals("#1", overviewRankLabel(1))
    }

    @Test
    fun `missing rank never becomes a first place label`() {
        assertEquals("未提供", overviewRankLabel(0))
        assertEquals("未提供", overviewRankLabel(-1))
    }
}
