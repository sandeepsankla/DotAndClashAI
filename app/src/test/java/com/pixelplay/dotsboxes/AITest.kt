package com.pixelplay.dotsboxes

import com.pixelplay.dotsboxes.ai.EasyAI
import com.pixelplay.dotsboxes.ai.HardAI
import com.pixelplay.dotsboxes.ai.MediumAI
import com.pixelplay.dotsboxes.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class AITest {

    private fun fresh(size: Int = 4) =
        newGame(size, GameMode.PVA, Difficulty.MEDIUM, "P1", "AI")

    // ── EasyAI ────────────────────────────────────────────────────────────────

    @Test
    fun `EasyAI returns a valid undrawn line`() {
        val s = fresh()
        val move = EasyAI().getBestMove(s)
        assertNotNull(move)
        assertFalse(s.isLineDrawn(move!!))
    }

    @Test
    fun `EasyAI returns null when no moves left`() {
        var s = fresh(2)
        s.undrawnLines().forEach { id -> if (!s.isLineDrawn(id)) s = s.applyMove(id) }
        assertNull(EasyAI().getBestMove(s))
    }

    // ── MediumAI ──────────────────────────────────────────────────────────────

    @Test
    fun `MediumAI completes available box`() {
        // Set up 3 sides of box(0,0): top, left, right
        var s = fresh()
        s = s.applyMove(LineId(0, 0, true))  // P1 top
        s = s.applyMove(LineId(3, 3, true))  // P2 unrelated
        s = s.applyMove(LineId(0, 0, false)) // P1 left
        s = s.applyMove(LineId(3, 2, true))  // P2 unrelated
        s = s.applyMove(LineId(0, 1, false)) // P1 right
        // Now it's P2's turn; box(0,0) has 3 sides; medium AI should complete it
        val move = MediumAI().getBestMove(s)
        assertNotNull(move)
        assertTrue("Medium AI should complete available box", s.wouldCompleteBox(move!!))
    }

    @Test
    fun `MediumAI prefers safe move over dangerous move`() {
        var s = fresh()
        // Draw 2 sides of box(0,0): top + left → right or bottom would be dangerous
        s = s.applyMove(LineId(0, 0, true))  // P1 top
        s = s.applyMove(LineId(0, 0, false)) // P2 left (now P1's turn)
        // P1 uses unrelated move to avoid box
        // Force P2 (AI) turn with a state we craft
        val aiState = s.copy(currentPlayer = PlayerType.TWO) // force AI turn

        val move = MediumAI().getBestMove(aiState)
        // The chosen move should NOT create a 3-sided box if any safe move exists
        if (aiState.undrawnLines().any { !aiState.wouldGiveOpponent3Sides(it) }) {
            assertFalse(
                "Medium AI should avoid giving 3-sided box when safe moves exist",
                move != null && aiState.wouldGiveOpponent3Sides(move)
            )
        }
    }

    @Test
    fun `MediumAI returns valid move`() {
        val s = fresh()
        val move = MediumAI().getBestMove(s)
        assertNotNull(move)
        assertFalse(s.isLineDrawn(move!!))
    }

    // ── HardAI ────────────────────────────────────────────────────────────────

    @Test
    fun `HardAI completes available box`() {
        var s = fresh()
        s = s.applyMove(LineId(0, 0, true))
        s = s.applyMove(LineId(3, 3, true))
        s = s.applyMove(LineId(0, 0, false))
        s = s.applyMove(LineId(3, 2, true))
        s = s.applyMove(LineId(0, 1, false))

        val move = HardAI().getBestMove(s)
        assertNotNull(move)
        assertTrue("Hard AI must take available box", s.wouldCompleteBox(move!!))
    }

    @Test
    fun `HardAI returns valid move on empty board`() {
        val s = fresh()
        val move = HardAI().getBestMove(s)
        assertNotNull(move)
        assertFalse(s.isLineDrawn(move!!))
    }

    @Test
    fun `HardAI returns null on full board`() {
        var s = fresh(2)
        s.undrawnLines().forEach { id -> if (!s.isLineDrawn(id)) s = s.applyMove(id) }
        assertNull(HardAI().getBestMove(s))
    }

    @Test
    fun `HardAI never plays a drawn line`() {
        var s = fresh()
        repeat(8) {
            val move = HardAI().getBestMove(s) ?: return
            assertFalse("AI played already-drawn line", s.isLineDrawn(move))
            s = s.applyMove(move)
        }
    }

    // ── Full game simulation ──────────────────────────────────────────────────

    @Test
    fun `full game with Hard AI terminates correctly`() {
        var s = fresh(3)
        var turns = 0
        val maxTurns = s.totalBoxes * 4 + 20  // safety cap

        while (!s.isGameOver && turns < maxTurns) {
            val move = if (s.currentPlayer == PlayerType.ONE) {
                EasyAI().getBestMove(s)
            } else {
                HardAI().getBestMove(s)
            } ?: break
            s = s.applyMove(move)
            turns++
        }
        assertTrue("Game should end", s.isGameOver)
        assertEquals(s.totalBoxes, s.p1Score + s.p2Score)
    }
}
