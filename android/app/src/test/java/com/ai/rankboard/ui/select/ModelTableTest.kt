package com.ai.rankboard.ui.select

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTableTest {
    @Test
    fun `sort by price puts free first and unpriced last`() {
        val rows = filterAndSortModels(
            listOf(
                model("pricey", priceCny = 30.0, overall = 90.0),
                model("free", priceCny = 0.0, overall = 60.0),
                model("unpriced", priceCny = null, overall = 99.0),
                model("cheap", priceCny = 0.5, overall = 50.0),
            ),
            ModelTableSort.PRICE,
            openness = 0,
        )

        assertEquals(listOf("free", "cheap", "pricey", "unpriced"), rows.map { it.slug })
    }

    @Test
    fun `sort by intelligence falls back to coding score`() {
        val rows = filterAndSortModels(
            listOf(
                model("a", priceCny = 1.0, overall = null, coding = 70.0),
                model("b", priceCny = 1.0, overall = 80.0, coding = null),
            ),
            ModelTableSort.INTELLIGENCE,
            openness = 0,
        )

        assertEquals(listOf("b", "a"), rows.map { it.slug })
    }

    @Test
    fun `openness filter splits open and proprietary`() {
        val models = listOf(
            model("open-model", priceCny = 1.0, license = "Apache 2.0"),
            model("closed-model", priceCny = 1.0, license = null),
        )

        assertEquals(
            listOf("open-model"),
            filterAndSortModels(models, ModelTableSort.INTELLIGENCE, openness = 1).map { it.slug },
        )
        assertEquals(
            listOf("closed-model"),
            filterAndSortModels(models, ModelTableSort.INTELLIGENCE, openness = 2).map { it.slug },
        )
        assertTrue(filterAndSortModels(models, ModelTableSort.INTELLIGENCE, openness = 0).size == 2)
    }

    private fun model(
        slug: String,
        priceCny: Double?,
        overall: Double? = null,
        coding: Double? = null,
        license: String? = null,
    ) = ScatterModel(
        slug = slug,
        displayName = slug,
        vendor = null,
        priceCny = priceCny,
        scores = buildMap {
            overall?.let { put("overall", it) }
            coding?.let { put("coding", it) }
        },
        license = license,
    )
}
