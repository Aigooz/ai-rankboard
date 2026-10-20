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
    }

    @Test fun unknownSpecificationsAreSeparatedWithoutInventingReleaseDate() {
        val model = ModelDetailDto("test", "Test", contextWindow = "128K")
        val specs = detailSpecifications(model)
        assertEquals(listOf("上下文" to "128K"), specs.filter { it.second != null })
        assertNull(specs.first { it.first == "发布日期" }.second)
        assertEquals(3, specs.count { it.second == null })
    }
}
