package com.pixelplay.dotsboxes.data.repository

import com.pixelplay.dotsboxes.data.local.GameDataStore
import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.PlayerStats
import com.pixelplay.dotsboxes.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow

class GameRepositoryImpl(
    private val dataStore: GameDataStore
) : GameRepository {

    override fun observeGameState(): Flow<GameState?> = dataStore.observeGameState()
    override suspend fun saveGameState(state: GameState) = dataStore.save(state)
    override suspend fun clearGameState() = dataStore.clear()

    override fun observeStats(): Flow<PlayerStats> = dataStore.observeStats()
    override suspend fun saveStats(stats: PlayerStats) = dataStore.saveStats(stats)
}
