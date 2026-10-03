package com.ai.rankboard.ui.favorites

import com.ai.rankboard.data.ScoreDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteSummaryTest {
    @Test
    fun `prefers overall dimension`() {
        val summary = favoriteScoreSummary(
            listOf(
                score("coding", "coding", 3000.0, 2),
                score("overall", "overall", 71.5, 4),
            ),
        )

        assertEquals("#4 · 71.5", summary)
    }

    @Test
    fun `falls back to best rank when no overall`() {
        val summary = favoriteScoreSummary(
            listOf(
                score("coding", "coding", 3000.0, 5),
                score("arena-text", "writing", null, 3),
            ),
        )

        assertEquals("#3", summary)
    }

    @Test
    fun `returns null when nothing is rankable`() {
        assertNull(favoriteScoreSummary(emptyList()))
        assertNull(favoriteScoreSummary(listOf(score("overall", "overall", null, 0))))
    }

    private fun score(boardSlug: String, dimension: String, value: Double?, rank: Int) = ScoreDto(
        boardSlug = boardSlug,
        boardName = boardSlug,
        dimension = dimension,
        scoreType = "score",
        rank = rank,
        score = value,
    )
}
