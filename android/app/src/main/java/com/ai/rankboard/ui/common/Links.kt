package com.ai.rankboard.ui.common

import android.content.Intent
import android.net.Uri
import java.net.URI

internal fun isSafeBrowserUrl(raw: String?): Boolean = runCatching {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return@runCatching false
    val uri = URI(value)
    uri.scheme?.lowercase() in setOf("http", "https") &&
        !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null &&
        uri.rawAuthority?.contains('@') == false &&
        value.none { it.isWhitespace() || it == '\\' }
}.getOrDefault(false)

internal fun safeBrowserUrl(raw: String?): Uri? {
    if (!isSafeBrowserUrl(raw)) return null
    return Uri.parse(raw!!.trim())
}

fun openUrl(context: android.content.Context, url: String?): Boolean {
    val uri = safeBrowserUrl(url) ?: return false
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    }.getOrDefault(false)
}
