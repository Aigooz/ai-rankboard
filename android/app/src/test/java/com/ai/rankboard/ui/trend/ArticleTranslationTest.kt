package com.ai.rankboard.ui.trend

import org.junit.Assert.*
import org.junit.Test

class ArticleTranslationTest {
    @Test fun chunksRespectLimitAndPreserveEveryCharacter() {
        val text = "Some sentence. ".repeat(100)
        val chunks = translationChunks(text)
        assertTrue(chunks.all { it.length <= 500 })
        assertEquals(text, chunks.joinToString(""))
        assertTrue(translationChunks("").isEmpty())
        assertEquals(listOf("short"), translationChunks("short"))
    }
    @Test fun chunksDoNotSplitSurrogatePairs() {
        val text = "a".repeat(499) + "😀" + "b".repeat(600)
        val chunks = translationChunks(text)
        assertTrue(chunks.all { it.length <= 500 })
        assertTrue(chunks.none { it.last().isHighSurrogate() || it.first().isLowSurrogate() })
        assertEquals(text, chunks.joinToString(""))
    }
    @Test fun translationResponseRequiresActualSuccessAndNonemptyText() {
        assertEquals("你好", parseTranslationResponse("""{"responseStatus":200,"responseData":{"translatedText":"你好"}}"""))
        listOf("""{"responseStatus":429,"responseData":{"translatedText":"quota error"}}""",
            """{"responseStatus":200,"responseData":{"translatedText":""}}""", "{}").forEach {
            assertTrue(runCatching { parseTranslationResponse(it) }.isFailure)
        }
    }
}
