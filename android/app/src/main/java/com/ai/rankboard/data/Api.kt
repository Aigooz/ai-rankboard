package com.ai.rankboard.data

import com.google.gson.annotations.SerializedName

data class SnapshotSourceDto(
    val id: String = "",
    val name: String = "",
    val url: String = "",
)

data class Snapshot(
    @SerializedName("schemaVersion") val schemaVersion: Int = 0,
    @SerializedName("generatedAt") val generatedAt: String = "",
    val source: SnapshotSourceDto? = null,
    val sources: List<SnapshotSourceDto> = emptyList(),
    val boards: List<BoardDto> = emptyList(),
    val entriesByBoard: Map<String, List<EntryDto>> = emptyMap(),
    val models: Map<String, ModelDetailDto> = emptyMap(),
    val usageRanking: UsageRankingDto? = null,
)

data class UsageRankingDto(
    @SerializedName("week_label") val weekLabel: String = "",
    @SerializedName("total_tokens") val totalTokens: String = "",
    @SerializedName("platform_wow") val platformWow: String = "",
    @SerializedName("generated_at") val generatedAt: String = "",
    val entries: List<UsageEntryDto> = emptyList(),
)

data class UsageEntryDto(
    val position: Int = 0,
    val name: String = "",
    @SerializedName("model_url") val modelUrl: String? = null,
    val tokens: String = "",
    val share: Double = 0.0,
    val wow: String? = null,
)

data class BoardDto(
    val slug: String,
    val name: String,
    val dimension: String,
    @SerializedName("source_id") val sourceId: String = "modelsage",
    @SerializedName("score_type") val scoreType: String? = null,
    val url: String? = null,
    @SerializedName("last_success_at") val lastSuccessAt: String? = null,
    @SerializedName("model_count") val modelCount: Int = 0,
)

data class EntryDto(
    val slug: String,
    @SerializedName("display_name") val displayName: String,
    val vendor: String? = null,
    @SerializedName("params_b") val paramsB: Double? = null,
    val license: String? = null,
    @SerializedName("context_window") val contextWindow: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    val rank: Int = 0,
    val score: Double? = null,
    @SerializedName("score_ci") val scoreCi: Double? = null,
    val votes: Int? = null,
    @SerializedName("price_in") val priceIn: Double? = null,
    @SerializedName("price_out") val priceOut: Double? = null,
    val currency: String = "CNY",
    @SerializedName("fetched_at") val fetchedAt: String = "",
    @SerializedName("source_url") val sourceUrl: String? = null,
)

data class EntriesResponse(
    val board: BoardDto,
    val entries: List<EntryDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 50,
    val offset: Int = 0,
    @SerializedName("score_min") val scoreMin: Double? = null,
    @SerializedName("score_max") val scoreMax: Double? = null,
)

data class ScoreDto(
    @SerializedName("board_slug") val boardSlug: String,
    @SerializedName("board_name") val boardName: String,
    val dimension: String,
    @SerializedName("score_type") val scoreType: String,
    val rank: Int,
    val score: Double? = null,
    @SerializedName("score_ci") val scoreCi: Double? = null,
    val votes: Int? = null,
    @SerializedName("price_in") val priceIn: Double? = null,
    @SerializedName("price_out") val priceOut: Double? = null,
    val currency: String = "CNY",
    @SerializedName("fetched_at") val fetchedAt: String = "",
    @SerializedName("score_min") val scoreMin: Double? = null,
    @SerializedName("score_max") val scoreMax: Double? = null,
    @SerializedName("entry_count") val entryCount: Int = 0,
)

data class ModelDetailDto(
    val slug: String,
    @SerializedName("display_name") val displayName: String,
    val vendor: String? = null,
    @SerializedName("params_b") val paramsB: Double? = null,
    val license: String? = null,
    @SerializedName("context_window") val contextWindow: String? = null,
    @SerializedName("source_url") val sourceUrl: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
)

data class ModelDetailResponse(val model: ModelDetailDto, val scores: List<ScoreDto> = emptyList())

const val PAGE_SIZE = 50
