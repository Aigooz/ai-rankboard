package com.ai.rankboard.data

import com.ai.rankboard.ui.common.isHleBoard
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class HleReferenceTest {
    @Test fun realSnapshotContainsOnlyPublicTextExampleAndSeparateBoards() {
        val asset = java.io.File("src/main/assets/leaderboards.json").takeIf { it.isFile }
            ?: java.io.File("app/src/main/assets/leaderboards.json")
        val snapshot = Gson().fromJson(asset.readText(), Snapshot::class.java)
        val reference = snapshot.benchmarkReferences?.get("hle")!!
        assertEquals("CAIS / Scale AI", reference.publisher)
        assertEquals(1, reference.examples!!.size)
        assertEquals("text", reference.examples.first().modality)
        assertEquals("Ecology", reference.examples.first().category)
        assertNotNull(sourceHttpUrl(reference.examples.first().sourceUrl))
        assertTrue(reference.licenseNotice!!.contains("勿公开分享"))
        assertTrue(reference.trainingWarning!!.contains("NEVER APPEAR IN TRAINING CORPORA"))
        val local = LocalSnapshotDataSource(snapshot)
        assertEquals(9, snapshot.entriesByBoard["hle-official-2025-04"]!!.size)
        assertEquals(8, snapshot.entriesByBoard["hle-diamond-no-tools-high"]!!.size)
        val boards = local.boardsForDimension("reasoning").filter(::isHleBoard)
        assertEquals(2, boards.size)
        boards.forEach { assertTrue(metricExplanation(it.scoreType).contains("不能混比")) }
        assertEquals(reference, local.benchmarkReference("hle"))
    }
    @Test fun absentReferenceAndNonHleBoardsStayCompatible() {
        assertNull(LocalSnapshotDataSource(Snapshot()).benchmarkReference("hle"))
        assertFalse(isHleBoard(null))
        assertFalse(isHleBoard(BoardDto("overall", "Overall", "overall")))
        assertTrue(isHleBoard(BoardDto("hle-x", "Long title", "reasoning", sourceId = "hle")))
        assertTrue(isHleBoard(BoardDto("x", "X", "reasoning", scoreType = "hle_diamond")))
    }
}
