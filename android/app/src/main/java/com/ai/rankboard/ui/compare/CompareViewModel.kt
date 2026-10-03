package com.ai.rankboard.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.LeaderboardRepository
import com.ai.rankboard.data.ModelDetailResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompareUiState(
    val models: List<ModelDetailResponse> = emptyList(),
    val loading: Boolean = false,
)

class CompareViewModel(private val repository: LeaderboardRepository) : ViewModel() {
    private val _state = MutableStateFlow(CompareUiState())
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.favorites().collect { favorites ->
                _state.update { it.copy(loading = true) }
                val details = favorites.take(4).mapNotNull { favorite ->
                    repository.modelDetail(favorite.modelSlug)
                }
                _state.update { CompareUiState(models = details, loading = false) }
            }
        }
    }

    companion object {
        fun factory(app: RankboardApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { CompareViewModel(app.repository) }
        }
    }
}
