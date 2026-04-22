package com.pixelplay.dotsboxes.domain.model

import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

// ── Board Skins ───────────────────────────────────────────────────────────────

enum class BoardSkin(val displayName: String, val emoji: String) {
    DEFAULT("Classic", "🎮"),
    FIRE("Fire",        "🔥"),
    GOLDEN("Golden",    "✨")
}

// ── Player Level ──────────────────────────────────────────────────────────────

enum class PlayerLevel(
    val title: String,
    val emoji: String,
    val minXp: Int,
    val nextLevelXp: Int
) {
    BEGINNER("Beginner", "🌱",    0,    100),
    ROOKIE  ("Rookie",   "🎯",  100,    300),
    PRO     ("Pro",      "🔥",  300,    700),
    MASTER  ("Master",   "💎",  700,   1500),
    LEGEND  ("Legend",   "👑", 1500, Int.MAX_VALUE);

    val isMax: Boolean get() = this == LEGEND

    companion object {
        fun fromXp(xp: Int): PlayerLevel =
            entries.filter { xp >= it.minXp }.maxByOrNull { it.minXp } ?: BEGINNER
    }
}

// ── Daily login reward (not serialised — display only) ────────────────────────

data class DailyLoginInfo(
    val dailyStreak: Int,
    val isConsecutive: Boolean,
    val xpBonus: Int,
    val hintCoinsBonus: Int,
    val unlockedSkin: BoardSkin?,
    val newBadge: String?
) {
    val isMilestone: Boolean
        get() = hintCoinsBonus > 0 || unlockedSkin != null || newBadge != null
}

// ── Per-difficulty stats ──────────────────────────────────────────────────────

@Serializable
data class DifficultyStats(
    val wins: Int          = 0,
    val losses: Int        = 0,
    val ties: Int          = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int    = 0
) {
    val totalGames: Int get() = wins + losses + ties
    val winRatePct: Int get() = if (totalGames == 0) 0 else (wins * 100 / totalGames)
}

// ── Player Stats (persisted across all games) ────────────────────────────────

