package com.example.domain.model

enum class League(
    val code: String,
    val displayName: String,
    val espnSport: String,
    val espnLeague: String,
    val oddsApiKey: String,
    val marginStdDev: Double,
    val totalStdDev: Double,
    val avgGameTotal: Double
) {
    NBA(
        code = "NBA",
        displayName = "NBA",
        espnSport = "basketball",
        espnLeague = "nba",
        oddsApiKey = "basketball_nba",
        marginStdDev = 11.8,
        totalStdDev = 17.5,
        avgGameTotal = 224.0
    ),
    NFL(
        code = "NFL",
        displayName = "NFL",
        espnSport = "football",
        espnLeague = "nfl",
        oddsApiKey = "americanfootball_nfl",
        marginStdDev = 13.5,
        totalStdDev = 14.2,
        avgGameTotal = 45.5
    ),
    NHL(
        code = "NHL",
        displayName = "NHL",
        espnSport = "hockey",
        espnLeague = "nhl",
        oddsApiKey = "icehockey_nhl",
        marginStdDev = 2.15,
        totalStdDev = 2.1,
        avgGameTotal = 6.0
    ),
    MLB(
        code = "MLB",
        displayName = "MLB",
        espnSport = "baseball",
        espnLeague = "mlb",
        oddsApiKey = "baseball_mlb",
        marginStdDev = 3.25,
        totalStdDev = 3.6,
        avgGameTotal = 8.5
    ),
    EPL(
        code = "EPL",
        displayName = "Premier League",
        espnSport = "soccer",
        espnLeague = "eng.1",
        oddsApiKey = "soccer_epl",
        marginStdDev = 1.45,
        totalStdDev = 1.55,
        avgGameTotal = 2.75
    ),
    NCAAB(
        code = "NCAAB",
        displayName = "NCAAB",
        espnSport = "basketball",
        espnLeague = "mens-college-basketball",
        oddsApiKey = "basketball_ncaab",
        marginStdDev = 11.2,
        totalStdDev = 15.8,
        avgGameTotal = 144.0
    ),
    NCAAF(
        code = "NCAAF",
        displayName = "NCAAF",
        espnSport = "football",
        espnLeague = "college-football",
        oddsApiKey = "americanfootball_ncaaf",
        marginStdDev = 15.6,
        totalStdDev = 16.4,
        avgGameTotal = 53.0
    )
}

enum class MarketType(val label: String) {
    MONEYLINE("Moneyline"),
    SPREAD("Spread"),
    ALT_SPREAD("Alt Spread"),
    TOTAL_OVER("Game Over"),
    TOTAL_UNDER("Game Under"),
    TEAM_TOTAL_OVER("Team Over"),
    TEAM_TOTAL_UNDER("Team Under"),
    PLAYER_PROP_MORE("Player More"),
    PLAYER_PROP_LESS("Player Less")
}

enum class PropSide(val label: String) {
    MORE("MORE"),
    LESS("LESS")
}

enum class EventState {
    UPCOMING,
    LIVE,
    FINAL
}

enum class DevigMethod(val label: String) {
    MULTIPLICATIVE("Multiplicative"),
    POWER("Power (Shin-Approx)")
}

enum class OddsFormat(val label: String) {
    AMERICAN("American (-110)"),
    DECIMAL("Decimal (1.91)")
}

enum class ParlaySizePreference(val label: String, val maxLegs: Int?) {
    TWO_LEGS("2-Pick Parlays", 2),
    THREE_LEGS("3-Pick Optimal", 3),
    ALL("2 & 3 Picks", null)
}

data class TeamInfo(
    val id: String,
    val name: String,
    val abbreviation: String,
    val logoUrl: String?,
    val record: String,
    val score: String?,
    val winPctFromRecord: Double,
    val avgPointsFor: Double? = null
)

data class MarketSnapshot(
    val providerName: String,
    val homeMoneyline: Int?,
    val awayMoneyline: Int?,
    val spreadLine: Double?,
    val homeSpreadOdds: Int?,
    val awaySpreadOdds: Int?,
    val totalLine: Double?,
    val overOdds: Int?,
    val underOdds: Int?,
    val bookCount: Int = 1,
    val sharpConsensusHomeProb: Double? = null,
    val sharpConsensusOverProb: Double? = null
)

data class GameEvent(
    val id: String,
    val league: League,
    val name: String,
    val shortName: String,
    val startTimeIso: String,
    val statusDetail: String,
    val state: EventState,
    val homeTeam: TeamInfo,
    val awayTeam: TeamInfo,
    val predictorHomeWinProb: Double?,
    val marketSnapshot: MarketSnapshot?,
    val dataSourceLabel: String
)

