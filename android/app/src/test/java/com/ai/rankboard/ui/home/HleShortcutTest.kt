package com.ai.rankboard.ui.home

import com.ai.rankboard.data.BoardDto
import org.junit.Assert.*
import org.junit.Test

class HleShortcutTest {
    private val ordinary = BoardDto("reasoning", "Reasoning", "reasoning", "modelsage")
    private val official = BoardDto("hle-official-2025-04", "HLE", "reasoning", "hle")
    private val diamond = BoardDto("hle-diamond-no-tools-high", "Diamond", "reasoning", "hle")
    @Test fun officialShortcutIsPreferredAndMissingHleHasNoEntry() {
        assertEquals(official, hleShortcut(listOf(diamond, ordinary, official)))
        assertEquals(diamond, hleShortcut(listOf(diamond, ordinary)))
        assertNull(hleShortcut(listOf(ordinary)))
        assertNull(hleShortcut(emptyList()))
    }
    @Test fun reasoningListsHleFirstWithoutChangingOtherDimensionsOrMembership() {
        val input = listOf(diamond, ordinary, official)
        assertEquals(listOf(official, diamond, ordinary), prioritizeBoards(input, "reasoning"))
        assertEquals(listOf(ordinary, diamond, official), prioritizeBoards(input, "overall"))
        assertEquals(input.toSet(), prioritizeBoards(input, "reasoning").toSet())
    }
}