@Serializable
data class PlayerStats(
    // Win / loss record
    val wins: Int               = 0,
    val losses: Int             = 0,
    val ties: Int               = 0,
    val currentStreak: Int      = 0,
    val bestStreak: Int         = 0,
    val totalBoxesCaptured: Int = 0,
    // Hint coins
    val hintCoins: Int          = 5,
    // Per-difficulty breakdown
    val byDifficulty: Map<String, DifficultyStats> = emptyMap(),
    // XP & level
    val xp: Int                 = 0,
    // Daily login streak
    val dailyStreak: Int        = 0,
    val lastPlayEpochDay: Long  = -1L,
    // Skins & cosmetics
    val unlockedSkins: Set<String> = setOf("DEFAULT"),
    val activeSkin: String         = "DEFAULT",
    // Dynamic difficulty — consecutive Hard losses
    val consecutiveLossesHard: Int = 0,
    // Achievement badges
    val badges: Set<String>        = emptySet()
) {
    // ── Computed helpers ──────────────────────────────────────────────────────

    val level: PlayerLevel get() = PlayerLevel.fromXp(xp)

    val xpProgressFraction: Float get() {
        val lvl = level
        if (lvl.isMax) return 1f
        val range = (lvl.nextLevelXp - lvl.minXp).toFloat()
        return ((xp - lvl.minXp).toFloat() / range).coerceIn(0f, 1f)
    }

    val xpToNext: Int get() =
        if (level.isMax) 0 else (level.nextLevelXp - xp).coerceAtLeast(0)

    val totalGames: Int get() = wins + losses + ties
    val winRatePct: Int get() = if (totalGames == 0) 0 else (wins * 100 / totalGames)

    fun diffStats(difficulty: Difficulty): DifficultyStats =
        byDifficulty[difficulty.name] ?: DifficultyStats()

    val activeSkinEnum: BoardSkin
        get() = runCatching { BoardSkin.valueOf(activeSkin) }.getOrDefault(BoardSkin.DEFAULT)

    // ── Game result ───────────────────────────────────────────────────────────

    fun afterResult(result: GameResult, difficulty: Difficulty? = null): PlayerStats {
        val newStreak = if (result == GameResult.WIN) currentStreak + 1 else 0
        val xpGain = when (result) {
            GameResult.WIN  -> 50
            GameResult.LOSE -> 15
            GameResult.TIE  -> 25
        }
        // Track consecutive Hard losses for DDA
        val newConsecLosses = when {
            difficulty == Difficulty.HARD && result == GameResult.LOSE -> consecutiveLossesHard + 1
            difficulty == Difficulty.HARD                              -> 0
            else                                                       -> consecutiveLossesHard
        }
        val updated = copy(
            wins                  = wins   + if (result == GameResult.WIN)  1 else 0,
            losses                = losses + if (result == GameResult.LOSE) 1 else 0,
            ties                  = ties   + if (result == GameResult.TIE)  1 else 0,
            currentStreak         = newStreak,
            bestStreak            = maxOf(bestStreak, newStreak),
            xp                    = xp + xpGain,
            consecutiveLossesHard = newConsecLosses
        )
        if (difficulty == null) return updated
        val key      = difficulty.name
        val existing = byDifficulty[key] ?: DifficultyStats()
        val dStreak  = if (result == GameResult.WIN) existing.currentStreak + 1 else 0
        return updated.copy(
            byDifficulty = byDifficulty + (key to existing.copy(
                wins          = existing.wins   + if (result == GameResult.WIN)  1 else 0,
                losses        = existing.losses + if (result == GameResult.LOSE) 1 else 0,
                ties          = existing.ties   + if (result == GameResult.TIE)  1 else 0,
                currentStreak = dStreak,
                bestStreak    = maxOf(existing.bestStreak, dStreak)
            ))
        )
    }

    // ── Daily login ───────────────────────────────────────────────────────────

    /** Call once at app open. Returns updated stats + reward info (null = already logged in today). */
    fun checkDailyLogin(): Pair<PlayerStats, DailyLoginInfo?> {
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        if (lastPlayEpochDay == today) return this to null   // already checked in

        val isConsecutive = lastPlayEpochDay == today - 1
        val newDailyStreak = if (isConsecutive) dailyStreak + 1 else 1
        val milestone = milestoneRewardFor(newDailyStreak)
        val hintsGained = milestone?.hintCoins ?: 0

        var updated = copy(
            dailyStreak      = newDailyStreak,
            lastPlayEpochDay = today,
            xp               = xp + 30,
            hintCoins        = hintCoins + hintsGained
        )
        milestone?.skin?.let  { skin  -> updated = updated.copy(unlockedSkins = unlockedSkins + skin.name) }
        milestone?.badge?.let { badge -> updated = updated.copy(badges = badges + badge) }

        return updated to DailyLoginInfo(
            dailyStreak     = newDailyStreak,
            isConsecutive   = isConsecutive,
            xpBonus         = 30,
            hintCoinsBonus  = hintsGained,
            unlockedSkin    = milestone?.skin,
            newBadge        = milestone?.badge
        )
    }

    // ── Skin helpers ──────────────────────────────────────────────────────────

    fun spendHint(): PlayerStats            = copy(hintCoins = (hintCoins - 1).coerceAtLeast(0))
    fun earnHints(n: Int): PlayerStats      = copy(hintCoins = hintCoins + n)
    fun withActiveSkin(skin: BoardSkin): PlayerStats = copy(activeSkin = skin.name)

    // ── Private: milestone table ──────────────────────────────────────────────

    private data class MilestoneReward(val hintCoins: Int, val skin: BoardSkin?, val badge: String?)

    private fun milestoneRewardFor(streak: Int): MilestoneReward? = when (streak) {
        3    -> MilestoneReward(1, null,              null)
        7    -> MilestoneReward(3, BoardSkin.FIRE,    "🔥 Fire Starter")
        14   -> MilestoneReward(3, null,              "⚡ Dedicated")
        30   -> MilestoneReward(5, BoardSkin.GOLDEN,  "👑 Legend")
        else -> if (streak > 30 && streak % 7 == 0)
                    MilestoneReward(2, null, null)
                else null
    }
}

enum class GameResult { WIN, LOSE, TIE }

// ── Enums ────────────────────────────────────────────────────────────────────

@Serializable enum class PlayerType { ONE, TWO }

@Serializable enum class GameMode { PVP, PVA }

@Serializable enum class Difficulty { EASY, MEDIUM, HARD }

// ── Line identifier ──────────────────────────────────────────────────────────

@Serializable
data class LineId(val row: Int, val col: Int, val isHorizontal: Boolean) {
    fun adjacentBoxes(gridSize: Int): List<Pair<Int, Int>> = buildList {
        if (isHorizontal) {
            if (row > 0)        add(row - 1 to col)
            if (row < gridSize) add(row     to col)
        } else {
            if (col > 0)        add(row to col - 1)
            if (col < gridSize) add(row to col)
        }
    }
}

// ── Immutable game state ─────────────────────────────────────────────────────

