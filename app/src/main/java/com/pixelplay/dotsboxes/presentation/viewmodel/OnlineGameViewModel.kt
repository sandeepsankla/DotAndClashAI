package com.pixelplay.dotsboxes.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pixelplay.dotsboxes.data.remote.FirebaseManager
import com.pixelplay.dotsboxes.domain.repository.GameRepository
import com.pixelplay.dotsboxes.domain.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class OnlineConnectionStatus { CONNECTED, RECONNECTING, OPPONENT_LEFT }

data class OnlineUiState(
    val gameState: GameState                     = GameState(),
    val isMyTurn: Boolean                        = false,
    val myRole: PlayerType                       = PlayerType.ONE,
    val opponentName: String                     = "Waiting...",
    val roomCode: String                         = "",
    val roomStatus: RoomStatus                   = RoomStatus.WAITING,
    val connectionStatus: OnlineConnectionStatus = OnlineConnectionStatus.CONNECTED,
    val lastLine: LineId?                        = null,
    val lastMoveHighlight: LineId?               = null,
    val errorMessage: String?                    = null,
    val showWinCoinBurst: Boolean                = false,
    val showXpScreen: Boolean                    = false,
    val xpEarned: Int                            = 0,
    val coinsEarned: Int                         = 0,
    val playerStats: PlayerStats                 = PlayerStats(),
    val specialBoxes: SpecialBoxes               = SpecialBoxes(),
    val specialReward: SpecialRewardEvent?       = null
)

