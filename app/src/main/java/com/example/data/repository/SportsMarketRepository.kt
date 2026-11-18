package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.CorrEdgeDao
import com.example.data.local.MarketCacheEntity
import com.example.data.remote.EspnApiService
import com.example.data.remote.EspnCompetitorDto
import com.example.data.remote.EspnEventDto
import com.example.data.remote.EspnLeaderCategoryDto
import com.example.data.remote.EspnOddsDto
import com.example.data.remote.EspnSummaryResponse
import com.example.data.remote.NetworkClient
import com.example.data.remote.OddsApiEventDto
import com.example.data.remote.OddsApiService
import com.example.domain.math.OddsMath
import com.example.domain.model.DevigMethod
import com.example.domain.model.EventState
import com.example.domain.model.FeedStatus
import com.example.domain.model.GameEvent
import com.example.domain.model.League
import com.example.domain.model.MarketLeg
import com.example.domain.model.MarketSnapshot
import com.example.domain.model.MarketType
import com.example.domain.model.PlayerPropPick
import com.example.domain.model.PropSide
import com.example.domain.model.TeamInfo
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

data class CollectedMarketData(
    val events: List<GameEvent>,
    val legs: List<MarketLeg>,
    val props: List<PlayerPropPick>,
    val status: FeedStatus
)

private data class RosterAthleteSeed(
    val id: String,
    val name: String,
    val position: String,
    val headshotUrl: String?
)

