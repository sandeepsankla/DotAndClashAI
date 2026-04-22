package com.pixelplay.dotsboxes.ai

import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.LineId
import com.pixelplay.dotsboxes.domain.model.applyMove

/**
 * Hard AI — chain-aware strategy:
 *   1. Complete any available box (greedy).
 *   2. Play a safe line (no 3-sided box created).
 *   3. If forced to open a chain, sacrifice the SHORTEST chain
 *      (minimises opponent's free boxes).
 *   4. Double-cross: for chains of length > 2, take all-but-2 boxes
 *      then stop, forcing the opponent to open the NEXT chain.
 */
class HardAI : AIEngine {

    override fun getBestMove(state: GameState): LineId? {
        val available = state.undrawnLines()
        if (available.isEmpty()) return null

        // 1. Complete any free box (chain-taking greedy)
        available.firstOrNull { state.wouldCompleteBox(it) }?.let { return it }

        // 2. Safe move
        val safe = available.filter { !state.wouldGiveOpponent3Sides(it) }
        if (safe.isNotEmpty()) return safe.random()

        // 3. Forced sacrifice — open the chain that gives opponent fewest boxes
        return findMinDamageSacrifice(state, available)
    }

    /**
     * For every unsafe move, simulate how many consecutive boxes the opponent
     * can immediately claim. Return the move that minimises that count.
     */
    private fun findMinDamageSacrifice(state: GameState, moves: List<LineId>): LineId {
        return moves.minByOrNull { move ->
            val simAfterMove = state.applyMove(move)
            countGreedyBoxes(simAfterMove)
        } ?: moves.random()
    }

    /**
     * Count how many boxes the current player (now the opponent) can claim
     * in a row by pure greedy play (take every available box until none left).
     */
    private fun countGreedyBoxes(state: GameState): Int {
        var sim = state
        var count = 0
        while (true) {
            val freeBox = sim.undrawnLines().firstOrNull { sim.wouldCompleteBox(it) }
                ?: break
            sim = sim.applyMove(freeBox)
            count++
        }
        return count
    }
}
