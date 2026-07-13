package com.pixelplay.dotsboxes.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pixelplay.dotsboxes.ai.AIFactory
import com.pixelplay.dotsboxes.ai.HardAI
import com.pixelplay.dotsboxes.domain.model.*
import com.pixelplay.dotsboxes.analytics.Analytics
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
    val playerJustLost: Boolean      = false,
    /** Non-null when playing a campaign level */
    val levelNumber: Int?            = null,
    /** Vibration haptic on/off */
    val isVibrationEnabled: Boolean  = true,
    /** Last drawn line (any player/mode) — pulsing highlight for 2 seconds */
    val lastMoveHighlight: LineId?   = null,
    /** Move countdown for timed levels (Level 8); null = no timer */
    val moveTimerSeconds: Int?       = null,
    /** Coins bursting from winner boxes (phase 1 of end animation) */
    val showWinCoinBurst: Boolean    = false,
    /** Show XP result screen (phase 2, after coin burst) */
    val showXpScreen: Boolean        = false,
    /** XP earned this game (for display in XP screen) */
    val xpEarned: Int                = 0,
    /** DotCoins earned this game (for display in XP screen) */
    val coinsToCollect: Int          = 0,
    /** 👑 Crown / 🎁 Mystery box positions on this board (PVA only) */
    val specialBoxes: SpecialBoxes   = SpecialBoxes(),
    /** Non-null when the human just captured a special box → show reward popup */
    val specialReward: SpecialRewardEvent? = null,
    /** True right after the 2nd daily win → offer a free Lucky Spin */
    val dailyMissionSpinEarned: Boolean = false
)

data class GameConfig(
    val gridSize: Int,
    val mode: GameMode,
    val difficulty: Difficulty,
    val p1Name: String,
    val p2Name: String,
    val levelNumber: Int? = null
)

