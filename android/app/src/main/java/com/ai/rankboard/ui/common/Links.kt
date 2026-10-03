package com.ai.rankboard.ui.common

import android.content.Intent
import android.net.Uri

fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
