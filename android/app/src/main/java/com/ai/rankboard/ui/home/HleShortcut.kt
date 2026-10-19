package com.ai.rankboard.ui.home

import com.ai.rankboard.data.BoardDto

internal fun hleShortcut(boards: List<BoardDto>): BoardDto? = boards.firstOrNull { it.slug == "hle-official-2025-04" }
    ?: boards.firstOrNull { it.sourceId == "hle" && it.dimension == "reasoning" }

internal fun prioritizeBoards(boards: List<BoardDto>, dimension: String): List<BoardDto> = boards.sortedWith(
    compareBy<BoardDto> {
        when {
            dimension == "reasoning" && it.slug == "hle-official-2025-04" -> 0
            dimension == "reasoning" && it.sourceId == "hle" -> 1
            it.sourceId == "modelsage" -> 2
            else -> 3
        }
    },
)
