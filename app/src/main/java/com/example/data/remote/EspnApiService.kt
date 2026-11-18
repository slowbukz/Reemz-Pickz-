package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface EspnApiService {

    @GET("apis/site/v2/sports/{sport}/{league}/scoreboard")
    suspend fun getScoreboard(
        @Path("sport") sport: String,
        @Path("league") league: String,
        @Query("dates") dates: String? = null
    ): EspnScoreboardResponse

    @GET("apis/site/v2/sports/{sport}/{league}/summary")
    suspend fun getEventSummary(
        @Path("sport") sport: String,
        @Path("league") league: String,
        @Query("event") eventId: String
    ): EspnSummaryResponse

    @GET("apis/site/v2/sports/{sport}/{league}/summary")
    suspend fun getEventSummaryRaw(
        @Path("sport") sport: String,
        @Path("league") league: String,
        @Query("event") eventId: String
    ): ResponseBody

    @GET("apis/site/v2/sports/{sport}/{league}/injuries")
    suspend fun getLeagueInjuriesRaw(
        @Path("sport") sport: String,
        @Path("league") league: String
    ): ResponseBody

    @GET("apis/site/v2/sports/{sport}/{league}/teams/{teamId}/roster")
    suspend fun getTeamRosterRaw(
        @Path("sport") sport: String,
        @Path("league") league: String,
        @Path("teamId") teamId: String,
        @Query("enable") enable: String? = null
    ): ResponseBody

    @GET("apis/site/v2/sports/basketball/nba/news")
    suspend fun getNbaNewsRaw(
        @Query("limit") limit: Int = 35
    ): ResponseBody

    @GET("apis/site/v2/sports/basketball/nba/transactions")
    suspend fun getNbaTransactionsRaw(
        @Query("limit") limit: Int = 25
    ): ResponseBody

    @GET
    suspend fun getRawUrl(@Url fullUrl: String): ResponseBody
}
