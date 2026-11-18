package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OddsApiEventDto(
    val id: String? = null,
    @Json(name = "sport_key") val sportKey: String? = null,
    @Json(name = "sport_title") val sportTitle: String? = null,
    @Json(name = "commence_time") val commenceTime: String? = null,
    @Json(name = "home_team") val homeTeam: String? = null,
    @Json(name = "away_team") val awayTeam: String? = null,
    val bookmakers: List<OddsApiBookmakerDto>? = null
)

@JsonClass(generateAdapter = true)
data class OddsApiBookmakerDto(
    val key: String? = null,
    val title: String? = null,
    @Json(name = "last_update") val lastUpdate: String? = null,
    val markets: List<OddsApiMarketDto>? = null
)

@JsonClass(generateAdapter = true)
data class OddsApiMarketDto(
    val key: String? = null, // "h2h", "spreads", "totals"
    val outcomes: List<OddsApiOutcomeDto>? = null
)

@JsonClass(generateAdapter = true)
data class OddsApiOutcomeDto(
    val name: String? = null,
    val price: Int? = null,
    val point: Double? = null
)
