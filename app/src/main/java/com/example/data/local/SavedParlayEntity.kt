package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_parlays")
data class SavedParlayEntity(
    @PrimaryKey val id: String,
    val title: String,
    val leagueCodes: String,
    val correlatedProb: Double,
    val independentProb: Double,
    val avgRho: Double,
    val correlatedEvPercent: Double,
    val marketAmericanOdds: Int,
    val payloadJson: String,
    val savedAtMillis: Long
)

@Entity(tableName = "market_cache")
data class MarketCacheEntity(
    @PrimaryKey val cacheKey: String = "latest_market_snapshot",
    val eventsJson: String,
    val legsJson: String,
    val propsJson: String = "[]",
    val espnEventsCount: Int,
    val oddsApiActive: Boolean,
    val oddsApiEventsCount: Int,
    val updatedAtMillis: Long
)
