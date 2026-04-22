package com.pixelplay.dotsboxes.ai

import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.LineId

/**
 * Medium AI — greedy + safe heuristic:
 *   1. Complete any available box (3 sides already drawn).
 *   2. Play a "safe" line that doesn't leave any box with 3 sides.
 *   3. Fallback: any random move.
 */
class MediumAI : AIEngine {
    override fun getBestMove(state: GameState): LineId? {
        val available = state.undrawnLines()
        if (available.isEmpty()) return null

        // Step 1: grab a free box
        available.firstOrNull { state.wouldCompleteBox(it) }?.let { return it }

        // Step 2: safe move (won't give opponent a 3-sided box)
        val safe = available.filter { !state.wouldGiveOpponent3Sides(it) }
        if (safe.isNotEmpty()) return safe.random()

        // Step 3: forced sacrifice — pick random
        return available.random()
    }
}
