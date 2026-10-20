package com.ai.rankboard.data

internal fun realVendorKey(vendor: String?): String? = vendor?.trim()?.lowercase(java.util.Locale.ROOT)
    ?.takeUnless { it in setOf("", "unknown", "unknown vendor", "未知", "未知厂商", "未获取", "n/a", "na", "-", "—") }
