package com.ai.rankboard.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.ai.rankboard.BuildConfig

class LeaderboardRepository(
    private val local: LocalSnapshotDataSource,
    private val db: AppDatabase,
    private val snapshotStore: SnapshotStore? = null,
    private val snapshotUrlProvider: () -> String = { BuildConfig.SNAPSHOT_URL },
) {
    private val dao get() = db.dao()
    private val boardsCache = mutableMapOf<String, List<BoardDto>>()

    fun boards(): List<BoardDto> = local.boards()

    fun usageRanking(): UsageRankingDto? = local.usageRanking()

    fun news(): NewsDto? = local.news()

    fun modelOptions(query: String = ""): List<ModelDetailDto> = local.modelOptions(query)

    fun recentlyReleasedModels(days: Int = 30, limit: Int = 12): List<EntryDto> =
        local.recentlyReleasedModels(days, limit)

    suspend fun relayRanking(url: String, apiKey: String): RelayRanking {
        val models = RelayModelClient().fetchModels(url, apiKey)
        return RelayModelRanking.rank(models, snapshotStore?.snapshot ?: Snapshot())
            .copy(url = url.trim())
    }

    fun periodOptions(dimension: String): List<PeriodOption> = local.periodOptions(dimension)

    suspend fun favoriteModels(): List<ModelDetailDto> = favorites().first().map { favorite ->
        local.modelDetail(favorite.modelSlug)?.model
            ?: ModelDetailDto(
                slug = favorite.modelSlug,
                displayName = favorite.displayName,
            )
    }

    suspend fun boardsForDimension(dimension: String): List<BoardDto>? =
        boardsCache.getOrPut(dimension) {
            local.boardsForDimension(dimension)
        }.ifEmpty { null }

    suspend fun page(
        boardSlug: String,
        sort: String,
        query: String?,
        vendor: Set<String>,
        license: Set<String>,
        limit: Int,
        offset: Int,
        periodDimension: String? = null,
        period: String? = null,
    ): EntriesResponse? = local.page(
        boardSlug = boardSlug,
            sort = sort,
            query = query?.takeIf { it.isNotBlank() },
            vendor = vendor,
            license = license,
            limit = limit,
            offset = offset,
            periodDimension = periodDimension,
            period = period,
        )

    suspend fun vendorOptions(
        boardSlug: String,
        periodDimension: String? = null,
        period: String? = null,
    ): List<String> = local.vendorOptions(boardSlug, periodDimension, period)

    suspend fun cachedEntries(boardSlug: String): List<CachedEntryEntity> = dao.cachedEntries(boardSlug)

    suspend fun saveEntries(boardSlug: String, entries: List<EntryDto>) {
        dao.clearBoard(boardSlug)
        dao.upsertEntries(entries.map { it.toCachedEntity(boardSlug) })
    }

    /** 翻页时只追加，不清空已有缓存，离线数据随浏览逐渐变全。 */
    suspend fun appendEntries(boardSlug: String, entries: List<EntryDto>) {
        dao.upsertEntries(entries.map { it.toCachedEntity(boardSlug) })
    }

    private fun EntryDto.toCachedEntity(boardSlug: String) = CachedEntryEntity(
        boardSlug = boardSlug,
        modelSlug = slug,
        displayName = displayName,
        vendor = vendor,
        rank = rank,
        score = score,
        priceIn = priceIn,
        priceOut = priceOut,
        currency = currency,
        fetchedAt = fetchedAt,
    )

    suspend fun modelDetail(slug: String): ModelDetailResponse? =
        local.modelDetail(slug)

    fun snapshotInfo(): SnapshotInfo? = snapshotStore?.info()

    suspend fun refreshSnapshot(): SnapshotUpdateResult {
        val store = snapshotStore ?: return SnapshotUpdateResult(
            SnapshotUpdateStatus.SKIPPED,
            "未配置远端快照地址",
        )
        val result = store.refresh(snapshotUrlProvider())
        if (result.status == SnapshotUpdateStatus.UPDATED) {
            boardsCache.clear()
        }
        return result
    }

    fun favorites(): Flow<List<FavoriteEntity>> = dao.favorites()

    fun favoriteSlugs(): Flow<List<String>> = dao.favoriteSlugs()

    suspend fun favoriteList(): List<FavoriteEntity> = dao.favoriteList()

    suspend fun cachedEntryCount(): Int = dao.cachedEntryCount()

    suspend fun clearCache() {
        dao.clearCachedEntries()
    }

    suspend fun importFavorites(favorites: List<FavoriteEntity>) {
        favorites.forEach { dao.addFavorite(it) }
    }

    /** 按目标状态（而不是"读库后取反"）设置收藏，连续调用结果可预期。 */
    suspend fun setFavorite(
        slug: String,
        name: String,
        favorite: Boolean,
        savedAt: Long = System.currentTimeMillis(),
    ) {
        if (favorite) {
            dao.addFavorite(FavoriteEntity(modelSlug = slug, displayName = name, savedAt = savedAt))
        } else {
            dao.removeFavorite(slug)
        }
    }
}
