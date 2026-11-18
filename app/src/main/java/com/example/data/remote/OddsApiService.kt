package com.example.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OddsApiService {

    @GET("v4/sports/{sportKey}/odds/")
    suspend fun getOdds(
        @Path("sportKey") sportKey: String,
        @Query("apiKey") apiKey: String,
        @Query("regions") regions: String = "us,eu",
        @Query("markets") markets: String = "h2h,spreads,totals",
        @Query("oddsFormat") oddsFormat: String = "american"
    ): List<OddsApiEventDto>
}
