package com.ai.rankboard.ui.compare

import com.ai.rankboard.data.ModelDetailDto
import com.ai.rankboard.data.ModelDetailResponse
import com.ai.rankboard.data.ScoreDto
import org.junit.Assert.assertEquals
import org.junit.Test

class CompareRadarTest {
    @Test
    fun `axes follow priority and cap at six`() {
        val models = listOf(
            response(
                score("overall", "overall", 80.0, 5),
                score("coding", "coding", 70.0, 9),
                score("writing", "writing", null, 2, entryCount = 20),
                score("speed", "speed", 60.0, 12),
                score("value", "value", 55.0, 14),
                score("search", "search", 50.0, 16),
                score("analysis", "analysis", 45.0, 18),
            ),
        )

        val axes = selectCompareAxes(models)

        assertEquals(
            listOf("overall", "coding", "writing", "analysis", "search", "speed"),
            axes,
        )
    }

    @Test
    fun `dimensions without any rankable score are skipped`() {
        val models = listOf(
            response(
                score("overall", "overall", 80.0, 5),
                score("empty", "empty", null, 0),
            ),
        )

        assertEquals(listOf("overall"), selectCompareAxes(models))
    }

    @Test
    fun `missing dimension maps to near-center ratio`() {
        val model = response(score("overall", "overall", 80.0, 5))

        val ratios = modelAxisRatios(model, listOf("overall", "coding"))

        assertEquals(2, ratios.size)
        assertEquals(0.12f, ratios[1], 0.0001f)
    }

    @Test
    fun `best score in a dimension wins over rank fallback`() {
        val model = response(
            score("coding", "coding", 10.0, 30, scoreMin = 0.0, scoreMax = 100.0),
            score("coding-alt", "coding", null, 3, entryCount = 10),
        )

        // 10/100 -> 0.1 会被压到 0.12；票选百分位 (10-3+1)/10 = 0.8 更高。
        val ratios = modelAxisRatios(model, listOf("coding"))

        assertEquals(0.8f, ratios[0], 0.0001f)
    }

    private fun score(
        boardSlug: String,
        dimension: String,
        value: Double?,
        rank: Int,
        entryCount: Int = 50,
        scoreMin: Double? = null,
        scoreMax: Double? = null,
    ) = ScoreDto(
        boardSlug = boardSlug,
        boardName = boardSlug,
        dimension = dimension,
        scoreType = "score",
        rank = rank,
        score = value,
        scoreMin = scoreMin,
        scoreMax = scoreMax,
        entryCount = entryCount,
    )

    private fun response(vararg scores: ScoreDto) =
        ModelDetailResponse(ModelDetailDto(slug = "m", displayName = "M"), scores.toList())
}
