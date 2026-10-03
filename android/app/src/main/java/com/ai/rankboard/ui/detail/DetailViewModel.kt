package com.ai.rankboard.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.LeaderboardRepository
import com.ai.rankboard.data.ModelDetailDto
import com.ai.rankboard.data.ScoreDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val model: ModelDetailDto? = null,
    val scores: List<ScoreDto> = emptyList(),
)

class DetailViewModel(
    private val repository: LeaderboardRepository,
    val slug: String,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    val favoriteSlugs: StateFlow<List<String>> = repository.favoriteSlugs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, failed = false) }
            val detail = repository.modelDetail(slug)
            _state.update {
                it.copy(
                    loading = false,
                    failed = detail == null,
                    model = detail?.model,
                    scores = detail?.scores ?: emptyList(),
                )
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            repository.toggleFavorite(slug, _state.value.model?.displayName ?: slug, isFavorite())
        }
    }

    fun isFavorite(): Boolean = slug in favoriteSlugs.value.toSet()

    companion object {
        fun factory(app: RankboardApp, slug: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { DetailViewModel(app.repository, slug) }
        }
    }
}

