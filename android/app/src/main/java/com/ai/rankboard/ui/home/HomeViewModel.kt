package com.ai.rankboard.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ai.rankboard.RankboardApp
import com.ai.rankboard.data.BoardDto
import com.ai.rankboard.data.EntryDto
import com.ai.rankboard.data.LeaderboardRepository
import com.ai.rankboard.data.PeriodOption
import com.ai.rankboard.data.Periods
import com.ai.rankboard.data.SnapshotInfo
import com.ai.rankboard.data.SnapshotUpdateStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeTab(val dimension: String, val label: String)

val HOME_TABS = listOf(
    HomeTab("overall", "综合榜"),
    HomeTab("coding", "代码榜"),
    HomeTab("writing", "写作榜"),
    HomeTab("multimodal", "多模态榜"),
    HomeTab(Periods.MONTH, "月榜"),
    HomeTab(Periods.QUARTER, "季榜"),
    HomeTab(Periods.YEAR, "年榜"),
    HomeTab("agent", "智能体榜"),
    HomeTab("search", "搜索榜"),
    HomeTab("speed", "速度榜"),
    HomeTab("value", "性价比榜"),
    HomeTab("math", "数学榜"),
    HomeTab("analysis", "数据分析榜"),
)

data class HomeUiState(
    val tab: String = HOME_TABS.first().dimension,
    val boardsForTab: List<BoardDto> = emptyList(),
    val selectedBoard: String = "",
    val allBoardsForTab: List<BoardDto> = emptyList(),
    val period: String? = null,
    val periodOptions: List<PeriodOption> = emptyList(),
    val sourceFilter: String? = "modelsage",
    val sort: String = "rank",
    val query: String = "",
    val vendorOptions: List<String> = emptyList(),
    val vendorFilter: Set<String> = emptySet(),
    val licenseFilter: Set<String> = emptySet(),
    val entries: List<EntryDto> = emptyList(),
    val total: Int = 0,
    val scoreMin: Double? = null,
    val scoreMax: Double? = null,
    val recentModels: List<EntryDto> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val offline: Boolean = false,
    val snapshotInfo: SnapshotInfo? = null,
    val updateMessage: String = "",
)

