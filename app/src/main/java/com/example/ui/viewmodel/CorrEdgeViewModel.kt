package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirestoreRepository
import com.example.data.firebase.SavedParlayDoc
import com.example.data.firebase.SavedPropDoc
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.NbaIntelRepository
import com.example.data.repository.PlayerResearchRepository
import com.example.data.repository.SportsMarketRepository
import com.example.domain.math.PlayerPropSimEngine
import com.example.domain.math.QuantCorrelationEngine
import com.example.domain.model.CorrelatedParlay
import com.example.domain.model.CorrelationLink
import com.example.domain.model.DevigMethod
import com.example.domain.model.EventState
import com.example.domain.model.FeedStatus
import com.example.domain.model.GameEvent
import com.example.domain.model.League
import com.example.domain.model.MarketLeg
import com.example.domain.model.MarketType
import com.example.domain.model.NbaIntelBundle
import com.example.domain.model.OddsFormat
import com.example.domain.model.ParlaySizePreference
import com.example.domain.model.PlayerInjuryInfo
import com.example.domain.model.PlayerPropPick
import com.example.domain.model.PlayerResearchProfile
import com.example.domain.model.PreviousGameAvailability
import com.example.domain.model.PreviousGameStatEntry
import com.example.domain.model.PropSimScenario
import com.example.domain.model.PropSimulationResult
import com.example.domain.model.QuantFilterPreferences
import com.example.domain.model.ResearchCandidatePlayer
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class EvSortMode(val label: String) {
    HIGHEST_EV("Highest +EV%"),
    HIGHEST_PROB("Highest Prob%"),
    SOONEST("Matchup")
}

enum class PropDirectionFilter(val label: String) {
    ALL("All Props"),
    MORE("Take MORE"),
    LESS("Take LESS")
}

enum class PropsTabMode(val label: String) {
    BOARD("Props Board"),
    RESEARCH("Player Research")
}

enum class LiveSlateSubTab(val label: String) {
    SCORES("Live Scores"),
    NBA_NEWS("NBA News & Week")
}

sealed interface CloudWatchlistState {
    data object Loading : CloudWatchlistState
    data class Ready(
        val parlays: List<SavedParlayDoc>,
        val props: List<SavedPropDoc>
    ) : CloudWatchlistState

    data class Error(val message: String) : CloudWatchlistState
}

data class PlayerResearchUiState(
    val tabMode: PropsTabMode = PropsTabMode.BOARD,
    val leagueFilter: League? = null,
    val upcomingEvents: List<GameEvent> = emptyList(),
    val selectedGame: GameEvent? = null,
    val matchupInjuries: List<PlayerInjuryInfo> = emptyList(),
    val injuriesLoading: Boolean = false,
    val candidates: List<ResearchCandidatePlayer> = emptyList(),
    val candidatesLoading: Boolean = false,
    val activeProfile: PlayerResearchProfile? = null,
    val selectedPropLabel: String? = null,
    val customLineValue: Double? = null,
    val selectedPreviousGameId: String? = null,
    val previousGameAvailability: PreviousGameAvailability? = null,
    val simScenario: PropSimScenario = PropSimScenario.BASELINE,
    val simRunCounter: Int = 0,
    val propSimulation: PropSimulationResult? = null
)

data class CorrEdgeUiState(
    val feedStatus: FeedStatus = FeedStatus(isLoading = true),
    val preferences: QuantFilterPreferences = QuantFilterPreferences(),
    val allEvents: List<GameEvent> = emptyList(),
    val allLegs: List<MarketLeg> = emptyList(),
    val allProps: List<PlayerPropPick> = emptyList(),
    val topThreeParlays: List<CorrelatedParlay> = emptyList(),
    val runnerUpParlays: List<CorrelatedParlay> = emptyList(),
    val watchlistState: CloudWatchlistState = CloudWatchlistState.Loading,
    val savedParlayDocs: List<SavedParlayDoc> = emptyList(),
    val savedPropDocs: List<SavedPropDoc> = emptyList(),
    val selectedScannerLeague: League? = null,
    val selectedPropsLeague: League? = null,
    val propDirectionFilter: PropDirectionFilter = PropDirectionFilter.ALL,
    val selectedSlateLeague: League? = League.NBA,
    val evSortMode: EvSortMode = EvSortMode.HIGHEST_PROB,
    val sandboxLegs: List<MarketLeg> = emptyList(),
    val sandboxResult: CorrelatedParlay? = null,
    val activeDetailParlay: CorrelatedParlay? = null,
    val selectedGameForDetail: GameEvent? = null,
    val selectedGameParlays: List<CorrelatedParlay> = emptyList(),
    val selectedGameProps: List<PlayerPropPick> = emptyList(),
    val selectedGameLines: List<MarketLeg> = emptyList(),
    val selectedPropForDetail: PlayerPropPick? = null,
    val selectedPropDetailEvent: GameEvent? = null,
    val selectedPropDetailProfile: PlayerResearchProfile? = null,
    val selectedPropDetailInjuries: List<PlayerInjuryInfo> = emptyList(),
    val selectedPropDetailLoading: Boolean = false,
    val researchState: PlayerResearchUiState = PlayerResearchUiState(),
    val liveSlateSubTab: LiveSlateSubTab = LiveSlateSubTab.SCORES,
    val nbaIntel: NbaIntelBundle = NbaIntelBundle(isLoading = true),
    val autoRefreshEnabled: Boolean = true,
    val firestoreActionError: String? = null
)