class OnlineGameViewModel(
    private val roomCode: String,
    private val isHost: Boolean,
    private val myName: String,
    private val gridSize: Int,
    private val firebase: FirebaseManager,
    private val repository: GameRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(OnlineUiState(
        roomCode = roomCode,
        myRole   = if (isHost) PlayerType.ONE else PlayerType.TWO,
        opponentName = "Waiting..."
    ))
    val uiState: StateFlow<OnlineUiState> = _ui.asStateFlow()

    private var appliedMoveCount  = 0
    private var highlightJob: Job? = null
    private var gameEndHandled    = false
    private var disconnectCancelled = false
    // Special boxes derived from the room code → identical for both players
    private val specialBoxes = generateSpecialBoxes(gridSize, roomCode.hashCode().toLong())
    private val awardedSpecials = mutableSetOf<Pair<Int, Int>>()

    init {
        _ui.update { it.copy(specialBoxes = specialBoxes) }
        listenToRoom()
        viewModelScope.launch {
            repository.observeStats().collect { stats ->
                _ui.update { it.copy(playerStats = stats) }
            }
        }
    }

    private fun listenToRoom() {
        viewModelScope.launch {
            firebase.listenToRoom(roomCode).collect { room ->
                if (room == null) return@collect

                // Match is live → stop auto-abandon on transient disconnects (ad/background/blip)
                if (room.status == RoomStatus.PLAYING && !disconnectCancelled) {
                    disconnectCancelled = true
                    com.pixelplay.dotsboxes.analytics.Analytics.onlineMatchStart()
                    firebase.cancelStatusOnDisconnect(roomCode)
                    // Count this online game toward the daily free-games quota
                    viewModelScope.launch {
                        val cur = repository.observeStats().first()
                        repository.saveStats(cur.withOnlineGamePlayed())
                    }
                }

                val opponentName = if (isHost) room.guestName ?: "Waiting..."
                                   else        room.hostName

                var state = newGame(
                    gridSize   = room.gridSize,
                    mode       = GameMode.PVP,
                    difficulty = Difficulty.MEDIUM,
                    p1Name     = room.hostName,
                    p2Name     = room.guestName ?: "Guest"
                )
                var lastApplied: LineId? = null
                for (move in room.moves) {
                    val lineId = LineId(move.row, move.col, move.isHorizontal)
                    if (!state.isLineDrawn(lineId)) {
                        state = state.applyMove(lineId)
                        lastApplied = lineId
                    }
                }

                val myRole   = if (isHost) PlayerType.ONE else PlayerType.TWO
                val isMyTurn = !state.isGameOver &&
                               state.currentPlayer == myRole &&
                               room.status == RoomStatus.PLAYING

                // 👑/🎁 reward if I captured a special box (each client tracks its own)
                val specialEvent = checkSpecialCaptures(state, myRole)

                val connStatus = when (room.status) {
                    RoomStatus.ABANDONED -> OnlineConnectionStatus.OPPONENT_LEFT
                    else                 -> OnlineConnectionStatus.CONNECTED
                }

                val isNewMove = room.moves.size > appliedMoveCount
                _ui.update {
                    it.copy(
                        gameState         = state,
                        isMyTurn          = isMyTurn,
                        opponentName      = opponentName,
                        roomStatus        = room.status,
                        connectionStatus  = connStatus,
                        lastLine          = if (isNewMove) lastApplied else it.lastLine,
                        lastMoveHighlight = if (isNewMove) lastApplied else it.lastMoveHighlight,
                        specialReward     = specialEvent ?: it.specialReward
                    )
                }
                appliedMoveCount = room.moves.size

                if (isNewMove && lastApplied != null) {
                    highlightJob?.cancel()
                    highlightJob = viewModelScope.launch {
                        delay(2000L)
                        _ui.update { it.copy(lastMoveHighlight = null) }
                    }
                }

                // Game over — trigger coin burst + XP screen
                if (state.isGameOver && !gameEndHandled) {
                    gameEndHandled = true
                    if (room.status == RoomStatus.PLAYING) firebase.finishRoom(roomCode)
                    handleGameEnd(state, myRole)
                }

                // Opponent left mid-game — also handle
                if (room.status == RoomStatus.ABANDONED && !gameEndHandled) {
                    gameEndHandled = true
                }
            }
        }
    }

    fun dismissSpecialReward() {
        _ui.update { it.copy(specialReward = null) }
    }

    fun addBonusCoins(n: Int) {
        if (n <= 0) return
        viewModelScope.launch {
            val cur = repository.observeStats().first()
            repository.saveStats(cur.earnDotCoins(n))
        }
    }

    fun setSkin(skin: BoardSkin) {
        viewModelScope.launch {
            val cur = repository.observeStats().first()
            repository.saveStats(cur.withActiveSkin(skin))
        }
    }

    /** If a special box is now owned by ME (and not yet rewarded), award it. */
    private fun checkSpecialCaptures(state: GameState, myRole: PlayerType): SpecialRewardEvent? {
        if (specialBoxes.isEmpty) return null
        var event: SpecialRewardEvent? = null
        fun consider(r: Int, c: Int) {
            if (state.boxes[r][c] != myRole) return
            if ((r to c) in awardedSpecials) return
            awardedSpecials.add(r to c)
            when {
                specialBoxes.isCrown(r, c) -> { event = SpecialRewardEvent.Crown; awardCrown() }
                specialBoxes.isMystery(r, c) -> {
                    val reward = rollMysteryReward()
                    event = SpecialRewardEvent.Mystery(reward)
                    awardMystery(reward)
                }
            }
        }
        specialBoxes.crowns.forEach { (r, c) -> consider(r, c) }
        specialBoxes.mystery?.let { (r, c) -> consider(r, c) }
        return event
    }

    private fun awardCrown() {
        com.pixelplay.dotsboxes.analytics.Analytics.crownCaptured("online")
        viewModelScope.launch {
            val cur = repository.observeStats().first()
            repository.saveStats(cur.addSpin())
        }
    }

    private fun awardMystery(reward: MysteryReward) {
        com.pixelplay.dotsboxes.analytics.Analytics.mysteryOpened("online", reward.kind.name)
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

    private fun handleGameEnd(state: GameState, myRole: PlayerType) {
        viewModelScope.launch {
            val myScore     = if (myRole == PlayerType.ONE) state.p1Score else state.p2Score
            val iWon        = state.winner == myRole
            val coinsEarned = if (iWon) myScore * 2 else 0
            val result      = when {
                state.winner == null -> GameResult.TIE
                iWon                 -> GameResult.WIN
                else                 -> GameResult.LOSE
            }
            com.pixelplay.dotsboxes.analytics.Analytics.onlineMatchEnd(result.name.lowercase())
            val xpEarned = when (result) {
                GameResult.WIN  -> 50
                GameResult.TIE  -> 25
                GameResult.LOSE -> 15
            }

            // Save stats
            val current = repository.observeStats().first()
            var updated = current.afterResult(result, null)
            if (coinsEarned > 0) updated = updated.earnDotCoins(coinsEarned)
            repository.saveStats(updated)

            // Phase 1: coin burst
            _ui.update { it.copy(showWinCoinBurst = true, xpEarned = xpEarned, coinsEarned = coinsEarned) }
            delay(1400L)
            // Phase 2: XP screen
            _ui.update { it.copy(showWinCoinBurst = false, showXpScreen = true) }
        }
    }

    fun dismissXpScreen() {
        _ui.update { it.copy(showXpScreen = false) }
    }

    fun onLineTapped(lineId: LineId) {
        val ui = _ui.value
        if (!ui.isMyTurn) return
        if (ui.gameState.isGameOver) return
        if (ui.gameState.isLineDrawn(lineId)) return

        viewModelScope.launch {
            val move = OnlineMove(lineId.row, lineId.col, lineId.isHorizontal)
            val result = firebase.pushMove(roomCode, move)
            result.onFailure { e ->
                _ui.update { it.copy(errorMessage = "Move failed: ${e.message}") }
            }
        }
    }

    fun leaveRoom() {
        viewModelScope.launch { firebase.abandonRoom(roomCode) }
    }

    fun clearError() = _ui.update { it.copy(errorMessage = null) }

    class Factory(
        private val roomCode: String,
        private val isHost: Boolean,
        private val myName: String,
        private val gridSize: Int,
        private val firebase: FirebaseManager,
        private val repository: GameRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            OnlineGameViewModel(roomCode, isHost, myName, gridSize, firebase, repository) as T
    }
}
