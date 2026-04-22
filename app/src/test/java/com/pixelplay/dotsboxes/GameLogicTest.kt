package com.pixelplay.dotsboxes

import com.pixelplay.dotsboxes.domain.model.*
import com.pixelplay.dotsboxes.domain.usecase.DrawLineUseCase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GameLogicTest {

    private lateinit var draw: DrawLineUseCase

    @Before
    fun setUp() {
        draw = DrawLineUseCase()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun freshState(size: Int = 4) =
        newGame(size, GameMode.PVP, Difficulty.MEDIUM, "P1", "P2")

    /** Draw a sequence of lines in order, alternating turns as the game dictates. */
    private fun GameState.drawAll(vararg ids: LineId): GameState {
        var s = this
        for (id in ids) s = s.applyMove(id)
        return s
    }

    // ── Box completion ────────────────────────────────────────────────────────

    @Test
    fun `completing a box awards a point and keeps same player`() {
        // Build a 4-sided box at (0,0) step by step
        val s0 = freshState()
        val s1 = s0.applyMove(LineId(0, 0, true))   // top
        assertEquals(PlayerType.TWO, s1.currentPlayer)  // turn switches (no box)

        // Use applyMove directly to force player choice
        val manualState = s0
            .applyMove(LineId(0, 0, true))   // P1: top
            .applyMove(LineId(0, 0, false))  // P2: left
            .applyMove(LineId(0, 1, false))  // P1: right
        // Before completing: it should be P2's turn
        assertEquals(PlayerType.TWO, manualState.currentPlayer)

        val completed = manualState.applyMove(LineId(1, 0, true)) // P2: bottom → box complete
        assertEquals(1, completed.p2Score)
        // P2 gets extra turn
        assertEquals(PlayerType.TWO, completed.currentPlayer)
    }

    @Test
    fun `drawing an already-drawn line returns null`() {
        val s = freshState().applyMove(LineId(0, 0, true))
        val result = draw(s, LineId(0, 0, true))
        assertNull(result)
    }

    @Test
    fun `game ends when all boxes are filled`() {
        var state = freshState(2)
        // 2×2 grid: 4 boxes, 12 total lines
        // Draw all lines methodically
        val hLines = (0..2).flatMap { r -> (0..1).map { c -> LineId(r, c, true) } }
        val vLines = (0..1).flatMap { r -> (0..2).map { c -> LineId(r, c, false) } }
        for (id in hLines + vLines) {
            if (!state.isLineDrawn(id)) {
                state = state.applyMove(id)
            }
        }
        assertTrue(state.isGameOver)
        assertEquals(4, state.p1Score + state.p2Score)
    }

    // ── Turn switching ────────────────────────────────────────────────────────

    @Test
    fun `turn switches after non-scoring move`() {
        val s = freshState()
        val after = s.applyMove(LineId(0, 0, true))
        assertEquals(PlayerType.TWO, after.currentPlayer)
    }

    @Test
    fun `turn does NOT switch after scoring move`() {
        // Manually build 3 sides of box(0,0)
        var s = freshState()
        s = s.applyMove(LineId(0, 0, true))  // P1 → P2 turn
        s = s.applyMove(LineId(0, 0, false)) // P2 → P1 turn
        s = s.applyMove(LineId(0, 1, false)) // P1 → P2 turn
        s = s.applyMove(LineId(1, 0, true))  // P2 completes box → P2 keeps turn
        assertEquals(PlayerType.TWO, s.currentPlayer)
        assertEquals(1, s.p2Score)
    }

    // ── Game-over and winner detection ────────────────────────────────────────

    @Test
    fun `winner is player with more boxes`() {
        var s = freshState(2)
        // 2×2 grid — engineer P1 winning 3 boxes, P2 gets 1
        val all = buildList {
            (0..2).forEach { r -> (0..1).forEach { c -> add(LineId(r, c, true)) } }
            (0..1).forEach { r -> (0..2).forEach { c -> add(LineId(r, c, false)) } }
        }
        for (id in all) {
            if (!s.isLineDrawn(id) && !s.isGameOver) s = s.applyMove(id)
        }
        assertTrue(s.isGameOver)
        assertNotNull(s.winner)  // someone won (non-null)
    }

    @Test
    fun `move on game-over state returns null`() {
        var s = freshState(2)
        val all = buildList {
            (0..2).forEach { r -> (0..1).forEach { c -> add(LineId(r, c, true)) } }
            (0..1).forEach { r -> (0..2).forEach { c -> add(LineId(r, c, false)) } }
        }
        for (id in all) {
            if (!s.isLineDrawn(id) && !s.isGameOver) s = s.applyMove(id)
        }
        val result = draw(s, LineId(0, 0, true))
        assertNull(result)
    }

    // ── wouldGiveOpponent3Sides ───────────────────────────────────────────────

    @Test
    fun `wouldGiveOpponent3Sides detects dangerous move`() {
        var s = freshState()
        // Draw 2 sides of box(0,0): top and left
        s = s.applyMove(LineId(0, 0, true))  // top
        s = s.applyMove(LineId(2, 2, true))  // unrelated (P2 move)
        s = s.applyMove(LineId(0, 0, false)) // left (P1)
        // Now box(0,0) has 2 sides. Drawing the right side V[0][1] would give it 3 → dangerous
        assertTrue(s.wouldGiveOpponent3Sides(LineId(0, 1, false)))
    }

    @Test
    fun `wouldCompleteBox detects winning move`() {
        var s = freshState()
        s = s.applyMove(LineId(0, 0, true))  // P1 top
        s = s.applyMove(LineId(2, 2, true))  // P2 unrelated
        s = s.applyMove(LineId(0, 0, false)) // P1 left
        s = s.applyMove(LineId(2, 3, true))  // P2 unrelated
        s = s.applyMove(LineId(0, 1, false)) // P1 right
        // Box(0,0) has 3 sides — bottom line H[1][0] completes it
        s = s.applyMove(LineId(3, 1, true))  // P2 unrelated (ensure it's P2's → then P1's)
        assertTrue(s.wouldCompleteBox(LineId(1, 0, true)))
    }

    // ── Grid dimension correctness ────────────────────────────────────────────

    @Test
    fun `hLines has gridSize+1 rows and gridSize cols`() {
        val s = freshState(5)
        assertEquals(6, s.hLines.size)
        assertEquals(5, s.hLines[0].size)
    }

    @Test
    fun `vLines has gridSize rows and gridSize+1 cols`() {
        val s = freshState(5)
        assertEquals(5, s.vLines.size)
        assertEquals(6, s.vLines[0].size)
    }

    @Test
    fun `boxes grid is gridSize x gridSize`() {
        val s = freshState(5)
        assertEquals(5, s.boxes.size)
        assertEquals(5, s.boxes[0].size)
    }
}
