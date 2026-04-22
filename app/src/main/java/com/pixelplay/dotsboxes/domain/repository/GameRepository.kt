package com.pixelplay.dotsboxes.domain.repository

import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.PlayerStats
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun observeGameState(): Flow<GameState?>
    suspend fun saveGameState(state: GameState)
    suspend fun clearGameState()

    fun observeStats(): Flow<PlayerStats>
    suspend fun saveStats(stats: PlayerStats)
}
