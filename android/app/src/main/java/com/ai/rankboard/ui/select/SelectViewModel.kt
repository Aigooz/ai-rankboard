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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CostInput(
    val monthlyInputMillions: Double = 3.0,
    val monthlyOutputMillions: Double = 1.0,
    val cacheHitRate: Float = 0.30f,
    val minScore: Double = 70.0,
    val openSource: Int = 0,
)

data class CostEstimate(
    val entry: EntryDto,
    val score: Double,
    val inputCost: Double,
    val outputCost: Double,
    val monthlyCost: Double,
) {
    val valueScore: Double
        get() = if (monthlyCost <= 0.0) 0.0 else score / maxOf(monthlyCost, 0.01)
}

data class SelectUiState(
    val loading: Boolean = true,
    val abilityBoards: List<BoardDto> = emptyList(),
    val selectedBoard: String = "",
    val entries: List<EntryDto> = emptyList(),
    val costInput: CostInput = CostInput(),
    val estimates: List<CostEstimate> = emptyList(),
    val recommended: CostEstimate? = null,
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

    fun setBoard(boardSlug: String) {
        if (_state.value.selectedBoard == boardSlug) return
        _state.update { it.copy(selectedBoard = boardSlug) }
        loadEstimates()
    }

    fun setCostInput(value: CostInput) {
        if (_state.value.costInput == value) return
        _state.update { it.copy(costInput = value).recalculate() }
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
            _state.update {
                it.copy(
                    loading = false,
                    abilityBoards = boards,
                    selectedBoard = preferred?.slug.orEmpty(),
                    snapshotDate = repository.snapshotInfo()?.generatedAt?.take(10).orEmpty(),
                    error = if (preferred == null) "暂无可选模型" else "",
                )
            }
            if (preferred != null) loadEstimates()
        }
    }

    private fun loadEstimates() {
        viewModelScope.launch {
            val board = _state.value.selectedBoard
            val response = runCatching {
                repository.page(
                    boardSlug = board,
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
                _state.update { it.copy(error = "数据加载失败，请稍后重试") }
                return@launch
            }

            _state.update {
                it.copy(
                    loading = false,
                    entries = response.entries,
                    latest = response.entries
                        .filter { !it.releaseDate.isNullOrBlank() }
                        .sortedByDescending { it.releaseDate }
                        .take(12),
                    error = if (response.entries.isEmpty()) "该榜单暂无模型" else "",
                )
            }
        }
    }

    private fun estimate(input: CostInput): List<CostEstimate> =
        _state.value.entries
            .filter { it.score != null && (it.priceIn != null || it.priceOut != null) }
            .map { entry ->
                val score = entry.score ?: 0.0
                val inputCost = (entry.priceIn ?: 0.0) *
                    input.monthlyInputMillions * (1.0 - input.cacheHitRate)
                val outputCost = (entry.priceOut ?: 0.0) *
                    input.monthlyOutputMillions
                CostEstimate(
                    entry = entry,
                    score = score,
                    inputCost = inputCost,
                    outputCost = outputCost,
                    monthlyCost = inputCost + outputCost,
                )
            }
            .filter { it.monthlyCost > 0.0 }

    private fun SelectUiState.recalculate(): SelectUiState {
        val estimates = estimate(costInput)
        return copy(
            estimates = estimates,
            recommended = estimates
                .filter { it.score >= costInput.minScore }
                .minByOrNull { it.monthlyCost },
        )
    }

    companion object {
        fun factory(app: RankboardApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { SelectViewModel(app.repository) }
        }
    }
}
