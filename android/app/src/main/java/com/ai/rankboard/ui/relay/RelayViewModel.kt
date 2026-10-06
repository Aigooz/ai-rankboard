package com.ai.rankboard.ui.relay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.LeaderboardRepository
import com.ai.rankboard.data.RelayMatchQuality
import com.ai.rankboard.data.RelayModelRank
import com.ai.rankboard.data.RelayRanking
import com.ai.rankboard.data.RelayEndpoint
import com.ai.rankboard.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RelayUiState(
    val url: String = "",
    val apiKey: String = "",
    val relayEndpoints: List<RelayEndpoint> = emptyList(),
    val query: String = "",
    val filter: RelayFilter = RelayFilter.ALL,
    val ranking: RelayRanking? = null,
    val loading: Boolean = false,
    val error: String = "",
) {
    val visibleModels: List<RelayModelRank>
        get() {
            val keyword = query.trim()
            return ranking?.models.orEmpty().filter { model ->
                val matchesFilter = when (filter) {
                    RelayFilter.ALL -> true
                    RelayFilter.MATCHED -> model.matchQuality == RelayMatchQuality.EXACT
                    RelayFilter.INFERRED -> model.matchQuality == RelayMatchQuality.INFERRED
                    RelayFilter.UNMATCHED -> model.matchQuality == RelayMatchQuality.NONE
                }
                matchesFilter && (
                    keyword.isBlank() ||
                        model.remoteId.contains(keyword, ignoreCase = true) ||
                        model.displayName.contains(keyword, ignoreCase = true) ||
                        model.vendor?.contains(keyword, ignoreCase = true) == true
                    )
            }
        }
}

enum class RelayFilter(val label: String) {
    ALL("全部"),
    MATCHED("精确"),
    INFERRED("推断"),
    UNMATCHED("未匹配"),
}

class RelayViewModel(
    private val repository: LeaderboardRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(
        RelayUiState(
            url = settingsStore.settings.value.relayUrl,
            apiKey = settingsStore.settings.value.relayApiKey,
            relayEndpoints = settingsStore.settings.value.relayEndpoints,
        ),
    )
    val state: StateFlow<RelayUiState> = _state.asStateFlow()

    init {
        if (_state.value.url.isNotBlank()) {
            fetch()
        }
    }

    fun setUrl(value: String) = _state.update { it.copy(url = value) }

    fun setApiKey(value: String) = _state.update { it.copy(apiKey = value) }

    fun selectEndpoint(endpoint: RelayEndpoint) {
        if (_state.value.loading) return
        settingsStore.setRelay(endpoint.url, endpoint.apiKey)
        _state.update {
            it.copy(
                url = endpoint.url,
                apiKey = endpoint.apiKey,
                relayEndpoints = settingsStore.settings.value.relayEndpoints,
            )
        }
        fetch()
    }

    fun removeEndpoint(endpoint: RelayEndpoint) {
        if (_state.value.loading) return
        settingsStore.removeRelayEndpoint(endpoint.url)
        _state.update {
            val clearedCurrent = it.url == endpoint.url
            it.copy(
                url = if (clearedCurrent) "" else it.url,
                apiKey = if (clearedCurrent) "" else it.apiKey,
                relayEndpoints = settingsStore.settings.value.relayEndpoints,
            )
        }
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun setFilter(value: RelayFilter) = _state.update { it.copy(filter = value) }

    fun fetch() {
        val url = _state.value.url.trim()
        if (url.isBlank() || _state.value.loading) return
        _state.update { it.copy(loading = true, error = "") }
        viewModelScope.launch {
            runCatching {
                repository.relayRanking(url, _state.value.apiKey.trim())
            }.onSuccess { ranking ->
                settingsStore.setRelay(url, _state.value.apiKey.trim())
                _state.update {
                    it.copy(
                        loading = false,
                        ranking = ranking,
                        relayEndpoints = settingsStore.settings.value.relayEndpoints,
                        error = "",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: "无法获取中转站模型列表",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(app: RankboardApp): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RelayViewModel(app.repository, app.settingsStore)
            }
        }
    }
}
