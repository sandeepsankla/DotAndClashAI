package com.pixelplay.dotsboxes.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.*
import com.pixelplay.dotsboxes.domain.model.BoardSkin
import com.pixelplay.dotsboxes.domain.model.GameState
import com.pixelplay.dotsboxes.domain.model.LineId
import com.pixelplay.dotsboxes.domain.model.PlayerType
import com.pixelplay.dotsboxes.presentation.theme.*
import kotlin.math.min
import kotlin.math.sqrt

private const val PADDING_FRACTION       = 0.08f
private const val DOT_RADIUS_FRACTION    = 0.07f
private const val STROKE_FRACTION        = 0.12f
private const val HIT_THRESHOLD_FRACTION = 0.42f

@Composable
fun GameBoard(
    state: GameState,
    lastLine: LineId?,
    onLineTap: (LineId) -> Unit,
    modifier: Modifier = Modifier,
    hintMove: LineId? = null,
    activeSkin: BoardSkin = BoardSkin.DEFAULT
) {
    val isDark = isSystemInDarkTheme()

    // Skin-aware player colors
    val (p1Color, p2Color) = when (activeSkin) {
        BoardSkin.DEFAULT -> Player1Blue    to Player2Orange
        BoardSkin.FIRE    -> Color(0xFFFF5722) to Color(0xFFFFD600)
        BoardSkin.GOLDEN  -> Color(0xFFFFD700) to Color(0xFFE040FB)
    }
    val p1BoxFill  = p1Color.copy(alpha = 0.28f)
    val p2BoxFill  = p2Color.copy(alpha = 0.28f)
    val dotColor   = if (isDark) DotColorDark else DotColor
    val guideColor = if (isDark) GridGuideDark else GridGuide

    var hoverLine by remember { mutableStateOf<LineId?>(null) }

    // Line draw animation — grows from start → end
    var animTarget     by remember { mutableFloatStateOf(0f) }
    var animatedLineId by remember { mutableStateOf<LineId?>(null) }

    LaunchedEffect(lastLine) {
        if (lastLine != null) {
            animatedLineId = lastLine
            animTarget = 0f
            animTarget = 1f
        }
    }
    val drawProgress by animateFloatAsState(
        targetValue   = animTarget,
        animationSpec = tween(durationMillis = 200, easing = EaseOutCubic),
        label         = "lineAnim"
    )

    // Continuous glow pulse for drawn lines
    val glowPulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue  = 0.55f,
        targetValue   = 0.85f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "glowAlpha"
    )

    // Hint move golden pulse
    val hintPulse by rememberInfiniteTransition(label = "hint").animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(tween(500, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "hintAlpha"
    )

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val boardSize = size.width.toFloat()
                    val pad  = boardSize * PADDING_FRACTION
                    val cell = (boardSize - 2 * pad) / state.gridSize

                    hoverLine = findLine(down.position, pad, cell, state)

                    var event: PointerEvent
                    do {
                        event = awaitPointerEvent(PointerEventPass.Main)
                        val pos = event.changes.firstOrNull()?.position ?: break
                        hoverLine = findLine(pos, pad, cell, state)
                    } while (event.changes.any { it.pressed })

                    hoverLine?.let { onLineTap(it) }
                    hoverLine = null
                }
            }
    ) {
        val boardSize = min(size.width, size.height)
        val pad    = boardSize * PADDING_FRACTION
        val cell   = (boardSize - 2 * pad) / state.gridSize
        val dotR   = cell * DOT_RADIUS_FRACTION
        val stroke = cell * STROKE_FRACTION

        // 1. Box fills (vibrant gradient-like fill)
        drawBoxFills(state, pad, cell, p1BoxFill, p2BoxFill, p1Color, p2Color)

        // 2. Guide lines
        drawGuideLines(state, pad, cell, guideColor, stroke * 0.35f)

        // 3. Drawn lines with glow
        drawDrawnLinesGlow(state, pad, cell, stroke, p1Color, p2Color, glowPulse, animatedLineId, drawProgress)

        // 4. Hint move golden highlight
        hintMove?.let { id ->
            if (!state.isLineDrawn(id)) drawHintLine(id, pad, cell, stroke, hintPulse)
        }

        // 4b. Hover highlight
        hoverLine?.let { id ->
            if (!state.isLineDrawn(id)) drawHoverLine(id, pad, cell, stroke)
        }

        // 5. Dots on top
        for (r in 0..state.gridSize) {
            for (c in 0..state.gridSize) {
                // Outer shadow ring
                drawCircle(dotColor.copy(alpha = 0.18f), dotR * 1.8f, dot(r, c, pad, cell))
                // Dot
                drawCircle(dotColor, dotR, dot(r, c, pad, cell))
            }
        }
    }
}

// ── Drawing ───────────────────────────────────────────────────────────────────

