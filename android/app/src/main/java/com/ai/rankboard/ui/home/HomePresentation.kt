package com.ai.rankboard.ui.home

internal fun overviewRankLabel(rank: Int): String =
    if (rank > 0) "#$rank" else "未提供"
