package com.ai.rankboard.ui.detail

import com.ai.rankboard.data.*
import org.junit.Assert.*
import org.junit.Test

class DetailPresentationTest {
    private fun score(input: Double? = 2.0, output: Double? = 3.0, currency: String = "USD") =
        ScoreDto("overall", "Overall", "overall", "index", 1, priceIn = input, priceOut = output, currency = currency)
    @Test fun priceRejectsInvalidNegativeNonFiniteInputAndMissingSide() {
        listOf("", "abc", "-1", "NaN", "Infinity").forEach { assertTrue(estimatePrice(score(), it, "1").contains("非负有限")) }
        assertTrue(estimatePrice(score(output = null), "1", "1").contains("无法估算总价"))
        assertTrue(estimatePrice(score(input = -1.0), "1", "1").contains("未获取完整"))
        assertTrue(estimatePrice(score(), "0", "0").contains("0.0000 USD"))
    }
    @Test fun currencyDoesNotInventCnyAndKeepsRealZeroPrice() {
        assertTrue(estimatePrice(score(currency = "EUR"), "1", "2").contains("8.0000 EUR（未换汇）"))
        assertTrue(estimatePrice(score(currency = ""), "1", "2").contains("币种未提供"))
        assertTrue(formatModelPrice(score(input = 0.0, output = null)).contains("输入 0.0000 / 输出 未获取"))
        assertEquals("名次未获取", detailRankLabel(0))
        assertTrue(detailRankLabel(1).contains("仅本榜"))
    }
    @Test fun bundledMetadataOnlyModelsRemainNavigable() {
        val path = java.io.File("src/main/assets/leaderboards.json").takeIf { it.isFile }
            ?: java.io.File("app/src/main/assets/leaderboards.json")
        val snapshot = com.google.gson.Gson().fromJson(path.readText(), Snapshot::class.java)
        val entrySlugs = snapshot.entriesByBoard.values.flatten().map { it.slug }.toSet()
        val metadataOnly = snapshot.models.values.filter { it.slug !in entrySlugs }
        assertTrue(metadataOnly.isNotEmpty())
        val local = LocalSnapshotDataSource(snapshot)
        metadataOnly.forEach {
            assertEquals(it, local.modelDetail(it.slug)!!.model)
            assertTrue(local.modelDetail(it.slug)!!.scores.isEmpty())
        }
    }

    @Test fun modelOnlyMetadataAndFullVendorPoolAreUsable() {
        val model = ModelDetailDto("target", "Target", "Vendor", contextWindow = "1M")
        val models = (1..350).associate { "other$it" to ModelDetailDto("other$it", "Other $it", "Else") } +
            mapOf("target" to model, "related" to ModelDetailDto("related", "Related", "vendor"))
        val local = LocalSnapshotDataSource(Snapshot(models = models))
        assertEquals(model, local.modelDetail("target")!!.model)
        assertTrue(local.modelDetail("target")!!.scores.isEmpty())
        assertEquals(listOf("related"), local.vendorModels(model).map { it.slug })
        assertTrue(local.vendorModels(model.copy(vendor = " ")).isEmpty())
        listOf("Unknown", "UNKNOWN VENDOR", "未知", "未知厂商", "未获取", "n/a", "na", "-", "—").forEach {
            assertNull(realVendorKey(it))
            assertTrue(local.vendorModels(model.copy(vendor = it)).isEmpty())
        }
        assertNull(local.modelDetail("absent"))
    }
}
