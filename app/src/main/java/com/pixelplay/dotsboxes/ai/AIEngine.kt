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
     * DDA (Dynamic Difficulty Adjustment) for all difficulties.
     * Thresholds: Easy→5 losses, Medium→3 losses, Hard→3 losses.
     * Mistake chance scales up with each additional loss (max 45%).
     */
    fun create(difficulty: Difficulty, consecLosses: Int = 0): AIEngine {
        val base: AIEngine = when (difficulty) {
            Difficulty.EASY   -> EasyAI()
            Difficulty.MEDIUM -> MediumAI()
            Difficulty.HARD   -> HardAI()
        }
        val threshold = when (difficulty) {
            Difficulty.EASY   -> 5
            Difficulty.MEDIUM -> 3
            Difficulty.HARD   -> 3
        }
        return if (consecLosses >= threshold) {
            val extra         = consecLosses - (threshold - 1)
            val mistakeChance = minOf(0.12f * extra, 0.45f)
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
