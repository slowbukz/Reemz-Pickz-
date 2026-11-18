package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.domain.math.OddsMath
import com.example.domain.model.CorrelatedParlay
import com.example.domain.model.PlayerPropPick
import com.example.domain.model.PropSide
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun safeDocId(rawId: String): String {
        val cleaned = rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        if (cleaned.isNotBlank() && cleaned.length <= 100) return cleaned
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawId.toByteArray(Charsets.UTF_8))
        return "doc_" + digest.joinToString("") { "%02x".format(it) }.take(36)
    }

    suspend fun ensureUserProfile(displayName: String, preferredFormat: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val safeName = displayName.trim().ifBlank { "Reemz Quant" }.take(80)
            val format = if (preferredFormat == "DECIMAL") "DECIMAL" else "AMERICAN"
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "preferredFormat" to format,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "preferredFormat" to format,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeSavedParlays(userId: String): Flow<List<SavedParlayDoc>> = flow {
        val path = "users/$userId/saved_parlays"
        emitAll(
            db.collection("users").document(userId).collection("saved_parlays")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        SavedParlayDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSavedProps(userId: String): Flow<List<SavedPropDoc>> = flow {
        val path = "users/$userId/saved_props"
        emitAll(
            db.collection("users").document(userId).collection("saved_props")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        SavedPropDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveParlay(parlay: CorrelatedParlay): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/saved_parlays"
        return try {
            val uid = requireUserId()
            val docId = safeDocId(parlay.id)
            val docRef = db.collection("users").document(uid)
                .collection("saved_parlays").document(docId)
            docPath = docRef.path

            val title = parlay.legs.joinToString(" + ") { it.selectionLabel }.take(200).ifBlank { "Correlated Parlay" }
            val leagueCodes = parlay.legs.map { it.league.code }.distinct().joinToString("/").take(40).ifBlank { "MIX" }
            val summaries = parlay.legs.take(5).map {
                "${it.selectionLabel} (${OddsMath.formatOdds(it.offeredAmericanOdds, com.example.domain.model.OddsFormat.AMERICAN)})"
            }
            val payload = mapOf(
                "userId" to uid,
                "title" to title,
                "leagueCodes" to leagueCodes,
                "correlatedProb" to parlay.correlatedProb.coerceIn(0.0, 1.0),
                "independentProb" to parlay.independentProb.coerceIn(0.0, 1.0),
                "avgRho" to parlay.avgRho.coerceIn(-1.0, 1.0),
                "correlatedEvPercent" to parlay.correlatedEvPercent.coerceIn(-1.0, 5.0),
                "marketAmericanOdds" to parlay.marketParlayAmericanOdds.toLong(),
                "primaryThesis" to parlay.primaryThesis.take(400).ifBlank { "Correlated market edge" },
                "legSummaries" to summaries,
                "sameGame" to parlay.isSameGame,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSavedParlay(docIdOrRawId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/saved_parlays"
        return try {
            val uid = requireUserId()
            val docId = safeDocId(docIdOrRawId)
            val docRef = db.collection("users").document(uid)
                .collection("saved_parlays").document(docId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveProp(prop: PlayerPropPick): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/saved_props"
        return try {
            val uid = requireUserId()
            val docId = safeDocId(prop.id)
            val docRef = db.collection("users").document(uid)
                .collection("saved_props").document(docId)
            docPath = docRef.path

            val recommendedProb = if (prop.recommendedSide == PropSide.MORE) prop.moreProb else prop.lessProb
            val recommendedEv = if (prop.recommendedSide == PropSide.MORE) prop.moreEvPercent else prop.lessEvPercent
            val headshot = prop.headshotUrl?.takeIf { it.isNotBlank() }?.take(500)

            val rawMap = mapOf(
                "userId" to uid,
                "playerName" to prop.playerName.take(100).ifBlank { "Player" },
                "teamAbbr" to prop.teamAbbr.take(16).ifBlank { "TM" },
                "leagueCode" to prop.league.code.take(16),
                "matchup" to prop.matchupShort.take(60).ifBlank { "Matchup" },
                "statLabel" to prop.statLabel.take(50).ifBlank { "Stat" },
                "lineValue" to prop.lineValue.coerceAtLeast(0.0),
                "projectedValue" to prop.projectedValue.coerceAtLeast(0.0),
                "recommendedSide" to prop.recommendedSide.name,
                "winProb" to recommendedProb.coerceIn(0.0, 1.0),
                "evPercent" to recommendedEv.coerceIn(-1.0, 5.0),
                "moreOdds" to prop.moreAmericanOdds.toLong(),
                "lessOdds" to prop.lessAmericanOdds.toLong(),
                "headshotUrl" to headshot,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            val payload = rawMap.filterValues { it != null } as Map<String, Any>
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSavedProp(propId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/saved_props"
        return try {
            val uid = requireUserId()
            val docId = safeDocId(propId)
            val docRef = db.collection("users").document(uid)
                .collection("saved_props").document(docId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
