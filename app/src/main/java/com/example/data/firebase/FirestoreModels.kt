package com.example.data.firebase

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val preferredFormat: String = "AMERICAN",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SavedParlayDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val leagueCodes: String = "",
    val correlatedProb: Double = 0.0,
    val independentProb: Double = 0.0,
    val avgRho: Double = 0.0,
    val correlatedEvPercent: Double = 0.0,
    val marketAmericanOdds: Long = 0L,
    val primaryThesis: String = "",
    val legSummaries: List<String> = emptyList(),
    val sameGame: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SavedPropDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val playerName: String = "",
    val teamAbbr: String = "",
    val leagueCode: String = "",
    val matchup: String = "",
    val statLabel: String = "",
    val lineValue: Double = 0.0,
    val projectedValue: Double = 0.0,
    val recommendedSide: String = "MORE",
    val winProb: Double = 0.5,
    val evPercent: Double = 0.0,
    val moreOdds: Long = -115L,
    val lessOdds: Long = -115L,
    val headshotUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