data class PlayerPropPick(
    val id: String,
    val eventId: String,
    val league: League,
    val matchupShort: String,
    val startTimeDisplay: String,
    val playerId: String,
    val playerName: String,
    val teamAbbr: String,
    val sideTag: String, // "HOME" or "AWAY"
    val position: String,
    val headshotUrl: String?,
    val statLabel: String,
    val lineValue: Double,
    val projectedValue: Double,
    val moreProb: Double,
    val lessProb: Double,
    val moreAmericanOdds: Int,
    val lessAmericanOdds: Int,
    val moreEvPercent: Double,
    val lessEvPercent: Double,
    val recommendedSide: PropSide,
    val confidenceThesis: String,
    val isTeamFavorite: Boolean,
    val expectedTotal: Double?,
    val expectedHomeMargin: Double?,
    val isSaved: Boolean = false
)

data class MarketLeg(
    val id: String,
    val eventId: String,
    val league: League,
    val matchupShort: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeLogoUrl: String?,
    val awayLogoUrl: String?,
    val startTimeDisplay: String,
    val marketType: MarketType,
    val selectionLabel: String,
    val sideTag: String, // "HOME", "AWAY", "OVER", "UNDER"
    val lineValue: Double?,
    val offeredAmericanOdds: Int,
    val offeredDecimalOdds: Double,
    val marketImpliedProb: Double,
    val fairTrueProb: Double,
    val evPercent: Double,
    val sharpBookLabel: String,
    val isFavoriteSide: Boolean,
    val expectedTotal: Double?,
    val expectedHomeMargin: Double?
)

data class CorrelationLink(
    val legAId: String,
    val legBId: String,
    val legALabel: String,
    val legBLabel: String,
    val rho: Double,
    val thesis: String
)

data class CorrelatedParlay(
    val id: String,
    val rank: Int,
    val legs: List<MarketLeg>,
    val correlationLinks: List<CorrelationLink>,
    val avgRho: Double,
    val independentProb: Double,
    val correlatedProb: Double,
    val probabilityLift: Double,
    val marketParlayDecimalOdds: Double,
    val marketParlayAmericanOdds: Int,
    val correlatedFairAmericanOdds: Int,
    val independentEvPercent: Double,
    val correlatedEvPercent: Double,
    val quarterKellyPercent: Double,
    val quantScore: Double,
    val primaryThesis: String,
    val isSameGame: Boolean,
    val generatedTimestampMillis: Long,
    val isSaved: Boolean = false,
    val gaussianCopulaProb: Double = correlatedProb,
    val studentTCopulaProb: Double = correlatedProb,
    val paceStressSimProb: Double = correlatedProb,
    val simConsensusCount: Int = 25000,
    val simAgreementScore: Double = 0.94,
    val sharpSoftwareGrade: String = "A+ QUANT SIM"
)

data class QuantFilterPreferences(
    val enabledLeagues: Set<League> = League.entries.toSet(),
    val minHitProbability: Double = 0.30,
    val minCorrelationRho: Double = 0.18,
    val parlaySizePreference: ParlaySizePreference = ParlaySizePreference.THREE_LEGS,
    val devigMethod: DevigMethod = DevigMethod.POWER,
    val oddsFormat: OddsFormat = OddsFormat.AMERICAN,
    val darkTheme: Boolean = true
)

data class FeedStatus(
    val isLoading: Boolean = false,
    val lastUpdatedMillis: Long? = null,
    val espnEventsCount: Int = 0,
    val oddsApiActive: Boolean = false,
    val oddsApiEventsCount: Int = 0,
    val activeLeagues: List<League> = emptyList(),
    val errorMessage: String? = null
)

enum class InjurySeverity {
    OUT,
    DOUBTFUL,
    QUESTIONABLE,
    DAY_TO_DAY,
    PROBABLE
}

data class PlayerInjuryInfo(
    val playerId: String,
    val playerName: String,
    val teamAbbr: String,
    val position: String,
    val status: String,
    val severity: InjurySeverity,
    val detail: String,
    val headshotUrl: String?
)

data class PreviousGamePlayerLine(
    val playerId: String,
    val playerName: String,
    val teamAbbr: String,
    val position: String,
    val played: Boolean,
    val minutesOrStatus: String,
    val statSummary: String,
    val didNotPlayReason: String?
)

data class PreviousGameAvailability(
    val eventId: String,
    val matchSummary: String,
    val playedPlayers: List<PreviousGamePlayerLine>,
    val didNotPlayPlayers: List<PreviousGamePlayerLine>,
    val isLoading: Boolean = false
)

data class PreviousGameStatEntry(
    val eventId: String,
    val dateDisplay: String,
    val opponentAbbr: String,
    val homeAway: String,
    val scoreResult: String,
    val propValues: Map<String, Double>,
    val rawStatsSummary: String,
    val minutesValue: Double? = null,
    val minutesDisplay: String = "",
    val fullStats: Map<String, String> = emptyMap(),
    val efficiencySummary: String = ""
)

