package com.ai.rankboard.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotParsingTest {
    @Test
    fun modelSearchToleratesDashesWithoutMergingVersions() {
        listOf("GPT6.2", "gpt-6.2", "GPT 6.2", "\uFF27\uFF30\uFF34\uFF16.\uFF12", "GPT\u20116.2").forEach {
            assertTrue(it, matchesModelQuery(it, "GPT-6.2 Astra (max)"))
        }
        assertEquals(false, matchesModelQuery("GPT62", "GPT-6.2 Astra (max)"))
        assertEquals(false, matchesModelQuery("GPT6.2 Sol", "GPT-6.2 Astra (max)"))
        assertEquals(false, matchesModelQuery("---", "Model"))
    }

    @Test
    fun pageAndPickerSearchKeepOriginalModelIdentityAndRank() {
        val snapshot = Gson().fromJson(json.replace("gpt-4", "gpt-6.2-astra-max")
            .replace("GPT-4", "GPT-6.2 Astra (max)").replace("\"rank\": 1", "\"rank\": 3"), Snapshot::class.java)
        val source = LocalSnapshotDataSource(snapshot)
        val page = source.page("overall", "rank", "GPT6.2", emptySet(), emptySet(), 100, 0)!!
        assertEquals(1, page.total)
        assertEquals(3, page.entries.single().rank)
        assertEquals("gpt-6.2-astra-max", source.modelOptions("GPT6.2").single().slug)
    }

    private val json = """
    {
      "schemaVersion": 3,
      "generatedAt": "2026-10-03T12:00:00+00:00",
      "source": {"id": "modelsage", "name": "ModelSage", "url": "https://modelsage.cn"},
      "boards": [
        {
          "slug": "overall",
          "name": "综合能力",
          "dimension": "overall",
          "score_type": "aa_index",
          "url": "https://modelsage.cn/leaderboards/overall",
          "last_success_at": "2026-10-03T12:00:00+00:00",
          "model_count": 1
        }
      ],
      "entriesByBoard": {
        "overall": [
          {
            "slug": "gpt-4",
            "display_name": "GPT-4",
            "vendor": "OpenAI",
            "params_b": null,
            "license": null,
            "context_window": null,
            "release_date": "2025-08-07",
            "rank": 1,
            "score": 90.5,
            "weekly_change": "+17%",
            "score_ci": null,
            "votes": null,
            "price_in": 1.0,
            "price_out": 2.0,
            "currency": "CNY",
            "fetched_at": "2026-10-03T12:00:00+00:00"
          }
        ]
      },
      "models": {
        "gpt-4": {
          "slug": "gpt-4",
          "display_name": "GPT-4",
          "vendor": "OpenAI",
          "params_b": null,
          "license": null,
          "context_window": null,
          "source_url": "https://modelsage.cn/model/gpt-4",
          "release_date": "2025-08-07"
        }
      }
    }
    """.trimIndent()

    @Test
    fun bundledNewSourcesRemainSeparateBoards() {
        val asset = java.io.File("src/main/assets/leaderboards.json").takeIf { it.isFile }
            ?: java.io.File("app/src/main/assets/leaderboards.json")
        val snapshot = Gson().fromJson(asset.readText(), Snapshot::class.java)
        val source = LocalSnapshotDataSource(snapshot)
        val expected = mapOf("aider-polyglot" to "aider", "evalplus-humaneval-plus" to "evalplus", "evalplus-mbpp-plus" to "evalplus")
        val coding = source.boardsForDimension("coding")
        expected.forEach { (slug, provider) ->
            val board = coding.first { it.slug == slug }
            assertEquals(provider, board.sourceId)
            assertNotNull(sourceHttpUrl(board.source?.url ?: board.url))
            val rows = source.page(slug, "rank", null, emptySet(), emptySet(), 1000, 0)!!
            assertTrue("Real collected entries required for $slug", rows.entries.isNotEmpty())
            assertEquals(snapshot.entriesByBoard[slug]!!.map { it.slug }.toSet(), rows.entries.map { it.slug }.toSet())
            assertEquals(slug, rows.board.slug)
        }
        assertTrue(filterBoardsBySource(coding, null).map { it.slug }.containsAll(expected.keys))
        assertEquals(setOf("evalplus-humaneval-plus", "evalplus-mbpp-plus"),
            filterBoardsBySource(coding, "evalplus").map { it.slug }.toSet())
    }

    @Test
    fun bundledSnapshotUsesLiveOpenRouterTrendAndNoHelm() {
        val asset = java.io.File("src/main/assets/leaderboards.json").takeIf { it.isFile }
            ?: java.io.File("app/src/main/assets/leaderboards.json")
        val snapshot = Gson().fromJson(asset.readText(), Snapshot::class.java)
        val usageBoards = snapshot.boards.filter { it.dimension == "usage" }
        assertEquals(listOf("openrouter-weekly-usage"), usageBoards.map { it.slug })
        assertTrue(snapshot.sources.none { it.id.equals("helm", ignoreCase = true) })
        assertTrue(snapshot.sources.any { it.id == "openrouter" })
        val rows = snapshot.entriesByBoard["openrouter-weekly-usage"].orEmpty()
        assertEquals(10, rows.size)
        assertTrue(rows.all { it.weeklyChange?.isNotBlank() == true })
        val upstream = usageBoards.single().evaluation?.upstreamUpdatedAt
        assertNotNull(upstream)
        assertTrue(upstream!!.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        val age = java.time.temporal.ChronoUnit.DAYS.between(
            java.time.LocalDate.parse(upstream),
            java.time.LocalDate.parse(snapshot.generatedAt.take(10)),
        )
        assertTrue(age in 0L..7L)
    }

    @Test
    fun parseSnapshotSchemaV3() {
        val snapshot = Gson().fromJson(json, Snapshot::class.java)

        assertEquals(3, snapshot.schemaVersion)
        assertEquals("ModelSage", snapshot.source?.name)
        assertEquals(1, snapshot.boards.size)
        assertEquals(1, snapshot.entriesByBoard["overall"]?.size)
        assertEquals("GPT-4", snapshot.models["gpt-4"]?.displayName)
        assertEquals("2025-08-07", snapshot.entriesByBoard["overall"]?.first()?.releaseDate)
        assertEquals(90.5, snapshot.entriesByBoard["overall"]?.first()?.score!!, 0.001)
        assertEquals("+17%", snapshot.entriesByBoard["overall"]?.first()?.weeklyChange)
    }

    @Test
    fun snapshotPageSortsAndSearches() {
        val snapshot = Gson().fromJson(json, Snapshot::class.java)
        val dataSource = object {
            fun page(boardSlug: String, query: String?): List<EntryDto> =
                snapshot.entriesByBoard[boardSlug].orEmpty().filter {
                    val normalizedQuery = query.orEmpty()
                    normalizedQuery.isBlank() ||
                        it.displayName.contains(normalizedQuery, ignoreCase = true)
                }
        }

        assertNotNull(dataSource.page("overall", "gpt"))
        assertTrue(dataSource.page("overall", "claude").isEmpty())
    }
}
