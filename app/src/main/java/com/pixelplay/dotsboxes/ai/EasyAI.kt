package com.pixelplay.dotsboxes.ai

import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.LineId

/** Picks a completely random valid line. Good for kids / beginners. */
class EasyAI : AIEngine {
    override fun getBestMove(state: GameState): LineId? =
        state.undrawnLines().randomOrNull()
}
