package com.example.dotsandboxes


data class GameState(
    val gridSize: Int = 7,
    val horizontalLines: Array<Array<Line>>,
    val verticalLines: Array<Array<Line>>,
    val boxes: Array<Array<Box>>,
    var currentPlayer: Player = Player.HUMAN,
    var humanScore: Int = 0,
    var botScore: Int = 0,
    var gameOver: Boolean = false
)

fun newGameState(size: Int = 7): GameState {
    val h = Array(size - 1) { r ->
        Array(size) { c -> Line(r, c, true) }
    }
    val v = Array(size) { r ->
        Array(size - 1) { c -> Line(r, c, false) }
    }
    val b = Array(size - 1) { r ->
        Array(size - 1) { c -> Box(r, c) }
    }
    return GameState(size, h, v, b)
}