@Serializable
data class GameState(
    val gridSize: Int = 4,
    val hLines: List<List<PlayerType?>> = buildHLines(4),
    val vLines: List<List<PlayerType?>> = buildVLines(4),
    val boxes:  List<List<PlayerType?>> = buildBoxes(4),
    val currentPlayer: PlayerType = PlayerType.ONE,
    val p1Score: Int = 0,
    val p2Score: Int = 0,
    val gameMode: GameMode = GameMode.PVP,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val p1Name: String = "Player 1",
    val p2Name: String = "Player 2",
    val isGameOver: Boolean = false,
    val winner: PlayerType? = null,
    val moveHistory: List<LineId> = emptyList()
) {
    val totalBoxes: Int get() = gridSize * gridSize

    fun playerName(type: PlayerType)  = if (type == PlayerType.ONE) p1Name  else p2Name
    fun playerScore(type: PlayerType) = if (type == PlayerType.ONE) p1Score else p2Score

    fun isLineDrawn(id: LineId): Boolean =
        if (id.isHorizontal) hLines[id.row][id.col] != null
        else                 vLines[id.row][id.col] != null

    fun countBoxSides(row: Int, col: Int): Int {
        var n = 0
        if (hLines[row][col]     != null) n++
        if (hLines[row + 1][col] != null) n++
        if (vLines[row][col]     != null) n++
        if (vLines[row][col + 1] != null) n++
        return n
    }

    fun wouldGiveOpponent3Sides(id: LineId): Boolean =
        id.adjacentBoxes(gridSize).any { (r, c) ->
            boxes[r][c] == null && countBoxSides(r, c) == 2
        }

    fun wouldCompleteBox(id: LineId): Boolean =
        id.adjacentBoxes(gridSize).any { (r, c) ->
            boxes[r][c] == null && countBoxSides(r, c) == 3
        }

    fun undrawnLines(): List<LineId> = buildList {
        for (r in 0..gridSize)      for (c in 0 until gridSize) if (hLines[r][c] == null) add(LineId(r, c, true))
        for (r in 0 until gridSize) for (c in 0..gridSize)      if (vLines[r][c] == null) add(LineId(r, c, false))
    }
}

// ── Pure state transitions ────────────────────────────────────────────────────

fun GameState.applyMove(id: LineId): GameState {
    require(!isLineDrawn(id)) { "Line $id already drawn" }

    val newHLines = if (id.isHorizontal) hLines.setCell(id.row, id.col, currentPlayer) else hLines
    val newVLines = if (!id.isHorizontal) vLines.setCell(id.row, id.col, currentPlayer) else vLines

    var newBoxes = boxes
    var scored = 0
    for ((r, c) in id.adjacentBoxes(gridSize)) {
        if (newBoxes[r][c] != null) continue
        if (countSidesWithNewLines(newHLines, newVLines, r, c) == 4) {
            newBoxes = newBoxes.setCell(r, c, currentPlayer); scored++
        }
    }

    val newP1 = if (currentPlayer == PlayerType.ONE) p1Score + scored else p1Score
    val newP2 = if (currentPlayer == PlayerType.TWO) p2Score + scored else p2Score
    val nextPlayer = if (scored > 0) currentPlayer else currentPlayer.opponent()

    val over = newP1 + newP2 == totalBoxes
    val w: PlayerType? = when { !over -> null; newP1 > newP2 -> PlayerType.ONE; newP2 > newP1 -> PlayerType.TWO; else -> null }

    return copy(
        hLines = newHLines, vLines = newVLines, boxes = newBoxes,
        currentPlayer = if (over) currentPlayer else nextPlayer,
        p1Score = newP1, p2Score = newP2,
        isGameOver = over, winner = w,
        moveHistory = moveHistory + id
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun PlayerType.opponent(): PlayerType =
    if (this == PlayerType.ONE) PlayerType.TWO else PlayerType.ONE

fun buildHLines(n: Int): List<List<PlayerType?>> = List(n + 1) { List(n) { null } }
fun buildVLines(n: Int): List<List<PlayerType?>> = List(n) { List(n + 1) { null } }
fun buildBoxes(n: Int):  List<List<PlayerType?>> = List(n) { List(n) { null } }

fun newGame(gridSize: Int, mode: GameMode, difficulty: Difficulty, p1Name: String, p2Name: String) =
    GameState(
        gridSize   = gridSize,
        hLines     = buildHLines(gridSize),
        vLines     = buildVLines(gridSize),
        boxes      = buildBoxes(gridSize),
        gameMode   = mode,
        difficulty = difficulty,
        p1Name     = p1Name.trim().ifBlank { "Player 1" },
        p2Name     = p2Name.trim().ifBlank { if (mode == GameMode.PVP) "Player 2" else "AI" }
    )

private fun <T> List<List<T>>.setCell(row: Int, col: Int, value: T): List<List<T>> =
    mapIndexed { r, rowList ->
        if (r == row) rowList.mapIndexed { c, cell -> if (c == col) value else cell } else rowList
    }

private fun countSidesWithNewLines(h: List<List<PlayerType?>>, v: List<List<PlayerType?>>, row: Int, col: Int): Int {
    var n = 0
    if (h[row][col] != null)     n++
    if (h[row + 1][col] != null) n++
    if (v[row][col] != null)     n++
    if (v[row][col + 1] != null) n++
    return n
}
