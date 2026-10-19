package com.ai.rankboard.ui.trend

import org.junit.Assert.*
import org.junit.Test

class ArticleReaderUrlTest {
    @Test fun nativeBodySplitsParagraphsAndKeepsLiteralText() {
        assertTrue(articleParagraphs(null).isEmpty())
        assertTrue(articleParagraphs("  \n\n ").isEmpty())
        assertEquals(listOf("First paragraph", "Second\nline", "<script>literal</script>"),
            articleParagraphs(" First paragraph\r\n\r\nSecond\nline\n\n\n<script>literal</script> "))
    }
    @Test fun optionalContentParsesWithoutInventingMissingBody() {
        val gson = com.google.gson.Gson()
        val old = gson.fromJson("""{"title":"Title","url":"/articles/x"}""", com.ai.rankboard.data.NewsArticleDto::class.java)
        assertNull(old.content)
        val body = gson.fromJson("""{"title":"Title","url":"/articles/x","published_at":"2026-10-19","content":"First\n\nSecond"}""", com.ai.rankboard.data.NewsArticleDto::class.java)
        assertEquals(listOf("First", "Second"), articleParagraphs(body.content))
        assertEquals("2026-10-19", body.publishedAt)
        val asset = java.io.File("src/main/assets/leaderboards.json").takeIf { it.isFile }
            ?: java.io.File("app/src/main/assets/leaderboards.json")
        val articles = gson.fromJson(asset.readText(), com.ai.rankboard.data.Snapshot::class.java).news!!.articles
        assertTrue(articles.any { articleParagraphs(it.content).size > 1 })
        assertTrue(articles.any { it.content == null })
    }
    @Test fun resolvesOfficialRelativeAndAbsoluteArticles() {
        assertEquals("https://artificialanalysis.ai/articles/example", articleReaderUrl("/articles/example"))
        assertEquals("https://artificialanalysis.ai/articles/example#section", articleReaderUrl("https://artificialanalysis.ai/articles/example#section"))
        assertNotNull(articleReaderUrl("https://www.artificialanalysis.ai/articles/example"))
    }
    @Test fun rejectsExternalSchemesHostsAndNonArticleRoutes() {
        listOf("", "javascript:alert(1)", "file:///articles/x", "intent://articles/x", "http://artificialanalysis.ai/articles/x",
            "https://evil.example/articles/x", "//evil.example/articles/x", "https://artificialanalysis.ai.evil.example/articles/x",
            "https://evil@artificialanalysis.ai/articles/x", "https://artificialanalysis.ai:8443/articles/x", "/articles/", "/articles/../login", "/leaderboard").forEach {
            assertNull(it, articleReaderUrl(it))
        }
    }
}
