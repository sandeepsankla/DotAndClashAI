package com.pixelplay.dotsboxes.domain.usecase

import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.LineId
import com.pixelplay.dotsboxes.domain.model.applyMove

class DrawLineUseCase {
    operator fun invoke(state: GameState, lineId: LineId): GameState? {
        if (state.isGameOver) return null
        if (state.isLineDrawn(lineId)) return null
        return state.applyMove(lineId)
    }
}

class IsGameOverUseCase {
    operator fun invoke(state: GameState): Boolean = state.isGameOver
}

class GetWinnerUseCase {
    operator fun invoke(state: GameState): String {
        require(state.isGameOver) { "Game not over yet" }
        return when {
            state.winner == null -> "It's a Tie!"
            else -> "${state.playerName(state.winner)} Wins!"
        }
    }
}
