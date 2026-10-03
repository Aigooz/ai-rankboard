package com.ai.rankboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayModelRankingTest {
    private val snapshot = Snapshot(
        generatedAt = "2026-10-03T12:00:00+00:00",
        entriesByBoard = mapOf(
            "overall" to listOf(
                EntryDto(
                    slug = "anthropic-claude-opus-5-5",
                    displayName = "Claude Opus 5.5 (max with fallback)",
                    vendor = "Anthropic",
                    rank = 1,
                    score = 100.0,
                ),
                EntryDto(
                    slug = "openai-gpt-6-astra",
                    displayName = "GPT-6 Astra (max)",
                    vendor = "OpenAI",
                    rank = 7,
                    score = 91.4,
                ),
                EntryDto(
                    slug = "openai-gpt-6-astra-high",
                    displayName = "GPT-6 Astra (high)",
                    vendor = "OpenAI",
                    rank = 15,
                    score = 89.0,
                ),
            ),
        ),
    )

    @Test
    fun `rank keeps global rank and puts unmatched models last`() {
        val ranking = RelayModelRanking.rank(
            listOf(
                RelayRemoteModel("private-unknown-model"),
                RelayRemoteModel("anthropic/claude-opus-5.5-max-fallback"),
                RelayRemoteModel("gpt-6-astra"),
            ),
            snapshot,
        )

        assertEquals(3, ranking.total)
        assertEquals(2, ranking.matched)
        assertEquals("Claude Opus 5.5 (max with fallback)", ranking.models.first().displayName)
        assertEquals(1, ranking.models.first().globalRank)
        assertEquals("GPT-6 Astra (max)", ranking.models[1].displayName)
        assertEquals(7, ranking.models[1].globalRank)
        assertEquals("private-unknown-model", ranking.models.last().remoteId)
    }

    @Test
    fun `explicit strength matches its own ranking variant`() {
        val ranking = RelayModelRanking.rank(
            listOf(RelayRemoteModel("gpt-6-astra-high")),
            snapshot,
        )

        assertEquals("openai-gpt-6-astra-high", ranking.models.single().matchedSlug)
        assertEquals(15, ranking.models.single().globalRank)
    }

    @Test
    fun `parses openai compatible response`() {
        val json = """
        {
          "object": "list",
          "data": [
            {"id": "gpt-6-astra", "owned_by": "openai"},
            {"id": "claude-opus-5.5", "owned_by": "anthropic"},
            {"id": "gpt-6-astra", "owned_by": "openai"}
          ]
        }
        """.trimIndent()

        val models = RelayModelClient.parseModels(json)
        assertEquals(2, models.size)
        assertEquals("gpt-6-astra", models.first().id)
        assertEquals("openai", models.first().ownedBy)
    }
}
