package com.example.dotsandboxes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun GameBoard(
    state: GameState,
    onLineSelected: (Line) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color(0xFFF2F2F2))
            .padding(16.dp)

    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        detectLine(
                            tap,
                            Size(size.width.toFloat(), size.height.toFloat()),
                            state
                        )?.let(onLineSelected)
                    }
                }
        ) {
            val padding = 32f
            val usable = size.width - padding * 2
            val spacing = usable / (state.gridSize - 1)

            fun dot(r: Int, c: Int) =
                Offset(padding + c * spacing, padding + r * spacing)

            // Boxes
            state.boxes.flatten().forEach {
                it.owner?.let { p ->
                    drawRect(
                        color = if (p == Player.HUMAN)
                            Color.Blue.copy(0.25f)
                        else
                            Color.Red.copy(0.25f),
                        topLeft = dot(it.row, it.col),
                        size = Size(spacing, spacing)
                    )
                }
            }

            // Horizontal lines
            state.horizontalLines.flatten().forEach { line ->
                line.drawnBy?.let { p ->
                    drawLine(
                        color = (if (p == Player.HUMAN) Color.Blue else Color.Red)
                            .copy(alpha = line.alpha),
                        start = dot(line.row, line.col),
                        end = dot(line.row, line.col + 1),
                        strokeWidth = 12f
                    )
                }
            }

            // Vertical lines
            state.verticalLines.flatten().forEach { line ->
                line.drawnBy?.let { p ->
                    drawLine(
                        color = (if (p == Player.HUMAN) Color.Blue else Color.Red)
                            .copy(alpha = line.alpha),
                        start = dot(line.row, line.col),
                        end = dot(line.row + 1, line.col),
                        strokeWidth = 12f
                    )
                }
            }

            // Dots
            repeat(state.gridSize) { r ->
                repeat(state.gridSize) { c ->
                    drawCircle(
                        color = Color.Black,
                        radius = 14f,
                        center = dot(r, c)
                    )
                }
            }
        }
    }
}

private fun detectLine(
    tap: Offset,
    size: Size,
    state: GameState
): Line? {
    val padding = 32f
    val usable = size.width - padding * 2
    val spacing = usable / (state.gridSize - 1)

    val x = tap.x - padding
    val y = tap.y - padding

    if (x < 0 || y < 0 || x > usable || y > usable) return null

    val col = (x / spacing).toInt()
    val row = (y / spacing).toInt()

    val dx = x - col * spacing
    val dy = y - row * spacing
    val threshold = spacing * 0.25f

    if (
        dy < threshold &&
        row < state.horizontalLines.size &&
        col < state.horizontalLines[0].size
    ) return state.horizontalLines[row][col]

    if (
        dx < threshold &&
        row < state.verticalLines.size &&
        col < state.verticalLines[0].size
    ) return state.verticalLines[row][col]

    return null
}
