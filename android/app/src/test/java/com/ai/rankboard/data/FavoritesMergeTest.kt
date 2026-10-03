package com.ai.rankboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesMergeTest {
    @Test
    fun `pending add appends and pending remove filters out`() {
        val merged = mergeFavoriteSlugs(
            base = listOf("a", "b", "c"),
            pending = mapOf("d" to true, "b" to false),
        )

        assertEquals(listOf("a", "c", "d"), merged)
    }

    @Test
    fun `remove wins when same slug is toggled twice`() {
        val merged = mergeFavoriteSlugs(
            base = listOf("a"),
            pending = mapOf("a" to false, "a" to true),
        )
        assertEquals(listOf("a"), merged)
    }

    @Test
    fun `empty base and pending yields empty`() {
        assertEquals(emptyList<String>(), mergeFavoriteSlugs(emptyList(), emptyMap()))
    }

    @Test
    fun `no pending keeps base order`() {
        assertEquals(listOf("x", "y"), mergeFavoriteSlugs(listOf("x", "y"), emptyMap()))
    }
}
