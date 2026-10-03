package com.ai.rankboard.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.BoardDto
import com.ai.rankboard.data.FavoriteEntity
import com.ai.rankboard.data.LeaderboardRepository
import com.ai.rankboard.data.ModelDetailDto
import com.ai.rankboard.data.ModelDetailResponse
import com.ai.rankboard.data.SettingsStore
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
    val selectedDimension: String = "overall",
    val selectedBoardSlug: String = "",
    val pickerOpen: Boolean = false,
    val loading: Boolean = false,
)

class CompareViewModel(
    private val repository: LeaderboardRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    // 收藏列表依赖逐条查详情，缓存避免快照未变时反复解析。
    private val detailCache = mutableMapOf<String, ModelDetailDto>()

    private val _state = MutableStateFlow(CompareUiState())
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val boards = repository.boards()
            repository.modelOptions("").forEach { detailCache[it.slug] = it }
            val favorites = favoriteDetails(repository.favoriteList())
            _state.update { it.copy(favoriteModels = favorites) }
            val savedSelection = settingsStore.settings.value.compareModels
                .filter { it in detailCache }
                .take(MAX_MODELS)
            val initialSelection = savedSelection.ifEmpty { favorites.take(MAX_MODELS).map { it.slug } }
            _state.update {
                it.copy(
                    boards = boards,
                    selectedDimension = "overall",
                    selectedBoardSlug = boards.firstOrNull { board -> board.slug == "overall" }?.slug
                        ?: boards.firstOrNull()?.slug.orEmpty(),
                    selectedSlugs = initialSelection,
                    pickerTab = if (favorites.isEmpty()) PickerTab.Vendors else PickerTab.Favorites,
                    candidates = pickerCandidates(""),
                    loading = initialSelection.isNotEmpty(),
                )
            }
            loadModels(initialSelection)
        }
        // 收藏列表保持实时：在其他页面点亮/取消星标后，选择器的收藏分组立即同步。
        viewModelScope.launch {
            repository.favorites().collect { favorites ->
                val details = favoriteDetails(favorites)
                _state.update { it.copy(favoriteModels = details) }
                _state.update { current ->
                    current.copy(candidates = pickerCandidates(current.query))
                }
            }
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
        settingsStore.setCompareModels(next)
        _state.update { it.copy(selectedSlugs = next, loading = true) }
        viewModelScope.launch { loadModels(next) }
    }

    fun toggleFavorite(model: ModelDetailDto) {
        val target = _state.value.favoriteModels.none { it.slug == model.slug }
        viewModelScope.launch {
            repository.setFavorite(model.slug, model.displayName, target)
        }
    }

    fun selectBoard(slug: String) {
        _state.update { state ->
            val dimension = state.boards.firstOrNull { it.slug == slug }?.dimension
            state.copy(
                selectedBoardSlug = slug,
                selectedDimension = dimension ?: state.selectedDimension,
            )
        }
    }

    fun selectDimension(dimension: String) {
        if (_state.value.selectedDimension == dimension) return
        _state.update { state ->
            val scopedBoards = state.boards.filter { dimension == "all" || it.dimension == dimension }
            val nextBoard = scopedBoards.firstOrNull { it.slug == state.selectedBoardSlug }
                ?: scopedBoards.firstOrNull()
            state.copy(
                selectedDimension = dimension,
                selectedBoardSlug = nextBoard?.slug.orEmpty(),
            )
        }
    }

    fun setPickerOpen(open: Boolean) = _state.update { it.copy(pickerOpen = open) }

    private suspend fun loadModels(slugs: List<String>) {
        val models = slugs.mapNotNull { slug -> repository.modelDetail(slug) }
        _state.update {
            if (it.selectedSlugs == slugs) it.copy(models = models, loading = false) else it
        }
    }

    private suspend fun favoriteDetails(favorites: List<FavoriteEntity>): List<ModelDetailDto> =
        favorites.map { favorite ->
            detailCache.getOrPut(favorite.modelSlug) {
                repository.modelDetail(favorite.modelSlug)?.model
                    ?: ModelDetailDto(slug = favorite.modelSlug, displayName = favorite.displayName)
            }
        }

    private fun pickerCandidates(query: String): List<ModelDetailDto> {
        val matchingFavorites = _state.value.favoriteModels.filter { ModelPickerGroups.matches(it, query) }
        return (matchingFavorites + repository.modelOptions(query))
            .distinctBy { it.slug }
    }

    companion object {
        const val MAX_MODELS = 4

        fun factory(app: RankboardApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { CompareViewModel(app.repository, app.settingsStore) }
        }
    }
}