class SportsMarketRepository(
    private val dao: CorrEdgeDao,
    private val espnApi: EspnApiService = NetworkClient.espnApiService,
    private val oddsApi: OddsApiService = NetworkClient.oddsApiService
) {
    private val moshi = NetworkClient.moshi
    private val eventsListAdapter = moshi.adapter<List<GameEvent>>(
        Types.newParameterizedType(List::class.java, GameEvent::class.java)
    )
    private val legsListAdapter = moshi.adapter<List<MarketLeg>>(
        Types.newParameterizedType(List::class.java, MarketLeg::class.java)
    )
    private val propsListAdapter = moshi.adapter<List<PlayerPropPick>>(
        Types.newParameterizedType(List::class.java, PlayerPropPick::class.java)
    )

    suspend fun loadCachedSnapshot(): CollectedMarketData? = withContext(Dispatchers.IO) {
        val cache = dao.getLatestMarketCache() ?: return@withContext null
        val events = runCatching { eventsListAdapter.fromJson(cache.eventsJson) }.getOrNull().orEmpty()
        val legs = runCatching { legsListAdapter.fromJson(cache.legsJson) }.getOrNull().orEmpty()
        val props = runCatching { propsListAdapter.fromJson(cache.propsJson) }.getOrNull().orEmpty()
        if (events.isEmpty() && legs.isEmpty() && props.isEmpty()) return@withContext null
        CollectedMarketData(
            events = events,
            legs = legs,
            props = props,
            status = FeedStatus(
                isLoading = false,
                lastUpdatedMillis = cache.updatedAtMillis,
                espnEventsCount = cache.espnEventsCount,
                oddsApiActive = cache.oddsApiActive,
                oddsApiEventsCount = cache.oddsApiEventsCount,
                activeLeagues = events.map { it.league }.distinct()
            )
        )
    }

    suspend fun refreshAllMarketData(
        enabledLeagues: Set<League>,
        devigMethod: DevigMethod
    ): CollectedMarketData = withContext(Dispatchers.IO) {
        val oddsApiKey = BuildConfig.THE_ODDS_API_KEY
        val hasOddsApiKey = oddsApiKey.isNotBlank() &&
            oddsApiKey != "YOUR_THE_ODDS_API_KEY" &&
            oddsApiKey != "MY_THE_ODDS_API_KEY"

        val targetLeagues = if (enabledLeagues.isEmpty()) League.entries else enabledLeagues.toList()
        val upcomingDates = buildUpcomingDateStrings(daysAhead = 5)

        val (espnRawByLeague, oddsRawByLeague) = supervisorScope {
            val espnJobs = targetLeagues.map { league ->
                async {
                    val dailyDateQueries = when (league) {
                        League.NFL, League.NCAAF, League.EPL -> upcomingDates.take(2)
                        else -> upcomingDates
                    }
                    val fetchedLists = supervisorScope {
                        val defaultJob = async {
                            runCatching {
                                espnApi.getScoreboard(league.espnSport, league.espnLeague).events.orEmpty()
                            }.onFailure {
                                Log.w("SportsMarketRepo", "ESPN default fetch failed for ${league.code}: ${it.message}")
                            }.getOrDefault(emptyList())
                        }
                        val datedJobs = dailyDateQueries.map { dateStr ->
                            async {
                                runCatching {
                                    espnApi.getScoreboard(
                                        sport = league.espnSport,
                                        league = league.espnLeague,
                                        dates = dateStr
                                    ).events.orEmpty()
                                }.onFailure {
                                    Log.w("SportsMarketRepo", "ESPN fetch failed for ${league.code} ($dateStr): ${it.message}")
                                }.getOrDefault(emptyList())
                            }
                        }
                        listOf(defaultJob.await()) + datedJobs.awaitAll()
                    }
                    league to fetchedLists.flatten().distinctBy { it.id }
                }
            }
            val oddsJobs = if (hasOddsApiKey) {
                targetLeagues.map { league ->
                    async {
                        league to runCatching {
                            oddsApi.getOdds(league.oddsApiKey, oddsApiKey)
                        }.onFailure {
                            Log.w("SportsMarketRepo", "OddsAPI fetch failed for ${league.code}: ${it.message}")
                        }.getOrDefault(emptyList())
                    }
                }
            } else {
                emptyList()
            }

            espnJobs.awaitAll().toMap() to oddsJobs.awaitAll().toMap()
        }

        // Enrich up to 24 upcoming/live events round-robin across active leagues (prioritizing LIVE then soonest UPCOMING)
        val orderedCandidatesByLeague: List<Pair<League, List<EspnEventDto>>> = espnRawByLeague.entries.mapNotNull { (league, list) ->
            val active = list.filter { dto ->
                dto.id != null && dto.competitions?.firstOrNull()?.status?.type?.state != "post"
            }.sortedWith(
                compareBy<EspnEventDto> {
                    if (it.competitions?.firstOrNull()?.status?.type?.state == "in") 0 else 1
                }.thenBy { it.date.orEmpty() }
            )
            if (active.isEmpty()) null else league to active
        }
        val candidatesForSummary = buildList {
            var index = 0
            while (size < 24) {
                var addedAny = false
                for ((league, list) in orderedCandidatesByLeague) {
                    if (index < list.size) {
                        add(league to list[index])
                        addedAny = true
                        if (size >= 24) break
                    }
                }
                if (!addedAny) break
                index++
            }
        }

        val summaryMap = supervisorScope {
            candidatesForSummary.map { (league, dto) ->
                val eventId = dto.id!!
                async {
                    eventId to runCatching {
                        espnApi.getEventSummary(league.espnSport, league.espnLeague, eventId)
                    }.getOrNull()
                }
            }.awaitAll().toMap()
        }

        val allEvents = mutableListOf<GameEvent>()
        val allLegs = mutableListOf<MarketLeg>()
        val allProps = mutableListOf<PlayerPropPick>()
        var oddsApiMatchCount = 0

        // Track teams needing roster fallback per league when leaders aren't included on scoreboard/summary
        val pendingRosterByLeague = linkedMapOf<League, MutableList<Pair<GameEvent, EspnEventDto>>>()

        for ((league, espnEvents) in espnRawByLeague) {
            val oddsApiList = oddsRawByLeague[league].orEmpty()
            for (dto in espnEvents) {
                val summary = summaryMap[dto.id]
                val gameEvent = buildGameEvent(
                    league = league,
                    dto = dto,
                    summary = summary,
                    oddsApiEvents = oddsApiList,
                    devigMethod = devigMethod
                ) ?: continue

                if (gameEvent.dataSourceLabel.contains("Odds API")) {
                    oddsApiMatchCount++
                }
                allEvents.add(gameEvent)

                if (gameEvent.state != EventState.FINAL) {
                    val oddsMatch = findMatchingOddsEvent(gameEvent, oddsApiList)
                    allLegs.addAll(deriveMarketLegs(gameEvent, oddsMatch, devigMethod))

                    val leaderProps = derivePropsFromLeaders(gameEvent, dto, summary)
                    if (leaderProps.isNotEmpty()) {
                        allProps.addAll(leaderProps)
                    } else {
                        pendingRosterByLeague.getOrPut(league) { mutableListOf() }.add(gameEvent to dto)
                    }
                }
            }
        }

        // Round-robin select upcoming games needing roster-derived props across all active leagues
        val pendingRosterEvents = buildList {
            val sortedQueues = pendingRosterByLeague.values.map { list ->
                list.sortedBy { it.first.startTimeIso }
            }
            var idx = 0
            while (size < 14) {
                var addedAny = false
                for (queue in sortedQueues) {
                    if (idx < queue.size) {
                        add(queue[idx])
                        addedAny = true
                        if (size >= 14) break
                    }
                }
                if (!addedAny) break
                idx++
            }
        }
        val teamsNeedingRoster = pendingRosterEvents.flatMap { (gameEvent, _) ->
            buildList {
                if (gameEvent.homeTeam.id.isNotBlank()) {
                    add(Triple(gameEvent.league, gameEvent.homeTeam.id, gameEvent.homeTeam.abbreviation))
                }
                if (gameEvent.awayTeam.id.isNotBlank()) {
                    add(Triple(gameEvent.league, gameEvent.awayTeam.id, gameEvent.awayTeam.abbreviation))
                }
            }
        }

        // Fetch fallback rosters in parallel for active games without leader stats
        if (teamsNeedingRoster.isNotEmpty()) {
            val uniqueTeams = teamsNeedingRoster.distinctBy { "${it.first.code}_${it.second}" }.take(20)
            val rosterMap = supervisorScope {
                uniqueTeams.map { (league, teamId, _) ->
                    async {
                        "${league.code}_$teamId" to fetchTeamRosterAthletes(league, teamId)
                    }
                }.awaitAll().toMap()
            }

            for ((gameEvent, _) in pendingRosterEvents) {
                val homeRoster = rosterMap["${gameEvent.league.code}_${gameEvent.homeTeam.id}"].orEmpty()
                val awayRoster = rosterMap["${gameEvent.league.code}_${gameEvent.awayTeam.id}"].orEmpty()
                allProps.addAll(derivePropsFromRosterSeeds(gameEvent, homeRoster, awayRoster))
            }
        }

        // Convert high-conviction Player Props into MarketLegs so Top 3 Correlated Parlays can pair Props + Game Lines!
        allProps.forEach { prop ->
            allLegs.add(propToMarketLeg(prop))
        }

        if (hasOddsApiKey) {
            val existingPairKeys = allEvents.map {
                normalizeTeamKey(it.homeTeam.name) + "_" + normalizeTeamKey(it.awayTeam.name)
            }.toSet()
            for ((league, oddsList) in oddsRawByLeague) {
                for (oddsDto in oddsList) {
                    val home = oddsDto.homeTeam ?: continue
                    val away = oddsDto.awayTeam ?: continue
                    val key = normalizeTeamKey(home) + "_" + normalizeTeamKey(away)
                    if (key in existingPairKeys) continue
                    val gameEvent = buildEventFromOddsApiOnly(league, oddsDto, devigMethod) ?: continue
                    oddsApiMatchCount++
                    allEvents.add(gameEvent)
                    allLegs.addAll(deriveMarketLegs(gameEvent, oddsDto, devigMethod))
                }
            }
        }

        val sortedEvents = allEvents.sortedWith(
            compareBy<GameEvent> {
                when (it.state) {
                    EventState.LIVE -> 0
                    EventState.UPCOMING -> 1
                    EventState.FINAL -> 2
                }
            }.thenBy { it.startTimeIso }
        )

        val dedupedProps = allProps
            .distinctBy { it.id }
            .sortedByDescending {
                max(it.moreEvPercent, it.lessEvPercent) * 1.5 + max(it.moreProb, it.lessProb) * 0.5
            }

        val now = System.currentTimeMillis()
        val status = FeedStatus(
            isLoading = false,
            lastUpdatedMillis = now,
            espnEventsCount = sortedEvents.size,
            oddsApiActive = hasOddsApiKey,
            oddsApiEventsCount = oddsApiMatchCount,
            activeLeagues = sortedEvents.map { it.league }.distinct(),
            errorMessage = if (sortedEvents.isEmpty()) {
                "No active games found on selected leagues right now. Check connection or expand leagues in Lab."
            } else null
        )

        if (sortedEvents.isNotEmpty()) {
            runCatching {
                dao.saveMarketCache(
                    MarketCacheEntity(
                        eventsJson = eventsListAdapter.toJson(sortedEvents),
                        legsJson = legsListAdapter.toJson(allLegs),
                        propsJson = propsListAdapter.toJson(dedupedProps),
                        espnEventsCount = sortedEvents.size,
                        oddsApiActive = hasOddsApiKey,
                        oddsApiEventsCount = oddsApiMatchCount,
                        updatedAtMillis = now
                    )
                )
            }
        }

        CollectedMarketData(
            events = sortedEvents,
            legs = allLegs,
            props = dedupedProps,
            status = status
        )
    }

    /**
     * Lightweight, low-latency live scoreboard refresh intended for frequent polling (every ~12s)
     * so in-progress scores, game clocks, and FINAL transitions update constantly without blocking
     * on heavy roster/summary calls.
     */
    suspend fun refreshLiveScoresOnly(
        enabledLeagues: Set<League>,
        currentEvents: List<GameEvent>
    ): Pair<List<GameEvent>, Long> = withContext(Dispatchers.IO) {
        val targetLeagues = if (enabledLeagues.isEmpty()) League.entries else enabledLeagues.toList()
        val liveEventsByLeague = supervisorScope {
            targetLeagues.map { league ->
                async {
                    val fetched = runCatching {
                        espnApi.getScoreboard(league.espnSport, league.espnLeague).events.orEmpty()
                    }.getOrDefault(emptyList())
                    league to fetched
                }
            }.awaitAll().toMap()
        }

        val existingById = currentEvents.associateBy { it.id }
        val updatedById = LinkedHashMap<String, GameEvent>(currentEvents.size + 16)
        currentEvents.forEach { updatedById[it.id] = it }

        for ((league, dtos) in liveEventsByLeague) {
            for (dto in dtos) {
                val existing = dto.id?.let { existingById[it] }
                val fresh = buildGameEvent(
                    league = league,
                    dto = dto,
                    summary = null,
                    oddsApiEvents = emptyList(),
                    devigMethod = DevigMethod.POWER
                ) ?: continue

                val merged = if (existing != null) {
                    fresh.copy(
                        predictorHomeWinProb = existing.predictorHomeWinProb ?: fresh.predictorHomeWinProb,
                        marketSnapshot = existing.marketSnapshot ?: fresh.marketSnapshot,
                        dataSourceLabel = if (existing.dataSourceLabel.contains("Odds API")) {
                            existing.dataSourceLabel
                        } else {
                            fresh.dataSourceLabel
                        }
                    )
                } else {
                    fresh
                }
                updatedById[merged.id] = merged
            }
        }

        val sorted = updatedById.values.sortedWith(
            compareBy<GameEvent> {
                when (it.state) {
                    EventState.LIVE -> 0
                    EventState.UPCOMING -> 1
                    EventState.FINAL -> 2
                }
            }.thenBy { it.startTimeIso }
        )
        sorted to System.currentTimeMillis()
    }

    private suspend fun fetchTeamRosterAthletes(
        league: League,
        teamId: String
    ): List<RosterAthleteSeed> {
        return runCatching {
            val raw = espnApi.getTeamRosterRaw(league.espnSport, league.espnLeague, teamId).string()
            val root = JSONObject(raw)
            val athletesArr = root.optJSONArray("athletes") ?: return@runCatching emptyList()
            val seeds = mutableListOf<RosterAthleteSeed>()
            for (i in 0 until athletesArr.length()) {
                val obj = athletesArr.optJSONObject(i) ?: continue
                val items = obj.optJSONArray("items")
                if (items != null) {
                    for (j in 0 until items.length()) {
                        parseAthleteJson(items.optJSONObject(j), league)?.let { seeds.add(it) }
                    }
                } else {
                    parseAthleteJson(obj, league)?.let { seeds.add(it) }
                }
            }
            seeds.filter { it.position.isNotBlank() }.take(8)
        }.getOrDefault(emptyList())
    }

    private fun parseAthleteJson(obj: JSONObject?, league: League): RosterAthleteSeed? {
        if (obj == null) return null
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = obj.optString("displayName").ifBlank { obj.optString("fullName") }
        if (name.isBlank()) return null
        val pos = obj.optJSONObject("position")?.optString("abbreviation").orEmpty()
        val headshotObj = obj.optJSONObject("headshot")?.optString("href")
        val headshotStr = obj.optString("headshot").takeIf { it.startsWith("http") }
        val url = headshotObj?.takeIf { it.startsWith("http") }
            ?: headshotStr
            ?: buildEspnHeadshotUrl(league, id)
        return RosterAthleteSeed(id = id, name = name, position = pos, headshotUrl = url)
    }

    private fun derivePropsFromLeaders(
        event: GameEvent,
        dto: EspnEventDto,
        summary: EspnSummaryResponse?
    ): List<PlayerPropPick> {
        val comp = dto.competitions?.firstOrNull()
        val timeDisplay = formatIsoTime(event.startTimeIso)
        val total = event.marketSnapshot?.totalLine ?: event.league.avgGameTotal
        val spread = event.marketSnapshot?.spreadLine ?: 0.0
        val expectedHomeMargin = -spread

        val collected = mutableListOf<PlayerPropPick>()
        val seenPlayerStat = mutableSetOf<String>()

        fun processCategory(
            category: EspnLeaderCategoryDto,
            teamId: String?,
            teamAbbr: String,
            sideTag: String,
            isFavorite: Boolean
        ) {
            val catName = category.name.orEmpty()
            val catDisplay = category.displayName ?: category.shortDisplayName ?: catName
            val statInfo = mapEspnStatCategory(event.league, catName, catDisplay) ?: return

            category.leaders.orEmpty().take(2).forEach { entry ->
                val athlete = entry.athlete ?: return@forEach
                val athleteId = athlete.id ?: return@forEach
                val playerName = athlete.displayName ?: athlete.fullName ?: athlete.shortName ?: return@forEach
                val baseline = entry.value ?: entry.displayValue?.toDoubleOrNull() ?: return@forEach
                if (baseline <= 0.1) return@forEach

                val key = "${event.id}_${athleteId}_${statInfo.label}"
                if (!seenPlayerStat.add(key)) return@forEach

                val resolvedTeamId = entry.team?.id ?: teamId
                val resolvedAbbr = when (resolvedTeamId) {
                    event.homeTeam.id -> event.homeTeam.abbreviation
                    event.awayTeam.id -> event.awayTeam.abbreviation
                    else -> teamAbbr
                }
                val resolvedSide = if (resolvedAbbr == event.homeTeam.abbreviation) "HOME" else "AWAY"
                val resolvedFav = if (resolvedSide == "HOME") expectedHomeMargin >= 0 else expectedHomeMargin < 0

                val headshot = athlete.headshot?.href?.takeIf { it.startsWith("http") }
                    ?: buildEspnHeadshotUrl(event.league, athleteId)

                collected.add(
                    buildPlayerPropPick(
                        event = event,
                        timeDisplay = timeDisplay,
                        playerId = athleteId,
                        playerName = playerName,
                        teamAbbr = resolvedAbbr,
                        sideTag = resolvedSide,
                        position = athlete.position?.abbreviation ?: statInfo.defaultPos,
                        headshotUrl = headshot,
                        statLabel = statInfo.label,
                        seasonAvg = baseline,
                        stdDev = statInfo.stdDev,
                        total = total,
                        expectedHomeMargin = expectedHomeMargin,
                        isTeamFavorite = resolvedFav
                    )
                )
            }
        }

        // 1. Scoreboard competitor leaders
        comp?.competitors.orEmpty().forEach { compTeam ->
            val tAbbr = compTeam.team?.abbreviation ?: "TM"
            val tId = compTeam.team?.id
            val side = if (compTeam.homeAway == "home") "HOME" else "AWAY"
            val fav = if (side == "HOME") expectedHomeMargin >= 0 else expectedHomeMargin < 0
            compTeam.leaders.orEmpty().forEach { cat ->
                processCategory(cat, tId, tAbbr, side, fav)
            }
        }

        // 2. Competition-level leaders
        comp?.leaders.orEmpty().forEach { cat ->
            processCategory(cat, event.homeTeam.id, event.homeTeam.abbreviation, "HOME", expectedHomeMargin >= 0)
        }

        // 3. Summary leaders
        summary?.leaders.orEmpty().forEach { teamLeaders ->
            val tId = teamLeaders.team?.id
            val tAbbr = teamLeaders.team?.abbreviation ?: if (tId == event.homeTeam.id) event.homeTeam.abbreviation else event.awayTeam.abbreviation
            val side = if (tId == event.homeTeam.id) "HOME" else "AWAY"
            val fav = if (side == "HOME") expectedHomeMargin >= 0 else expectedHomeMargin < 0
            teamLeaders.leaders.orEmpty().forEach { cat ->
                processCategory(cat, tId, tAbbr, side, fav)
            }
        }

        return collected.take(10)
    }

    private fun derivePropsFromRosterSeeds(
        event: GameEvent,
        homeSeeds: List<RosterAthleteSeed>,
        awaySeeds: List<RosterAthleteSeed>
    ): List<PlayerPropPick> {
        val total = event.marketSnapshot?.totalLine ?: event.league.avgGameTotal
        val spread = event.marketSnapshot?.spreadLine ?: 0.0
        val expectedHomeMargin = -spread
        val timeDisplay = formatIsoTime(event.startTimeIso)
        val picks = mutableListOf<PlayerPropPick>()

        fun pickForSide(seeds: List<RosterAthleteSeed>, teamAbbr: String, sideTag: String, isFav: Boolean) {
            val priorityPositions = when (event.league) {
                League.NFL, League.NCAAF -> listOf("QB", "WR", "RB", "TE")
                League.NBA, League.NCAAB -> listOf("PG", "SG", "SF", "PF", "C", "G", "F")
                League.NHL -> listOf("C", "LW", "RW", "D")
                League.MLB -> listOf("SP", "P", "1B", "OF", "SS", "DH")
                League.EPL -> listOf("F", "FW", "M", "MF")
            }
            val ordered = seeds.sortedBy { seed ->
                val idx = priorityPositions.indexOf(seed.position.uppercase())
                if (idx == -1) 99 else idx
            }.take(3)

            ordered.forEachIndexed { idx, athlete ->
                val baselineSpec = defaultBaselineForPosition(event.league, athlete.position, idx)
                picks.add(
                    buildPlayerPropPick(
                        event = event,
                        timeDisplay = timeDisplay,
                        playerId = athlete.id,
                        playerName = athlete.name,
                        teamAbbr = teamAbbr,
                        sideTag = sideTag,
                        position = athlete.position.ifBlank { baselineSpec.defaultPos },
                        headshotUrl = athlete.headshotUrl,
                        statLabel = baselineSpec.label,
                        seasonAvg = baselineSpec.baseline,
                        stdDev = baselineSpec.stdDev,
                        total = total,
                        expectedHomeMargin = expectedHomeMargin,
                        isTeamFavorite = isFav
                    )
                )
            }
        }

        pickForSide(homeSeeds, event.homeTeam.abbreviation, "HOME", expectedHomeMargin >= 0)
        pickForSide(awaySeeds, event.awayTeam.abbreviation, "AWAY", expectedHomeMargin < 0)
        return picks
    }

    private data class StatCategorySpec(
        val label: String,
        val stdDev: Double,
        val defaultPos: String,
        val baseline: Double = 10.0
    )

    private fun mapEspnStatCategory(league: League, name: String, displayName: String): StatCategorySpec? {
        val key = (name + " " + displayName).lowercase(Locale.US)
        return when (league) {
            League.NBA, League.NCAAB -> when {
                "point" in key -> StatCategorySpec("Points", 6.2, "F")
                "rebound" in key -> StatCategorySpec("Rebounds", 2.9, "C")
                "assist" in key -> StatCategorySpec("Assists", 2.4, "G")
                else -> null
            }
            League.NFL, League.NCAAF -> when {
                "pass" in key -> StatCategorySpec("Pass Yds", 48.0, "QB")
                "rush" in key -> StatCategorySpec("Rush Yds", 24.0, "RB")
                "receiv" in key -> StatCategorySpec("Rec Yds", 26.0, "WR")
                else -> null
            }
            League.NHL -> when {
                "point" in key -> StatCategorySpec("Points", 0.72, "C")
                "goal" in key -> StatCategorySpec("Shots on Goal", 1.35, "LW")
                "assist" in key -> StatCategorySpec("Assists", 0.65, "C")
                else -> null
            }
            League.MLB -> when {
                "strikeout" in key -> StatCategorySpec("Strikeouts", 1.85, "P")
                "home" in key || "hit" in key || "rbi" in key -> StatCategorySpec("Hits + Runs + RBIs", 1.25, "OF")
                else -> null
            }
            League.EPL -> when {
                "goal" in key -> StatCategorySpec("Shots on Target", 0.92, "FW")
                "assist" in key -> StatCategorySpec("Shots Attempted", 1.25, "MF")
                else -> null
            }
        }
    }

    private fun defaultBaselineForPosition(league: League, pos: String, index: Int): StatCategorySpec {
        val p = pos.uppercase(Locale.US)
        return when (league) {
            League.NBA, League.NCAAB -> when (index) {
                0 -> StatCategorySpec("Points", 6.2, p, if (league == League.NBA) 23.8 else 17.2)
                1 -> StatCategorySpec("Rebounds", 2.9, p, if (league == League.NBA) 8.4 else 7.1)
                else -> StatCategorySpec("Assists", 2.4, p, if (league == League.NBA) 6.4 else 4.8)
            }
            League.NFL, League.NCAAF -> when (p) {
                "QB" -> StatCategorySpec("Pass Yds", 48.0, p, 248.5)
                "RB" -> StatCategorySpec("Rush Yds", 24.0, p, 71.5)
                else -> StatCategorySpec("Rec Yds", 26.0, p, 66.5)
            }
            League.NHL -> when (index) {
                0 -> StatCategorySpec("Shots on Goal", 1.35, p, 3.2)
                else -> StatCategorySpec("Points", 0.72, p, 0.85)
            }
            League.MLB -> if (p.contains("P")) {
                StatCategorySpec("Strikeouts", 1.85, p, 5.8)
            } else {
                StatCategorySpec("Hits + Runs + RBIs", 1.25, p, 1.7)
            }
            League.EPL -> StatCategorySpec("Shots on Target", 0.92, p, 1.35)
        }
    }

    private fun buildPlayerPropPick(
        event: GameEvent,
        timeDisplay: String,
        playerId: String,
        playerName: String,
        teamAbbr: String,
        sideTag: String,
        position: String,
        headshotUrl: String?,
        statLabel: String,
        seasonAvg: Double,
        stdDev: Double,
        total: Double,
        expectedHomeMargin: Double,
        isTeamFavorite: Boolean
    ): PlayerPropPick {
        // Set realistic market half-point line near season baseline
        val marketLine = (round((seasonAvg - 0.15) * 2.0) / 2.0).let {
            if (it % 1.0 == 0.0) it - 0.5 else it
        }.coerceAtLeast(0.5)

        // Game-script projection adjustment: pace from Vegas total + team implied margin
        val totalRatio = (total / event.league.avgGameTotal).coerceIn(0.84, 1.18)
        val teamMarginFactor = if (sideTag == "HOME") expectedHomeMargin else -expectedHomeMargin
        val marginShiftPct = (teamMarginFactor / event.league.marginStdDev) * 0.055
        val paceShiftPct = (totalRatio - 1.0) * 0.68
        val projected = (seasonAvg * (1.0 + paceShiftPct + marginShiftPct)).coerceAtLeast(0.3)

        val moreProb = (1.0 - OddsMath.normalCdf((marketLine - projected) / stdDev)).coerceIn(0.24, 0.76)
        val lessProb = (1.0 - moreProb).coerceIn(0.24, 0.76)

        // Standard market prop pricing with slight juice near season average
        val rawMarketMoreProb = (1.0 - OddsMath.normalCdf((marketLine - seasonAvg) / stdDev)).coerceIn(0.35, 0.65)
        val moreOdds = OddsMath.probToAmerican((rawMarketMoreProb * 1.045).coerceIn(0.38, 0.68))
        val lessOdds = OddsMath.probToAmerican(((1.0 - rawMarketMoreProb) * 1.045).coerceIn(0.38, 0.68))

        val moreEv = OddsMath.expectedValue(moreProb, OddsMath.americanToDecimal(moreOdds))
        val lessEv = OddsMath.expectedValue(lessProb, OddsMath.americanToDecimal(lessOdds))

        val recommendedSide = if (moreEv >= lessEv) PropSide.MORE else PropSide.LESS
        val diffPct = ((projected - marketLine) / marketLine) * 100.0
        val thesis = if (recommendedSide == PropSide.MORE) {
            "Proj ${formatOneDec(projected)} vs ${formatLine(marketLine)} (${String.format("%+.1f%%", diffPct)} pace & matchup edge toward MORE)."
        } else {
            "Proj ${formatOneDec(projected)} vs ${formatLine(marketLine)} (${String.format("%+.1f%%", diffPct)} compressed script toward LESS)."
        }

        val cleanStatId = statLabel.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")
        return PlayerPropPick(
            id = "prop_${event.id}_${playerId}_$cleanStatId",
            eventId = event.id,
            league = event.league,
            matchupShort = event.shortName,
            startTimeDisplay = timeDisplay,
            playerId = playerId,
            playerName = playerName,
            teamAbbr = teamAbbr,
            sideTag = sideTag,
            position = position,
            headshotUrl = headshotUrl,
            statLabel = statLabel,
            lineValue = marketLine,
            projectedValue = projected,
            moreProb = moreProb,
            lessProb = lessProb,
            moreAmericanOdds = moreOdds,
            lessAmericanOdds = lessOdds,
            moreEvPercent = moreEv,
            lessEvPercent = lessEv,
            recommendedSide = recommendedSide,
            confidenceThesis = thesis,
            isTeamFavorite = isTeamFavorite,
            expectedTotal = total,
            expectedHomeMargin = expectedHomeMargin
        )
    }

    private fun propToMarketLeg(prop: PlayerPropPick): MarketLeg {
        val isMore = prop.recommendedSide == PropSide.MORE
        val odds = if (isMore) prop.moreAmericanOdds else prop.lessAmericanOdds
        val prob = if (isMore) prop.moreProb else prop.lessProb
        val ev = if (isMore) prop.moreEvPercent else prop.lessEvPercent
        val dec = OddsMath.americanToDecimal(odds)
        return MarketLeg(
            id = prop.id,
            eventId = prop.eventId,
            league = prop.league,
            matchupShort = prop.matchupShort,
            homeTeamName = prop.teamAbbr,
            awayTeamName = prop.teamAbbr,
            homeLogoUrl = prop.headshotUrl,
            awayLogoUrl = prop.headshotUrl,
            startTimeDisplay = prop.startTimeDisplay,
            marketType = if (isMore) MarketType.PLAYER_PROP_MORE else MarketType.PLAYER_PROP_LESS,
            selectionLabel = "${prop.playerName} ${prop.recommendedSide.label} ${formatLine(prop.lineValue)} ${prop.statLabel}",
            sideTag = prop.sideTag,
            lineValue = prop.lineValue,
            offeredAmericanOdds = odds,
            offeredDecimalOdds = dec,
            marketImpliedProb = OddsMath.americanToImpliedProb(odds),
            fairTrueProb = prob,
            evPercent = ev,
            sharpBookLabel = "Pace & Stat Projection",
            isFavoriteSide = prop.isTeamFavorite,
            expectedTotal = prop.expectedTotal,
            expectedHomeMargin = prop.expectedHomeMargin
        )
    }

    private fun buildEspnHeadshotUrl(league: League, athleteId: String): String {
        val sportPath = when (league) {
            League.NBA -> "nba"
            League.NFL -> "nfl"
            League.NHL -> "nhl"
            League.MLB -> "mlb"
            League.NCAAB -> "mens-college-basketball"
            League.NCAAF -> "college-football"
            League.EPL -> "soccer"
        }
        return "https://a.espncdn.com/i/headshots/$sportPath/players/full/$athleteId.png"
    }

    private fun buildUpcomingDateStrings(daysAhead: Int): List<String> {
        val easternTz = TimeZone.getTimeZone("America/New_York")
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = easternTz
        }
        val cal = Calendar.getInstance(easternTz)
        val dates = mutableListOf<String>()
        for (offset in 0..daysAhead) {
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, offset)
            dates.add(fmt.format(c.time))
        }
        return dates
    }

    private fun buildGameEvent(
        league: League,
        dto: EspnEventDto,
        summary: EspnSummaryResponse?,
        oddsApiEvents: List<OddsApiEventDto>,
        devigMethod: DevigMethod
    ): GameEvent? {
        val eventId = dto.id ?: return null
        val comp = dto.competitions?.firstOrNull() ?: return null
        val competitors = comp.competitors.orEmpty()
        val homeDto = competitors.find { it.homeAway == "home" } ?: competitors.getOrNull(0) ?: return null
        val awayDto = competitors.find { it.homeAway == "away" } ?: competitors.getOrNull(1) ?: return null

        val homeInfo = mapTeamInfo(homeDto)
        val awayInfo = mapTeamInfo(awayDto)

        val stateStr = comp.status?.type?.state ?: "pre"
        val state = when (stateStr) {
            "in" -> EventState.LIVE
            "post" -> EventState.FINAL
            else -> EventState.UPCOMING
        }
        val rawDetail = comp.status?.type?.shortDetail?.trim().orEmpty()
        val statusDetail = when {
            state == EventState.LIVE && (rawDetail.isBlank() || rawDetail.equals("In Progress", ignoreCase = true)) -> {
                val clock = comp.status?.displayClock?.takeIf { it.isNotBlank() }
                val period = comp.status?.period?.takeIf { it > 0 }
                when {
                    clock != null && period != null -> "P$period • $clock"
                    period != null -> "Period $period LIVE"
                    else -> "LIVE"
                }
            }
            rawDetail.isNotBlank() -> rawDetail
            else -> formatIsoTime(dto.date.orEmpty())
        }

        val predictorHomeProb = summary?.predictor?.homeTeam?.gameProjection?.toDoubleOrNull()
            ?.div(100.0)?.coerceIn(0.05, 0.95)

        val espnOdds = summary?.pickcenter?.firstOrNull() ?: comp.odds?.firstOrNull()
        val matchedOddsApi = oddsApiEvents.firstOrNull {
            teamsMatch(homeInfo.name, it.homeTeam.orEmpty()) &&
                teamsMatch(awayInfo.name, it.awayTeam.orEmpty())
        }

        val marketSnapshot = mergeMarketSnapshot(
            homeName = homeInfo.name,
            awayName = awayInfo.name,
            espnOdds = espnOdds,
            oddsApiEvent = matchedOddsApi,
            devigMethod = devigMethod
        )

        val sourceLabel = when {
            matchedOddsApi != null && espnOdds != null -> "Odds API (${marketSnapshot?.bookCount ?: 1} books) + ESPN"
            matchedOddsApi != null -> "The Odds API (${marketSnapshot?.bookCount ?: 1} books)"
            espnOdds != null -> "ESPN Market (${espnOdds.provider?.name ?: "PickCenter"})"
            predictorHomeProb != null -> "ESPN Analytics Model"
            else -> "ESPN Schedule Feed"
        }

        return GameEvent(
            id = eventId,
            league = league,
            name = dto.name ?: "${awayInfo.name} at ${homeInfo.name}",
            shortName = dto.shortName ?: "${awayInfo.abbreviation} @ ${homeInfo.abbreviation}",
            startTimeIso = dto.date.orEmpty(),
            statusDetail = statusDetail,
            state = state,
            homeTeam = homeInfo,
            awayTeam = awayInfo,
            predictorHomeWinProb = predictorHomeProb,
            marketSnapshot = marketSnapshot,
            dataSourceLabel = sourceLabel
        )
    }

    private fun buildEventFromOddsApiOnly(
        league: League,
        dto: OddsApiEventDto,
        devigMethod: DevigMethod
    ): GameEvent? {
        val id = dto.id ?: return null
        val homeName = dto.homeTeam ?: return null
        val awayName = dto.awayTeam ?: return null
        val snapshot = mergeMarketSnapshot(homeName, awayName, null, dto, devigMethod)
        val homeAbbr = homeName.split(" ").last().take(3).uppercase()
        val awayAbbr = awayName.split(" ").last().take(3).uppercase()

        return GameEvent(
            id = id,
            league = league,
            name = "$awayName at $homeName",
            shortName = "$awayAbbr @ $homeAbbr",
            startTimeIso = dto.commenceTime.orEmpty(),
            statusDetail = formatIsoTime(dto.commenceTime.orEmpty()),
            state = EventState.UPCOMING,
            homeTeam = TeamInfo(id = "${id}_h", name = homeName, abbreviation = homeAbbr, logoUrl = null, record = "", score = null, winPctFromRecord = 0.5),
            awayTeam = TeamInfo(id = "${id}_a", name = awayName, abbreviation = awayAbbr, logoUrl = null, record = "", score = null, winPctFromRecord = 0.5),
            predictorHomeWinProb = null,
            marketSnapshot = snapshot,
            dataSourceLabel = "The Odds API (${snapshot?.bookCount ?: 1} books)"
        )
    }

    private fun mapTeamInfo(dto: EspnCompetitorDto): TeamInfo {
        val recordSummary = dto.records?.firstOrNull { it.type == "total" || it.name == "overall" }?.summary
            ?: dto.records?.firstOrNull()?.summary.orEmpty()
        val winPct = parseRecordWinPct(recordSummary)
        val avgPts = dto.statistics?.firstOrNull {
            it.name?.contains("points", ignoreCase = true) == true ||
                it.name?.contains("avgPoints", ignoreCase = true) == true
        }?.displayValue?.toDoubleOrNull()

        return TeamInfo(
            id = dto.team?.id ?: dto.id.orEmpty(),
            name = dto.team?.displayName ?: dto.team?.shortDisplayName ?: "Team",
            abbreviation = dto.team?.abbreviation ?: "TM",
            logoUrl = dto.team?.logo,
            record = recordSummary,
            score = dto.score,
            winPctFromRecord = winPct,
            avgPointsFor = avgPts
        )
    }

    private fun parseRecordWinPct(record: String): Double {
        val parts = record.split("-").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size < 2) return 0.5
        val wins = parts[0]
        val losses = parts[1]
        val ties = parts.getOrNull(2) ?: 0
        val total = wins + losses + ties
        return if (total > 0) (wins + 0.5 * ties) / total.toDouble() else 0.5
    }

    private fun mergeMarketSnapshot(
        homeName: String,
        awayName: String,
        espnOdds: EspnOddsDto?,
        oddsApiEvent: OddsApiEventDto?,
        devigMethod: DevigMethod
    ): MarketSnapshot? {
        if (espnOdds == null && oddsApiEvent == null) return null

        val books = oddsApiEvent?.bookmakers.orEmpty()
        if (books.isNotEmpty()) {
            val pinnacle = books.firstOrNull { it.key == "pinnacle" }
            val sharpBook = pinnacle ?: books.firstOrNull { it.key in listOf("betonlineag", "bovada", "lowvig") }

            var bestHomeMl: Int? = null
            var bestAwayMl: Int? = null
            var spreadLine: Double? = null
            var bestHomeSpreadOdds: Int? = null
            var bestAwaySpreadOdds: Int? = null
            var totalLine: Double? = null
            var bestOverOdds: Int? = null
            var bestUnderOdds: Int? = null

            val homeFairProbs = mutableListOf<Double>()
            val overFairProbs = mutableListOf<Double>()

            for (book in books) {
                val h2h = book.markets?.firstOrNull { it.key == "h2h" }?.outcomes.orEmpty()
                val hOutcome = h2h.firstOrNull { teamsMatch(homeName, it.name.orEmpty()) }
                val aOutcome = h2h.firstOrNull { teamsMatch(awayName, it.name.orEmpty()) }
                if (hOutcome?.price != null && aOutcome?.price != null) {
                    if (bestHomeMl == null || hOutcome.price > bestHomeMl) bestHomeMl = hOutcome.price
                    if (bestAwayMl == null || aOutcome.price > bestAwayMl) bestAwayMl = aOutcome.price
                    val (fH, _) = OddsMath.devigTwoWay(
                        OddsMath.americanToImpliedProb(hOutcome.price),
                        OddsMath.americanToImpliedProb(aOutcome.price),
                        devigMethod
                    )
                    val weight = if (book.key == sharpBook?.key) 3 else 1
                    repeat(weight) { homeFairProbs.add(fH) }
                }

                val spreads = book.markets?.firstOrNull { it.key == "spreads" }?.outcomes.orEmpty()
                val hs = spreads.firstOrNull { teamsMatch(homeName, it.name.orEmpty()) }
                val aws = spreads.firstOrNull { teamsMatch(awayName, it.name.orEmpty()) }
                if (hs?.point != null && hs.price != null && aws?.price != null) {
                    if (spreadLine == null) spreadLine = hs.point
                    if (abs(spreadLine - hs.point) < 0.01) {
                        if (bestHomeSpreadOdds == null || hs.price > bestHomeSpreadOdds) bestHomeSpreadOdds = hs.price
                        if (bestAwaySpreadOdds == null || aws.price > bestAwaySpreadOdds) bestAwaySpreadOdds = aws.price
                    }
                }

                val totals = book.markets?.firstOrNull { it.key == "totals" }?.outcomes.orEmpty()
                val ov = totals.firstOrNull { it.name.equals("Over", ignoreCase = true) }
                val un = totals.firstOrNull { it.name.equals("Under", ignoreCase = true) }
                if (ov?.point != null && ov.price != null && un?.price != null) {
                    if (totalLine == null) totalLine = ov.point
                    if (abs(totalLine - ov.point) < 0.01) {
                        if (bestOverOdds == null || ov.price > bestOverOdds) bestOverOdds = ov.price
                        if (bestUnderOdds == null || un.price > bestUnderOdds) bestUnderOdds = un.price
                        val (fOv, _) = OddsMath.devigTwoWay(
                            OddsMath.americanToImpliedProb(ov.price),
                            OddsMath.americanToImpliedProb(un.price),
                            devigMethod
                        )
                        val weight = if (book.key == sharpBook?.key) 3 else 1
                        repeat(weight) { overFairProbs.add(fOv) }
                    }
                }
            }

            return MarketSnapshot(
                providerName = sharpBook?.title ?: books.firstOrNull()?.title ?: "Multi-Book Consensus",
                homeMoneyline = bestHomeMl ?: espnOdds?.homeTeamOdds?.moneyLine,
                awayMoneyline = bestAwayMl ?: espnOdds?.awayTeamOdds?.moneyLine,
                spreadLine = spreadLine ?: espnOdds?.spread,
                homeSpreadOdds = bestHomeSpreadOdds ?: espnOdds?.homeTeamOdds?.spreadOdds ?: -110,
                awaySpreadOdds = bestAwaySpreadOdds ?: espnOdds?.awayTeamOdds?.spreadOdds ?: -110,
                totalLine = totalLine ?: espnOdds?.overUnder,
                overOdds = bestOverOdds ?: espnOdds?.overOdds ?: -110,
                underOdds = bestUnderOdds ?: espnOdds?.underOdds ?: -110,
                bookCount = books.size,
                sharpConsensusHomeProb = homeFairProbs.takeIf { it.isNotEmpty() }?.average(),
                sharpConsensusOverProb = overFairProbs.takeIf { it.isNotEmpty() }?.average()
            )
        }

        val parsedSpread = espnOdds?.spread ?: parseSpreadFromDetails(espnOdds?.details, homeName)
        return MarketSnapshot(
            providerName = espnOdds?.provider?.name ?: "ESPN Market",
            homeMoneyline = espnOdds?.homeTeamOdds?.moneyLine,
            awayMoneyline = espnOdds?.awayTeamOdds?.moneyLine,
            spreadLine = parsedSpread,
            homeSpreadOdds = espnOdds?.homeTeamOdds?.spreadOdds ?: -110,
            awaySpreadOdds = espnOdds?.awayTeamOdds?.spreadOdds ?: -110,
            totalLine = espnOdds?.overUnder,
            overOdds = espnOdds?.overOdds ?: -110,
            underOdds = espnOdds?.underOdds ?: -110,
            bookCount = 1
        )
    }

    private fun parseSpreadFromDetails(details: String?, homeName: String): Double? {
        if (details.isNullOrBlank()) return null
        val tokens = details.trim().split(" ")
        val num = tokens.lastOrNull()?.toDoubleOrNull() ?: return null
        val teamToken = tokens.dropLast(1).joinToString(" ")
        return if (homeName.contains(teamToken, ignoreCase = true)) num else -num
    }

    private fun deriveMarketLegs(
        event: GameEvent,
        oddsApiEvent: OddsApiEventDto?,
        devigMethod: DevigMethod
    ): List<MarketLeg> {
        val snap = event.marketSnapshot
        val legs = mutableListOf<MarketLeg>()
        val timeDisplay = formatIsoTime(event.startTimeIso)
        val sharpLabel = if (oddsApiEvent != null) "Sharp ${snap?.bookCount ?: 1}-Book Devig" else "ESPN Predictor + Devig"

        val marketHomeMl = snap?.homeMoneyline
        val marketAwayMl = snap?.awayMoneyline
        val spread = snap?.spreadLine
        val total = snap?.totalLine

        val recordAdj = ((event.homeTeam.winPctFromRecord - event.awayTeam.winPctFromRecord) * 0.14)
        val spreadImpliedHomeProb = spread?.let { sp ->
            OddsMath.normalCdf((-sp) / event.league.marginStdDev)
        }

        val deviggedMlHomeProb = if (marketHomeMl != null && marketAwayMl != null) {
            OddsMath.devigTwoWay(
                OddsMath.americanToImpliedProb(marketHomeMl),
                OddsMath.americanToImpliedProb(marketAwayMl),
                devigMethod
            ).first
        } else null

        val fairHomeProb: Double? = when {
            snap?.sharpConsensusHomeProb != null && event.predictorHomeWinProb != null ->
                (snap.sharpConsensusHomeProb * 0.72 + event.predictorHomeWinProb * 0.28).coerceIn(0.08, 0.92)
            snap?.sharpConsensusHomeProb != null ->
                snap.sharpConsensusHomeProb
            event.predictorHomeWinProb != null && deviggedMlHomeProb != null ->
                (event.predictorHomeWinProb * 0.45 + deviggedMlHomeProb * 0.55).coerceIn(0.08, 0.92)
            event.predictorHomeWinProb != null ->
                event.predictorHomeWinProb
            deviggedMlHomeProb != null ->
                (deviggedMlHomeProb + recordAdj * 0.25).coerceIn(0.08, 0.92)
            spreadImpliedHomeProb != null ->
                (spreadImpliedHomeProb + recordAdj * 0.20).coerceIn(0.08, 0.92)
            else -> null
        }

        val expectedHomeMargin = when {
            spread != null -> -spread
            fairHomeProb != null -> OddsMath.inverseNormalCdf(fairHomeProb) * event.league.marginStdDev
            else -> 0.0
        }

        if (fairHomeProb != null) {
            val fairAwayProb = 1.0 - fairHomeProb
            val offeredHome = marketHomeMl ?: OddsMath.probToAmerican((fairHomeProb * 1.025).coerceIn(0.08, 0.92))
            val offeredAway = marketAwayMl ?: OddsMath.probToAmerican((fairAwayProb * 1.025).coerceIn(0.08, 0.92))

            legs.add(
                createLeg(
                    event, "ml_home", timeDisplay, MarketType.MONEYLINE,
                    "${event.homeTeam.abbreviation} ML", "HOME", null,
                    offeredHome, fairHomeProb, sharpLabel, fairHomeProb >= 0.5, total, expectedHomeMargin
                )
            )
            legs.add(
                createLeg(
                    event, "ml_away", timeDisplay, MarketType.MONEYLINE,
                    "${event.awayTeam.abbreviation} ML", "AWAY", null,
                    offeredAway, fairAwayProb, sharpLabel, fairAwayProb >= 0.5, total, expectedHomeMargin
                )
            )
        }

        if (spread != null) {
            val homeSpOdds = snap.homeSpreadOdds ?: -110
            val awaySpOdds = snap.awaySpreadOdds ?: -110
            val (devigHomeSp, _) = OddsMath.devigTwoWay(
                OddsMath.americanToImpliedProb(homeSpOdds),
                OddsMath.americanToImpliedProb(awaySpOdds),
                devigMethod
            )
            val modelHomeCoverProb = OddsMath.normalCdf((expectedHomeMargin + spread) / event.league.marginStdDev)
            val fairHomeSpProb = (devigHomeSp * 0.65 + modelHomeCoverProb * 0.35).coerceIn(0.15, 0.85)
            val fairAwaySpProb = 1.0 - fairHomeSpProb

            val homeSpreadText = if (spread > 0) "+${formatLine(spread)}" else formatLine(spread)
            val awaySpreadVal = -spread
            val awaySpreadText = if (awaySpreadVal > 0) "+${formatLine(awaySpreadVal)}" else formatLine(awaySpreadVal)

            legs.add(
                createLeg(
                    event, "sp_home", timeDisplay, MarketType.SPREAD,
                    "${event.homeTeam.abbreviation} $homeSpreadText", "HOME", spread,
                    homeSpOdds, fairHomeSpProb, sharpLabel, spread < 0.0, total, expectedHomeMargin
                )
            )
            legs.add(
                createLeg(
                    event, "sp_away", timeDisplay, MarketType.SPREAD,
                    "${event.awayTeam.abbreviation} $awaySpreadText", "AWAY", awaySpreadVal,
                    awaySpOdds, fairAwaySpProb, sharpLabel, awaySpreadVal < 0.0, total, expectedHomeMargin
                )
            )

            val cushion = when (event.league) {
                League.NFL, League.NCAAF -> 3.5
                League.NBA, League.NCAAB -> 4.5
                League.NHL, League.MLB -> 1.5
                League.EPL -> 0.5
            }
            val homeAltLine = round((spread + cushion) * 2.0) / 2.0
            val homeAltProb = OddsMath.normalCdf((expectedHomeMargin + homeAltLine) / event.league.marginStdDev)
                .coerceIn(0.55, 0.88)
            val homeAltAmerican = OddsMath.probToAmerican((homeAltProb * 1.015).coerceAtMost(0.90))
            val homeAltText = if (homeAltLine > 0) "+${formatLine(homeAltLine)}" else formatLine(homeAltLine)

            legs.add(
                createLeg(
                    event, "alt_sp_home", timeDisplay, MarketType.ALT_SPREAD,
                    "${event.homeTeam.abbreviation} $homeAltText (Alt)", "HOME", homeAltLine,
                    homeAltAmerican, homeAltProb, "Gaussian Margin Model", spread < 0.0, total, expectedHomeMargin
                )
            )
        }

        if (total != null && total > 0.5) {
            val overOdds = snap.overOdds ?: -110
            val underOdds = snap.underOdds ?: -110
            val (devigOver, _) = OddsMath.devigTwoWay(
                OddsMath.americanToImpliedProb(overOdds),
                OddsMath.americanToImpliedProb(underOdds),
                devigMethod
            )
            val avgCombinedPts = if (event.homeTeam.avgPointsFor != null && event.awayTeam.avgPointsFor != null) {
                event.homeTeam.avgPointsFor + event.awayTeam.avgPointsFor
            } else null
            val paceShift = if (avgCombinedPts != null) {
                ((avgCombinedPts - total) / event.league.totalStdDev) * 0.08
            } else 0.0

            val fairOverProb = ((snap.sharpConsensusOverProb ?: devigOver) + paceShift).coerceIn(0.20, 0.80)
            val fairUnderProb = 1.0 - fairOverProb

            legs.add(
                createLeg(
                    event, "tot_over", timeDisplay, MarketType.TOTAL_OVER,
                    "Over ${formatLine(total)}", "OVER", total,
                    overOdds, fairOverProb, sharpLabel, false, total, expectedHomeMargin
                )
            )
            legs.add(
                createLeg(
                    event, "tot_under", timeDisplay, MarketType.TOTAL_UNDER,
                    "Under ${formatLine(total)}", "UNDER", total,
                    underOdds, fairUnderProb, sharpLabel, false, total, expectedHomeMargin
                )
            )

            val homeImpliedTeamTotal = (total / 2.0) + (expectedHomeMargin / 2.0)
            val teamStd = event.league.totalStdDev / sqrt(2.0)
            val altDiscount = when (event.league) {
                League.NBA, League.NCAAB -> 3.5
                League.NFL, League.NCAAF -> 2.5
                League.NHL, League.MLB -> 0.5
                League.EPL -> 0.5
            }
            val homeTeamLine = (round((homeImpliedTeamTotal - altDiscount) * 2.0) / 2.0).coerceAtLeast(0.5)
            val homeTeamOverProb = OddsMath.normalCdf((homeImpliedTeamTotal - homeTeamLine) / teamStd)
                .coerceIn(0.45, 0.84)
            val homeTeamOffered = OddsMath.probToAmerican((homeTeamOverProb * 1.015).coerceAtMost(0.87))

            legs.add(
                createLeg(
                    event, "tt_home_over", timeDisplay, MarketType.TEAM_TOTAL_OVER,
                    "${event.homeTeam.abbreviation} TT Ov ${formatLine(homeTeamLine)}", "HOME", homeTeamLine,
                    homeTeamOffered, homeTeamOverProb, "Implied Team Total Model", expectedHomeMargin > 0.0, total, expectedHomeMargin
                )
            )

            val awayImpliedTeamTotal = (total / 2.0) - (expectedHomeMargin / 2.0)
            val awayTeamLine = (round((awayImpliedTeamTotal - altDiscount) * 2.0) / 2.0).coerceAtLeast(0.5)
            val awayTeamOverProb = OddsMath.normalCdf((awayImpliedTeamTotal - awayTeamLine) / teamStd)
                .coerceIn(0.45, 0.84)
            val awayTeamOffered = OddsMath.probToAmerican((awayTeamOverProb * 1.015).coerceAtMost(0.87))

            legs.add(
                createLeg(
                    event, "tt_away_over", timeDisplay, MarketType.TEAM_TOTAL_OVER,
                    "${event.awayTeam.abbreviation} TT Ov ${formatLine(awayTeamLine)}", "AWAY", awayTeamLine,
                    awayTeamOffered, awayTeamOverProb, "Implied Team Total Model", expectedHomeMargin < 0.0, total, expectedHomeMargin
                )
            )
        }

        return legs
    }

    private fun createLeg(
        event: GameEvent,
        suffix: String,
        timeDisplay: String,
        marketType: MarketType,
        selectionLabel: String,
        sideTag: String,
        lineValue: Double?,
        offeredAmerican: Int,
        fairTrueProb: Double,
        sharpLabel: String,
        isFavorite: Boolean,
        expectedTotal: Double?,
        expectedHomeMargin: Double?
    ): MarketLeg {
        val dec = OddsMath.americanToDecimal(offeredAmerican)
        val implied = OddsMath.americanToImpliedProb(offeredAmerican)
        val ev = OddsMath.expectedValue(fairTrueProb, dec)

        return MarketLeg(
            id = "${event.id}_$suffix",
            eventId = event.id,
            league = event.league,
            matchupShort = event.shortName,
            homeTeamName = event.homeTeam.name,
            awayTeamName = event.awayTeam.name,
            homeLogoUrl = event.homeTeam.logoUrl,
            awayLogoUrl = event.awayTeam.logoUrl,
            startTimeDisplay = timeDisplay,
            marketType = marketType,
            selectionLabel = selectionLabel,
            sideTag = sideTag,
            lineValue = lineValue,
            offeredAmericanOdds = offeredAmerican,
            offeredDecimalOdds = dec,
            marketImpliedProb = implied,
            fairTrueProb = fairTrueProb,
            evPercent = ev,
            sharpBookLabel = sharpLabel,
            isFavoriteSide = isFavorite,
            expectedTotal = expectedTotal,
            expectedHomeMargin = expectedHomeMargin
        )
    }

    private fun findMatchingOddsEvent(event: GameEvent, list: List<OddsApiEventDto>): OddsApiEventDto? {
        return list.firstOrNull {
            teamsMatch(event.homeTeam.name, it.homeTeam.orEmpty()) &&
                teamsMatch(event.awayTeam.name, it.awayTeam.orEmpty())
        }
    }

    private fun teamsMatch(a: String, b: String): Boolean {
        val na = normalizeTeamKey(a)
        val nb = normalizeTeamKey(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        return na == nb || na.contains(nb) || nb.contains(na)
    }

    private fun normalizeTeamKey(name: String): String {
        return name.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
    }

    private fun formatLine(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    }

    private fun formatOneDec(value: Double): String {
        return String.format(Locale.US, "%.1f", value)
    }

    private fun formatIsoTime(iso: String): String {
        if (iso.isBlank()) return "TBD"
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = parser.parse(iso)
                ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(iso)
            if (parsed != null) {
                SimpleDateFormat("MMM d • h:mm a", Locale.US).format(parsed)
            } else {
                iso.take(16)
            }
        }.getOrDefault(iso.take(16))
    }
}