class GameViewModel(
    private val repository: GameRepository,
    private val sound: SoundManager
) : ViewModel() {

    private val drawLine = DrawLineUseCase()

    private val _ui = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _ui.asStateFlow()

    private var aiJob: Job? = null
    private var aiGlowJob: Job? = null
    private var timerJob: Job? = null
    private var tutorialJob: Job? = null
    // Prevents init's DataStore restore from overriding an already-started game
    @Volatile private var gameExplicitlyStarted = false
    // When the current game started — used to skip interstitials after very short games
    private var gameStartMs = 0L

    /** True if the last game lasted long enough to justify showing an interstitial. */
    fun wasLongGame(): Boolean = System.currentTimeMillis() - gameStartMs >= 20_000L

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
                sound.setMuted(!stats.soundEnabled)
                _ui.update { it.copy(
                    playerStats        = stats,
                    isMuted            = !stats.soundEnabled,
                    isVibrationEnabled = stats.vibrationEnabled
                ) }
            }
        }
    }

    fun startNewGame(config: GameConfig) {
        gameExplicitlyStarted = true
        aiJob?.cancel()
        timerJob?.cancel()
        tutorialJob?.cancel()
        val state = newGame(
            gridSize   = config.gridSize,
            mode       = config.mode,
            difficulty = config.difficulty,
            p1Name     = config.p1Name,
            p2Name     = config.p2Name
        )
        // Special reward boxes — only vs AI, and not on the level-1 tutorial
        val special = if (config.mode == GameMode.PVA && config.levelNumber != 1)
            generateSpecialBoxes(config.gridSize) else SpecialBoxes()
        _ui.update { it.copy(
            gameState        = state,
            isAiThinking     = false,
            lastLine         = null,
            playerJustLost   = false,
            hintMove         = null,
            levelNumber      = config.levelNumber,
            lastMoveHighlight       = null,
            moveTimerSeconds = null,
            showWinCoinBurst = false,
            showXpScreen     = false,
            xpEarned         = 0,
            coinsToCollect   = 0,
            specialBoxes     = special,
            specialReward    = null
        ) }
        gameStartMs = System.currentTimeMillis()
        Analytics.gameStart(config.mode.name, config.difficulty.name, config.gridSize, config.levelNumber)
        persist(state)
        triggerAiIfNeeded(state)
        // Level 1: tutorial auto-hints; Level 8: move timer
        if (state.gameMode == GameMode.PVA && state.currentPlayer == PlayerType.ONE) {
            startMoveTimerIfNeeded()
            startTutorialHintIfNeeded()
        }
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

    fun toggleVibration() {
        _ui.update { it.copy(isVibrationEnabled = !it.isVibrationEnabled) }
    }

    /** Change the active board theme mid-game (persists to stats). */
    fun setSkin(skin: BoardSkin) {
        viewModelScope.launch {
            val current = repository.observeStats().first()
            repository.saveStats(current.withActiveSkin(skin))
        }
    }

    fun dismissSpecialReward() {
        _ui.update { it.copy(specialReward = null) }
    }

    /** Add bonus coins (e.g. from "Watch Ad → 2× Coins"). */
    fun addBonusCoins(n: Int) {
        if (n <= 0) return
        viewModelScope.launch {
            val cur = repository.observeStats().first()
            repository.saveStats(cur.earnDotCoins(n))
        }
    }

    fun dismissDailyMissionSpin() {
        _ui.update { it.copy(dailyMissionSpinEarned = false) }
    }

    /**
     * Detect special boxes captured by the human in this move, award their rewards,
     * and return (updated special-box set, event to show). No-op for AI / PvP moves.
     */
    private fun handleSpecialCaptures(before: GameState, after: GameState): Pair<SpecialBoxes, SpecialRewardEvent?> {
        var special = _ui.value.specialBoxes
        val mover   = before.currentPlayer
        if (special.isEmpty || before.gameMode != GameMode.PVA || mover != PlayerType.ONE) {
            return special to null
        }
        var event: SpecialRewardEvent? = null
        for (r in 0 until after.gridSize) for (c in 0 until after.gridSize) {
            if (before.boxes[r][c] == null && after.boxes[r][c] == mover) {
                when {
                    special.isCrown(r, c) -> {
                        special = special.without(r, c)
                        event   = SpecialRewardEvent.Crown
                        awardCrown()
                    }
                    special.isMystery(r, c) -> {
                        val reward = rollMysteryReward()
                        special = special.without(r, c)
                        event   = SpecialRewardEvent.Mystery(reward)
                        awardMystery(reward)
                    }
                }
            }
        }
        return special to event
    }

    private fun awardCrown() {
        sound.playBoxComplete()
        Analytics.crownCaptured("offline")
        viewModelScope.launch {
            val cur = repository.observeStats().first()
            repository.saveStats(cur.addSpin())
        }
    }

    private fun awardMystery(reward: MysteryReward) {
        Analytics.mysteryOpened("offline", reward.kind.name)
        viewModelScope.launch {
            val cur = repository.observeStats().first()
            val updated = when (reward.kind) {
                MysteryKind.COINS, MysteryKind.JACKPOT -> cur.earnDotCoins(reward.amount)
                MysteryKind.XP      -> cur.copy(xp = cur.xp + reward.amount)
                MysteryKind.HINTS   -> cur.earnHints(reward.amount)
                MysteryKind.NOTHING -> cur
            }
            repository.saveStats(updated)
        }
    }

    private fun applyLine(lineId: LineId) {
        // Cancel any running timer/tutorial — player acted
        timerJob?.cancel()
        tutorialJob?.cancel()
        _ui.update { it.copy(moveTimerSeconds = null) }

        val state    = _ui.value.gameState
        val isAiMove = state.gameMode == GameMode.PVA && state.currentPlayer == PlayerType.TWO
        val scored   = state.wouldCompleteBox(lineId)
        val newState = drawLine(state, lineId) ?: return

        if (scored) sound.playBoxComplete() else sound.playLineDraw()

        // 👑/🎁 special box captures (human only) → award + popup
        val (updatedSpecial, specialEvent) = handleSpecialCaptures(state, newState)
        val humanLost = newState.isGameOver &&
            newState.gameMode == GameMode.PVA &&
            newState.winner == PlayerType.TWO
        val humanWon = newState.isGameOver &&
            (newState.winner == PlayerType.ONE ||
             (newState.gameMode == GameMode.PVP && newState.winner != null))
        // 2 DotCoins per box captured by P1 (or P1 side in PVP)
        val coinsEarned = if (humanWon) newState.p1Score * 2 else 0
        // XP awarded matches afterResult() values
        val xpThisGame = if (newState.isGameOver) when (newState.winner) {
            PlayerType.ONE -> 50
            null           -> 25
            else           -> 15
        } else 0

        if (newState.isGameOver) {
            val resultStr = when (newState.winner) {
                PlayerType.ONE -> "win"; null -> "tie"; else -> "lose"
            }
            Analytics.gameEnd(newState.gameMode.name, resultStr, newState.p1Score, newState.p2Score)
            triggerWinSound(newState)
            persistStats(newState, coinsEarned)
            // Phase 1: coin burst on board for 1.4s, then XP screen
            viewModelScope.launch {
                delay(1400L)
                _ui.update { it.copy(showWinCoinBurst = false, showXpScreen = true) }
            }
        }

        _ui.update { it.copy(
            gameState         = newState,
            lastLine          = lineId,
            playerJustLost    = humanLost,
            lastMoveHighlight = lineId,        // highlight for ALL players/modes
            hintMove          = null,
            showWinCoinBurst  = newState.isGameOver,
            showXpScreen      = false,
            xpEarned          = xpThisGame,
            coinsToCollect    = coinsEarned,
            specialBoxes      = updatedSpecial,
            specialReward     = specialEvent ?: it.specialReward
        ) }
        persist(newState)

        // Auto-clear last move highlight after 2 seconds
        aiGlowJob?.cancel()
        aiGlowJob = viewModelScope.launch {
            delay(2000L)
            _ui.update { it.copy(lastMoveHighlight = null) }
        }

        triggerAiIfNeeded(newState)

        // After AI move, if now human's turn start timer/tutorial again
        if (!newState.isGameOver &&
            newState.gameMode == GameMode.PVA &&
            newState.currentPlayer == PlayerType.ONE) {
            startMoveTimerIfNeeded()
            startTutorialHintIfNeeded()
        }
    }

    private fun persistStats(finishedState: GameState, coinsEarned: Int = 0) {
        val result = when {
            finishedState.winner == null -> GameResult.TIE
            finishedState.winner == PlayerType.ONE -> GameResult.WIN
            else -> GameResult.LOSE
        }
        val difficulty = if (finishedState.gameMode == GameMode.PVA) finishedState.difficulty else null
        val currentLevel = _ui.value.levelNumber
        viewModelScope.launch {
            val current = repository.observeStats().first()
            var updated = current.afterResult(result, difficulty)
            if (result == GameResult.WIN && currentLevel != null) {
                updated = updated.afterLevelComplete(currentLevel)
                Analytics.levelComplete(currentLevel)
            }
            if (result == GameResult.WIN) {
                val wasComplete = current.dailyTaskComplete
                updated = updated.withDailyWin()
                // Just hit the 2-win daily target → reward a free Lucky Spin
                if (!wasComplete && updated.dailyTaskComplete) {
                    updated = updated.addSpin()
                    Analytics.dailyMissionComplete()
                    _ui.update { it.copy(dailyMissionSpinEarned = true) }
                }
            }
            if (coinsEarned > 0) {
                updated = updated.earnDotCoins(coinsEarned)
            }
            repository.saveStats(updated)
        }
    }

    fun dismissXpScreen() {
        _ui.update { it.copy(showXpScreen = false) }
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

    // ── Move timer (Level 8 — 15s per move) ───────────────────────────────────

    private fun startMoveTimerIfNeeded() {
        val lvl    = _ui.value.levelNumber ?: return
        val config = levelConfigFor(lvl)
        val limit  = config.timeLimitSeconds ?: return
        val state  = _ui.value.gameState
        if (state.isGameOver || state.currentPlayer != PlayerType.ONE) return

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (s in limit downTo 0) {
                _ui.update { it.copy(moveTimerSeconds = s) }
                if (s == 0) {
                    // Time's up — auto-play a random safe move
                    val cur  = _ui.value.gameState
                    val safe = cur.undrawnLines().filter { !cur.wouldGiveOpponent3Sides(it) }
                    val auto = (safe.ifEmpty { cur.undrawnLines() }).randomOrNull()
                    auto?.let { applyLine(it) }
                    break
                }
                delay(1000L)
            }
            _ui.update { it.copy(moveTimerSeconds = null) }
        }
    }

    // ── Tutorial auto-hint (Level 1 — free hint each human turn) ─────────────

    private fun startTutorialHintIfNeeded() {
        val lvl    = _ui.value.levelNumber ?: return
        val config = levelConfigFor(lvl)
        if (!config.hasTutorial) return
        val state = _ui.value.gameState
        if (state.isGameOver || state.currentPlayer != PlayerType.ONE) return

        tutorialJob?.cancel()
        tutorialJob = viewModelScope.launch {
            delay(600L)  // Let player see the board first
            val hint = HardAI().getBestMove(_ui.value.gameState) ?: return@launch
            _ui.update { it.copy(hintMove = hint) }
            delay(3000L)
            _ui.update { it.copy(hintMove = null) }
        }
    }

    private fun triggerAiIfNeeded(state: GameState) {
        if (state.isGameOver) return
        if (state.gameMode != GameMode.PVA) return
        if (state.currentPlayer != PlayerType.TWO) return

        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _ui.update { it.copy(isAiThinking = true) }
            delay(450L)
            val consecLosses = _ui.value.playerStats.consecutiveLossesAi
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
