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
    val news: NewsDto? = null,
    val historyByBoard: Map<String, List<BoardHistoryDto>>? = null,
    val benchmarkReferences: Map<String, BenchmarkReferenceDto>? = null,
)

data class BenchmarkReferenceDto(
    val id: String? = null, val name: String? = null, val publisher: String? = null,
    val description: String? = null, val sourceUrl: String? = null, val datasetUrl: String? = null,
    val paperUrl: String? = null, val licenseNotice: String? = null, val canary: String? = null,
    val trainingWarning: String? = null, val examples: List<BenchmarkExampleDto>? = null,
)
data class BenchmarkExampleDto(
    val id: String? = null, val category: String? = null, val question: String? = null,
    val sourceUrl: String? = null, val author: String? = null, val modality: String? = null, val note: String? = null,
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

data class NewsDto(
    @SerializedName("generated_at") val generatedAt: String = "",
    val articles: List<NewsArticleDto> = emptyList(),
)

data class NewsArticleDto(
    val title: String = "",
    val url: String = "",
    @SerializedName("published_at") val publishedAt: String? = null,
    val content: String? = null,
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
    val health: BoardHealthDto? = null,
    val metric: BoardMetricDto? = null,
    val source: SnapshotSourceDto? = null,
    val evaluation: BoardEvaluationDto? = null,
    val release: String? = null,
)

data class BoardHealthDto(
    val latestAttemptAt: String? = null,
    val latestAttemptStatus: String? = null,
    val error: String? = null,
    val lastSuccessAt: String? = null,
    val stale: Boolean? = null,
    val staleAfterHours: Int? = null,
    val anomalies: List<BoardAnomalyDto>? = null,
)
data class BoardAnomalyDto(val code: String? = null, val message: String? = null, val severity: String? = null)
data class BoardMetricDto(
    val name: String? = null, val description: String? = null, val unit: String? = null,
    val higherIsBetter: Boolean? = null, val comparableAcrossBoards: Boolean? = null,
)
data class BoardEvaluationDto(
    val version: String? = null, val versionSource: String? = null, val upstreamUpdatedAt: String? = null,
)
data class BoardHistoryDto(
    val fetchedAt: String? = null, val modelCount: Int? = null, val truncated: Boolean? = null,
    val evaluationVersion: String? = null, val entries: List<BoardHistoryEntryDto>? = null,
)
data class BoardHistoryEntryDto(
    val slug: String = "", @SerializedName("display_name") val displayName: String? = null,
    val rank: Int? = null, val score: Double? = null,
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
    val board: BoardDto? = null,
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
    @SerializedName("release_date_source_url") val releaseDateSourceUrl: String? = null,
)

data class ModelDetailResponse(val model: ModelDetailDto, val scores: List<ScoreDto> = emptyList())

const val PAGE_SIZE = 50