class CorrEdgeViewModel(
    private val currentUserId: String,
    private val displayName: String,
    private val marketRepo: SportsMarketRepository,
    private val firestoreRepo: FirestoreRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val researchRepo: PlayerResearchRepository = PlayerResearchRepository(),
    private val nbaIntelRepo: NbaIntelRepository = NbaIntelRepository()
) : ViewModel() {

    private data class PropDetailContext(
        val prop: PlayerPropPick? = null,
        val profile: PlayerResearchProfile? = null,
        val injuries: List<PlayerInjuryInfo> = emptyList(),
        val isLoading: Boolean = false
    )

    private val rawEventsFlow = MutableStateFlow<List<GameEvent>>(emptyList())
    private val rawLegsFlow = MutableStateFlow<List<MarketLeg>>(emptyList())
    private val rawPropsFlow = MutableStateFlow<List<PlayerPropPick>>(emptyList())
    private val feedStatusFlow = MutableStateFlow(FeedStatus(isLoading = true))

    private val scannerLeagueFlow = MutableStateFlow<League?>(null)
    private val propsLeagueFlow = MutableStateFlow<League?>(null)
    private val propDirectionFlow = MutableStateFlow(PropDirectionFilter.ALL)
    private val slateLeagueFlow = MutableStateFlow<League?>(League.NBA)
    private val evSortFlow = MutableStateFlow(EvSortMode.HIGHEST_PROB)
    private val sandboxLegsFlow = MutableStateFlow<List<MarketLeg>>(emptyList())
    private val detailParlayIdFlow = MutableStateFlow<String?>(null)
    private val detailParlayCustomFlow = MutableStateFlow<CorrelatedParlay?>(null)
    private val selectedGameFlow = MutableStateFlow<GameEvent?>(null)
    private val propDetailContextFlow = MutableStateFlow(PropDetailContext())
    private val researchStateFlow = MutableStateFlow(PlayerResearchUiState())
    private val liveSlateSubTabFlow = MutableStateFlow(LiveSlateSubTab.SCORES)
    private val nbaIntelFlow = MutableStateFlow(NbaIntelBundle(isLoading = true))
    private val autoRefreshEnabledFlow = MutableStateFlow(true)
    private val firestoreActionErrorFlow = MutableStateFlow<String?>(null)

    private var userSelectedSlateLeague = false
    private var researchGameJob: Job? = null
    private var researchPlayerJob: Job? = null
    private var previousBoxscoreJob: Job? = null
    private var propDetailJob: Job? = null
    private var nbaIntelJob: Job? = null

    private val cloudWatchlistFlow: Flow<CloudWatchlistState> = combine(
        firestoreRepo.observeSavedParlays(currentUserId),
        firestoreRepo.observeSavedProps(currentUserId)
    ) { parlays, props ->
        CloudWatchlistState.Ready(parlays = parlays, props = props) as CloudWatchlistState
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        emit(
            CloudWatchlistState.Error(
                firestoreError?.localizedMessage
                    ?: error.localizedMessage
                    ?: "Cloud watchlist sync error."
            )
        )
    }

    private data class ComputedMarketBundle(
        val topThree: List<CorrelatedParlay>,
        val runnerUps: List<CorrelatedParlay>,
        val sandboxResult: CorrelatedParlay?,
        val gameParlays: List<CorrelatedParlay>,
        val gameProps: List<PlayerPropPick>,
        val gameLines: List<MarketLeg>,
        val hydratedProps: List<PlayerPropPick>
    )

    private val computedFlow = combine(
        rawLegsFlow,
        rawPropsFlow,
        prefsRepo.preferencesFlow,
        cloudWatchlistFlow.map { state ->
            when (state) {
                is CloudWatchlistState.Ready ->
                    state.parlays.map { it.id }.toSet() to state.props.map { it.id }.toSet()
                else -> emptySet<String>() to emptySet<String>()
            }
        },
        sandboxLegsFlow,
        selectedGameFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val legs = values[0] as List<MarketLeg>
        @Suppress("UNCHECKED_CAST")
        val props = values[1] as List<PlayerPropPick>
        val prefs = values[2] as QuantFilterPreferences
        @Suppress("UNCHECKED_CAST")
        val savedPair = values[3] as Pair<Set<String>, Set<String>>
        val savedParlayDocIds = savedPair.first
        val savedPropDocIds = savedPair.second
        @Suppress("UNCHECKED_CAST")
        val sandboxLegs = values[4] as List<MarketLeg>
        val selectedGame = values[5] as GameEvent?

        withContext(Dispatchers.Default) {
            val ranked = QuantCorrelationEngine.buildTopCorrelatedParlays(
                legs = legs,
                prefs = prefs,
                savedParlayIds = emptySet()
            ).map { parlay ->
                parlay.copy(isSaved = firestoreRepo.safeDocId(parlay.id) in savedParlayDocIds)
            }

            val sandboxResult = buildSandboxParlay(sandboxLegs, savedParlayDocIds)
            val hydratedProps = props.map { prop ->
                prop.copy(isSaved = firestoreRepo.safeDocId(prop.id) in savedPropDocIds)
            }

            val gameParlays = if (selectedGame != null) {
                val gameLegs = legs.filter { it.eventId == selectedGame.id }
                QuantCorrelationEngine.buildTopCorrelatedParlays(
                    legs = gameLegs,
                    prefs = prefs.copy(
                        enabledLeagues = setOf(selectedGame.league),
                        minHitProbability = 0.25,
                        minCorrelationRho = 0.12
                    ),
                    savedParlayIds = emptySet()
                ).map { parlay ->
                    parlay.copy(isSaved = firestoreRepo.safeDocId(parlay.id) in savedParlayDocIds)
                }.take(6)
            } else emptyList()

            val gameProps = if (selectedGame != null) {
                hydratedProps.filter { it.eventId == selectedGame.id }
            } else emptyList()

            val gameLines = if (selectedGame != null) {
                legs.filter {
                    it.eventId == selectedGame.id &&
                        it.marketType != MarketType.PLAYER_PROP_MORE &&
                        it.marketType != MarketType.PLAYER_PROP_LESS
                }
            } else emptyList()

            ComputedMarketBundle(
                topThree = ranked.take(3),
                runnerUps = ranked.drop(3),
                sandboxResult = sandboxResult,
                gameParlays = gameParlays,
                gameProps = gameProps,
                gameLines = gameLines,
                hydratedProps = hydratedProps
            )
        }
    }

    private data class FilterBundle(
        val scannerLeague: League?,
        val propsLeague: League?,
        val propDirection: PropDirectionFilter,
        val slateLeague: League?,
        val evSort: EvSortMode
    )

    private val filterSelectionFlow = combine(
        scannerLeagueFlow,
        propsLeagueFlow,
        propDirectionFlow,
        slateLeagueFlow,
        evSortFlow
    ) { scannerL, propsL, propDir, slateL, sortMode ->
        FilterBundle(scannerL, propsL, propDir, slateL, sortMode)
    }

    private data class SheetBundle(
        val detailId: String?,
        val customDetail: CorrelatedParlay?,
        val selectedGame: GameEvent?,
        val propContext: PropDetailContext,
        val researchState: PlayerResearchUiState,
        val liveSlateSubTab: LiveSlateSubTab,
        val nbaIntel: NbaIntelBundle,
        val autoRefreshEnabled: Boolean,
        val actionError: String?
    )

    private val dialogPairFlow = combine(
        selectedGameFlow,
        propDetailContextFlow
    ) { selectedGame, propCtx ->
        selectedGame to propCtx
    }

    private val liveHubMetaFlow = combine(
        liveSlateSubTabFlow,
        nbaIntelFlow,
        autoRefreshEnabledFlow,
        firestoreActionErrorFlow
    ) { subTab, nba, autoRefresh, actionError ->
        Quadruple(subTab, nba, autoRefresh, actionError)
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    private val sheetBundleFlow = combine(
        detailParlayIdFlow,
        detailParlayCustomFlow,
        dialogPairFlow,
        researchStateFlow,
        liveHubMetaFlow
    ) { detailId, customDetail, dialogPair, research, liveMeta ->
        SheetBundle(
            detailId = detailId,
            customDetail = customDetail,
            selectedGame = dialogPair.first,
            propContext = dialogPair.second,
            researchState = research,
            liveSlateSubTab = liveMeta.first,
            nbaIntel = liveMeta.second,
            autoRefreshEnabled = liveMeta.third,
            actionError = liveMeta.fourth
        )
    }

    val uiState: StateFlow<CorrEdgeUiState> = combine(
        feedStatusFlow,
        prefsRepo.preferencesFlow,
        rawEventsFlow,
        rawLegsFlow,
        computedFlow,
        cloudWatchlistFlow,
        filterSelectionFlow,
        sandboxLegsFlow,
        sheetBundleFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val status = values[0] as FeedStatus
        val prefs = values[1] as QuantFilterPreferences
        @Suppress("UNCHECKED_CAST")
        val events = values[2] as List<GameEvent>
        @Suppress("UNCHECKED_CAST")
        val legs = values[3] as List<MarketLeg>
        val computed = values[4] as ComputedMarketBundle
        val watchlist = values[5] as CloudWatchlistState
        val filters = values[6] as FilterBundle
        @Suppress("UNCHECKED_CAST")
        val sandboxLegs = values[7] as List<MarketLeg>
        val sheets = values[8] as SheetBundle

        val savedParlayDocs = (watchlist as? CloudWatchlistState.Ready)?.parlays.orEmpty()
        val savedPropDocs = (watchlist as? CloudWatchlistState.Ready)?.props.orEmpty()
        val savedParlayIds = savedParlayDocs.map { it.id }.toSet()
        val savedPropIds = savedPropDocs.map { it.id }.toSet()

        val allRanked = computed.topThree + computed.runnerUps
        val activeDetail = when {
            sheets.detailId != null -> allRanked.find { it.id == sheets.detailId }
                ?: computed.gameParlays.find { it.id == sheets.detailId }
                ?: computed.sandboxResult?.takeIf { it.id == sheets.detailId }
                ?: sheets.customDetail?.takeIf { it.id == sheets.detailId }
            else -> null
        }?.let { detail ->
            detail.copy(isSaved = firestoreRepo.safeDocId(detail.id) in savedParlayIds)
        }

        val activePropDetail = sheets.propContext.prop?.let { p ->
            p.copy(isSaved = firestoreRepo.safeDocId(p.id) in savedPropIds)
        }
        val propDetailEvent = activePropDetail?.let { p ->
            events.firstOrNull { it.id == p.eventId }
        }

        val upcomingAll = filterUpcomingEvents(events, prefs.enabledLeagues)
        val syncedResearch = sheets.researchState.copy(upcomingEvents = upcomingAll)

        CorrEdgeUiState(
            feedStatus = status,
            preferences = prefs,
            allEvents = events,
            allLegs = legs,
            allProps = computed.hydratedProps,
            topThreeParlays = computed.topThree,
            runnerUpParlays = computed.runnerUps,
            watchlistState = watchlist,
            savedParlayDocs = savedParlayDocs,
            savedPropDocs = savedPropDocs,
            selectedScannerLeague = filters.scannerLeague,
            selectedPropsLeague = filters.propsLeague,
            propDirectionFilter = filters.propDirection,
            selectedSlateLeague = filters.slateLeague,
            evSortMode = filters.evSort,
            sandboxLegs = sandboxLegs,
            sandboxResult = computed.sandboxResult,
            activeDetailParlay = activeDetail,
            selectedGameForDetail = sheets.selectedGame,
            selectedGameParlays = computed.gameParlays,
            selectedGameProps = computed.gameProps,
            selectedGameLines = computed.gameLines,
            selectedPropForDetail = activePropDetail,
            selectedPropDetailEvent = propDetailEvent,
            selectedPropDetailProfile = sheets.propContext.profile,
            selectedPropDetailInjuries = sheets.propContext.injuries,
            selectedPropDetailLoading = sheets.propContext.isLoading,
            researchState = syncedResearch,
            liveSlateSubTab = sheets.liveSlateSubTab,
            nbaIntel = sheets.nbaIntel,
            autoRefreshEnabled = sheets.autoRefreshEnabled,
            firestoreActionError = sheets.actionError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CorrEdgeUiState()
    )

    init {
        viewModelScope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            firestoreRepo.ensureUserProfile(
                displayName = displayName,
                preferredFormat = prefs.oddsFormat.name
            )
            val cached = marketRepo.loadCachedSnapshot()
            if (cached != null) {
                rawEventsFlow.value = cached.events
                rawLegsFlow.value = cached.legs
                rawPropsFlow.value = cached.props
                feedStatusFlow.value = cached.status.copy(isLoading = true)
                syncDefaultSlateLeague(cached.events)
                maybeAutoSelectFirstResearchGame(cached.events)
            }
            refreshMarketDataInternal(silent = false)
            refreshNbaIntel(forceRefresh = false)
            startAutoRefreshLoop()
        }
    }

    private fun syncDefaultSlateLeague(events: List<GameEvent>) {
        if (userSelectedSlateLeague || events.isEmpty()) return
        val current = slateLeagueFlow.value
        if (current != null && events.any { it.league == current }) return
        events.firstOrNull()?.league?.let { slateLeagueFlow.value = it }
    }

    private fun maybeAutoSelectFirstResearchGame(events: List<GameEvent>) {
        val upcoming = filterUpcomingEvents(events, League.entries.toSet())
        val currentId = researchStateFlow.value.selectedGame?.id
        if (currentId != null && upcoming.any { it.id == currentId }) return
        val firstWithProps = upcoming.firstOrNull { ev ->
            rawPropsFlow.value.any { it.eventId == ev.id }
        } ?: upcoming.firstOrNull()
        if (firstWithProps != null) {
            selectResearchGame(firstWithProps)
        }
    }

    private fun filterUpcomingEvents(
        events: List<GameEvent>,
        enabledLeagues: Set<League>
    ): List<GameEvent> {
        return events.filter { ev ->
            ev.league in enabledLeagues && ev.state != EventState.FINAL
        }.sortedBy { parseIsoMillis(it.startTimeIso) ?: Long.MAX_VALUE }
    }

    private fun parseIsoMillis(iso: String): Long? {
        if (iso.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mmXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                return fmt.parse(iso)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun startAutoRefreshLoop() {
        viewModelScope.launch {
            var tickCount = 0
            while (isActive) {
                delay(12_000L)
                if (!autoRefreshEnabledFlow.value) continue
                tickCount++
                refreshLiveScoresInternal()
                if (tickCount % 6 == 0) {
                    refreshMarketDataInternal(silent = true)
                    refreshNbaIntel(forceRefresh = true, silent = true)
                }
            }
        }
    }

    private suspend fun refreshLiveScoresInternal() {
        runCatching {
            val prefs = prefsRepo.preferencesFlow.first()
            val (updatedEvents, updatedAt) = marketRepo.refreshLiveScoresOnly(
                enabledLeagues = prefs.enabledLeagues,
                currentEvents = rawEventsFlow.value
            )
            if (updatedEvents.isNotEmpty()) {
                rawEventsFlow.value = updatedEvents
                feedStatusFlow.update { status ->
                    status.copy(
                        lastUpdatedMillis = updatedAt,
                        espnEventsCount = updatedEvents.size,
                        activeLeagues = updatedEvents.map { it.league }.distinct()
                    )
                }
            }
        }
    }

    fun toggleAutoRefresh() {
        autoRefreshEnabledFlow.update { !it }
    }

    fun refreshLiveScoresNow() {
        viewModelScope.launch {
            refreshLiveScoresInternal()
        }
    }

    fun setLiveSlateSubTab(tab: LiveSlateSubTab) {
        liveSlateSubTabFlow.value = tab
        if (tab == LiveSlateSubTab.NBA_NEWS && nbaIntelFlow.value.lastUpdatedMillis == null) {
            refreshNbaIntel(forceRefresh = false)
        }
    }

    fun refreshNbaIntel(forceRefresh: Boolean = true, silent: Boolean = false) {
        nbaIntelJob?.cancel()
        nbaIntelJob = viewModelScope.launch {
            if (!silent) {
                nbaIntelFlow.update { it.copy(isLoading = true, errorMessage = null) }
            }
            runCatching {
                nbaIntelRepo.fetchNbaIntel(
                    activeNbaProps = rawPropsFlow.value,
                    forceRefresh = forceRefresh
                )
            }.onSuccess { bundle ->
                nbaIntelFlow.value = bundle
            }.onFailure { err ->
                if (err is CancellationException) throw err
                nbaIntelFlow.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Could not refresh NBA News & Weekly outlook."
                    )
                }
            }
        }
    }

    fun refreshMarketData() {
        viewModelScope.launch {
            refreshMarketDataInternal(silent = false)
        }
    }

    private suspend fun refreshMarketDataInternal(silent: Boolean) {
        if (!silent) {
            feedStatusFlow.update { it.copy(isLoading = true, errorMessage = null) }
        }
        try {
            val prefs = prefsRepo.preferencesFlow.first()
            val collected = marketRepo.refreshAllMarketData(
                enabledLeagues = prefs.enabledLeagues,
                devigMethod = prefs.devigMethod
            )
            rawEventsFlow.value = collected.events
            rawLegsFlow.value = collected.legs
            rawPropsFlow.value = collected.props
            feedStatusFlow.value = collected.status
            syncDefaultSlateLeague(collected.events)
            maybeAutoSelectFirstResearchGame(collected.events)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (!silent) {
                feedStatusFlow.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Live feed sync failed: ${t.localizedMessage ?: "Network unavailable"}"
                    )
                }
            }
        }
    }

    // --- Player Research ("Props Madness") Actions ---

    fun setPropsTabMode(mode: PropsTabMode) {
        researchStateFlow.update { it.copy(tabMode = mode) }
        if (mode == PropsTabMode.RESEARCH && researchStateFlow.value.selectedGame == null) {
            maybeAutoSelectFirstResearchGame(rawEventsFlow.value)
        }
    }

    fun setResearchLeagueFilter(league: League?) {
        researchStateFlow.update { it.copy(leagueFilter = league) }
        val filtered = uiState.value.researchState.upcomingEvents
            .filter { league == null || it.league == league }
        val current = researchStateFlow.value.selectedGame
        if (current == null || (league != null && current.league != league)) {
            filtered.firstOrNull()?.let { selectResearchGame(it) }
        }
    }

    fun selectResearchGame(
        event: GameEvent,
        preferredPlayerId: String? = null,
        preferredStatLabel: String? = null,
        preferredLine: Double? = null
    ) {
        researchGameJob?.cancel()
        researchStateFlow.update {
            it.copy(
                selectedGame = event,
                injuriesLoading = true,
                candidatesLoading = true,
                matchupInjuries = emptyList(),
                candidates = emptyList(),
                activeProfile = null,
                selectedPreviousGameId = null,
                previousGameAvailability = null
            )
        }

        researchGameJob = viewModelScope.launch {
            val injuriesDeferred = async { researchRepo.fetchMatchupInjuries(event) }
            val candidatesDeferred = async {
                researchRepo.fetchGameResearchCandidates(event, rawPropsFlow.value)
            }
            val injuries = injuriesDeferred.await()
            val candidates = candidatesDeferred.await()

            researchStateFlow.update {
                it.copy(
                    matchupInjuries = injuries,
                    injuriesLoading = false,
                    candidates = candidates,
                    candidatesLoading = false
                )
            }

            val target = candidates.firstOrNull { it.playerId == preferredPlayerId }
                ?: candidates.firstOrNull()
            if (target != null) {
                selectResearchCandidate(
                    candidate = target,
                    preferredStatLabel = preferredStatLabel,
                    preferredLine = preferredLine
                )
            }
        }
    }

    fun selectResearchCandidate(
        candidate: ResearchCandidatePlayer,
        preferredStatLabel: String? = null,
        preferredLine: Double? = null
    ) {
        researchPlayerJob?.cancel()
        previousBoxscoreJob?.cancel()
        researchStateFlow.update {
            it.copy(
                activeProfile = PlayerResearchProfile(
                    playerId = candidate.playerId,
                    playerName = candidate.playerName,
                    teamAbbr = candidate.teamAbbr,
                    league = candidate.league,
                    position = candidate.position,
                    headshotUrl = candidate.headshotUrl,
                    upcomingEventId = candidate.eventId,
                    matchupShort = candidate.matchupShort,
                    propLinesByLabel = emptyMap(),
                    gameLogs = emptyList(),
                    isLoading = true
                ),
                selectedPropLabel = preferredStatLabel,
                customLineValue = preferredLine,
                selectedPreviousGameId = null,
                previousGameAvailability = null,
                propSimulation = null
            )
        }

        researchPlayerJob = viewModelScope.launch {
            val profile = researchRepo.fetchPlayerResearchProfile(
                candidate = candidate,
                matchingProps = rawPropsFlow.value
            )
            val chosenLabel = preferredStatLabel?.takeIf { lbl ->
                profile.propLinesByLabel.containsKey(lbl) ||
                    profile.gameLogs.any { it.propValues.containsKey(lbl) }
            } ?: candidate.primaryProp?.statLabel?.takeIf { lbl ->
                profile.propLinesByLabel.containsKey(lbl)
            } ?: profile.propLinesByLabel.keys.firstOrNull()

            val chosenLine = preferredLine ?: chosenLabel?.let { profile.propLinesByLabel[it] }

            researchStateFlow.update {
                withRecomputedSimulation(
                    it.copy(
                        activeProfile = profile,
                        selectedPropLabel = chosenLabel,
                        customLineValue = chosenLine
                    )
                )
            }

            // Auto-load "Who Played / Who Didn't Play" for the most recent previous game
            profile.gameLogs.lastOrNull()?.let { latestGame ->
                selectPreviousGameBar(latestGame)
            }
        }
    }

    fun openPlayerResearchFromProp(prop: PlayerPropPick) {
        val game = rawEventsFlow.value.firstOrNull { it.id == prop.eventId }
        researchStateFlow.update {
            it.copy(
                tabMode = PropsTabMode.RESEARCH,
                leagueFilter = prop.league
            )
        }
        if (game != null) {
            selectResearchGame(
                event = game,
                preferredPlayerId = prop.playerId,
                preferredStatLabel = prop.statLabel,
                preferredLine = prop.lineValue
            )
        } else {
            val fallbackCandidate = ResearchCandidatePlayer(
                playerId = prop.playerId,
                playerName = prop.playerName,
                teamAbbr = prop.teamAbbr,
                position = prop.position,
                headshotUrl = prop.headshotUrl,
                eventId = prop.eventId,
                league = prop.league,
                matchupShort = prop.matchupShort,
                primaryProp = prop
            )
            selectResearchCandidate(
                candidate = fallbackCandidate,
                preferredStatLabel = prop.statLabel,
                preferredLine = prop.lineValue
            )
        }
    }

    fun openPlayerResearchForGame(event: GameEvent) {
        researchStateFlow.update {
            it.copy(
                tabMode = PropsTabMode.RESEARCH,
                leagueFilter = event.league
            )
        }
        selectResearchGame(event)
    }

    fun selectResearchPropStat(statLabel: String) {
        researchStateFlow.update { state ->
            val line = state.activeProfile?.propLinesByLabel?.get(statLabel)
                ?: state.activeProfile?.gameLogs?.mapNotNull { it.propValues[statLabel] }
                    ?.takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.let { (kotlin.math.round(it * 2.0) / 2.0).let { r -> if (r % 1.0 == 0.0) (r - 0.5).coerceAtLeast(0.5) else r } }
            withRecomputedSimulation(
                state.copy(
                    selectedPropLabel = statLabel,
                    customLineValue = line
                )
            )
        }
    }

    fun adjustResearchLine(delta: Double) {
        researchStateFlow.update { state ->
            val current = state.customLineValue
                ?: state.selectedPropLabel?.let { state.activeProfile?.propLinesByLabel?.get(it) }
                ?: return@update state
            val next = (current + delta).coerceAtLeast(0.5)
            withRecomputedSimulation(state.copy(customLineValue = next))
        }
    }

    fun setResearchCustomLine(line: Double) {
        researchStateFlow.update { state ->
            withRecomputedSimulation(state.copy(customLineValue = line.coerceAtLeast(0.5)))
        }
    }

    fun setResearchSimScenario(scenario: PropSimScenario) {
        researchStateFlow.update { state ->
            withRecomputedSimulation(state.copy(simScenario = scenario))
        }
    }

    fun rerunResearchSimulation() {
        researchStateFlow.update { state ->
            withRecomputedSimulation(state.copy(simRunCounter = state.simRunCounter + 1))
        }
    }

    private fun withRecomputedSimulation(state: PlayerResearchUiState): PlayerResearchUiState {
        val profile = state.activeProfile
        val statLabel = state.selectedPropLabel
        if (profile == null || profile.isLoading || statLabel == null) {
            return state.copy(propSimulation = null)
        }
        val activeLine = state.customLineValue ?: profile.propLinesByLabel[statLabel] ?: 0.5
        val historical = profile.gameLogs.mapNotNull { it.propValues[statLabel] }
        val result = PlayerPropSimEngine.simulatePropOutcomes(
            statLabel = statLabel,
            historicalValues = historical,
            lineValue = activeLine,
            upcomingGame = state.selectedGame,
            playerTeamAbbr = profile.teamAbbr,
            matchupInjuries = state.matchupInjuries,
            scenario = state.simScenario,
            runId = state.simRunCounter
        )
        return state.copy(propSimulation = result)
    }

    fun selectPreviousGameBar(entry: PreviousGameStatEntry) {
        val league = researchStateFlow.value.activeProfile?.league ?: return
        previousBoxscoreJob?.cancel()
        researchStateFlow.update {
            it.copy(
                selectedPreviousGameId = entry.eventId,
                previousGameAvailability = PreviousGameAvailability(
                    eventId = entry.eventId,
                    matchSummary = "${entry.opponentAbbr} (${entry.dateDisplay})",
                    playedPlayers = emptyList(),
                    didNotPlayPlayers = emptyList(),
                    isLoading = true
                )
            )
        }
        previousBoxscoreJob = viewModelScope.launch {
            val availability = researchRepo.fetchPreviousGameAvailability(
                league = league,
                eventId = entry.eventId,
                matchSummaryHint = "${entry.opponentAbbr} (${entry.dateDisplay}) ${entry.scoreResult}".trim()
            )
            researchStateFlow.update {
                it.copy(previousGameAvailability = availability)
            }
        }
    }

    // --- Watchlist & Navigation Actions ---

    fun toggleSaveParlay(parlay: CorrelatedParlay) {
        viewModelScope.launch {
            val result = if (parlay.isSaved) {
                firestoreRepo.deleteSavedParlay(parlay.id)
            } else {
                firestoreRepo.saveParlay(parlay)
            }
            result.onFailure { err ->
                firestoreActionErrorFlow.value =
                    err.localizedMessage ?: "Could not update saved parlay in cloud watchlist."
            }
        }
    }

    fun deleteSavedParlayDoc(docId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteSavedParlay(docId).onFailure { err ->
                firestoreActionErrorFlow.value =
                    err.localizedMessage ?: "Could not remove saved parlay."
            }
        }
    }

    fun toggleSaveProp(prop: PlayerPropPick) {
        viewModelScope.launch {
            val result = if (prop.isSaved) {
                firestoreRepo.deleteSavedProp(prop.id)
            } else {
                firestoreRepo.saveProp(prop)
            }
            result.onFailure { err ->
                firestoreActionErrorFlow.value =
                    err.localizedMessage ?: "Could not update saved player prop in cloud watchlist."
            }
        }
    }

    fun deleteSavedPropDoc(docId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteSavedProp(docId).onFailure { err ->
                firestoreActionErrorFlow.value =
                    err.localizedMessage ?: "Could not remove saved player prop."
            }
        }
    }

    fun openSavedParlayDoc(doc: SavedParlayDoc) {
        val matching = (uiState.value.topThreeParlays + uiState.value.runnerUpParlays)
            .firstOrNull { firestoreRepo.safeDocId(it.id) == doc.id }
        if (matching != null) {
            openParlayDetail(matching)
        }
    }

    fun clearFirestoreError() {
        firestoreActionErrorFlow.value = null
    }

    fun openParlayDetail(parlay: CorrelatedParlay) {
        detailParlayCustomFlow.value = parlay
        detailParlayIdFlow.value = parlay.id
    }

    fun closeParlayDetail() {
        detailParlayIdFlow.value = null
        detailParlayCustomFlow.value = null
    }

    fun openGameDetail(event: GameEvent) {
        selectedGameFlow.value = event
    }

    fun closeGameDetail() {
        selectedGameFlow.value = null
    }

    fun openPropDetail(prop: PlayerPropPick) {
        propDetailJob?.cancel()
        propDetailContextFlow.value = PropDetailContext(
            prop = prop,
            isLoading = true
        )
        val matchingEvent = rawEventsFlow.value.firstOrNull { it.id == prop.eventId }
        val candidate = ResearchCandidatePlayer(
            playerId = prop.playerId,
            playerName = prop.playerName,
            teamAbbr = prop.teamAbbr,
            position = prop.position,
            headshotUrl = prop.headshotUrl,
            eventId = prop.eventId,
            league = prop.league,
            matchupShort = prop.matchupShort,
            primaryProp = prop
        )
        propDetailJob = viewModelScope.launch {
            val injuriesDeferred = async {
                if (matchingEvent != null) researchRepo.fetchMatchupInjuries(matchingEvent) else emptyList()
            }
            val profileDeferred = async {
                researchRepo.fetchPlayerResearchProfile(candidate, rawPropsFlow.value)
            }
            val injuries = injuriesDeferred.await()
            val profile = profileDeferred.await()
            propDetailContextFlow.update { current ->
                if (current.prop?.id == prop.id) {
                    current.copy(profile = profile, injuries = injuries, isLoading = false)
                } else current
            }
        }
    }

    fun openLegPlayerDetail(leg: MarketLeg) {
        val matchingProp = rawPropsFlow.value.firstOrNull { it.id == leg.id }
        if (matchingProp != null) {
            openPropDetail(matchingProp)
        }
    }

    fun openSavedPropDoc(doc: SavedPropDoc) {
        val liveMatch = rawPropsFlow.value.firstOrNull { firestoreRepo.safeDocId(it.id) == doc.id }
        if (liveMatch != null) {
            openPropDetail(liveMatch)
            return
        }
        val league = League.entries.firstOrNull { it.code.equals(doc.leagueCode, ignoreCase = true) } ?: League.NBA
        val recSide = if (doc.recommendedSide.equals("LESS", ignoreCase = true)) {
            com.example.domain.model.PropSide.LESS
        } else {
            com.example.domain.model.PropSide.MORE
        }
        val idParts = doc.id.split("_")
        val parsedEventId = if (idParts.size >= 3 && idParts[0] == "prop") idParts[1] else doc.id
        val parsedPlayerId = if (idParts.size >= 4 && idParts[0] == "prop") idParts[2] else ""
        val thesis = "Proj ${String.format(Locale.US, "%.1f", doc.projectedValue)} vs ${doc.lineValue} ${doc.statLabel} toward ${recSide.label}."
        val synthetic = PlayerPropPick(
            id = doc.id,
            eventId = parsedEventId,
            league = league,
            matchupShort = doc.matchup,
            startTimeDisplay = "Saved Watchlist",
            playerId = parsedPlayerId,
            playerName = doc.playerName,
            teamAbbr = doc.teamAbbr,
            sideTag = "HOME",
            position = "",
            headshotUrl = doc.headshotUrl?.takeIf { it.isNotBlank() },
            statLabel = doc.statLabel,
            lineValue = doc.lineValue,
            projectedValue = doc.projectedValue,
            moreProb = if (recSide == com.example.domain.model.PropSide.MORE) doc.winProb else (1.0 - doc.winProb).coerceIn(0.2, 0.8),
            lessProb = if (recSide == com.example.domain.model.PropSide.LESS) doc.winProb else (1.0 - doc.winProb).coerceIn(0.2, 0.8),
            moreAmericanOdds = doc.moreOdds.toInt(),
            lessAmericanOdds = doc.lessOdds.toInt(),
            moreEvPercent = if (recSide == com.example.domain.model.PropSide.MORE) doc.evPercent else 0.0,
            lessEvPercent = if (recSide == com.example.domain.model.PropSide.LESS) doc.evPercent else 0.0,
            recommendedSide = recSide,
            confidenceThesis = thesis,
            isTeamFavorite = true,
            expectedTotal = league.avgGameTotal,
            expectedHomeMargin = 0.0,
            isSaved = true
        )
        openPropDetail(synthetic)
    }

    fun closePropDetail() {
        propDetailJob?.cancel()
        propDetailContextFlow.value = PropDetailContext()
    }

    fun setScannerLeague(league: League?) {
        scannerLeagueFlow.value = league
    }

    fun setPropsLeague(league: League?) {
        propsLeagueFlow.value = league
    }

    fun setPropDirectionFilter(filter: PropDirectionFilter) {
        propDirectionFlow.value = filter
    }

    fun setSlateLeague(league: League?) {
        userSelectedSlateLeague = true
        slateLeagueFlow.value = league
    }

    fun setEvSortMode(mode: EvSortMode) {
        evSortFlow.value = mode
    }

    fun toggleSandboxLeg(leg: MarketLeg) {
        sandboxLegsFlow.update { current ->
            if (current.any { it.id == leg.id }) {
                current.filterNot { it.id == leg.id }
            } else if (current.size < 3) {
                current + leg
            } else {
                current.drop(1) + leg
            }
        }
    }

    fun togglePropInSandbox(prop: PlayerPropPick) {
        val matchingLeg = rawLegsFlow.value.firstOrNull { it.id == prop.id } ?: return
        toggleSandboxLeg(matchingLeg)
    }

    fun clearSandbox() {
        sandboxLegsFlow.value = emptyList()
    }

    fun toggleLeaguePref(league: League) {
        viewModelScope.launch {
            prefsRepo.toggleLeague(league)
            refreshMarketData()
        }
    }

    fun setMinHitProbability(prob: Double) {
        viewModelScope.launch { prefsRepo.setMinHitProbability(prob) }
    }

    fun setMinCorrelationRho(rho: Double) {
        viewModelScope.launch { prefsRepo.setMinCorrelationRho(rho) }
    }

    fun setParlaySizePreference(size: ParlaySizePreference) {
        viewModelScope.launch { prefsRepo.setParlaySizePreference(size) }
    }

    fun setDevigMethod(method: DevigMethod) {
        viewModelScope.launch {
            prefsRepo.setDevigMethod(method)
            refreshMarketData()
        }
    }

    fun setOddsFormat(format: OddsFormat) {
        viewModelScope.launch {
            prefsRepo.setOddsFormat(format)
            firestoreRepo.ensureUserProfile(displayName, format.name)
        }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { prefsRepo.setDarkTheme(dark) }
    }

    private fun buildSandboxParlay(
        legs: List<MarketLeg>,
        savedDocIds: Set<String>
    ): CorrelatedParlay? {
        if (legs.size < 2) return null
        val links = mutableListOf<CorrelationLink>()
        val pairwise = mutableMapOf<Pair<Int, Int>, Double>()
        for (i in legs.indices) {
            for (j in i + 1 until legs.size) {
                val eval = QuantCorrelationEngine.evaluatePairCorrelation(legs[i], legs[j])
                val rho = eval?.rho ?: 0.0
                val thesis = eval?.thesis ?: if (legs[i].eventId == legs[j].eventId) {
                    "Opposing or redundant same-game legs reduce or zero correlation."
                } else {
                    "Independent cross-game legs (ρ = 0.00)."
                }
                pairwise[i to j] = rho
                links.add(
                    CorrelationLink(
                        legAId = legs[i].id,
                        legBId = legs[j].id,
                        legALabel = legs[i].selectionLabel,
                        legBLabel = legs[j].selectionLabel,
                        rho = rho,
                        thesis = thesis
                    )
                )
            }
        }
        val parlay = QuantCorrelationEngine.assembleParlay(
            legs = legs,
            links = links,
            pairwiseRhos = pairwise,
            savedParlayIds = emptySet(),
            nowMillis = System.currentTimeMillis()
        )
        return parlay.copy(isSaved = firestoreRepo.safeDocId(parlay.id) in savedDocIds)
    }
}
