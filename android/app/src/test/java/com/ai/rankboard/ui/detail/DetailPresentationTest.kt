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
        val model = snapshot.models.values.first()
        val metadataOnlySnapshot = snapshot.copy(entriesByBoard = snapshot.entriesByBoard.mapValues { (_, entries) ->
            entries.filterNot { it.slug == model.slug }
        })
        val local = LocalSnapshotDataSource(metadataOnlySnapshot)
        assertEquals(model, local.modelDetail(model.slug)!!.model)
        assertTrue(local.modelDetail(model.slug)!!.scores.isEmpty())
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

    @Test fun detailScoresKeepEachBoardsOwnRangeAndMetadataOnlyModels() {
        val boards = listOf(
            BoardDto("overall", "Overall", "overall"),
            BoardDto("arena", "Arena", "chat"),
            BoardDto("empty", "Empty", "coding"),
        )
        val snapshot = Snapshot(
            boards = boards,
            entriesByBoard = mapOf(
                "overall" to listOf(
                    EntryDto("target", "Target", rank = 2, score = 70.0),
                    EntryDto("other", "Other", rank = 1, score = 90.0),
                    EntryDto("no-score", "No score"),
                ),
                "arena" to listOf(EntryDto("target", "Target", rank = 1, score = 1500.0)),
            ),
            models = mapOf("target" to ModelDetailDto("target", "Target")),
        )
        val result = LocalSnapshotDataSource(snapshot).modelDetail("target")!!
        assertEquals(listOf("overall", "arena"), result.scores.map { it.boardSlug })
        assertEquals(70.0, result.scores[0].score)
        assertEquals(70.0, result.scores[0].scoreMin)
        assertEquals(90.0, result.scores[0].scoreMax)
        assertEquals(2, result.scores[0].entryCount)
        assertEquals(1500.0, result.scores[1].scoreMin)
        assertEquals(1500.0, result.scores[1].scoreMax)
        assertEquals(1, result.scores[1].entryCount)
    }

    @Test fun recentReleaseLookupUsesOverallBoardScoreWithoutRequiringBoardEntry() {
        val released = java.time.LocalDate.now().minusDays(1).toString()
        val models = mapOf(
            "target" to ModelDetailDto("target", "Target", releaseDate = released),
            "metadata" to ModelDetailDto("metadata", "Metadata", releaseDate = released),
        )
        val local = LocalSnapshotDataSource(Snapshot(
            models = models,
            entriesByBoard = mapOf("overall" to listOf(EntryDto("target", "Target", rank = 4, score = 81.5))),
        ))
        val recent = local.recentlyReleasedModels(days = 30)
        assertEquals(2, recent.size)
        val target = recent.first { it.slug == "target" }
        assertEquals(4, target.rank)
        assertEquals(81.5, target.score)
        assertNull(recent.first { it.slug == "metadata" }.score)
    }
}
