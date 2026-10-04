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
import com.ai.rankboard.data.priceDistribution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendUiState(
    val loading: Boolean = true,
    val usage: UsageRankingDto? = null,
    val news: NewsDto? = null,
    val distribution: List<PriceDistributionRow> = emptyList(),
    val distributionTotal: Int = 0,
    val nameToSlug: Map<String, String> = emptyMap(),
    val snapshotDate: String = "",
)

class TrendViewModel(
    private val repository: LeaderboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrendUiState())
    val state: StateFlow<TrendUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
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
                    news = news,
                    distribution = priceDistribution(entries),
                    distributionTotal = entries.size,
                    nameToSlug = byName,
                    snapshotDate = repository.snapshotInfo()?.generatedAt?.take(10).orEmpty(),
                )
            }
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
