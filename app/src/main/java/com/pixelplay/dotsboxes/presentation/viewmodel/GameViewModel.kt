package com.pixelplay.dotsboxes.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pixelplay.dotsboxes.ai.AIFactory
import com.pixelplay.dotsboxes.ai.HardAI
import com.pixelplay.dotsboxes.domain.model.*
import com.pixelplay.dotsboxes.domain.repository.GameRepository
import com.pixelplay.dotsboxes.domain.usecase.DrawLineUseCase
import com.pixelplay.dotsboxes.presentation.util.ShareCardGenerator
import com.pixelplay.dotsboxes.sound.SoundManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GameUiState(
    val gameState: GameState         = GameState(),
    val isAiThinking: Boolean        = false,
    val lastLine: LineId?            = null,
    val isMuted: Boolean             = false,
    val playerStats: PlayerStats     = PlayerStats(),
    val hintMove: LineId?            = null,
    val showEarnHintsDialog: Boolean = false,
    /** True when human player just lost — shows Revenge button */
    val playerJustLost: Boolean      = false
)

data class GameConfig(
    val gridSize: Int,
    val mode: GameMode,
    val difficulty: Difficulty,
    val p1Name: String,
    val p2Name: String
)

class GameViewModel(
    private val repository: GameRepository,
    private val sound: SoundManager
) : ViewModel() {

    private val drawLine = DrawLineUseCase()

    private val _ui = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _ui.asStateFlow()

    private var aiJob: Job? = null
    // Prevents init's DataStore restore from overriding an already-started game
    @Volatile private var gameExplicitlyStarted = false

    init {
        viewModelScope.launch {
            repository.observeGameState()
                .filterNotNull()
                .take(1)
                .collect { saved ->
                    if (!gameExplicitlyStarted) {
                        _ui.update { it.copy(gameState = saved) }
                    }
                }
        }
        viewModelScope.launch {
            repository.observeStats().collect { stats ->
                _ui.update { it.copy(playerStats = stats) }
            }
        }
    }

    fun startNewGame(config: GameConfig) {
        gameExplicitlyStarted = true
        aiJob?.cancel()
        val state = newGame(
            gridSize   = config.gridSize,
            mode       = config.mode,
            difficulty = config.difficulty,
            p1Name     = config.p1Name,
            p2Name     = config.p2Name
        )
        _ui.update { it.copy(gameState = state, isAiThinking = false, lastLine = null, playerJustLost = false, hintMove = null) }
        persist(state)
        triggerAiIfNeeded(state)
    }

    fun onLineTapped(lineId: LineId) {
        val current = _ui.value
        if (current.isAiThinking) return
        if (current.gameState.isGameOver) return
        if (current.gameState.isLineDrawn(lineId)) return
        if (current.gameState.gameMode == GameMode.PVA &&
            current.gameState.currentPlayer == PlayerType.TWO) return

        applyLine(lineId)
    }

    fun restartGame() {
        val s = _ui.value.gameState
        startNewGame(GameConfig(s.gridSize, s.gameMode, s.difficulty, s.p1Name, s.p2Name))
    }

    fun toggleMute() {
        val muted = sound.toggleMute()
        _ui.update { it.copy(isMuted = muted) }
    }

    private fun applyLine(lineId: LineId) {
        val state = _ui.value.gameState
        val scored = state.wouldCompleteBox(lineId)
        val newState = drawLine(state, lineId) ?: return

        if (scored) sound.playBoxComplete() else sound.playLineDraw()
        val humanLost = newState.isGameOver &&
            newState.gameMode == GameMode.PVA &&
            newState.winner == PlayerType.TWO

        if (newState.isGameOver) {
            triggerWinSound(newState)
            persistStats(newState)
        }

        _ui.update { it.copy(gameState = newState, lastLine = lineId, playerJustLost = humanLost) }
        persist(newState)
        triggerAiIfNeeded(newState)
    }

    private fun persistStats(finishedState: GameState) {
        val result = when {
            finishedState.winner == null -> GameResult.TIE
            finishedState.winner == PlayerType.ONE -> GameResult.WIN
            else -> GameResult.LOSE
        }
        // Only track difficulty stats in PvA mode (player vs AI)
        val difficulty = if (finishedState.gameMode == GameMode.PVA) finishedState.difficulty else null
        viewModelScope.launch {
            val current = repository.observeStats().first()
            repository.saveStats(current.afterResult(result, difficulty))
        }
    }

    // ── Hint system (Hard mode) ───────────────────────────────────────────────

    fun useHint() {
        val stats = _ui.value.playerStats
        val state = _ui.value.gameState
        if (state.isGameOver || state.currentPlayer == PlayerType.TWO) return
        if (stats.hintCoins <= 0) {
            _ui.update { it.copy(showEarnHintsDialog = true) }
            return
        }
        val hint = HardAI().getBestMove(state) ?: return
        viewModelScope.launch {
            val current = repository.observeStats().first()
            repository.saveStats(current.spendHint())
        }
        _ui.update { it.copy(hintMove = hint) }
        // Auto-dismiss hint highlight after 3 s
        viewModelScope.launch {
            delay(3000L)
            _ui.update { it.copy(hintMove = null) }
        }
    }

    fun earnHintsFromShare(context: Context) {
        viewModelScope.launch {
            val current = repository.observeStats().first()
            repository.saveStats(current.earnHints(3))
        }
        _ui.update { it.copy(showEarnHintsDialog = false) }
        ShareCardGenerator.shareAppInvite(context)
    }

    fun earnHintsFromAd() {
        _ui.update { it.copy(showEarnHintsDialog = false) }
        viewModelScope.launch {
            delay(1800L)   // simulate ad loading/watching
            val current = repository.observeStats().first()
            repository.saveStats(current.earnHints(2))
        }
    }

    fun dismissEarnHintsDialog() = _ui.update { it.copy(showEarnHintsDialog = false) }

    private fun triggerAiIfNeeded(state: GameState) {
        if (state.isGameOver) return
        if (state.gameMode != GameMode.PVA) return
        if (state.currentPlayer != PlayerType.TWO) return

        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _ui.update { it.copy(isAiThinking = true) }
            delay(450L)
            val consecLosses = _ui.value.playerStats.consecutiveLossesHard
            val ai = AIFactory.create(state.difficulty, consecLosses)
            val move = ai.getBestMove(state)
            _ui.update { it.copy(isAiThinking = false) }
            move?.let { applyLine(it) }
        }
    }

    private fun triggerWinSound(state: GameState) {
        when {
            state.winner == null -> sound.playTie()
            state.gameMode == GameMode.PVP -> sound.playWin()
            state.winner == PlayerType.ONE -> sound.playWin()
            else -> sound.playLose()
        }
    }

    private fun persist(state: GameState) {
        viewModelScope.launch { repository.saveGameState(state) }
    }

    // ── Factory for manual DI ──────────────────────────────────────────────────

    class Factory(
        private val repository: GameRepository,
        private val sound: SoundManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameViewModel(repository, sound) as T
    }
}
