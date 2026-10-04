package com.ai.rankboard.ui.trend

import com.ai.rankboard.data.EntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendViewModelTest {
    @Test
    fun `name map uses overall entry slugs so details can be opened`() {
        val entries = listOf(
            entry("glm-5-3-flash", "GLM 5.3 Flash"),
            entry("gpt-5-6-luna-max", "GPT-5.6 Luna (max)"),
        )

        val map = TrendViewModel.nameToSlug(entries)

        assertEquals("glm-5-3-flash", map["GLM 5.3 Flash"])
        assertEquals("gpt-5-6-luna-max", map["GPT-5.6 Luna (max)"])
    }

    @Test
    fun `slugFor matches hyphenated and bracketed usage names`() {
        val entries = listOf(
            entry("glm-5-3-flash", "GLM 5.3 Flash"),
            entry("gpt-5-6-luna-max", "GPT-5.6 Luna (max)"),
            entry("deepseek-v4-1-flash-max", "Deepseek V4.1 Flash (Max)"),
        )
        val map = TrendViewModel.nameToSlug(entries)

        assertEquals("glm-5-3-flash", map[TrendViewModel.baseNameKey("GLM-5.3-Flash")])
        assertEquals("gpt-5-6-luna-max", map[TrendViewModel.baseNameKey("GPT-5.6 Luna (max)")])
        assertEquals(
            "deepseek-v4-1-flash-max",
            map[TrendViewModel.baseNameKey("DeepSeek V4.1 Flash (max)")],
        )
        assertNull(map["space-bunny-alpha"])
    }

    private fun entry(slug: String, displayName: String) = EntryDto(
        slug = slug,
        displayName = displayName,
    )
}