class HomeViewModel(
    private val repository: LeaderboardRepository,
    initialTab: String = "overall",
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(tab = initialTab))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var loadKey: Pair<String, String>? = null

    init {
        _state.update { it.copy(snapshotInfo = repository.snapshotInfo()) }
        loadBoardsForTab(initialTab)
        viewModelScope.launch {
            val recent = runCatching { repository.recentlyReleasedModels() }.getOrDefault(emptyList())
            _state.update { it.copy(recentModels = recent) }
        }
    }

    fun selectTab(dimension: String) {
        if (_state.value.tab == dimension) return
        _state.update {
            it.copy(
                tab = dimension,
                allBoardsForTab = emptyList(),
                sourceFilter = "modelsage",
                boardsForTab = emptyList(),
                selectedBoard = "",
                period = null,
                periodOptions = emptyList(),
                entries = emptyList(),
                total = 0,
                offline = false,
                loading = true,
            )
        }
        loadBoardsForTab(dimension)
    }

    fun selectBoard(slug: String) {
        if (_state.value.selectedBoard == slug) return
        _state.update {
            it.copy(
                selectedBoard = slug,
                vendorFilter = emptySet(),
                licenseFilter = emptySet(),
            )
        }
        loadVendorOptions(slug)
        refresh(showLoading = true)
    }

    fun setSourceFilter(sourceId: String?) {
        if (_state.value.sourceFilter == sourceId) return
        _state.update {
            it.copy(
                sourceFilter = sourceId,
                selectedBoard = "",
                boardsForTab = emptyList(),
                entries = emptyList(),
                total = 0,
                vendorFilter = emptySet(),
                licenseFilter = emptySet(),
                loading = true,
            )
        }
        loadBoardsForTab(_state.value.tab)
    }

    fun setSort(sort: String) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        refresh(showLoading = true)
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            refresh(showLoading = _state.value.entries.isEmpty())
        }
    }

    fun setVendorFilter(value: String) {
        _state.update { it.copy(vendorFilter = it.vendorFilter.toggle(value)) }
        refresh(showLoading = true)
    }

    fun setLicenseFilter(value: String) {
        _state.update { it.copy(licenseFilter = it.licenseFilter.toggle(value)) }
        refresh(showLoading = true)
    }

    fun clearValueFilters() {
        val changed = _state.value.vendorFilter.isNotEmpty() ||
            _state.value.licenseFilter.isNotEmpty()
        if (!changed) return
        _state.update {
            it.copy(
                vendorFilter = emptySet(),
                licenseFilter = emptySet(),
            )
        }
        refresh(showLoading = true)
    }

    fun setPeriod(value: String) {
        if (_state.value.period == value) return
        _state.update { it.copy(period = value) }
        refresh(showLoading = true)
    }

    fun refresh(showLoading: Boolean = false) {
        loadJob?.cancel()
        val board = _state.value.selectedBoard
        if (board.isEmpty()) return
        loadJob = viewModelScope.launch {
            if (showLoading) _state.update { it.copy(loading = true) }
            _state.update { it.copy(refreshing = true) }
            fetchPage(board, reset = true)
            _state.update { it.copy(loading = false, refreshing = false) }
        }
    }

    fun refreshFromUser() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val updateResult = runCatching { repository.refreshSnapshot() }.getOrNull()
            _state.update {
                it.copy(
                    snapshotInfo = repository.snapshotInfo(),
                    updateMessage = updateResult?.message.orEmpty(),
                )
            }
            if (updateResult?.status == SnapshotUpdateStatus.UPDATED) {
                _state.update { it.copy(selectedBoard = "") }
                loadBoardsForTab(_state.value.tab)
            } else {
                fetchPage(_state.value.selectedBoard, reset = true)
            }
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.refreshing || s.entries.size >= s.total || s.selectedBoard.isEmpty()) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            fetchPage(s.selectedBoard, reset = false)
            _state.update { it.copy(loadingMore = false) }
        }
    }

    private fun loadBoardsForTab(dimension: String) {
        viewModelScope.launch {
            if (dimension in Periods.supported) {
                val options = runCatching { repository.periodOptions(dimension) }
                    .getOrDefault(emptyList())
                if (options.isEmpty()) {
                    _state.update { it.copy(loading = false, entries = emptyList(), total = 0) }
                    return@launch
                }
                val selectedPeriod = _state.value.period
                    ?.takeIf { value -> options.any { it.id == value } }
                    ?: options.first().id
                val board = BoardDto(
                    slug = "overall",
                    name = "${Periods.label(dimension, selectedPeriod)}综合榜",
                    dimension = dimension,
                )
                _state.update {
                    it.copy(
                        period = selectedPeriod,
                        periodOptions = options,
                        selectedBoard = board.slug,
                        allBoardsForTab = listOf(board),
                        boardsForTab = listOf(board),
                        loading = false,
                    )
                }
                loadVendorOptions(board.slug)
                refresh(showLoading = true)
                return@launch
            }
            val allBoards = repository.boardsForDimension(dimension) ?: FALLBACK_BOARDS[dimension].orEmpty()
            val preferredSource = _state.value.sourceFilter
            val filtered = allBoards.filter { board ->
                preferredSource == null || board.sourceId == preferredSource
            }
            val effectiveSource = when {
                filtered.isNotEmpty() -> preferredSource
                allBoards.isNotEmpty() -> null
                else -> preferredSource
            }
            val displayedBoards = when (effectiveSource) {
                null -> allBoards
                else -> filtered
            }
            val sortedBoards = displayedBoards.sortedWith(
                compareByDescending { it.sourceId.equals("modelsage", ignoreCase = true) },
            )
            _state.update {
                it.copy(
                    allBoardsForTab = sortedBoards,
                    boardsForTab = sortedBoards,
                    sourceFilter = effectiveSource,
                    loading = false,
                )
            }
            if (sortedBoards.isNotEmpty() && _state.value.selectedBoard.isEmpty()) {
                selectBoard(sortedBoards.first().slug)
            }
        }
    }

    private fun loadVendorOptions(boardSlug: String) {
        viewModelScope.launch {
            val s = _state.value
            val options = runCatching {
                repository.vendorOptions(
                    boardSlug = boardSlug,
                    periodDimension = s.tab.takeIf { it in Periods.supported },
                    period = s.period,
                )
            }.getOrDefault(emptyList())
            _state.update { it.copy(vendorOptions = options) }
        }
    }

    private suspend fun fetchPage(boardSlug: String, reset: Boolean) {
        val s = _state.value
        val key = boardSlug to s.query
        loadKey = key
        val offset = if (reset) 0 else s.entries.size
        val response = repository.page(
            boardSlug = boardSlug,
            sort = s.sort,
            query = s.query,
            vendor = s.vendorFilter,
            license = s.licenseFilter,
            limit = com.ai.rankboard.data.PAGE_SIZE,
            offset = offset,
            periodDimension = s.tab.takeIf { it in Periods.supported },
            period = s.period,
        )
        if (loadKey != key) return
        if (response != null) {
            val combined = if (reset) {
                response.entries
            } else {
                val existing = s.entries.map { it.slug }.toSet()
                s.entries + response.entries.filterNot { it.slug in existing }
            }
            _state.update {
                it.copy(
                    entries = combined,
                    total = response.total,
                    scoreMin = response.scoreMin,
                    scoreMax = response.scoreMax,
                    offline = false,
                )
            }
            if (reset) {
                runCatching { repository.saveEntries(boardSlug, response.entries) }
            } else {
                runCatching { repository.appendEntries(boardSlug, response.entries) }
            }
        } else {
            if (reset) {
                val cached = runCatching { repository.cachedEntries(boardSlug) }.getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        entries = cached.map { c -> c.toDto() },
                        total = cached.size,
                        scoreMin = null,
                        scoreMax = null,
                        offline = true,
                    )
                }
            } else {
                _state.update { it.copy(offline = true) }
            }
        }
    }

    private fun com.ai.rankboard.data.CachedEntryEntity.toDto() = EntryDto(
        slug = modelSlug,
        displayName = displayName,
        vendor = vendor,
        rank = rank,
        score = score,
        priceIn = priceIn,
        priceOut = priceOut,
        currency = currency,
        fetchedAt = fetchedAt,
    )

    companion object {
        fun factory(app: RankboardApp, initialTab: String = "overall"): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(app.repository, initialTab) }
        }
    }
}

private val FALLBACK_BOARDS = mapOf(
    "overall" to listOf(BoardDto("overall", "综合能力", "overall")),
    "coding" to listOf(
        BoardDto("coding", "ModelSage 代码能力", "coding", sourceId = "modelsage"),
        BoardDto("livebench-coding", "LiveBench 代码", "coding", sourceId = "livebench"),
    ),
    "writing" to listOf(BoardDto("arena-text", "Arena 写作盲测", "writing")),
    "multimodal" to listOf(
        BoardDto("arena-vision", "Arena 视觉理解", "multimodal"),
        BoardDto("arena-image", "Arena 图像生成", "multimodal"),
        BoardDto("arena-video", "Arena 视频生成", "multimodal"),
    ),
    "math" to listOf(BoardDto("livebench-math", "LiveBench 数学", "math", sourceId = "livebench")),
    "analysis" to listOf(BoardDto("livebench-data-analysis", "LiveBench 数据分析", "analysis", sourceId = "livebench")),
)

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
