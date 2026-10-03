package com.ai.rankboard.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.BoardDto
import com.ai.rankboard.data.LeaderboardRepository
import com.ai.rankboard.data.ModelDetailDto
import com.ai.rankboard.data.ModelDetailResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompareUiState(
    val selectedSlugs: List<String> = emptyList(),
    val models: List<ModelDetailResponse> = emptyList(),
    val candidates: List<ModelDetailDto> = emptyList(),
    val favoriteModels: List<ModelDetailDto> = emptyList(),
    val query: String = "",
    val pickerTab: PickerTab = PickerTab.Favorites,
    val boards: List<BoardDto> = emptyList(),
    val selectedBoardSlug: String = "",
    val pickerOpen: Boolean = false,
    val boardPickerOpen: Boolean = false,
    val loading: Boolean = false,
)

class CompareViewModel(private val repository: LeaderboardRepository) : ViewModel() {
    private var favoriteModels: List<ModelDetailDto> = emptyList()

    private val _state = MutableStateFlow(CompareUiState())
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val boards = repository.boards()
            favoriteModels = repository.favoriteModels()
            val favorites = favoriteModels.map { it.slug }
            val initialSelection = favorites.take(MAX_MODELS)
            _state.update {
                it.copy(
                    boards = boards,
                    selectedBoardSlug = boards.firstOrNull { board -> board.slug == "overall" }?.slug
                        ?: boards.firstOrNull()?.slug.orEmpty(),
                    selectedSlugs = initialSelection,
                    favoriteModels = favoriteModels,
                    pickerTab = if (favorites.isEmpty()) PickerTab.Vendors else PickerTab.Favorites,
                    candidates = pickerCandidates(""),
                    loading = initialSelection.isNotEmpty(),
                )
            }
            loadModels(initialSelection)
        }
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value, candidates = pickerCandidates(value)) }
    }

    fun setPickerTab(tab: PickerTab) {
        _state.update { it.copy(pickerTab = tab, candidates = pickerCandidates(it.query)) }
    }

    fun toggleSelection(slug: String) {
        val current = _state.value.selectedSlugs
        val next = when {
            slug in current -> current - slug
            current.size >= MAX_MODELS -> current
            else -> current + slug
        }
        if (next == current) return
        _state.update { it.copy(selectedSlugs = next, loading = true) }
        viewModelScope.launch { loadModels(next) }
    }

    fun selectBoard(slug: String) {
        _state.update { it.copy(selectedBoardSlug = slug, boardPickerOpen = false) }
    }

    fun setPickerOpen(open: Boolean) = _state.update { it.copy(pickerOpen = open) }

    fun setBoardPickerOpen(open: Boolean) = _state.update { it.copy(boardPickerOpen = open) }

    private suspend fun loadModels(slugs: List<String>) {
        val models = slugs.mapNotNull { slug -> repository.modelDetail(slug) }
        _state.update {
            if (it.selectedSlugs == slugs) it.copy(models = models, loading = false) else it
        }
    }

    private fun pickerCandidates(query: String): List<ModelDetailDto> {
        val matchingFavorites = favoriteModels.filter { ModelPickerGroups.matches(it, query) }
        return (matchingFavorites + repository.modelOptions(query))
            .distinctBy { it.slug }
    }

    companion object {
        const val MAX_MODELS = 4

        fun factory(app: RankboardApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { CompareViewModel(app.repository) }
        }
    }
}
