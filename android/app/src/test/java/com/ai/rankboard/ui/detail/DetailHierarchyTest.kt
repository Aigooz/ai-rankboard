package com.ai.rankboard.ui.detail

import com.ai.rankboard.data.ModelDetailDto
import com.ai.rankboard.data.ScoreDto
import org.junit.Assert.*
import org.junit.Test

class DetailHierarchyTest {
    private fun score(slug: String, value: Double?) = ScoreDto(slug, slug, "overall", "index", 5, score = value)

    @Test fun referencePrefersOverallNotLargestCrossBoardScore() {
        val arena = score("arena", 1800.0)
        val overall = score("overall", 72.0)
        assertEquals(overall, primaryDetailScore(listOf(arena, overall)))
        assertEquals(arena, primaryDetailScore(listOf(arena)))
        assertNull(primaryDetailScore(listOf(score("overall", null), score("invalid", Double.NaN))))
        assertNull(primaryDetailScore(listOf(ScoreDto(
            "openrouter-weekly-usage", "Usage", "usage", "openrouter_usage", 1, score = 62.1,
        ))))
        assertEquals("coding", primaryDetailScore(listOf(
            ScoreDto("openrouter-weekly-usage", "Usage", "usage", "openrouter_usage", 1, score = 62.1),
            ScoreDto("coding", "Coding", "coding", "index", 2, score = 73.0),
        ))?.boardSlug)
    }

    @Test fun usageScoresShowUnitAndNeverUseAbilityRangeCopy() {
        val usage = ScoreDto(
            "openrouter-weekly-usage", "OpenRouter 周 token 使用趋势", "usage",
            "openrouter_usage", 1, score = 62.1,
        )
        assertTrue(usageScore(usage))
        assertEquals("62.1 万亿 tokens/周", usageScoreValue(usage))
        assertFalse(usageScore(score("overall", 72.0)))
    }

    @Test fun unknownSpecificationsAreSeparatedWithoutInventingReleaseDate() {
        val model = ModelDetailDto("test", "Test", contextWindow = "128K")
        val specs = detailSpecifications(model)
        assertEquals(listOf("上下文" to "128K"), specs.filter { it.second != null })
        assertNull(specs.first { it.first == "发布日期" }.second)
        assertEquals(3, specs.count { it.second == null })
    }
}