private fun DrawScope.drawBoxFills(
    state: GameState,
    pad: Float, cell: Float,
    p1Fill: Color, p2Fill: Color,
    p1Border: Color, p2Border: Color
) {
    for (r in 0 until state.gridSize) {
        for (c in 0 until state.gridSize) {
            val owner = state.boxes[r][c] ?: continue
            val fill   = if (owner == PlayerType.ONE) p1Fill   else p2Fill
            val border = if (owner == PlayerType.ONE) p1Border else p2Border
            val tl = dot(r, c, pad, cell)

            // Fill
            drawRect(fill, tl, Size(cell, cell))
            // Subtle inner border to make box crisp
            drawRect(
                color   = border.copy(alpha = 0.45f),
                topLeft = tl,
                size    = Size(cell, cell),
                style   = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }
    }
}

private fun DrawScope.drawGuideLines(
    state: GameState, pad: Float, cell: Float, color: Color, stroke: Float
) {
    for (r in 0..state.gridSize) for (c in 0 until state.gridSize)
        if (state.hLines[r][c] == null)
            drawLine(color, dot(r, c, pad, cell), dot(r, c + 1, pad, cell), stroke, StrokeCap.Round)

    for (r in 0 until state.gridSize) for (c in 0..state.gridSize)
        if (state.vLines[r][c] == null)
            drawLine(color, dot(r, c, pad, cell), dot(r + 1, c, pad, cell), stroke, StrokeCap.Round)
}

private fun DrawScope.drawDrawnLinesGlow(
    state: GameState,
    pad: Float, cell: Float, stroke: Float,
    p1: Color, p2: Color,
    glowAlpha: Float,
    animLine: LineId?, progress: Float
) {
    fun drawGlowLine(start: Offset, end: Offset, color: Color, isAnimating: Boolean, prog: Float) {
        val endPt = if (isAnimating) start + (end - start) * prog else end
        // Layer 1 — soft outer glow
        drawLine(color.copy(alpha = glowAlpha * 0.25f), start, endPt, stroke * 3.2f, StrokeCap.Round)
        // Layer 2 — mid glow
        drawLine(color.copy(alpha = glowAlpha * 0.5f),  start, endPt, stroke * 1.8f, StrokeCap.Round)
        // Layer 3 — solid core
        drawLine(color, start, endPt, stroke, StrokeCap.Round)
    }

    for (r in 0..state.gridSize) for (c in 0 until state.gridSize) {
        val owner = state.hLines[r][c] ?: continue
        val id    = LineId(r, c, true)
        drawGlowLine(dot(r, c, pad, cell), dot(r, c + 1, pad, cell),
            if (owner == PlayerType.ONE) p1 else p2, id == animLine, progress)
    }
    for (r in 0 until state.gridSize) for (c in 0..state.gridSize) {
        val owner = state.vLines[r][c] ?: continue
        val id    = LineId(r, c, false)
        drawGlowLine(dot(r, c, pad, cell), dot(r + 1, c, pad, cell),
            if (owner == PlayerType.ONE) p1 else p2, id == animLine, progress)
    }
}

private fun DrawScope.drawHintLine(id: LineId, pad: Float, cell: Float, stroke: Float, pulse: Float) {
    // Gold outer glow + solid core
    val gold = Color(0xFFFFD700)
    val (start, end) = lineEndpoints(id, pad, cell)
    drawLine(gold.copy(alpha = pulse * 0.3f), start, end, stroke * 4f, StrokeCap.Round)
    drawLine(gold.copy(alpha = pulse * 0.6f), start, end, stroke * 2f, StrokeCap.Round)
    drawLine(gold.copy(alpha = pulse),        start, end, stroke,      StrokeCap.Round)
}

private fun lineEndpoints(id: LineId, pad: Float, cell: Float): Pair<Offset, Offset> =
    if (id.isHorizontal)
        dot(id.row, id.col, pad, cell) to dot(id.row, id.col + 1, pad, cell)
    else
        dot(id.row, id.col, pad, cell) to dot(id.row + 1, id.col, pad, cell)

private fun DrawScope.drawHoverLine(id: LineId, pad: Float, cell: Float, stroke: Float) {
    val color = Color.White.copy(alpha = 0.5f)
    val (start, end) = lineEndpoints(id, pad, cell)
    drawLine(color, start, end, stroke * 0.75f, StrokeCap.Round)
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun dot(row: Int, col: Int, pad: Float, cell: Float) =
    Offset(pad + col * cell, pad + row * cell)

private fun findLine(tap: Offset, pad: Float, cell: Float, state: GameState): LineId? {
    val threshold = cell * HIT_THRESHOLD_FRACTION
    var best: LineId? = null
    var bestDist = Float.MAX_VALUE

    fun check(id: LineId, mid: Offset) {
        if (state.isLineDrawn(id)) return
        val d = dist(tap, mid)
        if (d < threshold && d < bestDist) { bestDist = d; best = id }
    }

    for (r in 0..state.gridSize) for (c in 0 until state.gridSize)
        check(LineId(r, c, true), Offset(pad + (c + 0.5f) * cell, pad + r * cell))

    for (r in 0 until state.gridSize) for (c in 0..state.gridSize)
        check(LineId(r, c, false), Offset(pad + c * cell, pad + (r + 0.5f) * cell))

    return best
}

private fun dist(a: Offset, b: Offset): Float {
    val dx = a.x - b.x; val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}
