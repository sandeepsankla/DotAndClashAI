package com.pixelplay.dotsboxes.domain.model

import kotlin.random.Random

/**
 * Positions of special reward boxes on a board. Kept OUTSIDE GameState so the
 * game engine / AI / serialization stay untouched. Rewards only trigger when the
 * human (PlayerType.ONE) captures the box.
 *
 * 👑 Crown Box  → capturing it awards a Lucky Spin.
 * 🎁 Mystery Box → capturing it rolls a random reward.
 */
data class SpecialBoxes(
    val crowns: Set<Pair<Int, Int>> = emptySet(),
    val mystery: Pair<Int, Int>? = null
) {
    fun isCrown(r: Int, c: Int)   = (r to c) in crowns
    fun isMystery(r: Int, c: Int) = mystery == (r to c)
    val isEmpty: Boolean get() = crowns.isEmpty() && mystery == null

    /** Return a copy with the given box removed (so it can't retrigger). */
    fun without(r: Int, c: Int): SpecialBoxes = copy(
        crowns  = crowns - (r to c),
        mystery = if (mystery == (r to c)) null else mystery
    )
}

/**
 * Pick 2 crown boxes + 1 mystery box at distinct random positions.
 * Pass a [seed] (e.g. derived from the room code) so both online players get the
 * SAME layout deterministically. Omit it for a fresh random layout (offline).
 */
fun generateSpecialBoxes(gridSize: Int, seed: Long? = null): SpecialBoxes {
    val rng = if (seed != null) Random(seed) else Random.Default
    val all = buildList {
        for (r in 0 until gridSize) for (c in 0 until gridSize) add(r to c)
    }.shuffled(rng)
    if (all.size < 3) return SpecialBoxes()
    val crowns  = all.take(2).toSet()
    val mystery = all[2]
    return SpecialBoxes(crowns = crowns, mystery = mystery)
}

// ── Mystery reward ─────────────────────────────────────────────────────────────

enum class MysteryKind { COINS, XP, HINTS, JACKPOT, NOTHING }

data class MysteryReward(
    val kind: MysteryKind,
    val amount: Int,
    val emoji: String,
    val label: String
)

/** Weighted random mystery reward. */
fun rollMysteryReward(rng: Random = Random.Default): MysteryReward {
    val roll = rng.nextInt(100)
    return when {
        roll < 40 -> {                               // 40% coins
            val c = listOf(50, 75, 100, 125).random(rng)
            MysteryReward(MysteryKind.COINS, c, "🪙", "+$c Coins")
        }
        roll < 65 -> {                               // 25% XP
            val x = listOf(30, 50, 80).random(rng)
            MysteryReward(MysteryKind.XP, x, "⭐", "+$x XP")
        }
        roll < 85 -> {                               // 20% hints
            val h = listOf(2, 3).random(rng)
            MysteryReward(MysteryKind.HINTS, h, "💡", "+$h Hints")
        }
        roll < 95 -> {                               // 10% jackpot
            MysteryReward(MysteryKind.JACKPOT, 250, "💎", "+250 Coins Jackpot!")
        }
        else -> {                                    // 5% nothing
            MysteryReward(MysteryKind.NOTHING, 0, "🌫️", "Empty… next time!")
        }
    }
}

/** UI event shown when the human captures a special box. */
sealed interface SpecialRewardEvent {
    data object Crown : SpecialRewardEvent
    data class Mystery(val reward: MysteryReward) : SpecialRewardEvent
}
