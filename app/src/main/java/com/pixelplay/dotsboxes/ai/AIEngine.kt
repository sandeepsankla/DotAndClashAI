package com.pixelplay.dotsboxes.ai

import com.pixelplay.dotsboxes.domain.model.Difficulty
import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.LineId
import kotlin.random.Random

interface AIEngine {
    fun getBestMove(state: GameState): LineId?
}

object AIFactory {
    /**
     * @param difficulty     Chosen difficulty level.
     * @param consecLosses   Consecutive Hard-mode losses by the human.
     *                       When ≥ 3 the Hard AI secretly makes occasional "mistakes"
     *                       so the player can finally land a win (Dynamic Difficulty).
     */
    fun create(difficulty: Difficulty, consecLosses: Int = 0): AIEngine {
        val base: AIEngine = when (difficulty) {
            Difficulty.EASY   -> EasyAI()
            Difficulty.MEDIUM -> MediumAI()
            Difficulty.HARD   -> HardAI()
        }
        // DDA kicks in after 3 consecutive Hard losses — scales up to 40 % mistake chance
        return if (difficulty == Difficulty.HARD && consecLosses >= 3) {
            val mistakeChance = minOf(0.15f * (consecLosses - 2), 0.40f)
            DDAWrapper(base, mistakeChance)
        } else base
    }
}

/**
 * Dynamic Difficulty Adjustment wrapper.
 * Randomly replaces the AI's best move with a "safe-but-suboptimal" move
 * so the player finally feels the "I beat it!" moment on Hard.
 */
private class DDAWrapper(
    private val real: AIEngine,
    private val mistakeChance: Float
) : AIEngine {
    override fun getBestMove(state: GameState): LineId? {
        if (Random.nextFloat() < mistakeChance) {
            // Play a safe line (doesn't hand opponent a box) — looks natural, not stupid
            val safe = state.undrawnLines().filter { !state.wouldGiveOpponent3Sides(it) }
            return (safe.ifEmpty { state.undrawnLines() }).randomOrNull()
        }
        return real.getBestMove(state)
    }
}
