package com.ai.rankboard.ui.detail

import com.ai.rankboard.data.ScoreDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarChartTest {
    @Test
    fun `selects one score per dimension and limits chart to six axes`() {
        val scores = listOf(
            score("overall-1", "overall", 90.0, 12),
            score("overall-2", "overall", 70.0, 20),
            score("coding", "coding", 3000.0, 3),
            score("writing", "writing", 60.0, 7),
            score("multimodal", "multimodal", 80.0, 9),
            score("agent", "agent", 70.0, 10),
            score("math", "math", 50.0, 15),
            score("search", "search", 40.0, 18),
        )

        val selected = selectRadarScores(scores)

        assertEquals(6, selected.size)
        assertEquals(listOf("overall", "coding", "writing", "multimodal", "agent", "math"), selected.map { it.dimension })
    }

    @Test
    fun `uses rank as percentile fallback when range is unavailable`() {
        val score = score("coding", "coding", null, 3, entryCount = 10)

        assertEquals(0.8f, radarRatio(score))
    }

    private fun score(
        slug: String,
        dimension: String,
        value: Double?,
        rank: Int,
        entryCount: Int = 20,
    ) = ScoreDto(
        boardSlug = slug,
        boardName = slug,
        dimension = dimension,
        scoreType = "test",
        rank = rank,
        score = value,
        scoreMin = value?.minus(10.0),
        scoreMax = value?.plus(10.0),
        entryCount = entryCount,
    )
}
