package com.ai.rankboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class PriceTiersTest {
    @Test
    fun `blended price is three-to-one mix in cny`() {
        val cny = entry(priceIn = 2.0, priceOut = 4.0, currency = "CNY")
        assertEquals(2.5, cny.blendedPriceCny()!!, 1e-9)

        val usd = entry(priceIn = 1.0, priceOut = 2.0, currency = "USD")
        assertEquals(((1.0 * 7.2) * 3 + 2.0 * 7.2) / 4.0, usd.blendedPriceCny()!!, 1e-9)
    }

    @Test
    fun `blended price falls back to the available side`() {
        assertEquals(3.0, entry(priceIn = 3.0, priceOut = null).blendedPriceCny()!!, 1e-9)
        assertEquals(8.0, entry(priceIn = null, priceOut = 8.0).blendedPriceCny()!!, 1e-9)
        assertNull(entry(priceIn = null, priceOut = null).blendedPriceCny())
    }

    @Test
    fun `tier boundaries follow labels`() {
        assertEquals(PRICE_FREE, priceTierOf(0.0))
        assertEquals(PRICE_LT1, priceTierOf(0.5))
        assertEquals(PRICE_1TO5, priceTierOf(1.0))
        assertEquals(PRICE_5TO10, priceTierOf(5.0))
        assertEquals(PRICE_10PLUS, priceTierOf(99.0))
    }

    @Test
    fun `cheapest by tier picks lowest blended price per band`() {
        val picks = cheapestByTier(
            listOf(
                entry("flagship-cheap", score = 88.0, priceIn = 1.0, priceOut = 2.0),
                entry("flagship-pricey", score = 90.0, priceIn = 30.0, priceOut = 60.0),
                entry("main-1", score = 75.0, priceIn = 0.5, priceOut = 1.0),
                entry("main-2", score = 72.0, priceIn = 2.0, priceOut = 4.0),
                entry("unpriced", score = 76.0, priceIn = null, priceOut = null),
                entry("noscore", priceIn = 0.1, priceOut = 0.2),
            ),
        )

        val labels = picks.map { it.label }
        assertEquals(listOf("旗舰", "主力"), labels)

        assertEquals("flagship-cheap", picks[0].models.first().slug)
        assertEquals("main-1", picks[1].models.first().slug)
        assertEquals(2, picks[1].models.size)
        assertEquals(1.25, picks[0].cheapestPrice!!, 1e-9)
        assertEquals(37.5, picks[0].highestPrice!!, 1e-9)
    }

    @Test
    fun `value picks prefer source score and fall back to score per price`() {
        val source = bestValuePicks(
            listOf(
                entry("paid", score = 90.0, priceIn = 10.0, priceOut = 20.0),
                entry("free", score = 80.0, priceIn = 0.0, priceOut = 0.0),
                entry("unpriced", score = 99.0, priceIn = null, priceOut = null),
            ),
            isSourceBoard = true,
        )

        assertTrue(source.isSourceBoard)
        assertEquals(listOf("paid", "free"), source.models.map { it.slug })

        val fallback = bestValuePicks(
            listOf(
                entry("expensive", score = 90.0, priceIn = 40.0, priceOut = 40.0),
                entry("cheap-strong", score = 85.0, priceIn = 1.0, priceOut = 1.0),
                entry("cheap-weak", score = 30.0, priceIn = 0.2, priceOut = 0.2),
            ),
            isSourceBoard = false,
        )

        assertEquals(listOf("cheap-strong", "cheap-weak", "expensive"), fallback.models.map { it.slug })
    }

    @Test
    fun `scenario recommendation gives strong low cost alternatives`() {
        val result = scenarioRecommendation(
            listOf(
                entry("top", score = 90.0, priceIn = 40.0, priceOut = 40.0),
                entry("near", score = 82.0, priceIn = 2.0, priceOut = 2.0),
                entry("weak-cheap", score = 60.0, priceIn = 0.1, priceOut = 0.1),
            ),
            slug = "coding",
        )!!

        assertEquals("top", result.strengthPick.slug)
        assertEquals(listOf("near"), result.valuePicks.map { it.slug })
        assertEquals(20.0, result.savingRatio!!, 1e-9)
    }

    @Test
    fun `price distribution buckets models and keeps unpriced last`() {
        val rows = priceDistribution(
            listOf(
                entry(priceIn = 0.0, priceOut = 0.0),
                entry(priceIn = 0.5, priceOut = 0.5),
                entry(priceIn = 20.0, priceOut = 20.0),
                entry(priceIn = 20.0, priceOut = 25.0),
                entry(priceIn = null, priceOut = null),
            ),
        )

        assertEquals(listOf("免费", "<¥1", "≥¥10", "未公开价格"), rows.map { it.label })
        assertEquals(listOf(1, 1, 2, 1), rows.map { it.count })
    }

    private fun entry(
        slug: String = "m",
        score: Double? = null,
        priceIn: Double?,
        priceOut: Double?,
        currency: String = "CNY",
    ) = EntryDto(
        slug = slug,
        displayName = slug,
        rank = 1,
        score = score,
        priceIn = priceIn,
        priceOut = priceOut,
        currency = currency,
    )
}
