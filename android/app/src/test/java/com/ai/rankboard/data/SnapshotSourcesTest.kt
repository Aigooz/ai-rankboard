package com.ai.rankboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotSourcesTest {

    private val rawUrl = "https://raw.githubusercontent.com/Aigooz/ai-rankboard/main/leaderboards.json"
    private val apiUrl = "https://api.github.com/repos/Aigooz/ai-rankboard/contents/leaderboards.json?ref=main"

    @Test fun apiContentsUrlNormalizesToRaw() {
        assertEquals(rawUrl, canonicalRawUrl(apiUrl))
        assertEquals(rawUrl, canonicalRawUrl(rawUrl))
    }

    @Test fun customUrlIsNeverRewrittenToMirrors() {
        val custom = "https://example.com/data/leaderboards.json"
        assertNull(canonicalRawUrl(custom))
        val candidates = snapshotSourceCandidates(custom)
        assertEquals(listOf(custom), candidates.map { it.url })
        assertEquals("自定义数据源", candidates.single().label)
    }

    @Test fun officialSourceProvidesMirrorsAndKeepsApiLast() {
        val candidates = snapshotSourceCandidates(apiUrl)
        assertEquals(rawUrl, candidates.first().url)
        assertTrue(candidates.any { it.host == "gh-proxy.com" })
        assertTrue(candidates.any { it.host == "ghfast.top" })
        assertTrue(candidates.any { it.host == "cdn.jsdelivr.net" })
        assertEquals("GitHub API", candidates.last().label)
        // 每个入口都必须能推出自己的校验文件地址。
        candidates.forEach { candidate ->
            val hashUrl = snapshotHashUrl(candidate.url)
            val path = hashUrl.substringBefore('?').substringBefore('#')
            assertTrue(path.endsWith(".sha256"))
        }
    }

    @Test fun preferredHostMovesToFrontWithoutDroppingSources() {
        val baseline = snapshotSourceCandidates(rawUrl)
        val reordered = snapshotSourceCandidates(rawUrl, preferredHost = "gh-proxy.com")
        assertEquals("gh-proxy.com", reordered.first().host)
        assertEquals(baseline.map { it.url }.toSet(), reordered.map { it.url }.toSet())
    }

    @Test fun unknownPreferredHostIsIgnored() {
        val baseline = snapshotSourceCandidates(rawUrl)
        val reordered = snapshotSourceCandidates(rawUrl, preferredHost = "mirror.example.com")
        assertEquals(baseline.map { it.url }, reordered.map { it.url })
    }

    @Test fun hashUrlKeepsQueryAndFragment() {
        assertEquals(
            "https://raw.githubusercontent.com/Aigooz/ai-rankboard/main/leaderboards.json.sha256",
            snapshotHashUrl(rawUrl),
        )
        assertEquals(
            "https://api.github.com/repos/o/r/contents/leaderboards.json.sha256?ref=dev#x",
            snapshotHashUrl("https://api.github.com/repos/o/r/contents/leaderboards.json?ref=dev#x"),
        )
    }

    @Test fun skipDownloadOnlyWhenHashMatches() {
        val hash = "ABCDEF".repeat(10) + "ABCD"
        assertTrue(shouldSkipSnapshotDownload(hash, hash.lowercase()))
        assertFalse(shouldSkipSnapshotDownload(hash, "0".repeat(64)))
        assertFalse(shouldSkipSnapshotDownload(null, hash))
        assertFalse(shouldSkipSnapshotDownload(hash, null))
        assertFalse(shouldSkipSnapshotDownload("  ", "  "))
    }

    @Test fun malformedHashIsRejected() {
        assertFalse(isSha256("1234"))
        assertFalse(isSha256("z".repeat(64)))
        assertTrue(isSha256("a".repeat(64)))
    }

    @Test fun cacheBustPreservesQueryAndFragment() {
        assertEquals(
            "https://example.com/data.json?ref=main&sync=123#part",
            withCacheBust("https://example.com/data.json?ref=main#part", 123),
        )
        assertEquals(
            "https://example.com/data.json?sync=123",
            withCacheBust("https://example.com/data.json", 123),
        )
    }

    @Test fun blankConfigurationYieldsNoCandidates() {
        assertTrue(snapshotSourceCandidates("   ").isEmpty())
    }
}
