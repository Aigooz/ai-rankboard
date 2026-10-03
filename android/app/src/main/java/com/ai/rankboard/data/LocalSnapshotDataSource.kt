package com.ai.rankboard.data

class LocalSnapshotDataSource(private val store: SnapshotStore) {
    private val snapshot get() = store.snapshot

    private fun EntryDto.withModelMetadata(): EntryDto {
        val model = snapshot.models[slug] ?: return this
        return copy(
            vendor = vendor?.takeIf { it.isNotBlank() } ?: model.vendor?.takeIf { it.isNotBlank() },
            paramsB = paramsB ?: model.paramsB,
            license = license?.takeIf { it.isNotBlank() } ?: model.license?.takeIf { it.isNotBlank() },
            contextWindow = contextWindow?.takeIf { it.isNotBlank() }
                ?: model.contextWindow?.takeIf { it.isNotBlank() },
            releaseDate = releaseDate ?: model.releaseDate,
            sourceUrl = sourceUrl?.takeIf { it.isNotBlank() } ?: model.sourceUrl,
        )
    }

    fun boards(): List<BoardDto> = snapshot.boards

    fun info(): SnapshotInfo = store.info()

    fun boardsForDimension(dimension: String): List<BoardDto> =
        snapshot.boards.filter { it.dimension == dimension }

    fun vendorOptions(
        boardSlug: String,
        periodDimension: String? = null,
        period: String? = null,
    ): List<String> {
        val baseEntries = if (periodDimension in Periods.supported && !period.isNullOrBlank()) {
            snapshot.entriesByBoard[boardSlug].orEmpty()
                .filter { Periods.matches(periodDimension!!, period, it.releaseDate) }
        } else {
            snapshot.entriesByBoard[boardSlug].orEmpty()
        }
        return baseEntries
            .map { it.withModelMetadata() }
            .mapNotNull { it.vendor?.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedWith(compareByDescending { it.length })
            .take(40)
    }

    fun periodOptions(dimension: String): List<PeriodOption> = Periods.options(
        dimension,
        snapshot.entriesByBoard["overall"].orEmpty().mapNotNull { it.releaseDate },
    )

    fun page(
        boardSlug: String,
        sort: String,
        query: String?,
        vendor: Set<String>,
        license: Set<String>,
        params: Set<String>,
        limit: Int,
        offset: Int,
        periodDimension: String? = null,
        period: String? = null,
        price: Set<String> = emptySet(),
    ): EntriesResponse? {
        val board = snapshot.boards.firstOrNull { it.slug == boardSlug } ?: return null
        val baseEntries = snapshot.entriesByBoard[boardSlug].orEmpty()
            .map { it.withModelMetadata() }
        val filteredByPeriod = if (periodDimension in Periods.supported && !period.isNullOrBlank()) {
            baseEntries.filter { Periods.matches(periodDimension!!, period, it.releaseDate) }
        } else {
            baseEntries
        }
        val keyword = query?.trim().orEmpty()
        val rankedByPeriod = if (periodDimension in Periods.supported) {
            scoreRanked(filteredByPeriod)
        } else {
            filteredByPeriod
        }
        val entries = rankedByPeriod.filter { entry ->
            keyword.isBlank() ||
                entry.displayName.contains(keyword, ignoreCase = true) ||
                entry.vendor?.contains(keyword, ignoreCase = true) == true ||
                entry.slug.contains(keyword, ignoreCase = true)
        }
            .filter { entry ->
                vendor.isEmpty() || vendor.any { entry.vendor.equals(it, ignoreCase = true) }
            }
            .filter { entry ->
                license.isEmpty() || license.any { entry.matchesLicense(it) }
            }
            .filter { entry ->
                params.isEmpty() || params.any { entry.matchesParams(it) }
            }
            .filter { entry ->
                entry.matchesPriceTiers(price)
            }
        val sorted = sortEntries(entries, sort)
        return EntriesResponse(
            board = board,
            entries = sorted.drop(offset).take(limit),
            total = sorted.size,
            limit = limit,
            offset = offset,
            scoreMin = sorted.mapNotNull { it.score }.minOrNull(),
            scoreMax = sorted.mapNotNull { it.score }.maxOrNull(),
        )
    }

    private fun scoreRanked(entries: List<EntryDto>): List<EntryDto> =
        entries.sortedWith(compareByDescending { it.score ?: Double.NEGATIVE_INFINITY })
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }

