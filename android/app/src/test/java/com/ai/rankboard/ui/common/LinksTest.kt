package com.ai.rankboard.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test
    fun `browser links allow only clean http and https addresses`() {
        assertTrue(isSafeBrowserUrl("https://example.com/a"))
        assertTrue(isSafeBrowserUrl(" HTTP://example.com/a "))
        assertFalse(isSafeBrowserUrl("javascript:alert(1)"))
        assertFalse(isSafeBrowserUrl("intent://example.com"))
        assertFalse(isSafeBrowserUrl("file:///data/local/tmp/x"))
        assertFalse(isSafeBrowserUrl("https://user:password@example.com/x"))
        assertFalse(isSafeBrowserUrl("https:///missing-host"))
        assertFalse(isSafeBrowserUrl("https://example.com/bad path"))
        assertFalse(isSafeBrowserUrl("https:\\example.com"))
        assertFalse(isSafeBrowserUrl(null))
    }
}
