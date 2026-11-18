package com.example.data.repository

import android.util.Log
import com.example.data.remote.EspnApiService
import com.example.data.remote.EspnEventDto
import com.example.data.remote.NetworkClient
import com.example.domain.model.InjurySeverity
import com.example.domain.model.League
import com.example.domain.model.NbaAvailabilityBucket
import com.example.domain.model.NbaIntelBundle
import com.example.domain.model.NbaNewsArticle
import com.example.domain.model.NbaNewsCategory
import com.example.domain.model.NbaTeamStandingRow
import com.example.domain.model.NbaTeamWeekSchedule
import com.example.domain.model.NbaTransactionItem
import com.example.domain.model.NbaWeeklyPlayerStatus
import com.example.domain.model.PlayerPropPick
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

class NbaIntelRepository(
    private val espnApi: EspnApiService = NetworkClient.espnApiService
) {

    @Volatile
    private var cachedBundle: NbaIntelBundle? = null
    @Volatile
    private var lastFetchMillis: Long = 0L

    suspend fun fetchNbaIntel(
        activeNbaProps: List<PlayerPropPick>,
        forceRefresh: Boolean = false
    ): NbaIntelBundle = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = cachedBundle
        if (!forceRefresh && existing != null && (now - lastFetchMillis) < 45_000L) {
            return@withContext enrichStatusesWithProps(existing, activeNbaProps)
        }

        val easternTz = TimeZone.getTimeZone("America/New_York")
        val dateFmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = easternTz }
        val displayDayFmt = SimpleDateFormat("MMM d", Locale.US).apply { timeZone = easternTz }

        val cal = Calendar.getInstance(easternTz)
        val yesterdayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val endWeekCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
        val weekRangeDisplay = "${displayDayFmt.format(cal.time)} – ${displayDayFmt.format(endWeekCal.time)}"

        val yesterdayKey = dateFmt.format(yesterdayCal.time)
        val weekDateKeys = (0..6).map { offset ->
            val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            dateFmt.format(c.time)
        }
        val allQueryDates = listOf(yesterdayKey) + weekDateKeys

        val (articles, injuriesJson, transactions, standings, scoreboardsByDay) = supervisorScope {
            val newsDeferred = async {
                runCatching {
                    val raw = espnApi.getNbaNewsRaw(35).string()
                    parseNbaNewsJson(raw)
                }.onFailure {
                    Log.w("NbaIntelRepo", "NBA news fetch failed: ${it.message}")
                }.getOrDefault(emptyList())
            }

            val injuriesDeferred = async {
                runCatching {
                    espnApi.getLeagueInjuriesRaw("basketball", "nba").string()
                }.onFailure {
                    Log.w("NbaIntelRepo", "NBA injuries fetch failed: ${it.message}")
                }.getOrNull()
            }

            val txDeferred = async {
                runCatching {
                    val raw = espnApi.getNbaTransactionsRaw(25).string()
                    parseNbaTransactionsJson(raw)
                }.onFailure {
                    Log.w("NbaIntelRepo", "NBA transactions fetch failed: ${it.message}")
                }.getOrDefault(emptyList())
            }

            val standingsDeferred = async {
                runCatching {
                    val raw = espnApi.getRawUrl(
                        "https://site.api.espn.com/apis/v2/sports/basketball/nba/standings"
                    ).string()
                    parseNbaStandingsJson(raw)
                }.onFailure {
                    Log.w("NbaIntelRepo", "NBA standings fetch failed: ${it.message}")
                }.getOrDefault(emptyList())
            }

            val scheduleDeferred = async {
                supervisorScope {
                    allQueryDates.map { dateStr ->
                        async {
                            val events = runCatching {
                                espnApi.getScoreboard("basketball", "nba", dates = dateStr).events.orEmpty()
                            }.getOrDefault(emptyList())
                            dateStr to events
                        }
                    }.awaitAll()
                }
            }

            Pentad(
                newsDeferred.await(),
                injuriesDeferred.await(),
                txDeferred.await(),
                standingsDeferred.await(),
                scheduleDeferred.await()
            )
        }

        val teamSchedules = buildWeeklyTeamSchedules(
            yesterdayKey = yesterdayKey,
            weekDateKeys = weekDateKeys,
            scoreboardsByDay = scoreboardsByDay
        )

        val parsedInjuries = injuriesJson?.let { raw ->
            parseNbaWeeklyInjuries(raw, teamSchedules)
        }.orEmpty()

        val bundle = NbaIntelBundle(
            articles = articles,
            weeklyPlayerStatuses = parsedInjuries,
            teamWeekSchedules = teamSchedules.values.sortedWith(
                compareByDescending<NbaTeamWeekSchedule> { it.hasBackToBack }
                    .thenByDescending { it.gamesThisWeek }
                    .thenByDescending { it.highestTotalLine ?: 0.0 }
            ),
            standings = standings,
            transactions = transactions,
            weekRangeDisplay = weekRangeDisplay,
            lastUpdatedMillis = System.currentTimeMillis(),
            isLoading = false,
            errorMessage = if (articles.isEmpty() && parsedInjuries.isEmpty() && teamSchedules.isEmpty()) {
                "Unable to reach ESPN NBA feeds right now. Tap refresh to retry."
            } else null
        )

        cachedBundle = bundle
        lastFetchMillis = System.currentTimeMillis()
        enrichStatusesWithProps(bundle, activeNbaProps)
    }

    private fun enrichStatusesWithProps(
        bundle: NbaIntelBundle,
        activeNbaProps: List<PlayerPropPick>
    ): NbaIntelBundle {
        val nbaProps = activeNbaProps.filter { it.league == League.NBA }
        if (nbaProps.isEmpty()) return bundle

        val scheduleByAbbr = bundle.teamWeekSchedules.associateBy { it.teamAbbr.uppercase(Locale.US) }
        val propsByTeam = nbaProps.groupBy { it.teamAbbr.uppercase(Locale.US) }
        val injuredIds = bundle.weeklyPlayerStatuses.map { it.playerId }.toSet()
        val injuredNames = bundle.weeklyPlayerStatuses.map { it.playerName.lowercase(Locale.US) }.toSet()

        val enrichedInjured = bundle.weeklyPlayerStatuses.map { status ->
            if (status.bucket == NbaAvailabilityBucket.WONT_PLAY) {
                val beneficiaries = propsByTeam[status.teamAbbr.uppercase(Locale.US)]
                    .orEmpty()
                    .filterNot { it.playerId == status.playerId || it.playerName.equals(status.playerName, ignoreCase = true) }
                    .distinctBy { it.playerId }
                    .take(2)
                    .map { "${it.playerName} (${formatStatLine(it.lineValue)} ${it.statLabel})" }
                status.copy(beneficiaryTeammates = beneficiaries)
            } else {
                status
            }
        }

        // Also highlight key active NBA stars expected to play this week with posted lines
        val activeStarsThisWeek = nbaProps
            .distinctBy { it.playerId }
            .filterNot { prop ->
                prop.playerId in injuredIds || prop.playerName.lowercase(Locale.US) in injuredNames
            }
            .take(18)
            .map { prop ->
                val teamSched = scheduleByAbbr[prop.teamAbbr.uppercase(Locale.US)]
                val gamesCount = teamSched?.gamesThisWeek ?: 1
                val nextMatch = teamSched?.nextOpponentDisplay ?: prop.matchupShort
                NbaWeeklyPlayerStatus(
                    playerId = prop.playerId,
                    playerName = prop.playerName,
                    teamAbbr = prop.teamAbbr,
                    position = prop.position,
                    headshotUrl = prop.headshotUrl,
                    statusLabel = "EXPECTED ACTIVE",
                    bucket = NbaAvailabilityBucket.PLAYING_THIS_WEEK,
                    severity = InjurySeverity.PROBABLE,
                    detail = "Active in rotation • Proj ${String.format(Locale.US, "%.1f", prop.projectedValue)} vs ${formatStatLine(prop.lineValue)} ${prop.statLabel} (${prop.recommendedSide.label})",
                    gamesThisWeek = gamesCount,
                    nextMatchupDisplay = nextMatch,
                    isBackToBackTeam = teamSched?.hasBackToBack == true,
                    beneficiaryTeammates = emptyList()
                )
            }

        val combined = (enrichedInjured + activeStarsThisWeek)
            .distinctBy { "${it.bucket.name}_${it.teamAbbr}_${it.playerName.lowercase(Locale.US)}" }

        return bundle.copy(weeklyPlayerStatuses = combined)
    }

    private fun buildWeeklyTeamSchedules(
        yesterdayKey: String,
        weekDateKeys: List<String>,
        scoreboardsByDay: List<Pair<String, List<EspnEventDto>>>
    ): Map<String, NbaTeamWeekSchedule> {
        data class MutableTeamWeek(
            val teamId: String,
            val abbr: String,
            val name: String,
            val logoUrl: String?,
            val playedDates: MutableSet<String> = linkedSetOf(),
            val futureDates: MutableList<String> = mutableListOf(),
            var nextMatchup: String = "This Week",
            var nextMatchupDateKey: String = "99999999",
            val totals: MutableList<Double> = mutableListOf()
        )

        val map = linkedMapOf<String, MutableTeamWeek>()
        val weekSet = weekDateKeys.toSet()

        scoreboardsByDay.forEach { (dateKey, events) ->
            events.forEach { dto ->
                val comp = dto.competitions?.firstOrNull() ?: return@forEach
                val competitors = comp.competitors.orEmpty()
                val home = competitors.firstOrNull { it.homeAway == "home" } ?: competitors.getOrNull(0) ?: return@forEach
                val away = competitors.firstOrNull { it.homeAway == "away" } ?: competitors.getOrNull(1) ?: return@forEach

                val homeAbbr = home.team?.abbreviation.orEmpty().ifBlank { "HOME" }
                val awayAbbr = away.team?.abbreviation.orEmpty().ifBlank { "AWAY" }
                val totalLine = comp.odds?.firstOrNull()?.overUnder
                val timeStr = comp.status?.type?.shortDetail?.takeIf { it.isNotBlank() } ?: dateKey

                listOf(
                    Triple(home, awayAbbr, "vs $awayAbbr"),
                    Triple(away, homeAbbr, "@ $homeAbbr")
                ).forEach { (teamComp, _, oppLabel) ->
                    val abbr = teamComp.team?.abbreviation.orEmpty().ifBlank { return@forEach }
                    val key = abbr.uppercase(Locale.US)
                    val entry = map.getOrPut(key) {
                        MutableTeamWeek(
                            teamId = teamComp.team?.id ?: teamComp.id.orEmpty(),
                            abbr = abbr,
                            name = teamComp.team?.displayName ?: teamComp.team?.shortDisplayName ?: abbr,
                            logoUrl = teamComp.team?.logo
                        )
                    }
                    entry.playedDates.add(dateKey)
                    if (dateKey in weekSet) {
                        if (dateKey !in entry.futureDates) {
                            entry.futureDates.add(dateKey)
                        }
                        if (totalLine != null && totalLine > 150.0) {
                            entry.totals.add(totalLine)
                        }
                        if (dateKey < entry.nextMatchupDateKey) {
                            entry.nextMatchupDateKey = dateKey
                            entry.nextMatchup = "$oppLabel • $timeStr"
                        }
                    }
                }
            }
        }

        val orderedKeys = listOf(yesterdayKey) + weekDateKeys
        return map.mapValues { (_, entry) ->
            var hasB2B = false
            for (i in 0 until orderedKeys.lastIndex) {
                if (orderedKeys[i] in entry.playedDates && orderedKeys[i + 1] in entry.playedDates) {
                    hasB2B = true
                    break
                }
            }
            NbaTeamWeekSchedule(
                teamId = entry.teamId,
                teamAbbr = entry.abbr,
                teamName = entry.name,
                logoUrl = entry.logoUrl,
                gamesThisWeek = entry.futureDates.size,
                hasBackToBack = hasB2B,
                nextOpponentDisplay = entry.nextMatchup,
                highestTotalLine = entry.totals.maxOrNull(),
                avgTotalLine = entry.totals.takeIf { it.isNotEmpty() }?.average()
            )
        }
    }

    internal fun parseNbaNewsJson(rawJson: String): List<NbaNewsArticle> {
        val root = JSONObject(rawJson)
        val arr = root.optJSONArray("articles") ?: return emptyList()
        val list = mutableListOf<NbaNewsArticle>()
        val seenIds = mutableSetOf<String>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val headline = obj.optString("headline").trim()
            if (headline.isBlank()) continue
            val id = obj.optString("id").ifBlank { obj.optString("dataSourceIdentifier").ifBlank { headline.hashCode().toString() } }
            if (!seenIds.add(id)) continue

            val summary = obj.optString("description").trim()
            val published = obj.optString("published").trim()
            val byline = obj.optString("byline").trim()

            val imageUrl = obj.optJSONArray("images")?.optJSONObject(0)?.optString("url")
                ?.takeIf { it.startsWith("http") }
            val articleUrl = obj.optJSONObject("links")
                ?.optJSONObject("web")
                ?.optString("href")
                ?.takeIf { it.startsWith("http") }

            val tags = mutableListOf<String>()
            val cats = obj.optJSONArray("categories")
            if (cats != null) {
                for (c in 0 until cats.length()) {
                    val catObj = cats.optJSONObject(c) ?: continue
                    val type = catObj.optString("type")
                    val desc = catObj.optString("description").trim()
                    if (type == "team" && desc.isNotBlank()) {
                        tags.add(desc)
                    }
                }
            }

            val combinedText = "$headline $summary".lowercase(Locale.US)
            val category = classifyNewsCategory(combinedText)
            val pickImpact = buildPickImpactNote(category, combinedText)

            list.add(
                NbaNewsArticle(
                    id = id,
                    headline = headline,
                    summary = summary,
                    publishedIso = published,
                    publishedDisplay = formatRelativeOrShortTime(published),
                    category = category,
                    pickImpactNote = pickImpact,
                    imageUrl = imageUrl,
                    articleUrl = articleUrl,
                    teamTags = tags.distinct().take(3),
                    byline = byline
                )
            )
        }
        return list
    }

    internal fun parseNbaWeeklyInjuries(
        rawJson: String,
        teamSchedules: Map<String, NbaTeamWeekSchedule>
    ): List<NbaWeeklyPlayerStatus> {
        val root = JSONObject(rawJson)
        val arr = root.optJSONArray("injuries") ?: return emptyList()
        val results = mutableListOf<NbaWeeklyPlayerStatus>()
        val seen = mutableSetOf<String>()

        for (i in 0 until arr.length()) {
            val teamBlock = arr.optJSONObject(i) ?: continue
            val teamObj = teamBlock.optJSONObject("team")
            val teamDisplay = teamObj?.optString("displayName") ?: teamBlock.optString("displayName")
            val teamAbbr = teamObj?.optString("abbreviation")?.takeIf { it.isNotBlank() }
                ?: inferNbaTeamAbbr(teamDisplay)
            val schedule = teamSchedules[teamAbbr.uppercase(Locale.US)]
                ?: teamSchedules.values.firstOrNull {
                    teamDisplay.isNotBlank() && it.teamName.contains(teamDisplay.substringAfterLast(" "), ignoreCase = true)
                }

            val items = teamBlock.optJSONArray("injuries") ?: continue
            for (j in 0 until items.length()) {
                val inj = items.optJSONObject(j) ?: continue
                val athlete = inj.optJSONObject("athlete") ?: continue
                val name = athlete.optString("displayName")
                    .ifBlank { athlete.optString("fullName") }
                    .ifBlank { athlete.optString("shortName") }
                if (name.isBlank()) continue

                val id = athlete.optString("id").ifBlank { "${teamAbbr}_$name" }
                if (!seen.add("${teamAbbr}_$id")) continue

                val pos = athlete.optJSONObject("position")?.optString("abbreviation").orEmpty()
                val headshot = athlete.optJSONObject("headshot")?.optString("href")
                    ?.takeIf { it.startsWith("http") }
                    ?: if (id.all { it.isDigit() }) {
                        "https://a.espncdn.com/i/headshots/nba/players/full/$id.png"
                    } else null

                val statusText = inj.optString("status")
                    .ifBlank { inj.optJSONObject("type")?.optString("description").orEmpty() }
                    .ifBlank { "Questionable" }

                val detailsObj = inj.optJSONObject("details")
                val returnDate = detailsObj?.optString("returnDate")?.takeIf { it.isNotBlank() }
                val comment = inj.optString("shortComment")
                    .ifBlank { inj.optString("longComment").take(110) }
                    .ifBlank { detailsObj?.optString("detail").orEmpty() }
                    .ifBlank { "Listed on NBA official injury report" }
                val fullDetail = buildString {
                    append(comment.trim())
                    if (returnDate != null && !comment.contains(returnDate, ignoreCase = true)) {
                        append(" • Est. Return: $returnDate")
                    }
                }

                val severity = mapSeverity(statusText)
                val bucket = when (severity) {
                    InjurySeverity.OUT, InjurySeverity.DOUBTFUL -> NbaAvailabilityBucket.WONT_PLAY
                    InjurySeverity.QUESTIONABLE, InjurySeverity.DAY_TO_DAY -> NbaAvailabilityBucket.QUESTIONABLE
                    InjurySeverity.PROBABLE -> NbaAvailabilityBucket.PLAYING_THIS_WEEK
                }

                results.add(
                    NbaWeeklyPlayerStatus(
                        playerId = id,
                        playerName = name,
                        teamAbbr = schedule?.teamAbbr ?: teamAbbr,
                        position = pos,
                        headshotUrl = headshot,
                        statusLabel = statusText.uppercase(Locale.US),
                        bucket = bucket,
                        severity = severity,
                        detail = fullDetail,
                        gamesThisWeek = schedule?.gamesThisWeek ?: 0,
                        nextMatchupDisplay = schedule?.nextOpponentDisplay ?: "Schedule TBD",
                        isBackToBackTeam = schedule?.hasBackToBack == true
                    )
                )
            }
        }

        return results.sortedWith(
            compareBy<NbaWeeklyPlayerStatus> {
                when (it.bucket) {
                    NbaAvailabilityBucket.WONT_PLAY -> 0
                    NbaAvailabilityBucket.QUESTIONABLE -> 1
                    NbaAvailabilityBucket.PLAYING_THIS_WEEK -> 2
                }
            }.thenByDescending { it.gamesThisWeek }.thenBy { it.teamAbbr }.thenBy { it.playerName }
        )
    }

    internal fun parseNbaTransactionsJson(rawJson: String): List<NbaTransactionItem> {
        val root = JSONObject(rawJson)
        val arr = root.optJSONArray("transactions") ?: return emptyList()
        val list = mutableListOf<NbaTransactionItem>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val desc = obj.optString("description").trim()
            if (desc.isBlank()) continue
            val dateRaw = obj.optString("date").trim()
            val teamObj = obj.optJSONObject("team")
            val teamAbbr = teamObj?.optString("abbreviation").orEmpty().ifBlank { "NBA" }
            val teamName = teamObj?.optString("displayName").orEmpty().ifBlank { teamAbbr }
            val id = "${dateRaw}_${teamAbbr}_${desc.hashCode()}"
            val lower = desc.lowercase(Locale.US)
            val tag = when {
                "recalled" in lower || "assigned" in lower -> "ROSTER / G-LEAGUE"
                "signed" in lower || "10-day" in lower || "two-way" in lower -> "DEPTH ADDITION"
                "waived" in lower || "traded" in lower -> "ROTATION SHIFT"
                "suspended" in lower -> "AVAILABILITY"
                else -> "TRANSACTION"
            }
            list.add(
                NbaTransactionItem(
                    id = id,
                    dateDisplay = formatRelativeOrShortTime(dateRaw),
                    teamAbbr = teamAbbr,
                    teamName = teamName,
                    description = desc,
                    impactTag = tag
                )
            )
            if (list.size >= 15) break
        }
        return list
    }

    internal fun parseNbaStandingsJson(rawJson: String): List<NbaTeamStandingRow> {
        val root = JSONObject(rawJson)
        val children = root.optJSONArray("children") ?: return emptyList()
        val rows = mutableListOf<NbaTeamStandingRow>()

        for (i in 0 until children.length()) {
            val confObj = children.optJSONObject(i) ?: continue
            val confName = confObj.optString("shortName")
                .ifBlank { confObj.optString("name") }
                .ifBlank { "Conference" }
                .replace(" Conference", "")
            val entries = confObj.optJSONObject("standings")?.optJSONArray("entries") ?: continue

            for (j in 0 until entries.length()) {
                val entry = entries.optJSONObject(j) ?: continue
                val teamObj = entry.optJSONObject("team") ?: continue
                val abbr = teamObj.optString("abbreviation").ifBlank { continue }
                val name = teamObj.optString("displayName").ifBlank { abbr }
                val logo = teamObj.optJSONArray("logos")?.optJSONObject(0)?.optString("href")

                val statsArr = entry.optJSONArray("stats") ?: JSONArray()
                val statMap = mutableMapOf<String, String>()
                for (s in 0 until statsArr.length()) {
                    val st = statsArr.optJSONObject(s) ?: continue
                    val key = st.optString("name").ifBlank { st.optString("abbreviation") }
                    val disp = st.optString("displayValue").ifBlank { st.optString("value") }
                    if (key.isNotBlank()) statMap[key] = disp
                }

                val wins = statMap["wins"]?.toDoubleOrNull()?.toInt() ?: 0
                val losses = statMap["losses"]?.toDoubleOrNull()?.toInt() ?: 0
                val winPct = statMap["winPercent"] ?: ".500"
                val streak = statMap["streak"] ?: "-"
                val l10 = statMap["Last Ten Games"] ?: statMap["L10"] ?: "-"
                val diff = statMap["differential"] ?: statMap["pointDifferential"] ?: "0.0"

                rows.add(
                    NbaTeamStandingRow(
                        conference = confName,
                        seed = j + 1,
                        teamAbbr = abbr,
                        teamName = name,
                        logoUrl = logo,
                        wins = wins,
                        losses = losses,
                        winPctDisplay = winPct,
                        streakDisplay = streak,
                        lastTenDisplay = l10,
                        pointDiffDisplay = diff
                    )
                )
            }
        }
        return rows
    }

    internal fun classifyNewsCategory(textLower: String): NbaNewsCategory {
        val injuryKeywords = listOf(
            "injury", "out ", "out-", "sidelined", "questionable", "doubtful", "probable",
            "ankle", "knee", "hamstring", "shoulder", "back", "calf", "wrist", "concussion",
            "surgery", "mri", "miss ", "misses", "return", "rest", "load management", "il ", "day-to-day"
        )
        val rotationKeywords = listOf(
            "starting", "starter", "lineup", "bench", "minutes", "rotation", "trade",
            "signed", "waived", "suspended", "coach", "rookie", "promoted", "recalled"
        )
        val edgeKeywords = listOf(
            "streak", "points", "triple-double", "pace", "defense", "scoring", "cup",
            "playoff", "standings", "mvp", "clutch", "record", "shooting", "three-point"
        )
        return when {
            injuryKeywords.any { it in textLower } -> NbaNewsCategory.INJURY_UPDATE
            rotationKeywords.any { it in textLower } -> NbaNewsCategory.LINEUP_ROTATION
            edgeKeywords.any { it in textLower } -> NbaNewsCategory.PICK_EDGE
            else -> NbaNewsCategory.LEAGUE_HEADLINE
        }
    }

    internal fun buildPickImpactNote(category: NbaNewsCategory, textLower: String): String {
        return when (category) {
            NbaNewsCategory.INJURY_UPDATE -> when {
                "out" in textLower || "miss" in textLower || "sidelined" in textLower || "surgery" in textLower ->
                    "Pick Edge: Absence opens primary usage & rebounds for active teammates; avoid correlated overs on sidelined team without adjustment."
                "return" in textLower || "available" in textLower || "cleared" in textLower ->
                    "Pick Edge: Returning rotation player can trim secondary bench usage — check minutes limit before taking MORE."
                else ->
                    "Pick Edge: Monitor official status before lock; watch teammate assist & usage redistribution."
            }
            NbaNewsCategory.LINEUP_ROTATION ->
                "Pick Edge: Rotation/minute shifts directly impact role-player Rebounds, Assists, and Alt Points ladders."
            NbaNewsCategory.PICK_EDGE ->
                "Pick Edge: High-form pace & team form trend supports correlated game totals and primary creator props."
            NbaNewsCategory.LEAGUE_HEADLINE ->
                "League Context: Track matchup intensity, coaching adjustments, and team motivation entering this week."
            NbaNewsCategory.ALL ->
                "League Intel: Factor lineup and form news into upcoming picks."
        }
    }

    private fun mapSeverity(status: String): InjurySeverity {
        val s = status.lowercase(Locale.US)
        return when {
            "out" in s || "inj" in s || "susp" in s -> InjurySeverity.OUT
            "doubt" in s -> InjurySeverity.DOUBTFUL
            "question" in s -> InjurySeverity.QUESTIONABLE
            "day" in s -> InjurySeverity.DAY_TO_DAY
            "prob" in s -> InjurySeverity.PROBABLE
            else -> InjurySeverity.QUESTIONABLE
        }
    }

    private fun formatRelativeOrShortTime(iso: String): String {
        if (iso.isBlank()) return "Recent"
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mmXXX"
        )
        for (p in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(p, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(iso)
            }.getOrNull()
            if (parsed != null) {
                val diffMin = ((System.currentTimeMillis() - parsed.time) / 60_000L).coerceAtLeast(0L)
                return when {
                    diffMin < 60L -> "${diffMin}m ago"
                    diffMin < 1440L -> "${diffMin / 60L}h ago"
                    else -> SimpleDateFormat("MMM d", Locale.US).format(parsed)
                }
            }
        }
        return iso.take(10)
    }

    private fun inferNbaTeamAbbr(displayName: String): String {
        val lower = displayName.lowercase(Locale.US)
        return when {
            "hawks" in lower -> "ATL"
            "celtics" in lower -> "BOS"
            "nets" in lower -> "BKN"
            "hornets" in lower -> "CHA"
            "bulls" in lower -> "CHI"
            "cavaliers" in lower -> "CLE"
            "mavericks" in lower -> "DAL"
            "nuggets" in lower -> "DEN"
            "pistons" in lower -> "DET"
            "warriors" in lower -> "GS"
            "rockets" in lower -> "HOU"
            "pacers" in lower -> "IND"
            "clippers" in lower -> "LAC"
            "lakers" in lower -> "LAL"
            "grizzlies" in lower -> "MEM"
            "heat" in lower -> "MIA"
            "bucks" in lower -> "MIL"
            "timberwolves" in lower -> "MIN"
            "pelicans" in lower -> "NO"
            "knicks" in lower -> "NY"
            "thunder" in lower -> "OKC"
            "magic" in lower -> "ORL"
            "76ers" in lower -> "PHI"
            "suns" in lower -> "PHX"
            "trail blazers" in lower -> "POR"
            "kings" in lower -> "SAC"
            "spurs" in lower -> "SA"
            "raptors" in lower -> "TOR"
            "jazz" in lower -> "UTAH"
            "wizards" in lower -> "WSH"
            else -> displayName.take(3).uppercase(Locale.US).ifBlank { "NBA" }
        }
    }

    private fun formatStatLine(value: Double): String {
        return if (abs(value - value.toInt()) < 0.01) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    private data class Pentad<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
