package com.ai.rankboard.data

class LocalSnapshotDataSource(private val store: SnapshotStore) {
    private val snapshot get() = store.snapshot

    fun info(): SnapshotInfo = store.info()

    fun boardsForDimension(dimension: String): List<BoardDto> =
        snapshot.boards.filter { it.dimension == dimension }

    fun vendorOptions(boardSlug: String): List<String> =
        snapshot.entriesByBoard[boardSlug].orEmpty()
            .mapNotNull { it.vendor?.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedWith(compareByDescending { it.length })
            .take(12)

    fun page(
        boardSlug: String,
        sort: String,
        query: String?,
        vendor: String?,
        license: String?,
        params: String?,
        limit: Int,
        offset: Int,
    ): EntriesResponse? {
        val board = snapshot.boards.firstOrNull { it.slug == boardSlug } ?: return null
        val keyword = query?.trim().orEmpty()
        val entries = snapshot.entriesByBoard[boardSlug].orEmpty().filter { entry ->
            keyword.isBlank() ||
                entry.displayName.contains(keyword, ignoreCase = true) ||
                entry.vendor?.contains(keyword, ignoreCase = true) == true ||
                entry.slug.contains(keyword, ignoreCase = true)
        }
            .filter { entry ->
                vendor.isNullOrBlank() || entry.vendor.equals(vendor, ignoreCase = true)
            }
            .filter { entry ->
                when (license) {
                    "open" -> entry.license?.contains("open", ignoreCase = true) == true ||
                        entry.license?.contains("apache", ignoreCase = true) == true ||
                        entry.license?.contains("mit", ignoreCase = true) == true
                    "proprietary" -> entry.license.isNullOrBlank() ||
                        !(entry.license.contains("open", ignoreCase = true) ||
                            entry.license.contains("apache", ignoreCase = true) ||
                            entry.license.contains("mit", ignoreCase = true))
                    else -> true
                }
            }
            .filter { entry ->
                when (params) {
                    "small" -> entry.paramsB != null && entry.paramsB <= 10.0
                    "medium" -> entry.paramsB != null && entry.paramsB > 10.0 && entry.paramsB <= 100.0
                    "large" -> entry.paramsB != null && entry.paramsB > 100.0
                    else -> true
                }
            }
        val sorted = when (sort) {
            "score" -> entries.sortedWith(compareByDescending { it.score ?: Double.NEGATIVE_INFINITY })
            "updated" -> entries.sortedByDescending { it.fetchedAt }
            else -> entries.sortedBy { it.rank }
        }
        return EntriesResponse(
            board = board,
            entries = sorted.drop(offset).take(limit),
            total = sorted.size,
            limit = limit,
            offset = offset,
        )
    }

    fun modelDetail(slug: String): ModelDetailResponse? {
        val firstEntry = snapshot.entriesByBoard.values.flatten().firstOrNull { it.slug == slug }
            ?: return null
        val model = snapshot.models[slug] ?: ModelDetailDto(
            slug = firstEntry.slug,
            displayName = firstEntry.displayName,
            vendor = firstEntry.vendor,
            paramsB = firstEntry.paramsB,
            license = firstEntry.license,
            contextWindow = firstEntry.contextWindow,
            sourceUrl = firstEntry.sourceUrl,
        )
        val scores = snapshot.boards.mapNotNull { board ->
            val entry = snapshot.entriesByBoard[board.slug]?.firstOrNull { it.slug == slug }
                ?: return@mapNotNull null
            ScoreDto(
                boardSlug = board.slug,
                boardName = board.name,
                dimension = board.dimension,
                scoreType = board.scoreType.orEmpty(),
                rank = entry.rank,
                score = entry.score,
                scoreCi = entry.scoreCi,
                votes = entry.votes,
                priceIn = entry.priceIn,
                priceOut = entry.priceOut,
                currency = entry.currency,
                fetchedAt = entry.fetchedAt,
            )
        }
        return ModelDetailResponse(model, scores)
    }
}
