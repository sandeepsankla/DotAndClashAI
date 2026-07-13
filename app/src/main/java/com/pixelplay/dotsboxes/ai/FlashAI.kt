package com.pixelplay.dotsboxes.ai

import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.LineId
import kotlin.random.Random

/**
 * Flash-challenge AI — between MEDIUM and HARD.
 * Greedy box-completer with 18% chance of picking a random safe move
 * instead of the optimal one, keeping games competitive but winnable.
 */
class FlashAI : AIEngine {

    private val hard = HardAI()

    override fun getBestMove(state: GameState): LineId? {
        val available = state.undrawnLines()
        if (available.isEmpty()) return null

        // Always take a free box — no mistakes on scoring moves
        available.firstOrNull { state.wouldCompleteBox(it) }?.let { return it }

        // 18% chance of a random safe move instead of optimal
        if (Random.nextFloat() < 0.18f) {
            val safe = available.filter { !state.wouldGiveOpponent3Sides(it) }
            return (safe.ifEmpty { available }).random()
        }

        return hard.getBestMove(state)
    }
}
