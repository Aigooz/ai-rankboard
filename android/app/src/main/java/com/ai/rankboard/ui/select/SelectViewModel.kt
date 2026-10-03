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
import com.ai.rankboard.data.cheapestByTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SelectUiState(
    val loading: Boolean = true,
    val entries: List<EntryDto> = emptyList(),
    val tierPicks: List<PriceTierPicks> = emptyList(),
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
                    params = emptySet(),
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
                    entries = response.entries,
                    tierPicks = cheapestByTier(response.entries),
                    latest = response.entries
                        .filter { !it.releaseDate.isNullOrBlank() }
                        .sortedByDescending { it.releaseDate }
                        .take(12),
                    error = if (response.entries.isEmpty()) "该榜单暂无模型" else "",
                )
            }
        }
    }

    companion object {
        fun factory(app: RankboardApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { SelectViewModel(app.repository) }
        }
    }
}
