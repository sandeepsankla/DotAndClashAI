package com.pixelplay.dotsboxes.domain.model

import java.util.concurrent.TimeUnit
import kotlin.random.Random

// Pre-generated flash challenge board — same for everyone on the same calendar day
object FlashChallengeGenerator {

    fun todaySeed(): Long = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())

    /**
     * Generates today's flash board deterministically.
     * All remaining undrawn lines complete at least one box — every tap scores.
     */
    fun generate(seed: Long = todaySeed()): FlashBoard {
        val rng      = Random(seed)
        val gridSize = 4

        // 1. Simulate a full random game with the seeded RNG
        var sim = newGame(gridSize, GameMode.PVA, Difficulty.HARD, "You", "AI")
        val totalLines = (gridSize + 1) * gridSize * 2  // 40 for 4×4
        val target     = (totalLines * 0.74).toInt()     // draw ~30 lines

        repeat(target) {
            if (sim.isGameOver) return@repeat
            val moves = sim.undrawnLines()
            if (moves.isEmpty()) return@repeat
            sim = sim.applyMove(moves[rng.nextInt(moves.size)])
        }

        // 2. Force-draw any remaining lines that DON'T complete a box
        //    so that every user-visible move scores a box
        var forced = sim
        var changed = true
        while (changed) {
            changed = false
            val nonScoring = forced.undrawnLines().filter { !forced.wouldCompleteBox(it) }
            for (line in nonScoring) {
                if (!forced.isGameOver) {
                    forced  = forced.applyMove(line)
                    changed = true
                }
            }
        }

        // 3. Reset scores — only boxes captured in 15 s count
        val preFilledLines = forced.moveHistory.toSet()
        val cleanState = forced.copy(
            p1Score       = 0,
            p2Score       = 0,
            boxes         = buildBoxes(gridSize),
            currentPlayer = PlayerType.ONE,
            isGameOver    = false,
            winner        = null,
            moveHistory   = emptyList()
        )

        return FlashBoard(state = cleanState, preFilledLines = preFilledLines)
    }
}

/** Wrapper that keeps track of which lines were pre-drawn (for gray rendering). */
data class FlashBoard(
    val state: GameState,
    val preFilledLines: Set<LineId>   // lines drawn during generation → shown gray
)

/** Spin prize slots on the lucky wheel. */
enum class SpinPrize(val label: String, val coins: Int, val xp: Int) {
    COINS_50  ("50 Coins",  50,  0),
    COINS_100 ("100 Coins", 100, 0),
    COINS_200 ("200 Coins", 200, 0),
    COINS_300 ("300 Coins", 300, 0),
    COINS_500 ("500 Coins", 500, 0),
    XP_30     ("30 XP",     0,   30),
    XP_50     ("50 XP",     0,   50),
    COINS_150 ("150 Coins", 150, 0),
}
