package com.ai.rankboard.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.LeaderboardRepository
import com.ai.rankboard.data.NewsDto
import com.ai.rankboard.data.PriceDistributionRow
import com.ai.rankboard.data.UsageRankingDto
import com.ai.rankboard.data.EntryDto
import com.ai.rankboard.data.BoardDto
import com.ai.rankboard.data.priceDistribution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendUiState(
    val loading: Boolean = true,
    val usage: UsageRankingDto? = null,
    val usageBoard: BoardDto? = null,
    val usageEntries: List<EntryDto> = emptyList(),
    val generatedAt: String? = null,
    val refreshing: Boolean = false,
    val refreshMessage: String = "",
    val news: NewsDto? = null,
    val distribution: List<PriceDistributionRow> = emptyList(),
    val distributionTotal: Int = 0,
    val nameToSlug: Map<String, String> = emptyMap(),
    val snapshotDate: String = "",
    val boardChanges: Map<String, List<String>> = emptyMap(),
)

class TrendViewModel(
    private val repository: LeaderboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrendUiState())
    val state: StateFlow<TrendUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                val message = runCatching { repository.refreshSnapshot().message }.getOrDefault("更新失败，保留缓存")
                loadData()
                _state.update { it.copy(refreshMessage = message) }
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            loadData()
        }
    }

    private suspend fun loadData() {
            val usageBoard = repository.boards().firstOrNull { it.dimension == "usage" }
            val usageEntries = usageBoard?.let {
                repository.page(it.slug, "rank", null, emptySet(), emptySet(), 1000, 0)?.entries
            }.orEmpty()
            val usage = runCatching { repository.usageRanking() }.getOrNull()
            val news = runCatching { repository.news() }.getOrNull()
            val overall = runCatching {
                repository.page(
                    boardSlug = "overall",
                    sort = "rank",
                    query = null,
                    vendor = emptySet(),
                    license = emptySet(),
                    limit = 1000,
                    offset = 0,
                )
            }.getOrNull()
            val entries = overall?.entries.orEmpty()
            val byName = nameToSlug(entries)
            _state.update {
                it.copy(
                    loading = false,
                    usage = usage,
                    usageBoard = usageBoard,
                    usageEntries = usageEntries,
                    generatedAt = repository.snapshotInfo()?.generatedAt,
                    news = news,
                    distribution = priceDistribution(entries),
                    distributionTotal = entries.size,
                    nameToSlug = byName,
                    snapshotDate = repository.snapshotInfo()?.generatedAt?.take(10).orEmpty(),
                    boardChanges = repository.historyByBoard().mapNotNull { (slug, history) ->
                        val lines = com.ai.rankboard.data.historyChangeLines(history)
                        if (lines.isEmpty()) null else
                            (repository.boards().firstOrNull { board -> board.slug == slug }?.name ?: slug) to lines
                    }.toMap(),
                )
            }
    }

    fun slugFor(name: String): String? =
        _state.value.nameToSlug[name] ?: _state.value.nameToSlug[baseNameKey(name)]

    companion object {
        fun nameToSlug(entries: List<EntryDto>): Map<String, String> {
            val byName = HashMap<String, String>(entries.size)
            entries.forEach { entry ->
                byName.putIfAbsent(entry.displayName, entry.slug)
                byName.putIfAbsent(baseNameKey(entry.displayName), entry.slug)
            }
            return byName
        }

        fun baseNameKey(name: String): String =
            name.lowercase()
                .substringBefore("(")
                .replace(Regex("[-_]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        fun factory(app: RankboardApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { TrendViewModel(app.repository) }
        }
    }
}
