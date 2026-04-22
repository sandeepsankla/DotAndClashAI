package com.pixelplay.dotsboxes.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.PlayerStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "game_prefs")

class GameDataStore(private val context: Context) {

    private val json       = Json { ignoreUnknownKeys = true }
    private val KEY_STATE  = stringPreferencesKey("game_state")
    private val KEY_STATS  = stringPreferencesKey("player_stats")

    // ── Game state ────────────────────────────────────────────────────────────

    fun observeGameState(): Flow<GameState?> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_STATE]?.let {
                runCatching { json.decodeFromString<GameState>(it) }.getOrNull()
            }
        }

    suspend fun save(state: GameState) {
        context.dataStore.edit { it[KEY_STATE] = json.encodeToString(state) }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(KEY_STATE) }
    }

    // ── Player stats ──────────────────────────────────────────────────────────

    fun observeStats(): Flow<PlayerStats> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_STATS]?.let {
                runCatching { json.decodeFromString<PlayerStats>(it) }.getOrNull()
            } ?: PlayerStats()
        }

    suspend fun saveStats(stats: PlayerStats) {
        context.dataStore.edit { it[KEY_STATS] = json.encodeToString(stats) }
    }
}