    private fun EntryDto.matchesLicense(value: String): Boolean = when (value) {
        "open" -> isLikelyOpenSource()
        "proprietary" -> license.isNullOrBlank() || !isLikelyOpenSource()
        else -> true
    }

    private fun EntryDto.isLikelyOpenSource(): Boolean {
        val licenseText = license?.lowercase() ?: return false
        return OPEN_SOURCE_MARKERS.any { licenseText.contains(it) }
    }

    private fun EntryDto.matchesParams(value: String): Boolean = when (value) {
        "small" -> paramsB != null && paramsB <= 10.0
        "medium" -> paramsB != null && paramsB > 10.0 && paramsB <= 100.0
        "large" -> paramsB != null && paramsB > 100.0
        else -> true
    }

    private fun sortEntries(entries: List<EntryDto>, sort: String): List<EntryDto> = when (sort) {
        "score" -> entries.sortedByDescending { it.score ?: Double.NEGATIVE_INFINITY }
        "updated" -> entries.sortedByDescending { it.fetchedAt }
        else -> entries.sortedWith(
            compareBy<EntryDto> { if (it.rank > 0) it.rank else Int.MAX_VALUE }
                .thenByDescending { it.score ?: Double.NEGATIVE_INFINITY },
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
                releaseDate = firstEntry.releaseDate,
        )
        val scores = snapshot.boards.mapNotNull { board ->
            val entry = snapshot.entriesByBoard[board.slug]?.firstOrNull { it.slug == slug }
                ?: return@mapNotNull null
            val boardScores = snapshot.entriesByBoard[board.slug].orEmpty()
                .mapNotNull { it.score }
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
                scoreMin = boardScores.minOrNull(),
                scoreMax = boardScores.maxOrNull(),
                entryCount = boardScores.size,
            )
        }
        return ModelDetailResponse(model, scores)
    }

    /** 按发布日期取最近 N 天内上新的模型，附带综合榜成绩，供首页"最近上新"展示。 */
    fun recentlyReleasedModels(days: Int = 30, limit: Int = 12): List<EntryDto> {
        val cutoff = java.time.LocalDate.now().minusDays(days.toLong())
        return snapshot.models.values.mapNotNull { model ->
            val date = model.releaseDate?.take(10)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val release = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: return@mapNotNull null
            if (release.isBefore(cutoff)) return@mapNotNull null
            val overall = snapshot.entriesByBoard["overall"]?.firstOrNull { it.slug == model.slug }
            EntryDto(
                slug = model.slug,
                displayName = model.displayName,
                vendor = model.vendor,
                releaseDate = model.releaseDate,
                score = overall?.score,
                rank = overall?.rank ?: 0,
                priceIn = overall?.priceIn,
                priceOut = overall?.priceOut,
                currency = overall?.currency ?: "CNY",
            )
        }
            .sortedByDescending { it.releaseDate }
            .take(limit)
    }

    fun modelOptions(query: String = ""): List<ModelDetailDto> {
        val keyword = query.trim()
        return snapshot.models.values.filter { model ->
            keyword.isBlank() ||
                model.displayName.contains(keyword, ignoreCase = true) ||
                model.vendor?.contains(keyword, ignoreCase = true) == true ||
                model.slug.contains(keyword, ignoreCase = true)
        }.sortedWith(
            compareBy<ModelDetailDto> { it.displayName.length }
                .thenBy { it.displayName.lowercase() },
        ).take(300)
    }
}

private val OPEN_SOURCE_MARKERS = listOf(
    "open", "apache", "mit", "bsd", "gpl", "lgpl", "agpl", "mpl", "epl",
    "cc-by", "llama", "qwen", "gemma", "falcon", "community", "research",
)
