package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CorrEdgeDao {

    @Query("SELECT * FROM saved_parlays ORDER BY savedAtMillis DESC")
    fun observeSavedParlays(): Flow<List<SavedParlayEntity>>

    @Query("SELECT id FROM saved_parlays")
    fun observeSavedParlayIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedParlay(entity: SavedParlayEntity)

    @Query("DELETE FROM saved_parlays WHERE id = :id")
    suspend fun deleteSavedParlay(id: String)

    @Query("SELECT * FROM market_cache WHERE cacheKey = 'latest_market_snapshot' LIMIT 1")
    suspend fun getLatestMarketCache(): MarketCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMarketCache(cache: MarketCacheEntity)
}
