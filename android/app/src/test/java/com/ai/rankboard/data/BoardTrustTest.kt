package com.ai.rankboard.data

import com.google.gson.Gson
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class BoardTrustTest {
    private val now = Instant.parse("2026-10-19T00:00:00Z")
    @Test fun compactStatusKeepsFailuresAndAgeWithoutLongExplanation() {
        val board = BoardDto("x", "X", "coding", lastSuccessAt = "2026-10-18T00:00:00Z")
        assertEquals("抓取 2026-10-18", boardCompactStatus(board, now))
        val failed = board.copy(health = BoardHealthDto(latestAttemptStatus = "error", stale = true))
        assertTrue(boardCompactStatus(failed, now).startsWith("抓取失败 · 可能过期"))
        assertTrue(boardCompactStatus(board.copy(lastSuccessAt = null), now).contains("未知"))
        assertFalse(boardCompactStatus(board, now).contains("上游"))
    }
    @Test fun sourceFilteringAcceptsNewAndUnknownProvidersWithoutMixingBoards() {
        val boards = listOf("modelsage", "aider", "evalplus", "future-source").map {
            BoardDto(it, it, "coding", sourceId = it)
        }
        assertEquals(4, filterBoardsBySource(boards, null).size)
        assertEquals(listOf("aider"), filterBoardsBySource(boards, "aider").map { it.slug })
        assertEquals(listOf("evalplus"), filterBoardsBySource(boards, "evalplus").map { it.slug })
        assertEquals(4, boards.size) // filtering must not discard complete source options
        assertEquals("future-source", boardSourceLabel(boards.last()))
        assertEquals("Official name", boardSourceLabel(boards.last().copy(source = SnapshotSourceDto(name = "Official name"))))
        assertTrue(metricExplanation("evalplus").contains("不能"))
        assertTrue(metricExplanation("aider_polyglot").contains("通过率"))
    }
    @Test fun safeSourceLinksOnly() {
        assertEquals("https://livebench.ai/leaderboard", sourceHttpUrl(" https://livebench.ai/leaderboard "))
        listOf(null, "", "javascript:alert(1)", "file:///etc/passwd", "https:///no-host", "/local").forEach {
            assertNull(sourceHttpUrl(it))
        }
    }
    @Test fun ageDoesNotUseSnapshotGeneration() {
        assertTrue(collectionAgeMessage("2026-10-16T00:00:00Z", now).contains("可能过期"))
        assertTrue(collectionAgeMessage(null, now).contains("缺失"))
        assertTrue(collectionAgeMessage("nonsense", now).contains("无效"))
        assertTrue(collectionAgeMessage("2026-10-20T00:00:00Z", now).contains("晚于"))
        assertTrue(collectionAgeMessage("2026-10-18T00:00:00Z", now).contains("不代表上游"))
    }
    @Test fun optionalMetadataOldAndNewSnapshots() {
        val old = Gson().fromJson("""{"slug":"x","name":"X","dimension":"coding","last_success_at":"2026-10-16T00:00:00Z"}""", BoardDto::class.java)
        assertNull(old.health)
        assertTrue(boardHealthLines(old, now).any { it.contains("未提供") })
        assertTrue(boardHealthLines(old, now).any { it.contains("可能过期") })
        val new = Gson().fromJson("""{"slug":"x","name":"X","dimension":"coding","source_id":"modelsage","health":{"latestAttemptStatus":"error","error":"timeout","lastSuccessAt":"2026-10-18T00:00:00Z"},"evaluation":{"version":"2026-10","versionSource":"registry","upstreamUpdatedAt":null},"metric":{"description":"definition"}}""", BoardDto::class.java)
        assertEquals("2026-10", new.evaluation?.version)
        assertEquals("definition", new.metric?.description)
        assertTrue(boardHealthLines(new, now).any { it.contains("抓取失败") && it.contains("timeout") })
        assertTrue(boardHealthLines(new.copy(health = new.health!!.copy(stale = true, staleAfterHours = 24)), now)
            .any { it.contains("来源快照标记") && it.contains("24") })
    }
    @Test fun metricTypesExplainedWithoutAssumingUnifiedScale() {
        assertTrue(metricExplanation("arena_elo").contains("不是正确率"))
        assertTrue(metricExplanation("swe_bench").contains("解决率"))
        assertTrue(metricExplanation("livebench").contains("版本"))
        assertTrue(metricExplanation(null).contains("未提供"))
    }
    private fun point(at: String, version: String? = "v1", rank: Int = 2, score: Double = 50.0) =
        BoardHistoryDto(fetchedAt = at, evaluationVersion = version, entries = listOf(BoardHistoryEntryDto("x", "X", rank, score)))
    @Test fun historyNeedsDistinctTimesAndKnownMatchingVersions() {
        val one = point("2026-10-17T00:00:00Z")
        assertTrue(historyChangeLines(emptyList()).isEmpty())
        assertTrue(historyChangeLines(listOf(one, one)).isEmpty())
        assertTrue(historyChangeLines(listOf(one, point("2026-10-18T00:00:00Z", null))).single().contains("版本未知"))
        assertTrue(historyChangeLines(listOf(one, point("2026-10-18T00:00:00Z", "v2"))).single().contains("已切换"))
        val changed = historyChangeLines(listOf(point("2026-10-18T00:00:00Z", rank = 1, score = 55.0), one))
        assertTrue(changed.any { it.contains("名次上升 1") && it.contains("+5.00") })
        assertTrue(changed.any { it.contains("截断样本") })
    }
    @Test fun missingHistorySamplesDoNotImplyNoChange() {
        val before = point("2026-10-17T00:00:00Z").copy(entries = listOf(BoardHistoryEntryDto("x")))
        val after = point("2026-10-18T00:00:00Z").copy(entries = listOf(BoardHistoryEntryDto("y")))
        assertTrue(historyChangeLines(listOf(before, after)).single().contains("没有共同"))
        assertTrue(historyChangeLines(listOf(before, after.copy(entries = listOf(BoardHistoryEntryDto("x"))))).single().contains("缺少可比较"))
    }
    @Test fun snapshotParsesOptionalHistoryAndNullCollections() {
        val snapshot = Gson().fromJson("""{"schemaVersion":3,"historyByBoard":{"x":[{"fetchedAt":"2026-10-17T00:00:00Z","evaluationVersion":"v1","entries":[{"slug":"x","display_name":"X","rank":1,"score":50}]}]}}""", Snapshot::class.java)
        assertEquals("X", snapshot.historyByBoard?.get("x")?.first()?.entries?.first()?.displayName)
        assertTrue(historyChangeLines(listOf(BoardHistoryDto(entries = null))).isEmpty())
    }
}
