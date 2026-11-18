package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.DevigMethod
import com.example.domain.model.League
import com.example.domain.model.OddsFormat
import com.example.domain.model.ParlaySizePreference
import com.example.domain.model.QuantFilterPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.quantDataStore: DataStore<Preferences> by preferencesDataStore(name = "corredge_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val ENABLED_LEAGUES = stringSetPreferencesKey("enabled_leagues")
        val MIN_HIT_PROB = doublePreferencesKey("min_hit_prob")
        val MIN_RHO = doublePreferencesKey("min_rho")
        val PARLAY_SIZE = stringPreferencesKey("parlay_size_v2")
        val DEVIG_METHOD = stringPreferencesKey("devig_method")
        val ODDS_FORMAT = stringPreferencesKey("odds_format")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val preferencesFlow: Flow<QuantFilterPreferences> = context.quantDataStore.data.map { prefs ->
        val leagueSet = prefs[Keys.ENABLED_LEAGUES]
            ?.mapNotNull { code -> League.entries.find { it.code == code } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: League.entries.toSet()

        val sizePref = prefs[Keys.PARLAY_SIZE]?.let { name ->
            ParlaySizePreference.entries.find { it.name == name }
        } ?: ParlaySizePreference.THREE_LEGS

        val devig = prefs[Keys.DEVIG_METHOD]?.let { name ->
            DevigMethod.entries.find { it.name == name }
        } ?: DevigMethod.POWER

        val format = prefs[Keys.ODDS_FORMAT]?.let { name ->
            OddsFormat.entries.find { it.name == name }
        } ?: OddsFormat.AMERICAN

        QuantFilterPreferences(
            enabledLeagues = leagueSet,
            minHitProbability = prefs[Keys.MIN_HIT_PROB] ?: 0.30,
            minCorrelationRho = prefs[Keys.MIN_RHO] ?: 0.18,
            parlaySizePreference = sizePref,
            devigMethod = devig,
            oddsFormat = format,
            darkTheme = prefs[Keys.DARK_THEME] ?: true
        )
    }

    suspend fun toggleLeague(league: League) {
        context.quantDataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_LEAGUES]?.toMutableSet()
                ?: League.entries.map { it.code }.toMutableSet()
            if (league.code in current && current.size > 1) {
                current.remove(league.code)
            } else {
                current.add(league.code)
            }
            prefs[Keys.ENABLED_LEAGUES] = current
        }
    }

    suspend fun setMinHitProbability(prob: Double) {
        context.quantDataStore.edit { it[Keys.MIN_HIT_PROB] = prob.coerceIn(0.15, 0.75) }
    }

    suspend fun setMinCorrelationRho(rho: Double) {
        context.quantDataStore.edit { it[Keys.MIN_RHO] = rho.coerceIn(0.05, 0.65) }
    }

    suspend fun setParlaySizePreference(size: ParlaySizePreference) {
        context.quantDataStore.edit { it[Keys.PARLAY_SIZE] = size.name }
    }

    suspend fun setDevigMethod(method: DevigMethod) {
        context.quantDataStore.edit { it[Keys.DEVIG_METHOD] = method.name }
    }

    suspend fun setOddsFormat(format: OddsFormat) {
        context.quantDataStore.edit { it[Keys.ODDS_FORMAT] = format.name }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.quantDataStore.edit { it[Keys.DARK_THEME] = dark }
    }
}
