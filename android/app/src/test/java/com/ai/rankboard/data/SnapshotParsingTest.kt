package com.ai.rankboard.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotParsingTest {
    private val json = """
    {
      "schemaVersion": 2,
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
            "rank": 1,
            "score": 90.5,
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
          "source_url": "https://modelsage.cn/model/gpt-4"
        }
      }
    }
    """.trimIndent()

    @Test
    fun parseSnapshotSchemaV2() {
        val snapshot = Gson().fromJson(json, Snapshot::class.java)

        assertEquals(2, snapshot.schemaVersion)
        assertEquals("ModelSage", snapshot.source?.name)
        assertEquals(1, snapshot.boards.size)
        assertEquals(1, snapshot.entriesByBoard["overall"]?.size)
        assertEquals("GPT-4", snapshot.models["gpt-4"]?.displayName)
        assertEquals(90.5, snapshot.entriesByBoard["overall"]?.first()?.score!!, 0.001)
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
