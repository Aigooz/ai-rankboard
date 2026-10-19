package com.ai.rankboard.ui.trend

import java.net.URI

/** Validate the explicit external-original fallback; native reader never loads a web page. */
fun articleReaderUrl(value: String): String? = runCatching {
    val uri = URI("https://artificialanalysis.ai").resolve(value.trim()).normalize()
    if (uri.scheme != "https" || uri.host?.lowercase() !in setOf("artificialanalysis.ai", "www.artificialanalysis.ai") ||
        uri.rawUserInfo != null || uri.port !in setOf(-1, 443) || !uri.path.startsWith("/articles/") ||
        uri.path.removePrefix("/articles/").isBlank()) null else uri.toASCIIString()
}.getOrNull()
