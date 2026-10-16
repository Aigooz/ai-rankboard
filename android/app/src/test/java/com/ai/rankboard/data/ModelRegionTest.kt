package com.ai.rankboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRegionTest {
    @Test
    fun `domestic vendors support snapshot aliases`() {
        listOf(
            "Alibaba", "Alibaba Qwen", "Alibaba-ATH", "Qwen", "阿里百炼", "DeepSeek",
            "Z.ai", "Zhipu AI", "智谱AI", "Bytedance", "火山引擎/豆包", "Tencent", "腾讯混元",
            "Moonshot AI", "Moonshot/Kimi", "MiniMax", "Xiaomi", "小米", "StepFun",
            "Baidu", "百度千帆", "KlingAI", "Meituan", "Ant Group", "HiDream",
        ).forEach { vendor ->
            assertEquals(vendor, ModelRegion.DOMESTIC, modelRegion(vendor))
        }
    }

    @Test
    fun `international vendors support snapshot aliases and mixed casing`() {
        listOf(
            "OpenAI", "Anthropic", "Google", "Google DeepMind", "SpaceXAI", "xAI",
            "Mistral", "Meta", "Nvidia", "Microsoft AI", "Amazon", "Ai2", "IBM",
            "Black Forest Labs", "Perplexity AI", "Upstage", "  oPeNaI  ",
        ).forEach { vendor ->
            assertEquals(vendor, ModelRegion.INTERNATIONAL, modelRegion(vendor))
        }
    }

    @Test
    fun `unknown vendors are not silently classified as international`() {
        listOf(null, "", "  ", "-", "Unknown Labs", "Thinky", "Not OpenAI").forEach { vendor ->
            assertNull(modelRegion(vendor))
            assertTrue(ModelRegion.ALL.matches(vendor))
            assertFalse(ModelRegion.DOMESTIC.matches(vendor))
            assertFalse(ModelRegion.INTERNATIONAL.matches(vendor))
        }
    }

    private val entries = listOf(
        entry("cn-open", "DeepSeek", 1, "MIT", "2026-10-01", 95.0),
        entry("us-open", "Meta", 2, "MIT", "2026-10-02", 90.0),
        entry("cn-closed", "Alibaba Qwen", 3, "proprietary", "2026-09-01", 85.0),
        entry("us-closed", "OpenAI", 4, "proprietary", "2026-10-03", 80.0),
        entry("unknown", null, 5, "MIT", "2026-10-01", 75.0),
        entry("cn-metadata", null, 6, "MIT", "2026-10-04", 70.0),
    )
    private val source = LocalSnapshotDataSource(
        Snapshot(
            boards = listOf(BoardDto("overall", "Overall", "overall")),
            entriesByBoard = mapOf("overall" to entries),
            models = mapOf(
                "cn-metadata" to ModelDetailDto("cn-metadata", "cn-metadata", vendor = "智谱AI"),
            ),
        ),
    )

    @Test
    fun `region filtering happens before pagination and score bounds`() {
        val first = page(region = ModelRegion.DOMESTIC, limit = 2)
        val second = page(region = ModelRegion.DOMESTIC, limit = 2, offset = 2)

        assertEquals(listOf("cn-open", "cn-closed"), first.entries.map { it.slug })
        assertEquals(listOf("cn-metadata"), second.entries.map { it.slug })
        assertEquals(3, first.total)
        assertEquals(3, second.total)
        assertEquals(70.0, first.scoreMin!!, 0.001)
        assertEquals(95.0, first.scoreMax!!, 0.001)
        assertEquals(listOf(1, 3), first.entries.map { it.rank })
    }

    @Test
    fun `international excludes unknown vendors and all restores every model`() {
        assertEquals(listOf("us-open", "us-closed"), page(ModelRegion.INTERNATIONAL).entries.map { it.slug })
        assertEquals(entries.size, page(ModelRegion.ALL).total)
    }

    @Test
    fun `region combines with search vendor and license`() {
        val result = page(
            region = ModelRegion.DOMESTIC,
            query = "cn",
            vendor = setOf("deepseek"),
            license = setOf("open"),
        )
        assertEquals(listOf("cn-open"), result.entries.map { it.slug })
        assertEquals(1, result.total)
        assertEquals(0, page(ModelRegion.DOMESTIC, vendor = setOf("OpenAI")).total)
        assertEquals(0, page(ModelRegion.INTERNATIONAL, query = "not-found").total)
    }

    @Test
    fun `region combines with release period and sorting`() {
        val result = page(ModelRegion.DOMESTIC, periodDimension = Periods.MONTH, period = "2026-10", sort = "updated")
        assertEquals(listOf("cn-metadata", "cn-open"), result.entries.map { it.slug })
        assertEquals(2, result.total)
    }

    @Test
    fun `region uses model metadata when board vendor is missing`() {
        val metadataEntry = page(ModelRegion.DOMESTIC).entries.first { it.slug == "cn-metadata" }
        assertEquals("智谱AI", metadataEntry.vendor)
    }

    private fun page(
        region: ModelRegion,
        query: String? = null,
        vendor: Set<String> = emptySet(),
        license: Set<String> = emptySet(),
        limit: Int = 50,
        offset: Int = 0,
        periodDimension: String? = null,
        period: String? = null,
        sort: String = "rank",
    ): EntriesResponse = requireNotNull(source.page(
        boardSlug = "overall",
        sort = sort,
        query = query,
        vendor = vendor,
        license = license,
        limit = limit,
        offset = offset,
        periodDimension = periodDimension,
        period = period,
        region = region,
    ))

    private fun entry(slug: String, vendor: String?, rank: Int, license: String, date: String, score: Double) =
        EntryDto(slug, slug, vendor = vendor, rank = rank, license = license,
            releaseDate = date, fetchedAt = date, score = score)
}
