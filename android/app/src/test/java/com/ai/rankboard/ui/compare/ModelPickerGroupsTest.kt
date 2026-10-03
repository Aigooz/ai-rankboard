package com.ai.rankboard.ui.compare

import com.ai.rankboard.data.ModelDetailDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPickerGroupsTest {
    @Test
    fun groupsModelsByVendorAndKeepsKnownVendorsFirst() {
        val groups = ModelPickerGroups.group(
            listOf(
                ModelDetailDto(slug = "zhipu-glm", displayName = "GLM", vendor = "智谱AI"),
                ModelDetailDto(slug = "openai-gpt", displayName = "GPT", vendor = "OpenAI"),
                ModelDetailDto(slug = "unknown-model", displayName = "Model", vendor = "Test Lab"),
                ModelDetailDto(slug = "blank-vendor", displayName = "Model 2", vendor = null),
            ),
        )

        assertEquals(listOf("OpenAI", "智谱AI", "Test Lab", "其他"), groups.map { it.vendor })
    }

    @Test
    fun matchesNameVendorOrSlug() {
        val model = ModelDetailDto(
            slug = "deepseek-v4",
            displayName = "DeepSeek V4",
            vendor = "DeepSeek",
        )

        assertEquals(true, ModelPickerGroups.matches(model, "deep"))
        assertEquals(true, ModelPickerGroups.matches(model, "V4"))
        assertEquals(false, ModelPickerGroups.matches(model, "claude"))
    }
}
