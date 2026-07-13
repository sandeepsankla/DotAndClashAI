package com.pixelplay.dotsboxes.domain.model

import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

// ── Board Skins ───────────────────────────────────────────────────────────────

enum class BoardSkin(val displayName: String, val emoji: String) {
    DEFAULT ("Classic",       "🎮"),
    FIRE    ("Fire",          "🔥"),
    GOLDEN  ("Golden",        "✨"),
    CONTRAST("High Contrast", "⬛")
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

private const val HINT_EXPIRY_DAYS = 2L   // hints expire 2 days after last hint activity

@Serializable
data class PlayerStats(
    val playerName: String      = "Player",
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
    // Dynamic difficulty — consecutive AI losses (all difficulties)
    val consecutiveLossesAi: Int = 0,
    // Achievement badges
    val badges: Set<String>        = emptySet(),
    // Campaign progress
    val highestLevelUnlocked: Int  = 1,
    val levelsCompleted: Set<Int>  = emptySet(),
    // DotCoins — virtual currency (2 per box won, spent on cosmetics)
    val dotCoins: Int              = 0,
    // Daily task — 2 wins per day
    val dailyWinsCount: Int        = 0,
    val lastDailyWinsEpochDay: Long = -1L,
    // Flash challenge
    val lastFlashChallengeDay: Long = -1L,
    // Lucky spin
    val pendingSpins: Int          = 0,
    // Avatar
    val activeAvatarId: Int        = 0,
    val unlockedAvatarIds: Set<Int> = setOf(0),
    // Settings
    val soundEnabled: Boolean      = true,
    val vibrationEnabled: Boolean  = true,
    // Online free-games quota
    val onlineGamesToday: Int      = 0,
    val lastOnlineEpochDay: Long   = -1L,
    // Hints expire 2 days after last hint activity (use-it-or-lose-it)
    val hintsExpiryEpochDay: Long  = -1L
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
        // Track consecutive AI losses across all difficulties for DDA
        val newConsecLosses = when {
            difficulty != null && result == GameResult.LOSE -> consecutiveLossesAi + 1
            difficulty != null && result == GameResult.WIN  -> 0
            else                                            -> consecutiveLossesAi
        }
        val updated = copy(
            wins               = wins   + if (result == GameResult.WIN)  1 else 0,
            losses             = losses + if (result == GameResult.LOSE) 1 else 0,
            ties               = ties   + if (result == GameResult.TIE)  1 else 0,
            currentStreak      = newStreak,
            bestStreak         = maxOf(bestStreak, newStreak),
            xp                 = xp + xpGain,
            consecutiveLossesAi = newConsecLosses
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

    fun spendHint(): PlayerStats =
        copy(hintCoins = (hintCoins - 1).coerceAtLeast(0)).refreshHintExpiry()
    fun earnHints(n: Int): PlayerStats =
        copy(hintCoins = hintCoins + n).refreshHintExpiry()
    fun earnDotCoins(n: Int): PlayerStats   = copy(dotCoins = dotCoins + n)

    // ── Hint expiry (2 days) ──────────────────────────────────────────────────
    /** Any hint activity pushes the expiry 2 days out; no hints ⇒ no expiry. */
    private fun refreshHintExpiry(): PlayerStats =
        copy(hintsExpiryEpochDay = if (hintCoins > 0) todayEpoch() + HINT_EXPIRY_DAYS else -1L)

    /** Whole days left before hints expire (Int.MAX_VALUE if none set). */
    val hintsExpiryDaysLeft: Int get() =
        if (hintCoins <= 0 || hintsExpiryEpochDay < 0) Int.MAX_VALUE
        else (hintsExpiryEpochDay - todayEpoch()).toInt()

    /** Call on app open: wipe hints if their 2-day window has passed. */
    fun expireHintsIfDue(): PlayerStats =
        if (hintCoins > 0 && hintsExpiryEpochDay in 0 until todayEpoch())
            copy(hintCoins = 0, hintsExpiryEpochDay = -1L)
        else this
    fun withActiveSkin(skin: BoardSkin): PlayerStats = copy(activeSkin = skin.name)

    fun purchaseSkin(skin: BoardSkin, cost: Int): PlayerStats = copy(
        dotCoins      = (dotCoins - cost).coerceAtLeast(0),
        unlockedSkins = unlockedSkins + skin.name
    )

    fun purchaseHintPack(hints: Int, cost: Int): PlayerStats = copy(
        dotCoins  = (dotCoins - cost).coerceAtLeast(0),
        hintCoins = hintCoins + hints
    ).refreshHintExpiry()

    fun canAfford(cost: Int): Boolean = dotCoins >= cost

    // ── Daily task helpers ────────────────────────────────────────────────────

    private fun todayEpoch() = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())

    val todayWins: Int get() {
        val today = todayEpoch()
        return if (lastDailyWinsEpochDay == today) dailyWinsCount else 0
    }
    val dailyTaskComplete: Boolean get() = todayWins >= 2

    fun withDailyWin(): PlayerStats {
        val today = todayEpoch()
        val count = if (lastDailyWinsEpochDay == today) dailyWinsCount + 1 else 1
        return copy(dailyWinsCount = count, lastDailyWinsEpochDay = today)
    }

    // ── Online free-games quota ───────────────────────────────────────────────
    val todayOnlineGames: Int get() =
        if (lastOnlineEpochDay == todayEpoch()) onlineGamesToday else 0

    fun withOnlineGamePlayed(): PlayerStats {
        val today = todayEpoch()
        val count = if (lastOnlineEpochDay == today) onlineGamesToday + 1 else 1
        return copy(onlineGamesToday = count, lastOnlineEpochDay = today)
    }

    // ── Flash challenge helpers ───────────────────────────────────────────────

    val hasPlayedFlashToday: Boolean get() = lastFlashChallengeDay == todayEpoch()

    fun withFlashChallengePlayed(): PlayerStats =
        copy(lastFlashChallengeDay = todayEpoch())

    fun addSpin(n: Int = 1): PlayerStats = copy(pendingSpins = pendingSpins + n)
    fun useSpin(): PlayerStats = copy(pendingSpins = (pendingSpins - 1).coerceAtLeast(0))

    // ── Avatar ────────────────────────────────────────────────────────────────
    fun isAvatarUnlocked(id: Int) = id in unlockedAvatarIds
    fun purchaseAvatar(id: Int, cost: Int): PlayerStats =
        copy(dotCoins = dotCoins - cost, unlockedAvatarIds = unlockedAvatarIds + id, activeAvatarId = id)
    fun setAvatar(id: Int): PlayerStats = copy(activeAvatarId = id)

    // ── Campaign ──────────────────────────────────────────────────────────────

    fun afterLevelComplete(levelNumber: Int): PlayerStats {
        val cfg  = CAMPAIGN_LEVELS.getOrNull(levelNumber - 1)
        val next = levelNumber + 1
        return copy(
            levelsCompleted      = levelsCompleted + levelNumber,
            // Always allow next level (infinite levels beyond 10 keep unlocking)
            highestLevelUnlocked = maxOf(highestLevelUnlocked, next),
            hintCoins            = hintCoins + (cfg?.hintBonus ?: 0)
        )
    }

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

// ── Campaign level definitions ────────────────────────────────────────────────

data class LevelConfig(
    val number: Int,
    val title: String,
    val emoji: String,
    val gridSize: Int,
    val difficulty: Difficulty,
    val hintBonus: Int = 0,
    /** Level 1 auto-shows free hints each turn */
    val hasTutorial: Boolean = false,
    /** Move time limit in seconds; null = no limit */
    val timeLimitSeconds: Int? = null
)

val CAMPAIGN_LEVELS: List<LevelConfig> = listOf(
    // ── 3×3 tier — Learn the basics ──────────────────────────────────────────
    LevelConfig(1,  "Baby Steps",   "🐣", 3, Difficulty.EASY,   0, hasTutorial = true),
    LevelConfig(2,  "Warming Up",   "🌱", 4, Difficulty.EASY,   0),
    LevelConfig(3,  "First Fight",  "⚔️", 4, Difficulty.MEDIUM, 0),
    // ── 4×4 tier — Grid expands ──────────────────────────────────────────────
    LevelConfig(4,  "Getting Real", "⚡", 4, Difficulty.EASY,   1),
    LevelConfig(5,  "Challenge",    "🎯", 4, Difficulty.MEDIUM, 0),
    LevelConfig(6,  "Mind Games",   "🧩", 4, Difficulty.MEDIUM, 1),
    LevelConfig(7,  "Hard Entry",   "🎮", 4, Difficulty.HARD,   0),
    // ── 5×5 tier — Mid game ──────────────────────────────────────────────────
    LevelConfig(8,  "Big Board",    "🔥", 5, Difficulty.EASY,   1),
    LevelConfig(9,  "Step Up",      "📈", 5, Difficulty.MEDIUM, 1),
    LevelConfig(10, "Clash Mode",   "⚔️", 5, Difficulty.HARD,  1),
    LevelConfig(11, "No Mercy",     "💀", 5, Difficulty.HARD,  2, timeLimitSeconds = 20),
    LevelConfig(12, "Blitz",        "⚡", 5, Difficulty.HARD,  0, timeLimitSeconds = 12),
    // ── 6×6 tier — Boss territory ────────────────────────────────────────────
    LevelConfig(13, "Master Class", "💎", 6, Difficulty.MEDIUM, 1),
    LevelConfig(14, "Endgame",      "🔮", 6, Difficulty.HARD,  2),
    LevelConfig(15, "Time Warp",    "⏱️", 6, Difficulty.HARD,  2, timeLimitSeconds = 20),
    LevelConfig(16, "Speed Chess",  "♟️", 6, Difficulty.HARD,  2, timeLimitSeconds = 15),
    LevelConfig(17, "Lightning",    "🌩️", 6, Difficulty.HARD,  1, timeLimitSeconds = 12),
    LevelConfig(18, "Elite",        "🏅", 6, Difficulty.HARD,  3),
    LevelConfig(19, "Final Stand",  "🔱", 6, Difficulty.HARD,  3, timeLimitSeconds = 15),
    LevelConfig(20, "LEGEND",       "👑", 6, Difficulty.HARD,  5)
)

/** Generates an infinite-mode level config for level 11, 12, 13... */
fun infiniteLevelConfig(number: Int) = LevelConfig(
    number           = number,
    title            = "Infinity ${number - CAMPAIGN_LEVELS.size}",
    emoji            = "♾️",
    gridSize         = 6,
    difficulty       = Difficulty.HARD,
    hintBonus        = 0,
    hasTutorial      = false,
    timeLimitSeconds = null
)

/** Returns the config for any level number (handles infinite levels too) */
fun levelConfigFor(number: Int): LevelConfig =
    CAMPAIGN_LEVELS.getOrNull(number - 1) ?: infiniteLevelConfig(number)

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

// ── Player Avatars ────────────────────────────────────────────────────────────

data class PlayerAvatar(
    val id: Int,
    val emoji: String,
    val label: String,
    val cost: Int        // 0 = free
)

val ALL_AVATARS = listOf(
    // ── Free ──────────────────────────────────────────
    PlayerAvatar(0,  "😎", "Cool",        0),

    // ── Superheroes ───────────────────────────────────
    PlayerAvatar(1,  "🦸", "Hero",        100),
    PlayerAvatar(2,  "🦇", "Batman",      150),
    PlayerAvatar(3,  "🕷️", "Spidey",      150),
    PlayerAvatar(4,  "🥷", "Ninja",       100),
    PlayerAvatar(5,  "🦹", "Rogue",       120),
    PlayerAvatar(6,  "🕵️", "Agent",       180),
    PlayerAvatar(7,  "👨‍🚀", "Astronaut",  200),

    // ── Royalty ───────────────────────────────────────
    PlayerAvatar(8,  "👑", "King",        250),
    PlayerAvatar(9,  "🤴", "Prince",      200),
    PlayerAvatar(10, "👸", "Queen",       200),
    PlayerAvatar(11, "🧝", "Elf",         300),

    // ── Legends ───────────────────────────────────────
    PlayerAvatar(12, "🐉", "Dragon",      350),
    PlayerAvatar(13, "🦁", "Lion",        300),
    PlayerAvatar(14, "🐯", "Tiger",       250),
    PlayerAvatar(15, "🐺", "Wolf",        200),
    PlayerAvatar(16, "🦊", "Fox",         220),
    PlayerAvatar(17, "🧙", "Wizard",      300),

    // ── Elite ─────────────────────────────────────────
    PlayerAvatar(18, "💎", "Diamond",     500),
    PlayerAvatar(19, "💀", "Skull",       400),
    PlayerAvatar(20, "👽", "Alien",       350),
    PlayerAvatar(21, "🔥", "Blaze",       180),
    PlayerAvatar(22, "🤖", "Cyborg",      220),
    PlayerAvatar(23, "👻", "Phantom",     150)
)
