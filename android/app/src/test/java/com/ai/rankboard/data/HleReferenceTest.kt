package com.ai.rankboard.data

import com.ai.rankboard.ui.common.hasHleReference
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class HleReferenceTest {
    @Test fun realSnapshotRetainsPublicReferenceWithoutHleScoreBoards() {
        val asset = java.io.File("src/main/assets/leaderboards.json").takeIf { it.isFile }
            ?: java.io.File("app/src/main/assets/leaderboards.json")
        val snapshot = Gson().fromJson(asset.readText(), Snapshot::class.java)
        val reference = snapshot.benchmarkReferences?.get("hle")!!
        assertEquals("CAIS / Scale AI", reference.publisher)
        assertTrue(hasHleReference(reference))
        assertEquals(1, reference.examples!!.size)
        assertEquals("text", reference.examples.first().modality)
        assertEquals("Ecology", reference.examples.first().category)
        assertNotNull(sourceHttpUrl(reference.examples.first().sourceUrl))
        assertTrue(reference.licenseNotice!!.contains("勿公开分享"))
        assertTrue(reference.trainingWarning!!.contains("NEVER APPEAR IN TRAINING CORPORA"))
        assertEquals(37, snapshot.boards.size)
        assertEquals(8, snapshot.boards.count { it.scoreType == "livebench_v2" && !it.historical })
        assertEquals(8, snapshot.boards.count { it.scoreType == "livebench" && it.historical })
        assertTrue(snapshot.boards.all { snapshot.entriesByBoard[it.slug].orEmpty().isNotEmpty() })
        assertFalse(snapshot.boards.any { it.sourceId == "hle" || it.slug.startsWith("hle-") })
        assertFalse(snapshot.entriesByBoard.keys.any { it.startsWith("hle-") })
        assertFalse(snapshot.news!!.articles.isEmpty())
        assertEquals(reference, LocalSnapshotDataSource(snapshot).benchmarkReference("hle"))
    }
    @Test fun absentReferenceStaysCompatibleWithoutRequiringAnyScoreBoard() {
        assertNull(LocalSnapshotDataSource(Snapshot()).benchmarkReference("hle"))
        assertFalse(hasHleReference(null))
        assertFalse(hasHleReference(BenchmarkReferenceDto(id = "other")))
        assertTrue(hasHleReference(BenchmarkReferenceDto(id = "hle")))
    }
}
