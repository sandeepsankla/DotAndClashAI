package com.example.dotsandboxes


import kotlinx.coroutines.*

fun playMove(
    line: Line,
    state: GameState,
    scope: CoroutineScope
): Boolean {

    if (line.drawnBy != null || state.gameOver) return false

    line.drawnBy = state.currentPlayer
    line.alpha = 0f

    // SAFE animation (no Compose calls)
    scope.launch {
        repeat(10) {
            delay(16)
            line.alpha += 0.1f
        }
        line.alpha = 1f
    }

    var completed = 0

    for (r in state.boxes.indices) {
        for (c in state.boxes[r].indices) {
            val box = state.boxes[r][c]
            if (box.owner == null && isBoxCompleted(r, c, state)) {
                box.owner = state.currentPlayer
                completed++
            }
        }
    }

    if (completed > 0) {
        if (state.currentPlayer == Player.HUMAN)
            state.humanScore += completed
        else
            state.botScore += completed
    } else {
        state.currentPlayer =
            if (state.currentPlayer == Player.HUMAN) Player.BOT else Player.HUMAN
    }

    val totalBoxes = state.boxes.size * state.boxes[0].size
    if (state.humanScore + state.botScore == totalBoxes) {
        state.gameOver = true
    }

    return completed > 0
}
private fun isBoxCompleted(
    r: Int,
    c: Int,
    state: GameState
): Boolean {

    val h = state.horizontalLines
    val v = state.verticalLines

    // SAFE bounds check (no gridSize assumptions)
    if (r < 0 || c < 0) return false
    if (r + 1 >= h.size) return false
    if (c >= h[0].size) return false
    if (r >= v.size) return false
    if (c + 1 >= v[0].size) return false

    return h[r][c].drawnBy != null &&
            h[r + 1][c].drawnBy != null &&
            v[r][c].drawnBy != null &&
            v[r][c + 1].drawnBy != null
}

