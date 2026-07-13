package com.pixelplay.dotsboxes.domain.model

// ── Online player (logged-in user) ────────────────────────────────────────────

data class OnlinePlayer(
    val uid: String       = "",
    val displayName: String = "Guest",
    val email: String?    = null,
    val photoUrl: String? = null,
    val isGuest: Boolean  = false
)

// ── A single move stored in Firebase ─────────────────────────────────────────

data class OnlineMove(
    val row: Int           = 0,
    val col: Int           = 0,
    val isHorizontal: Boolean = true
)

// ── Room status ───────────────────────────────────────────────────────────────

enum class RoomStatus { WAITING, PLAYING, FINISHED, ABANDONED }

// ── Full room snapshot from Firebase ─────────────────────────────────────────

data class OnlineRoom(
    val code: String        = "",
    val hostUid: String     = "",
    val hostName: String    = "Host",
    val guestUid: String?   = null,
    val guestName: String?  = null,
    val gridSize: Int       = 4,
    val status: RoomStatus  = RoomStatus.WAITING,
    val moves: List<OnlineMove> = emptyList()
) {
    val isFull: Boolean get() = guestUid != null
}
