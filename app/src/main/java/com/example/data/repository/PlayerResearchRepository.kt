package com.example.data.repository

import android.util.Log
import com.example.data.remote.EspnApiService
import com.example.data.remote.NetworkClient
import com.example.domain.model.GameEvent
import com.example.domain.model.InjurySeverity
import com.example.domain.model.League
import com.example.domain.model.PlayerInjuryInfo
import com.example.domain.model.PlayerPropPick
import com.example.domain.model.PlayerResearchProfile
import com.example.domain.model.PreviousGameAvailability
import com.example.domain.model.PreviousGamePlayerLine
import com.example.domain.model.PreviousGameStatEntry
import com.example.domain.model.ResearchCandidatePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.round

class PlayerResearchRepository(
    private val espnApi: EspnApiService = NetworkClient.espnApiService
) {
    private val injuriesCache = ConcurrentHashMap<String, List<PlayerInjuryInfo>>()
    private val rosterCache = ConcurrentHashMap<String, List<ResearchCandidatePlayer>>()
    private val gamelogCache = ConcurrentHashMap<String, PlayerResearchProfile>()
    private val boxscoreCache = ConcurrentHashMap<String, PreviousGameAvailability>()

    suspend fun fetchMatchupInjuries(event: GameEvent): List<PlayerInjuryInfo> =
        withContext(Dispatchers.IO) {
            injuriesCache[event.id]?.let { return@withContext it }

            val collected = mutableListOf<PlayerInjuryInfo>()
            val seenIds = mutableSetOf<String>()

            // 1. Primary: Event summary injuries block
            runCatching {
                val raw = espnApi.getEventSummaryRaw(
                    event.league.espnSport,
                    event.league.espnLeague,
                    event.id
                ).string()
                val root = JSONObject(raw)
                parseInjuriesArray(
                    array = root.optJSONArray("injuries"),
                    event = event,
                    collected = collected,
                    seenIds = seenIds
                )
            }.onFailure {
                Log.w("ResearchRepo", "Summary injuries fetch failed for ${event.id}: ${it.message}")
            }

            // 2. Fallback: League-wide injuries feed filtered to Home & Away teams
            if (collected.isEmpty()) {
                runCatching {
                    val raw = espnApi.getLeagueInjuriesRaw(
                        event.league.espnSport,
                        event.league.espnLeague
                    ).string()
                    val root = JSONObject(raw)
                    parseInjuriesArray(
                        array = root.optJSONArray("injuries"),
                        event = event,
                        collected = collected,
                        seenIds = seenIds
                    )
                }.onFailure {
                    Log.w("ResearchRepo", "League injuries fetch failed for ${event.league.code}: ${it.message}")
                }
            }

            val sorted = collected.sortedBy {
                when (it.severity) {
                    InjurySeverity.OUT -> 0
                    InjurySeverity.DOUBTFUL -> 1
                    InjurySeverity.QUESTIONABLE -> 2
                    InjurySeverity.DAY_TO_DAY -> 3
                    InjurySeverity.PROBABLE -> 4
                }
            }
            injuriesCache[event.id] = sorted
            sorted
        }

    private fun parseInjuriesArray(
        array: JSONArray?,
        event: GameEvent,
        collected: MutableList<PlayerInjuryInfo>,
        seenIds: MutableSet<String>
    ) {
        if (array == null) return
        val homeId = event.homeTeam.id
        val awayId = event.awayTeam.id
        val homeName = event.homeTeam.name.lowercase(Locale.US)
        val awayName = event.awayTeam.name.lowercase(Locale.US)

        for (i in 0 until array.length()) {
            val teamBlock = array.optJSONObject(i) ?: continue
            val teamObj = teamBlock.optJSONObject("team")
            val blockTeamId = teamObj?.optString("id").orEmpty()
            val blockTeamName = (teamObj?.optString("displayName")
                ?: teamBlock.optString("displayName")).lowercase(Locale.US)
            val blockTeamAbbr = teamObj?.optString("abbreviation").orEmpty()

            val matchesHome = (homeId.isNotBlank() && blockTeamId == homeId) ||
                blockTeamAbbr.equals(event.homeTeam.abbreviation, ignoreCase = true) ||
                (homeName.isNotBlank() && blockTeamName.contains(homeName.substringAfterLast(" ")))
            val matchesAway = (awayId.isNotBlank() && blockTeamId == awayId) ||
                blockTeamAbbr.equals(event.awayTeam.abbreviation, ignoreCase = true) ||
                (awayName.isNotBlank() && blockTeamName.contains(awayName.substringAfterLast(" ")))

            if (!matchesHome && !matchesAway) continue
            val resolvedAbbr = if (matchesHome) event.homeTeam.abbreviation else event.awayTeam.abbreviation

            val items = teamBlock.optJSONArray("injuries") ?: continue
            for (j in 0 until items.length()) {
                val inj = items.optJSONObject(j) ?: continue
                val athlete = inj.optJSONObject("athlete") ?: continue
                val athleteId = athlete.optString("id").ifBlank {
                    athlete.optString("displayName")
                }
                if (athleteId.isBlank() || !seenIds.add("${resolvedAbbr}_$athleteId")) continue

                val playerName = athlete.optString("displayName")
                    .ifBlank { athlete.optString("fullName") }
                    .ifBlank { athlete.optString("shortName") }
                if (playerName.isBlank()) continue

                val pos = athlete.optJSONObject("position")?.optString("abbreviation").orEmpty()
                val headshot = extractHeadshot(athlete, event.league, athleteId)
                val statusStr = inj.optString("status")
                    .ifBlank { inj.optJSONObject("type")?.optString("description").orEmpty() }
                    .ifBlank { "Questionable" }
                val comment = inj.optString("shortComment")
                    .ifBlank { inj.optJSONObject("details")?.optString("type").orEmpty() }
                    .ifBlank { inj.optJSONObject("details")?.optString("detail").orEmpty() }
                    .ifBlank { inj.optString("longComment").take(90) }
                    .ifBlank { "Listed on team injury report" }

                collected.add(
                    PlayerInjuryInfo(
                        playerId = athleteId,
                        playerName = playerName,
                        teamAbbr = resolvedAbbr,
                        position = pos,
                        status = statusStr,
                        severity = mapSeverity(statusStr),
                        detail = comment,
                        headshotUrl = headshot
                    )
                )
            }
        }
    }

    suspend fun fetchGameResearchCandidates(
        event: GameEvent,
        existingProps: List<PlayerPropPick>
    ): List<ResearchCandidatePlayer> = withContext(Dispatchers.IO) {
        val propsForGame = existingProps.filter { it.eventId == event.id }
        val result = mutableListOf<ResearchCandidatePlayer>()
        val seenIds = mutableSetOf<String>()

        propsForGame.forEach { prop ->
            if (seenIds.add(prop.playerId)) {
                result.add(
                    ResearchCandidatePlayer(
                        playerId = prop.playerId,
                        playerName = prop.playerName,
                        teamAbbr = prop.teamAbbr,
                        position = prop.position,
                        headshotUrl = prop.headshotUrl,
                        eventId = event.id,
                        league = event.league,
                        matchupShort = event.shortName,
                        primaryProp = prop
                    )
                )
            }
        }

        // Supplement with key starters/roster players from both teams
        val (awayRoster, homeRoster) = supervisorScope {
            val aJob = async { fetchRosterCandidates(event, event.awayTeam.id, event.awayTeam.abbreviation) }
            val hJob = async { fetchRosterCandidates(event, event.homeTeam.id, event.homeTeam.abbreviation) }
            aJob.await() to hJob.await()
        }

        val combinedRoster = (awayRoster.take(6) + homeRoster.take(6))
        combinedRoster.forEach { cand ->
            if (seenIds.add(cand.playerId)) {
                result.add(cand)
            }
        }

        result
    }

    private suspend fun fetchRosterCandidates(
        event: GameEvent,
        teamId: String,
        teamAbbr: String
    ): List<ResearchCandidatePlayer> {
        if (teamId.isBlank()) return emptyList()
        val cacheKey = "${event.league.code}_$teamId"
        rosterCache[cacheKey]?.let { cached ->
            return cached.map { it.copy(eventId = event.id, matchupShort = event.shortName) }
        }

        val parsed = runCatching {
            val raw = espnApi.getTeamRosterRaw(
                event.league.espnSport,
                event.league.espnLeague,
                teamId
            ).string()
            val root = JSONObject(raw)
            val arr = root.optJSONArray("athletes") ?: return@runCatching emptyList()
            val list = mutableListOf<ResearchCandidatePlayer>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val items = obj.optJSONArray("items")
                if (items != null) {
                    for (j in 0 until items.length()) {
                        parseRosterCandidate(items.optJSONObject(j), event, teamAbbr)?.let { list.add(it) }
                    }
                } else {
                    parseRosterCandidate(obj, event, teamAbbr)?.let { list.add(it) }
                }
            }
            list.take(12)
        }.getOrDefault(emptyList())

        if (parsed.isNotEmpty()) {
            rosterCache[cacheKey] = parsed
        }
        return parsed
    }

    private fun parseRosterCandidate(
        obj: JSONObject?,
        event: GameEvent,
        teamAbbr: String
    ): ResearchCandidatePlayer? {
        if (obj == null) return null
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = obj.optString("displayName").ifBlank { obj.optString("fullName") }
        if (name.isBlank()) return null
        val pos = obj.optJSONObject("position")?.optString("abbreviation").orEmpty()
        val headshot = extractHeadshot(obj, event.league, id)
        return ResearchCandidatePlayer(
            playerId = id,
            playerName = name,
            teamAbbr = teamAbbr,
            position = pos,
            headshotUrl = headshot,
            eventId = event.id,
            league = event.league,
            matchupShort = event.shortName
        )
    }

    suspend fun fetchPlayerResearchProfile(
        candidate: ResearchCandidatePlayer,
        matchingProps: List<PlayerPropPick>
    ): PlayerResearchProfile = withContext(Dispatchers.IO) {
        val cacheKey = "${candidate.league.code}_${candidate.playerId}_${candidate.eventId}"
        gamelogCache[cacheKey]?.let { return@withContext it }

        val url = "https://site.web.api.espn.com/apis/common/v3/sports/" +
            "${candidate.league.espnSport}/${candidate.league.espnLeague}/athletes/${candidate.playerId}/gamelog"

        val logsResult = runCatching {
            val raw = espnApi.getRawUrl(url).string()
            parseGamelogJson(raw, candidate.league)
        }

        val gameLogs = logsResult.getOrDefault(emptyList())
        val propLines = mutableMapOf<String, Double>()

        // Seed from active market props for this player first
        matchingProps.filter { it.playerId == candidate.playerId }.forEach { prop ->
            propLines[prop.statLabel] = prop.lineValue
        }

        // Compute realistic half-point threshold lines for all prop categories present in gamelog
        val allCategories = gameLogs.flatMap { it.propValues.keys }.distinct()
        allCategories.forEach { statLabel ->
            if (statLabel !in propLines) {
                val vals = gameLogs.mapNotNull { it.propValues[statLabel] }
                if (vals.isNotEmpty()) {
                    val avg = vals.average()
                    val halfLine = (round(avg * 2.0) / 2.0).let {
                        if (it % 1.0 == 0.0) (it - 0.5).coerceAtLeast(0.5) else it
                    }.coerceAtLeast(0.5)
                    propLines[statLabel] = halfLine
                }
            }
        }

        val profile = PlayerResearchProfile(
            playerId = candidate.playerId,
            playerName = candidate.playerName,
            teamAbbr = candidate.teamAbbr,
            league = candidate.league,
            position = candidate.position,
            headshotUrl = candidate.headshotUrl,
            upcomingEventId = candidate.eventId,
            matchupShort = candidate.matchupShort,
            propLinesByLabel = propLines,
            gameLogs = gameLogs,
            isLoading = false,
            errorMessage = when {
                logsResult.isFailure -> "Could not load ESPN gamelog for ${candidate.playerName}."
                gameLogs.isEmpty() -> "No recent completed games recorded in current season gamelog."
                else -> null
            }
        )
        if (gameLogs.isNotEmpty()) {
            gamelogCache[cacheKey] = profile
        }
        profile
    }

    private fun parseGamelogJson(raw: String, league: League): List<PreviousGameStatEntry> {
        val root = JSONObject(raw)
        val labels = jsonArrayToList(root.optJSONArray("labels"))
        val names = jsonArrayToList(root.optJSONArray("names"))
        val eventsMap = root.optJSONObject("events") ?: JSONObject()

        val rawEntries = mutableListOf<Pair<String, List<String>>>()
        val seasonTypes = root.optJSONArray("seasonTypes") ?: JSONArray()

        for (s in 0 until seasonTypes.length()) {
            val st = seasonTypes.optJSONObject(s) ?: continue
            val categories = st.optJSONArray("categories")
            if (categories != null) {
                for (c in 0 until categories.length()) {
                    val cat = categories.optJSONObject(c) ?: continue
                    val evArr = cat.optJSONArray("events") ?: continue
                    for (e in 0 until evArr.length()) {
                        val evObj = evArr.optJSONObject(e) ?: continue
                        val eid = evObj.optString("eventId")
                        val stats = jsonArrayToList(evObj.optJSONArray("stats"))
                        if (eid.isNotBlank() && stats.isNotEmpty()) {
                            rawEntries.add(eid to stats)
                        }
                    }
                }
            }
            val directEvents = st.optJSONArray("events")
            if (directEvents != null) {
                for (e in 0 until directEvents.length()) {
                    val evObj = directEvents.optJSONObject(e) ?: continue
                    val eid = evObj.optString("eventId")
                    val stats = jsonArrayToList(evObj.optJSONArray("stats"))
                    if (eid.isNotBlank() && stats.isNotEmpty()) {
                        rawEntries.add(eid to stats)
                    }
                }
            }
        }

        // Deduplicate by eventId, take most recent 10 games, and order oldest -> newest left-to-right
        val mapped = rawEntries.distinctBy { it.first }.take(10).mapNotNull { (eventId, stats) ->
            val meta = eventsMap.optJSONObject(eventId)
            val gameDate = meta?.optString("gameDate").orEmpty()
            val oppAbbr = meta?.optJSONObject("opponent")?.optString("abbreviation")
                ?.ifBlank { meta.optString("opponentAbbr") }
                ?: "OPP"
            val atVs = meta?.optString("atVs").orEmpty().ifBlank {
                if (meta?.optString("homeAway") == "away") "@" else "vs"
            }
            val scoreResult = buildString {
                val res = meta?.optString("gameResult").orEmpty()
                val sc = meta?.optString("score").orEmpty()
                if (res.isNotBlank()) append("$res ")
                if (sc.isNotBlank()) append(sc)
            }.trim()

            val statMap = mutableMapOf<String, String>()
            val orderedDisplayStats = linkedMapOf<String, String>()
            for (idx in stats.indices) {
                val labelKey = labels.getOrNull(idx)?.uppercase(Locale.US)
                val nameKey = names.getOrNull(idx)?.lowercase(Locale.US)
                if (!labelKey.isNullOrBlank()) {
                    statMap[labelKey] = stats[idx]
                    orderedDisplayStats[labelKey] = stats[idx]
                }
                if (!nameKey.isNullOrBlank()) statMap[nameKey] = stats[idx]
            }

            val propValues = extractPropValues(league, statMap)
            if (propValues.isEmpty()) return@mapNotNull null

            val minsRaw = statMap["MIN"] ?: statMap["TOI"] ?: statMap["minutes"]
            val minsVal = minsRaw?.let { rawMin ->
                if (":" in rawMin) {
                    val parts = rawMin.split(":")
                    val m = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                    val sec = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                    m + sec / 60.0
                } else {
                    rawMin.toDoubleOrNull()
                }
            }
            val effSummary = buildEfficiencySummary(league, statMap)

            val summaryStr = propValues.entries.take(4).joinToString(" • ") { (k, v) ->
                val vStr = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
                "$vStr $k"
            }

            PreviousGameStatEntry(
                eventId = eventId,
                dateDisplay = formatShortDate(gameDate),
                opponentAbbr = "$atVs$oppAbbr",
                homeAway = atVs,
                scoreResult = scoreResult,
                propValues = propValues,
                rawStatsSummary = summaryStr,
                minutesValue = minsVal,
                minutesDisplay = minsRaw.orEmpty(),
                fullStats = orderedDisplayStats,
                efficiencySummary = effSummary
            )
        }

        // ESPN gamelogs typically arrive newest-first; reverse so chart reads oldest -> latest
        return mapped.reversed()
    }

    private fun buildEfficiencySummary(league: League, statMap: Map<String, String>): String {
        val parts = mutableListOf<String>()
        when (league) {
            League.NBA, League.NCAAB -> {
                statMap["FG"]?.let { parts.add("$it FG") }
                statMap["3PT"]?.let { parts.add("$it 3PT") }
                statMap["FT"]?.let { parts.add("$it FT") }
                (statMap["TO"] ?: statMap["turnovers"])?.let { parts.add("$it TO") }
                statMap["PF"]?.let { parts.add("$it PF") }
            }
            League.NFL, League.NCAAF -> {
                statMap["CMP"]?.let { cmp ->
                    val att = statMap["ATT"]
                    if (att != null) parts.add("$cmp-$att PASS") else parts.add("$cmp CMP")
                }
                (statMap["INT"] ?: statMap["interceptions"])?.let { parts.add("$it INT") }
                statMap["TGTS"]?.let { parts.add("$it TGTS") }
            }
            League.NHL -> {
                statMap["TOI"]?.let { parts.add("$it TOI") }
                statMap["+/-"]?.let { parts.add("$it +/-") }
                statMap["PIM"]?.let { parts.add("$it PIM") }
            }
            League.MLB -> {
                statMap["AB"]?.let { parts.add("$it AB") }
                statMap["BB"]?.let { parts.add("$it BB") }
                statMap["IP"]?.let { parts.add("$it IP") }
                statMap["ER"]?.let { parts.add("$it ER") }
            }
            League.EPL -> {
                statMap["MIN"]?.let { parts.add("${it}m") }
                statMap["YC"]?.let { parts.add("$it YC") }
            }
        }
        return parts.joinToString(" • ")
    }

    private fun extractPropValues(
        league: League,
        statMap: Map<String, String>
    ): Map<String, Double> {
        val props = linkedMapOf<String, Double>()

        fun num(vararg keys: String): Double? {
            for (k in keys) {
                val raw = statMap[k] ?: continue
                val clean = if ("-" in raw && !raw.startsWith("-")) {
                    raw.substringBefore("-")
                } else raw
                clean.toDoubleOrNull()?.let { return it }
            }
            return null
        }

        when (league) {
            League.NBA, League.NCAAB -> {
                val pts = num("PTS", "points")
                val reb = num("REB", "rebounds", "totalrebounds")
                val ast = num("AST", "assists")
                val threes = num("3PT", "3PM", "threepointfieldgoalsmade-threepointfieldgoalsattempted")
                val stl = num("STL", "steals")
                val blk = num("BLK", "blocks")

                if (pts != null) props["Points"] = pts
                if (reb != null) props["Rebounds"] = reb
                if (ast != null) props["Assists"] = ast
                if (threes != null) props["3PM"] = threes
                if (pts != null && reb != null && ast != null) {
                    props["Pts+Reb+Ast"] = pts + reb + ast
                }
                if (stl != null && blk != null) {
                    props["Stl+Blk"] = stl + blk
                }
            }

            League.NFL, League.NCAAF -> {
                num("passingyards", "PYDS")?.let { props["Pass Yds"] = it }
                num("passingtouchdowns", "PTD")?.let { props["Pass TDs"] = it }
                num("rushingyards", "RYDS")?.let { props["Rush Yds"] = it }
                num("receivingyards", "RECYDS")?.let { props["Rec Yds"] = it }
                num("receptions", "REC")?.let { props["Receptions"] = it }
                // Fallback when gamelog uses generic YDS/TD labels
                if (props.isEmpty()) {
                    num("YDS")?.let { props["Yards"] = it }
                    num("TD")?.let { props["TDs"] = it }
                }
            }

            League.NHL -> {
                val g = num("G", "goals")
                val a = num("A", "assists")
                val pts = num("PTS", "points") ?: if (g != null && a != null) g + a else null
                val sog = num("SOG", "S", "shots", "shotsontarget")
                if (sog != null) props["Shots on Goal"] = sog
                if (pts != null) props["Points"] = pts
                if (g != null) props["Goals"] = g
                if (a != null) props["Assists"] = a
            }

            League.MLB -> {
                val h = num("H", "hits")
                val r = num("R", "runs")
                val rbi = num("RBI", "rbis")
                val hr = num("HR", "homeruns")
                val so = num("SO", "K", "strikeouts")
                if (so != null && h == null) {
                    props["Strikeouts"] = so
                } else {
                    if (h != null && r != null && rbi != null) {
                        props["Hits + Runs + RBIs"] = h + r + rbi
                    }
                    if (h != null) props["Hits"] = h
                    if (hr != null) props["Home Runs"] = hr
                }
            }

            League.EPL -> {
                val sh = num("SH", "shots", "totalshots")
                val st = num("ST", "SOG", "shotsontarget")
                val g = num("G", "goals") ?: 0.0
                val a = num("A", "assists") ?: 0.0
                if (st != null) props["Shots on Target"] = st
                if (sh != null) props["Shots Attempted"] = sh
                props["Goals + Assists"] = g + a
            }
        }
        return props
    }

    suspend fun fetchPreviousGameAvailability(
        league: League,
        eventId: String,
        matchSummaryHint: String
    ): PreviousGameAvailability = withContext(Dispatchers.IO) {
        val cacheKey = "${league.code}_$eventId"
        boxscoreCache[cacheKey]?.let { return@withContext it }

        val played = mutableListOf<PreviousGamePlayerLine>()
        val didNotPlay = mutableListOf<PreviousGamePlayerLine>()
        val seenPlayedIds = mutableSetOf<String>()
        val seenDnpIds = mutableSetOf<String>()
        var headerSummary = matchSummaryHint

        runCatching {
            val raw = espnApi.getEventSummaryRaw(
                league.espnSport,
                league.espnLeague,
                eventId
            ).string()
            val root = JSONObject(raw)

            // Build readable header from competition if available
            val comp = root.optJSONObject("header")?.optJSONArray("competitions")?.optJSONObject(0)
            val comps = comp?.optJSONArray("competitors")
            if (comps != null && comps.length() >= 2) {
                val t0 = comps.optJSONObject(0)
                val t1 = comps.optJSONObject(1)
                val s0 = "${t0?.optJSONObject("team")?.optString("abbreviation").orEmpty()} ${t0?.optString("score").orEmpty()}"
                val s1 = "${t1?.optJSONObject("team")?.optString("abbreviation").orEmpty()} ${t1?.optString("score").orEmpty()}"
                if (s0.isNotBlank() && s1.isNotBlank()) {
                    headerSummary = "$s1 vs $s0"
                }
            }

            // Parse boxscore.players
            val playersTeams = root.optJSONObject("boxscore")?.optJSONArray("players") ?: JSONArray()
            for (t in 0 until playersTeams.length()) {
                val teamBlock = playersTeams.optJSONObject(t) ?: continue
                val teamAbbr = teamBlock.optJSONObject("team")?.optString("abbreviation").orEmpty().ifBlank { "TM" }
                val statisticsArr = teamBlock.optJSONArray("statistics") ?: continue

                for (s in 0 until statisticsArr.length()) {
                    val statBlock = statisticsArr.optJSONObject(s) ?: continue
                    val labels = jsonArrayToList(statBlock.optJSONArray("labels"))
                        .ifEmpty { jsonArrayToList(statBlock.optJSONArray("names")) }
                    val athletes = statBlock.optJSONArray("athletes") ?: continue

                    for (a in 0 until athletes.length()) {
                        val row = athletes.optJSONObject(a) ?: continue
                        val ath = row.optJSONObject("athlete") ?: continue
                        val pid = ath.optString("id").ifBlank { ath.optString("displayName") }
                        val pName = ath.optString("displayName").ifBlank { ath.optString("shortName") }
                        if (pid.isBlank() || pName.isBlank()) continue

                        val pos = ath.optJSONObject("position")?.optString("abbreviation").orEmpty()
                        val dnpFlag = row.optBoolean("didNotPlay", false)
                        val reason = row.optString("reason")
                        val stats = jsonArrayToList(row.optJSONArray("stats"))

                        if (dnpFlag || stats.isEmpty()) {
                            if (seenDnpIds.add("${teamAbbr}_$pid")) {
                                didNotPlay.add(
                                    PreviousGamePlayerLine(
                                        playerId = pid,
                                        playerName = pName,
                                        teamAbbr = teamAbbr,
                                        position = pos,
                                        played = false,
                                        minutesOrStatus = "DNP",
                                        statSummary = "",
                                        didNotPlayReason = reason.ifBlank { "Did Not Play / Inactive" }
                                    )
                                )
                            }
                        } else if (seenPlayedIds.add("${teamAbbr}_$pid")) {
                            val minIdx = labels.indexOfFirst {
                                it.equals("MIN", ignoreCase = true) || it.equals("TOI", ignoreCase = true)
                            }
                            val mins = if (minIdx >= 0) stats.getOrNull(minIdx).orEmpty() else ""
                            val compactStats = labels.zip(stats)
                                .filter { (lbl, _) ->
                                    !lbl.equals("MIN", true) &&
                                        !lbl.equals("PF", true) &&
                                        !lbl.equals("+/-", true)
                                }
                                .take(4)
                                .joinToString(" • ") { (lbl, valStr) -> "$valStr $lbl" }

                            played.add(
                                PreviousGamePlayerLine(
                                    playerId = pid,
                                    playerName = pName,
                                    teamAbbr = teamAbbr,
                                    position = pos,
                                    played = true,
                                    minutesOrStatus = if (mins.isNotBlank()) "${mins}m" else "Played",
                                    statSummary = compactStats.ifBlank { stats.take(3).joinToString(" • ") },
                                    didNotPlayReason = null
                                )
                            )
                        }
                    }
                }
            }

            // Also include any players listed in the historical game's injuries list who aren't already in played
            val histInjuries = root.optJSONArray("injuries") ?: JSONArray()
            for (i in 0 until histInjuries.length()) {
                val teamBlock = histInjuries.optJSONObject(i) ?: continue
                val teamAbbr = teamBlock.optJSONObject("team")?.optString("abbreviation").orEmpty().ifBlank { "TM" }
                val items = teamBlock.optJSONArray("injuries") ?: continue
                for (j in 0 until items.length()) {
                    val inj = items.optJSONObject(j) ?: continue
                    val ath = inj.optJSONObject("athlete") ?: continue
                    val pid = ath.optString("id").ifBlank { ath.optString("displayName") }
                    val pName = ath.optString("displayName").ifBlank { ath.optString("shortName") }
                    if (pid.isBlank() || pName.isBlank()) continue
                    if ("${teamAbbr}_$pid" in seenPlayedIds) continue
                    if (seenDnpIds.add("${teamAbbr}_$pid")) {
                        val status = inj.optString("status").ifBlank { "OUT" }
                        val reason = inj.optString("shortComment")
                            .ifBlank { inj.optJSONObject("details")?.optString("type").orEmpty() }
                            .ifBlank { status }
                        didNotPlay.add(
                            PreviousGamePlayerLine(
                                playerId = pid,
                                playerName = pName,
                                teamAbbr = teamAbbr,
                                position = ath.optJSONObject("position")?.optString("abbreviation").orEmpty(),
                                played = false,
                                minutesOrStatus = status.uppercase(Locale.US),
                                statSummary = "",
                                didNotPlayReason = reason
                            )
                        )
                    }
                }
            }
        }.onFailure {
            Log.w("ResearchRepo", "Boxscore fetch failed for $eventId: ${it.message}")
        }

        val availability = PreviousGameAvailability(
            eventId = eventId,
            matchSummary = headerSummary,
            playedPlayers = played,
            didNotPlayPlayers = didNotPlay,
            isLoading = false
        )
        if (played.isNotEmpty() || didNotPlay.isNotEmpty()) {
            boxscoreCache[cacheKey] = availability
        }
        availability
    }

    private fun mapSeverity(status: String): InjurySeverity {
        val s = status.lowercase(Locale.US)
        return when {
            "out" in s || "ir" in s || "susp" in s -> InjurySeverity.OUT
            "doubt" in s -> InjurySeverity.DOUBTFUL
            "question" in s || "gtd" in s -> InjurySeverity.QUESTIONABLE
            "day" in s -> InjurySeverity.DAY_TO_DAY
            else -> InjurySeverity.PROBABLE
        }
    }

    private fun extractHeadshot(athlete: JSONObject, league: League, athleteId: String): String {
        val objHref = athlete.optJSONObject("headshot")?.optString("href")
        val strHref = athlete.optString("headshot").takeIf { it.startsWith("http") }
        if (!objHref.isNullOrBlank() && objHref.startsWith("http")) return objHref
        if (!strHref.isNullOrBlank()) return strHref
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

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return List(arr.length()) { idx -> arr.optString(idx) }
    }

    private fun formatShortDate(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mmXXX",
            "yyyy-MM-dd"
        )
        for (p in patterns) {
            try {
                val parser = SimpleDateFormat(p, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = parser.parse(isoDate) ?: continue
                return SimpleDateFormat("M/d", Locale.US).format(parsed)
            } catch (_: Exception) {
            }
        }
        return isoDate.take(5)
    }
}
