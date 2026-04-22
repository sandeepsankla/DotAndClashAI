package com.example.dotsandboxes

import kotlinx.coroutines.CoroutineScope

fun botMove(state: GameState, scope: CoroutineScope) {

    val horizontal = state.horizontalLines
    val vertical = state.verticalLines
    val boxes = state.boxes

    val allLines =
        horizontal.flatten() + vertical.flatten()

    // 1️⃣ Try to complete a box
    for (line in allLines) {
        if (line.drawnBy != null) continue

        line.drawnBy = Player.BOT

        var completesBox = false

        for (r in boxes.indices) {
            for (c in boxes[r].indices) {
                if (boxes[r][c].owner == null &&
                    isBoxCompletedSafe(r, c, horizontal, vertical)
                ) {
                    completesBox = true
                    break
                }
            }
            if (completesBox) break
        }

        line.drawnBy = null

        if (completesBox) {
            playMove(line, state, scope)
            return
        }
    }

    // 2️⃣ Otherwise: play first free line (safe fallback)
    allLines.firstOrNull { it.drawnBy == null }?.let {
        playMove(it, state, scope)
    }
}

/**
 * 100% SAFE box check
 * NO gridSize usage
 * ONLY real array bounds
 */
private fun isBoxCompletedSafe(
    r: Int,
    c: Int,
    h: Array<Array<Line>>,
    v: Array<Array<Line>>
): Boolean {

    // horizontal: [rows = boxes+1][cols = boxes]
    // vertical:   [rows = boxes][cols = boxes+1]

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
