package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EspnScoreboardResponse(
    val events: List<EspnEventDto>? = null
)

@JsonClass(generateAdapter = true)
data class EspnEventDto(
    val id: String? = null,
    val date: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    val competitions: List<EspnCompetitionDto>? = null
)

@JsonClass(generateAdapter = true)
data class EspnCompetitionDto(
    val id: String? = null,
    val date: String? = null,
    val competitors: List<EspnCompetitorDto>? = null,
    val status: EspnStatusDto? = null,
    val odds: List<EspnOddsDto>? = null,
    val leaders: List<EspnLeaderCategoryDto>? = null
)

@JsonClass(generateAdapter = true)
data class EspnCompetitorDto(
    val id: String? = null,
    val homeAway: String? = null,
    val score: String? = null,
    val winner: Boolean? = null,
    val team: EspnTeamDto? = null,
    val records: List<EspnRecordDto>? = null,
    val statistics: List<EspnStatisticDto>? = null,
    val leaders: List<EspnLeaderCategoryDto>? = null
)

@JsonClass(generateAdapter = true)
data class EspnTeamDto(
    val id: String? = null,
    val abbreviation: String? = null,
    val displayName: String? = null,
    val shortDisplayName: String? = null,
    val logo: String? = null,
    val color: String? = null
)

@JsonClass(generateAdapter = true)
data class EspnRecordDto(
    val name: String? = null,
    val type: String? = null,
    val summary: String? = null
)

@JsonClass(generateAdapter = true)
data class EspnStatisticDto(
    val name: String? = null,
    val displayValue: String? = null
)

@JsonClass(generateAdapter = true)
data class EspnStatusDto(
    val clock: Double? = null,
    val displayClock: String? = null,
    val period: Int? = null,
    val type: EspnStatusTypeDto? = null
)

@JsonClass(generateAdapter = true)
data class EspnStatusTypeDto(
    val id: String? = null,
    val name: String? = null,
    val state: String? = null, // "pre", "in", "post"
    val completed: Boolean? = null,
    val shortDetail: String? = null
)

@JsonClass(generateAdapter = true)
data class EspnOddsDto(
    val details: String? = null,
    val overUnder: Double? = null,
    val spread: Double? = null,
    val overOdds: Int? = null,
    val underOdds: Int? = null,
    val provider: EspnProviderDto? = null,
    val homeTeamOdds: EspnTeamOddsDto? = null,
    val awayTeamOdds: EspnTeamOddsDto? = null
)

@JsonClass(generateAdapter = true)
data class EspnProviderDto(
    val id: String? = null,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class EspnTeamOddsDto(
    val favorite: Boolean? = null,
    val underdog: Boolean? = null,
    val moneyLine: Int? = null,
    val spreadOdds: Int? = null
)

@JsonClass(generateAdapter = true)
data class EspnSummaryResponse(
    val pickcenter: List<EspnOddsDto>? = null,
    val predictor: EspnPredictorDto? = null,
    val leaders: List<EspnSummaryTeamLeadersDto>? = null
)

@JsonClass(generateAdapter = true)
data class EspnSummaryTeamLeadersDto(
    val team: EspnTeamDto? = null,
    val leaders: List<EspnLeaderCategoryDto>? = null
)

@JsonClass(generateAdapter = true)
data class EspnLeaderCategoryDto(
    val name: String? = null,
    val displayName: String? = null,
    val shortDisplayName: String? = null,
    val leaders: List<EspnLeaderEntryDto>? = null
)

@JsonClass(generateAdapter = true)
data class EspnLeaderEntryDto(
    val displayValue: String? = null,
    val value: Double? = null,
    val athlete: EspnAthleteDto? = null,
    val team: EspnTeamRefDto? = null
)

@JsonClass(generateAdapter = true)
data class EspnTeamRefDto(
    val id: String? = null
)

data class EspnHeadshotRef(val href: String? = null)

@JsonClass(generateAdapter = true)
data class EspnAthleteDto(
    val id: String? = null,
    val fullName: String? = null,
    val displayName: String? = null,
    val shortName: String? = null,
    val headshot: EspnHeadshotRef? = null,
    val position: EspnPositionDto? = null
)

@JsonClass(generateAdapter = true)
data class EspnPositionDto(
    val abbreviation: String? = null
)

@JsonClass(generateAdapter = true)
data class EspnPredictorDto(
    val header: String? = null,
    val homeTeam: EspnPredictorTeamDto? = null,
    val awayTeam: EspnPredictorTeamDto? = null
)

@JsonClass(generateAdapter = true)
data class EspnPredictorTeamDto(
    val id: String? = null,
    @Json(name = "gameProjection") val gameProjection: String? = null,
    @Json(name = "teamChanceLoss") val teamChanceLoss: String? = null
)