enum class PropSimScenario(val label: String, val multiplier: Double, val subtitle: String) {
    BASELINE("Baseline Env", 1.00, "Vegas total pace & spread"),
    FAST_PACE("Fast Pace (+8%)", 1.08, "High-tempo / shootout script"),
    SLOW_GRIND("Slow Grind (-8%)", 0.92, "Half-court / defensive pace"),
    HIGH_USAGE("Usage Bump (+12%)", 1.12, "Key teammate out / high share"),
    BLOWOUT_RISK("Low Mins (-12%)", 0.88, "Foul trouble / early benching")
}

data class SimHistogramBucket(
    val label: String,
    val midpoint: Double,
    val count: Int,
    val fraction: Double,
    val isMoreSide: Boolean
)

data class PropSimulationResult(
    val statLabel: String,
    val lineValue: Double,
    val scenario: PropSimScenario,
    val trialCount: Int,
    val simMean: Double,
    val simMedian: Double,
    val floorP10: Double,
    val ceilingP90: Double,
    val moreProb: Double,
    val lessProb: Double,
    val fairMoreOdds: Int,
    val fairLessOdds: Int,
    val paceMultiplierApplied: Double,
    val histogram: List<SimHistogramBucket>,
    val runId: Int
)

data class ResearchCandidatePlayer(
    val playerId: String,
    val playerName: String,
    val teamAbbr: String,
    val position: String,
    val headshotUrl: String?,
    val eventId: String,
    val league: League,
    val matchupShort: String,
    val primaryProp: PlayerPropPick? = null
)

data class PlayerResearchProfile(
    val playerId: String,
    val playerName: String,
    val teamAbbr: String,
    val league: League,
    val position: String,
    val headshotUrl: String?,
    val upcomingEventId: String,
    val matchupShort: String,
    val propLinesByLabel: Map<String, Double>,
    val gameLogs: List<PreviousGameStatEntry>,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

enum class NbaNewsCategory(val label: String) {
    ALL("All Intel"),
    INJURY_UPDATE("Injuries"),
    LINEUP_ROTATION("Lineups & Moves"),
    PICK_EDGE("Pick Edges"),
    LEAGUE_HEADLINE("Major News")
}

data class NbaNewsArticle(
    val id: String,
    val headline: String,
    val summary: String,
    val publishedIso: String,
    val publishedDisplay: String,
    val category: NbaNewsCategory,
    val pickImpactNote: String,
    val imageUrl: String?,
    val articleUrl: String?,
    val teamTags: List<String> = emptyList(),
    val byline: String = ""
)

enum class NbaAvailabilityBucket(val label: String) {
    WONT_PLAY("Won't Play (Out)"),
    QUESTIONABLE("Questionable / GTD"),
    PLAYING_THIS_WEEK("Active This Week")
}

data class NbaWeeklyPlayerStatus(
    val playerId: String,
    val playerName: String,
    val teamAbbr: String,
    val position: String,
    val headshotUrl: String?,
    val statusLabel: String,
    val bucket: NbaAvailabilityBucket,
    val severity: InjurySeverity,
    val detail: String,
    val gamesThisWeek: Int,
    val nextMatchupDisplay: String,
    val isBackToBackTeam: Boolean = false,
    val beneficiaryTeammates: List<String> = emptyList()
)

data class NbaTeamWeekSchedule(
    val teamId: String,
    val teamAbbr: String,
    val teamName: String,
    val logoUrl: String?,
    val gamesThisWeek: Int,
    val hasBackToBack: Boolean,
    val nextOpponentDisplay: String,
    val highestTotalLine: Double?,
    val avgTotalLine: Double?
)

data class NbaTeamStandingRow(
    val conference: String,
    val seed: Int,
    val teamAbbr: String,
    val teamName: String,
    val logoUrl: String?,
    val wins: Int,
    val losses: Int,
    val winPctDisplay: String,
    val streakDisplay: String,
    val lastTenDisplay: String,
    val pointDiffDisplay: String
)

data class NbaTransactionItem(
    val id: String,
    val dateDisplay: String,
    val teamAbbr: String,
    val teamName: String,
    val description: String,
    val impactTag: String
)

data class NbaIntelBundle(
    val articles: List<NbaNewsArticle> = emptyList(),
    val weeklyPlayerStatuses: List<NbaWeeklyPlayerStatus> = emptyList(),
    val teamWeekSchedules: List<NbaTeamWeekSchedule> = emptyList(),
    val standings: List<NbaTeamStandingRow> = emptyList(),
    val transactions: List<NbaTransactionItem> = emptyList(),
    val weekRangeDisplay: String = "This Week",
    val lastUpdatedMillis: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

