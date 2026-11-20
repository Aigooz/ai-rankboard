package com.ai.rankboard.data

private val SEARCH_PUNCTUATION = Regex("[\\s_\\p{Pd}]+")

private fun normalizeSearchKey(value: String?): String = java.text.Normalizer.normalize(
    value.orEmpty(),
    java.text.Normalizer.Form.NFKC,
).lowercase(java.util.Locale.ROOT).replace(SEARCH_PUNCTUATION, "")

internal fun matchesModelQuery(query: String?, vararg fields: String?): Boolean {
    val raw = query?.trim().orEmpty().lowercase(java.util.Locale.ROOT)
    if (raw.isEmpty()) return true
    val normalized = normalizeSearchKey(raw)
    return fields.any { value ->
        value.orEmpty().lowercase(java.util.Locale.ROOT).contains(raw) ||
            (normalized.isNotEmpty() && normalizeSearchKey(value).contains(normalized))
    }
}

class LocalSnapshotDataSource private constructor(
    private val snapshotProvider: () -> Snapshot,
    private val infoProvider: () -> SnapshotInfo,
) {
    constructor(store: SnapshotStore) : this({ store.snapshot }, { store.info() })

    internal constructor(snapshot: Snapshot) : this({ snapshot }, {
        SnapshotInfo(snapshot.generatedAt, snapshot.source?.id.orEmpty(),
            snapshot.source?.name.orEmpty(), isDownloaded = false, verified = true)
    })

    internal constructor(snapshotProvider: () -> Snapshot) : this(snapshotProvider, {
        val snapshot = snapshotProvider()
        SnapshotInfo(snapshot.generatedAt, snapshot.source?.id.orEmpty(),
            snapshot.source?.name.orEmpty(), isDownloaded = false, verified = true)
    })

    private val snapshot get() = snapshotProvider()

    private data class SnapshotIndex(
        val snapshot: Snapshot,
        val enrichedEntriesByBoard: Map<String, List<EntryDto>>,
        val entryIndexesByBoard: Map<String, Map<String, EntryDto>>,
        val scoreBoundsByBoard: Map<String, Triple<Double?, Double?, Int>>,
        val vendorOptionsByBoard: Map<String, List<String>>,
        val sortedModels: List<ModelDetailDto>,
        val lowerModelFields: Map<String, List<String>>,
        val normalizedModelFields: Map<String, List<String>>,
    )

    @Volatile
    private var cachedIndex: SnapshotIndex? = null

    private fun index(): SnapshotIndex {
        val current = snapshot
        cachedIndex?.takeIf { it.snapshot === current }?.let { return it }
        return synchronized(this) {
            cachedIndex?.takeIf { it.snapshot === current } ?: buildIndex(current).also { cachedIndex = it }
        }
    }

    private fun buildIndex(current: Snapshot): SnapshotIndex {
        val enriched = current.entriesByBoard.mapValues { (_, entries) ->
            entries.map { entry ->
                val model = current.models[entry.slug] ?: return@map entry
                entry.copy(
                    vendor = entry.vendor?.takeIf { it.isNotBlank() } ?: model.vendor?.takeIf { it.isNotBlank() },
                    paramsB = entry.paramsB ?: model.paramsB,
                    license = entry.license?.takeIf { it.isNotBlank() } ?: model.license?.takeIf { it.isNotBlank() },
                    contextWindow = entry.contextWindow?.takeIf { it.isNotBlank() }
                        ?: model.contextWindow?.takeIf { it.isNotBlank() },
                    releaseDate = entry.releaseDate ?: model.releaseDate,
                    sourceUrl = entry.sourceUrl?.takeIf { it.isNotBlank() } ?: model.sourceUrl,
                )
            }
        }
        val entryIndexes = enriched.mapValues { (_, entries) -> entries.associateBy { it.slug } }
        val bounds = enriched.mapValues { (_, entries) ->
            val scores = entries.asSequence().mapNotNull { it.score }.toList()
            Triple(scores.minOrNull(), scores.maxOrNull(), scores.size)
        }
        val vendors = enriched.mapValues { (_, entries) ->
            entries.mapNotNull { it.vendor?.trim()?.takeIf(String::isNotBlank) }
                .distinctBy { it.lowercase() }
                .sortedByDescending { it.length }
                .take(40)
        }
        val models = current.models.values.sortedWith(
            compareBy<ModelDetailDto> { it.displayName.length }
                .thenBy { it.displayName.lowercase() },
        )
        val normalizedModelFields = models.associate { model ->
            model.slug to listOf(model.displayName, model.vendor.orEmpty(), model.slug)
                .map(::normalizeSearchKey)
        }
        val lowerModelFields = models.associate { model ->
            model.slug to listOf(model.displayName, model.vendor.orEmpty(), model.slug)
                .map { it.lowercase(java.util.Locale.ROOT) }
        }
        return SnapshotIndex(current, enriched, entryIndexes, bounds, vendors, models, lowerModelFields, normalizedModelFields)
    }

    fun boards(): List<BoardDto> = snapshot.boards

    fun historyByBoard(): Map<String, List<BoardHistoryDto>> = snapshot.historyByBoard.orEmpty()

    fun usageRanking(): UsageRankingDto? = snapshot.usageRanking

    fun news(): NewsDto? = snapshot.news

    fun benchmarkReference(id: String): BenchmarkReferenceDto? = snapshot.benchmarkReferences?.get(id)

    fun info(): SnapshotInfo = infoProvider()

    fun boardsForDimension(dimension: String): List<BoardDto> =
        snapshot.boards.filter { it.dimension == dimension }

    fun vendorOptions(
        boardSlug: String,
        periodDimension: String? = null,
        period: String? = null,
    ): List<String> {
        val currentIndex = index()
        val baseEntries = if (periodDimension in Periods.supported && !period.isNullOrBlank()) {
            currentIndex.enrichedEntriesByBoard[boardSlug].orEmpty()
                .filter { Periods.matches(periodDimension!!, period, it.releaseDate) }
        } else {
            currentIndex.enrichedEntriesByBoard[boardSlug].orEmpty()
        }
        if (periodDimension !in Periods.supported || period.isNullOrBlank()) {
            return currentIndex.vendorOptionsByBoard[boardSlug].orEmpty()
        }
        return baseEntries
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
        limit: Int,
        offset: Int,
        periodDimension: String? = null,
        period: String? = null,
        region: ModelRegion = ModelRegion.ALL,
    ): EntriesResponse? {
        val currentIndex = index()
        val board = currentIndex.snapshot.boards.firstOrNull { it.slug == boardSlug } ?: return null
        val baseEntries = currentIndex.enrichedEntriesByBoard[boardSlug].orEmpty()
        val filteredByPeriod = if (periodDimension in Periods.supported && !period.isNullOrBlank()) {
            baseEntries.filter { Periods.matches(periodDimension!!, period, it.releaseDate) }
        } else {
            baseEntries
        }
        val rankedByPeriod = if (periodDimension in Periods.supported) {
            scoreRanked(filteredByPeriod)
        } else {
            filteredByPeriod
        }
        val entries = rankedByPeriod.filter { entry ->
            matchesModelQuery(query, entry.displayName, entry.vendor, entry.slug)
        }
            .filter { entry ->
                vendor.isEmpty() || vendor.any { entry.vendor.equals(it, ignoreCase = true) }
            }
            .filter { entry ->
                license.isEmpty() || license.any { entry.matchesLicense(it) }
            }
            .filter { entry -> region.matches(entry.vendor) }
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
        "open" -> isLikelyOpenSourceLicense(license)
        "proprietary" -> license.isNullOrBlank() || !isLikelyOpenSourceLicense(license)
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
        val currentIndex = index()
        val current = currentIndex.snapshot
        val model = current.models[slug] ?: current.entriesByBoard.values.asSequence().flatten()
            .firstOrNull { it.slug == slug }?.let { entry -> ModelDetailDto(
                slug = entry.slug, displayName = entry.displayName, vendor = entry.vendor,
                paramsB = entry.paramsB, license = entry.license, contextWindow = entry.contextWindow,
                sourceUrl = entry.sourceUrl, releaseDate = entry.releaseDate,
            ) } ?: return null
        val scores = current.boards.mapNotNull { board ->
            val entry = currentIndex.entryIndexesByBoard[board.slug]?.get(slug) ?: return@mapNotNull null
            val bounds = currentIndex.scoreBoundsByBoard[board.slug] ?: Triple(null, null, 0)
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
                scoreMin = bounds.first,
                scoreMax = bounds.second,
                entryCount = bounds.third,
                board = board,
            )
        }
        return ModelDetailResponse(model, scores)
    }

    /** 按发布日期取最近 N 天内上新的模型，附带综合榜成绩，供首页"最近上新"展示。 */
    fun recentlyReleasedModels(days: Int = 30, limit: Int = 12): List<EntryDto> {
        val cutoff = java.time.LocalDate.now().minusDays(days.toLong())
        val currentIndex = index()
        val current = currentIndex.snapshot
        val overallBySlug = currentIndex.entryIndexesByBoard["overall"].orEmpty()
        return current.models.values.mapNotNull { model ->
            val date = model.releaseDate?.take(10)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val release = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: return@mapNotNull null
            if (release.isBefore(cutoff)) return@mapNotNull null
            val overall = overallBySlug[model.slug]
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

    fun vendorModels(model: ModelDetailDto): List<ModelDetailDto> {
        val vendor = realVendorKey(model.vendor) ?: return emptyList()
        return index().snapshot.models.values.filter {
            it.slug != model.slug && realVendorKey(it.vendor) == vendor
        }.distinctBy { it.slug }.sortedBy { it.displayName }.take(8)
    }

    fun modelOptions(query: String = ""): List<ModelDetailDto> {
        val currentIndex = index()
        val raw = query.trim().lowercase(java.util.Locale.ROOT)
        if (raw.isEmpty()) return currentIndex.sortedModels.take(300)
        val normalized = normalizeSearchKey(raw)
        return currentIndex.sortedModels.filter { model ->
            val lowerFields = currentIndex.lowerModelFields[model.slug].orEmpty()
            val normalizedFields = currentIndex.normalizedModelFields[model.slug].orEmpty()
            lowerFields.any { it.contains(raw) } ||
                (normalized.isNotEmpty() && normalizedFields.any { it.contains(normalized) })
        }.take(300)
    }
}
