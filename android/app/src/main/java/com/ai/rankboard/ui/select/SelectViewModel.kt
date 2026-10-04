package com.ai.rankboard.ui.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.BoardDto
import com.ai.rankboard.data.EntryDto
import com.ai.rankboard.data.LeaderboardRepository
import com.ai.rankboard.data.PriceTierPicks
import com.ai.rankboard.data.blendedPriceCny
import com.ai.rankboard.data.cheapestByTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SelectUiState(
    val loading: Boolean = true,
    val tierPicks: List<PriceTierPicks> = emptyList(),
    val scatterModels: List<ScatterModel> = emptyList(),
    val latest: List<EntryDto> = emptyList(),
    val snapshotDate: String = "",
    val error: String = "",
)

class SelectViewModel(
    private val repository: LeaderboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SelectUiState())
    val state: StateFlow<SelectUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val boards = runCatching { repository.boardsForDimension("overall") }
                .getOrNull().orEmpty()
                .sortedWith(
                    compareByDescending<BoardDto> { it.sourceId.equals("modelsage", true) }
                        .thenBy { it.name },
                )
            val preferred = boards.firstOrNull { it.slug == "overall" } ?: boards.firstOrNull()
            if (preferred == null) {
                _state.update { it.copy(loading = false, error = "暂无可选模型") }
                return@launch
            }
            val response = runCatching {
                repository.page(
                    boardSlug = preferred.slug,
                    sort = "rank",
                    query = null,
                    vendor = emptySet(),
                    license = emptySet(),
                    limit = 1000,
                    offset = 0,
                )
            }.getOrNull()

            if (response == null) {
                _state.update { it.copy(loading = false, error = "数据加载失败，请稍后重试") }
                return@launch
            }

            _state.update {
                it.copy(
                    loading = false,
                    tierPicks = cheapestByTier(response.entries),
                    latest = response.entries
                        .filter { !it.releaseDate.isNullOrBlank() }
                        .sortedByDescending { it.releaseDate }
                        .take(12),
                    error = if (response.entries.isEmpty()) "该榜单暂无模型" else "",
                )
            }
            val scatter = loadScatterModels(preferred.slug, response.entries)
            _state.update { it.copy(scatterModels = scatter) }
        }
    }

    /** 汇总各能力榜的成绩，供散点图切换质量轴；价格取任一榜单中的可用值。 */
    private suspend fun loadScatterModels(primarySlug: String, primaryEntries: List<EntryDto>): List<ScatterModel> {
        val aggregate = LinkedHashMap<String, MutableScatterModel>()
        fun absorb(entries: List<EntryDto>, metric: String?) {
            entries.forEach { entry ->
                if (entry.score == null && entry.blendedPriceCny() == null) return@forEach
                val model = aggregate.getOrPut(entry.slug) {
                    MutableScatterModel(entry.slug, entry.displayName, entry.vendor)
                }
                if (metric != null) {
                    entry.score?.let { score -> model.scores[metric] = score }
                }
                if (model.priceCny == null) {
                    model.priceCny = entry.blendedPriceCny()
                }
            }
        }
        absorb(primaryEntries, SCATTER_METRICS.firstOrNull { it.first == primarySlug }?.first)
        SCATTER_METRICS.forEach { (slug, _) ->
            if (slug == primarySlug) return@forEach
            val response = runCatching {
                repository.page(
                    boardSlug = slug,
                    sort = "rank",
                    query = null,
                    vendor = emptySet(),
                    license = emptySet(),
                    limit = 1000,
                    offset = 0,
                )
            }.getOrNull() ?: return@forEach
            absorb(response.entries, slug)
        }
        return aggregate.values
            .filter { it.scores.isNotEmpty() }
            .map { ScatterModel(it.slug, it.displayName, it.vendor, it.priceCny, it.scores.toMap()) }
    }

    private data class MutableScatterModel(
        val slug: String,
        val displayName: String,
        val vendor: String?,
        var priceCny: Double? = null,
        val scores: MutableMap<String, Double> = mutableMapOf(),
    )

    companion object {
        fun factory(app: RankboardApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { SelectViewModel(app.repository) }
        }
    }
}
