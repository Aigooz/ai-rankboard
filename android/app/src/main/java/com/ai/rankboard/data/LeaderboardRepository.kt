package com.ai.rankboard.data

import kotlinx.coroutines.flow.Flow
import com.ai.rankboard.BuildConfig

class LeaderboardRepository(
    private val local: LocalSnapshotDataSource,
    private val db: AppDatabase,
    private val snapshotStore: SnapshotStore? = null,
    private val snapshotUrlProvider: () -> String = { BuildConfig.SNAPSHOT_URL },
) {
    private val dao get() = db.dao()
    private val boardsCache = mutableMapOf<String, List<BoardDto>>()

    suspend fun boardsForDimension(dimension: String): List<BoardDto>? =
        boardsCache.getOrPut(dimension) {
            local.boardsForDimension(dimension)
        }.ifEmpty { null }

    suspend fun page(
        boardSlug: String,
        sort: String,
        query: String?,
        vendor: String?,
        license: String?,
        params: String?,
        limit: Int,
        offset: Int,
    ): EntriesResponse? = local.page(
        boardSlug = boardSlug,
            sort = sort,
            query = query?.takeIf { it.isNotBlank() },
            vendor = vendor?.takeIf { it.isNotBlank() },
            license = license?.takeIf { it.isNotBlank() },
            params = params?.takeIf { it.isNotBlank() },
            limit = limit,
            offset = offset,
        )

    suspend fun vendorOptions(boardSlug: String): List<String> = local.vendorOptions(boardSlug)

    suspend fun cachedEntries(boardSlug: String): List<CachedEntryEntity> = dao.cachedEntries(boardSlug)

    suspend fun saveEntries(boardSlug: String, entries: List<EntryDto>) {
        dao.clearBoard(boardSlug)
        dao.upsertEntries(
            entries.map {
                CachedEntryEntity(
                    boardSlug = boardSlug,
                    modelSlug = it.slug,
                    displayName = it.displayName,
                    vendor = it.vendor,
                    rank = it.rank,
                    score = it.score,
                    priceIn = it.priceIn,
                    priceOut = it.priceOut,
                    currency = it.currency,
                    fetchedAt = it.fetchedAt,
                )
            },
        )
    }

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

    suspend fun toggleFavorite(slug: String, name: String, isFavorite: Boolean) {
        if (isFavorite) {
            dao.removeFavorite(slug)
        } else {
            dao.addFavorite(FavoriteEntity(modelSlug = slug, displayName = name, savedAt = System.currentTimeMillis()))
        }
    }
}
