package com.ai.rankboard.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.ModelDetailDto
import com.ai.rankboard.data.ScoreDto
import com.ai.rankboard.data.LeaderboardRepository
import com.ai.rankboard.data.mergeFavoriteSlugs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    // 本地记录尚未写回数据库的切换结果，星标状态先行更新，避免快速连点时读到滞后数据。
    private val pendingFavorites = MutableStateFlow(mapOf<String, Boolean>())

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    val favoriteSlugs: StateFlow<List<String>> = combine(
        repository.favoriteSlugs(),
        pendingFavorites,
        ::mergeFavoriteSlugs,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            backfillFavoriteName(detail?.model?.displayName)
        }
    }

    fun toggleFavorite() {
        val target = slug !in favoriteSlugs.value.toSet()
        pendingFavorites.update { it + (slug to target) }
        viewModelScope.launch {
            repository.setFavorite(slug, _state.value.model?.displayName ?: slug, target)
            pendingFavorites.update { current -> if (current[slug] == target) current - slug else current }
        }
    }

    // 模型加载完成前收藏的条目只有 slug，加载后把正式显示名补上（保留原收藏时间）。
    private suspend fun backfillFavoriteName(displayName: String?) {
        val name = displayName?.takeIf { it.isNotBlank() } ?: return
        val saved = repository.favoriteList().firstOrNull { it.modelSlug == slug } ?: return
        if (saved.displayName != name) {
            repository.setFavorite(slug, name, true, savedAt = saved.savedAt)
        }
    }

    companion object {
        fun factory(app: RankboardApp, slug: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { DetailViewModel(app.repository, slug) }
        }
    }
}
