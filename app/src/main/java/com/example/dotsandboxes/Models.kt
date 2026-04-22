package com.example.dotsandboxes


enum class Player {
    HUMAN, BOT
}

data class Line(
    val row: Int,
    val col: Int,
    val isHorizontal: Boolean,
    var drawnBy: Player? = null,
    var alpha: Float = 1f   // animation-safe
)



data class Box(
    val row: Int,
    val col: Int,
    var owner: Player? = null
)
