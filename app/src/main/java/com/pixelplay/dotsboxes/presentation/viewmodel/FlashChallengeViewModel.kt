package com.pixelplay.dotsboxes.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pixelplay.dotsboxes.ai.FlashAI
import com.pixelplay.dotsboxes.data.remote.FirebaseManager
import com.pixelplay.dotsboxes.domain.repository.GameRepository
import com.pixelplay.dotsboxes.domain.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FlashUiState(
    val gameState: GameState          = GameState(),
    val preFilledLines: Set<LineId>   = emptySet(),
    val playerFreshLines: Set<LineId> = emptySet(),
    val aiFreshLines: Set<LineId>     = emptySet(),
    val secondsLeft: Int              = 15,
    val isRunning: Boolean            = false,
    val isPlayerTurn: Boolean         = true,
    val isOver: Boolean               = false,
    val playerScore: Int              = 0,
    val aiScore: Int                  = 0,
    val playerWon: Boolean            = false,
    val spinAwarded: Boolean          = false,
    val playerStats: PlayerStats      = PlayerStats(),
    val statsLoaded: Boolean          = false   // true once DB stats are read
)

class FlashChallengeViewModel(
    private val repository: GameRepository,
    private val firebase: FirebaseManager
) : ViewModel() {

    private val board = FlashChallengeGenerator.generate()
    private val ai    = FlashAI()

    private val _ui = MutableStateFlow(
        FlashUiState(
            gameState      = board.state,
            preFilledLines = board.preFilledLines
        )
    )
    val uiState: StateFlow<FlashUiState> = _ui.asStateFlow()

    private var timerJob: Job? = null
    private var secondsUsed = 0

    init {
        viewModelScope.launch {
            // Read stats from DB first, THEN decide whether to allow play
            repository.observeStats().collect { stats ->
                _ui.update { it.copy(playerStats = stats, statsLoaded = true) }
            }
        }
    }

    /** Called by screen after statsLoaded=true; starts game only if not played today. */
    fun tryStart() {
        val ui = _ui.value
        if (!ui.statsLoaded) return
        if (ui.playerStats.hasPlayedFlashToday) return  // already played — show locked screen
        if (ui.isRunning || ui.isOver) return
        _ui.update { it.copy(isRunning = true, isPlayerTurn = true) }
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            repeat(15) { tick ->
                delay(1000L)
                secondsUsed = tick + 1
                val left = 14 - tick
                _ui.update { it.copy(secondsLeft = left) }
                if (left == 0) endGame()
            }
        }
    }

    /** Player taps a line — only allowed on player's strict turn. */
    fun onLineTapped(lineId: LineId) {
        val ui = _ui.value
        if (!ui.isRunning || ui.isOver || !ui.isPlayerTurn) return
        if (ui.gameState.isGameOver) return
        if (ui.gameState.isLineDrawn(lineId)) return

        // Force current player = ONE so applyMove attributes line correctly
        val stateAsP1 = ui.gameState.copy(currentPlayer = PlayerType.ONE)
        val next      = stateAsP1.applyMove(lineId)

        _ui.update {
            it.copy(
                gameState        = next.copy(currentPlayer = PlayerType.TWO), // hand off to AI
                playerFreshLines = it.playerFreshLines + lineId,
                playerScore      = next.p1Score,
                aiScore          = next.p2Score,
                isPlayerTurn     = false
            )
        }

        if (next.isGameOver) { endGame(); return }
        triggerAi()
    }

    private fun triggerAi() {
        viewModelScope.launch {
            delay(400L) // small pause so player sees their line appear
            val ui = _ui.value
            if (!ui.isRunning || ui.isOver) return@launch

            val remaining = ui.gameState.undrawnLines()
            if (remaining.isEmpty()) { endGame(); return@launch }

            // AI picks one move (forced as PlayerType.TWO)
            val stateAsP2 = ui.gameState.copy(currentPlayer = PlayerType.TWO)
            val move      = ai.getBestMove(stateAsP2) ?: return@launch
            val next      = stateAsP2.applyMove(move)

            _ui.update {
                it.copy(
                    gameState    = next.copy(currentPlayer = PlayerType.ONE), // hand back to player
                    aiFreshLines = it.aiFreshLines + move,
                    playerScore  = next.p1Score,
                    aiScore      = next.p2Score,
                    isPlayerTurn = true
                )
            }

            if (next.isGameOver) endGame()
        }
    }

    private fun endGame() {
        if (_ui.value.isOver) return
        timerJob?.cancel()
        val ui        = _ui.value
        val playerWon = ui.playerScore > ui.aiScore

        _ui.update {
            it.copy(
                isRunning    = false,
                isOver       = true,
                isPlayerTurn = false,
                playerWon    = playerWon,
                spinAwarded  = playerWon,
                secondsLeft  = 0
            )
        }

        viewModelScope.launch {
            runCatching {
                val current = repository.observeStats().first()
                var updated = current.withFlashChallengePlayed()
                if (playerWon) updated = updated.addSpin()
                if (ui.playerScore > 0) updated = updated.earnDotCoins(ui.playerScore * 3)
                repository.saveStats(updated)

                if (ui.playerScore > 0 || ui.aiScore > 0) {
                    firebase.saveFlashScore(
                        playerName = current.playerName,
                        score      = ui.playerScore,
                        timeTaken  = secondsUsed
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    class Factory(
        private val repository: GameRepository,
        private val firebase: FirebaseManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FlashChallengeViewModel(repository, firebase) as T
    }
}
