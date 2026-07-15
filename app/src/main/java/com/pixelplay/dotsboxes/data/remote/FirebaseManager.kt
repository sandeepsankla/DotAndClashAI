package com.pixelplay.dotsboxes.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.pixelplay.dotsboxes.domain.model.OnlineMove
import com.pixelplay.dotsboxes.domain.model.OnlineRoom
import com.pixelplay.dotsboxes.domain.model.RoomStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class LeaderboardEntry(
    val uid: String,
    val name: String,
    val score: Int,
    val timeTaken: Int
)

class FirebaseManager {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseDatabase.getInstance().reference

    // ── Auth ──────────────────────────────────────────────────────────────────

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun currentDisplayName(): String? = currentUser?.displayName

    fun currentOnlinePlayer() = currentUser?.let { user ->
        com.pixelplay.dotsboxes.domain.model.OnlinePlayer(
            uid         = user.uid,
            displayName = user.displayName ?: "Player",
            email       = user.email,
            photoUrl    = user.photoUrl?.toString(),
            isGuest     = user.isAnonymous
        )
    }

    suspend fun signInAsGuest(): Result<FirebaseUser> = runCatching {
        auth.signInAnonymously().await().user!!
    }

    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await().user!!
    }

    fun signOut() = auth.signOut()

    /**
     * Permanently delete the signed-in user's account and server-side data:
     *  1. Remove their leaderboard entries across all day buckets.
     *  2. Delete the Firebase Auth record (uid + email from Google sign-in).
     *
     * Best-effort: leaderboard cleanup runs first so PII is removed even if the
     * auth delete needs a recent re-login. Local device data (coins/XP/progress)
     * is wiped separately by the caller.
     */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        val uid  = user.uid

        // 1. Remove this user's leaderboard entries (name + score) from every day bucket.
        runCatching {
            val root = db.child("flash_leaderboard").get().await()
            root.children.forEach { dayBucket ->
                val dayKey = dayBucket.key ?: return@forEach
                if (dayBucket.hasChild(uid)) {
                    db.child("flash_leaderboard").child(dayKey).child(uid)
                        .removeValue().await()
                }
            }
        }

        // 2. Delete the auth account itself.
        user.delete().await()
    }

    suspend fun updateDisplayName(name: String) {
        val update = com.google.firebase.auth.userProfileChangeRequest {
            displayName = name
        }
        auth.currentUser?.updateProfile(update)?.await()
    }

    // ── Room CRUD ─────────────────────────────────────────────────────────────

    /** Creates a room and returns the 6-char code */
    suspend fun createRoom(hostName: String, gridSize: Int): Result<String> = runCatching {
        val uid  = auth.currentUser?.uid ?: error("Not logged in")
        val code = generateCode()
        val room = mapOf(
            "hostUid"   to uid,
            "hostName"  to hostName,
            "guestUid"  to null,
            "guestName" to null,
            "gridSize"  to gridSize,
            "status"    to "WAITING",
            "createdAt" to System.currentTimeMillis()
        )
        db.child("rooms").child(code).setValue(room).await()
        // NOTE: no onDisconnect auto-abandon — it fired on brief backgrounding (host
        // switching to WhatsApp to share) and wrongly killed rooms before anyone joined.
        // Cleanup is handled by the 2-min host waiting timer + 3-min join expiry +
        // explicit leave (back press).
        code
    }

    /** Guest joins an existing room */
    suspend fun joinRoom(code: String, guestName: String): Result<OnlineRoom> = runCatching {
        val uid     = auth.currentUser?.uid ?: error("Not logged in")
        val roomRef = db.child("rooms").child(code.uppercase())
        val snap    = roomRef.get().await()

        check(snap.exists()) { "Room not found. Check the code and try again." }

        val createdAt = snap.child("createdAt").getValue(Long::class.java) ?: 0L
        val ageMinutes = (System.currentTimeMillis() - createdAt) / 60_000
        check(ageMinutes < 3) { "Room code has expired. Ask your friend to create a new room." }

        val status = snap.child("status").getValue(String::class.java) ?: "WAITING"
        check(status == "WAITING") { "Room is not available (status: $status)" }
        check(snap.child("guestUid").value == null) { "Room is already full" }

        val updates = mapOf(
            "guestUid"  to uid,
            "guestName" to guestName,
            "status"    to "PLAYING"
        )
        roomRef.updateChildren(updates).await()
        // No onDisconnect auto-abandon (see createRoom note). Leaving is explicit (back press).
        snapshotToRoom(snap.ref.get().await(), code.uppercase())
    }

    /** Push one move to Firebase */
    suspend fun pushMove(code: String, move: OnlineMove): Result<Unit> = runCatching {
        val moveMap = mapOf(
            "row" to move.row,
            "col" to move.col,
            "h"   to move.isHorizontal
        )
        db.child("rooms").child(code).child("moves").push().setValue(moveMap).await()
    }

    /** Real-time room listener → Flow */
    fun listenToRoom(code: String): Flow<OnlineRoom?> = callbackFlow {
        val ref = db.child("rooms").child(code)
        val listener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                trySend(snapshotToRoom(snap, code))
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Once the match is live, stop auto-abandoning on a transient disconnect (ads, backgrounding). */
    fun cancelStatusOnDisconnect(code: String) {
        db.child("rooms").child(code).child("status").onDisconnect().cancel()
    }

    suspend fun abandonRoom(code: String) {
        runCatching {
            db.child("rooms").child(code).child("status").setValue("ABANDONED").await()
        }
    }

    suspend fun finishRoom(code: String) {
        runCatching {
            db.child("rooms").child(code).child("status").setValue("FINISHED").await()
        }
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    /** Ensure user is signed in (anonymous if needed) — required for leaderboard */
    suspend fun ensureSignedIn(): String {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user!!
        return user.uid
    }

    /** Save flash challenge score for today */
    suspend fun saveFlashScore(playerName: String, score: Int, timeTaken: Int) {
        runCatching {
            val uid     = ensureSignedIn()
            val dayKey  = java.util.concurrent.TimeUnit.MILLISECONDS
                .toDays(System.currentTimeMillis()).toString()
            val entry   = mapOf(
                "name"      to playerName,
                "score"     to score,
                "time"      to timeTaken,   // seconds used (lower = faster for tiebreak)
                "timestamp" to System.currentTimeMillis()
            )
            db.child("flash_leaderboard").child(dayKey).child(uid)
                .setValue(entry).await()
        }
    }

    /** Fetch today's leaderboard — top 50 by score desc */
    suspend fun fetchTodayLeaderboard(): List<LeaderboardEntry> = runCatching {
        val dayKey = java.util.concurrent.TimeUnit.MILLISECONDS
            .toDays(System.currentTimeMillis()).toString()
        val snap   = db.child("flash_leaderboard").child(dayKey).get().await()
        snap.children.mapNotNull { child ->
            val name  = child.child("name").getValue(String::class.java)  ?: return@mapNotNull null
            val score = child.child("score").getValue(Int::class.java)    ?: return@mapNotNull null
            val time  = child.child("time").getValue(Int::class.java)     ?: 15
            LeaderboardEntry(uid = child.key ?: "", name = name, score = score, timeTaken = time)
        }
        .sortedWith(compareByDescending<LeaderboardEntry> { it.score }.thenBy { it.timeTaken })
        .take(50)
    }.getOrDefault(emptyList())

    /** Live leaderboard listener */
    fun listenLeaderboard(): Flow<List<LeaderboardEntry>> = callbackFlow {
        val dayKey = java.util.concurrent.TimeUnit.MILLISECONDS
            .toDays(System.currentTimeMillis()).toString()
        val ref = db.child("flash_leaderboard").child(dayKey)
        val listener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val entries = snap.children.mapNotNull { child ->
                    val name  = child.child("name").getValue(String::class.java)  ?: return@mapNotNull null
                    val score = child.child("score").getValue(Int::class.java)    ?: return@mapNotNull null
                    val time  = child.child("time").getValue(Int::class.java)     ?: 15
                    LeaderboardEntry(child.key ?: "", name, score, time)
                }
                .sortedWith(compareByDescending<LeaderboardEntry> { it.score }.thenBy { it.timeTaken })
                .take(50)
                trySend(entries)
            }
            override fun onCancelled(error: DatabaseError) { trySend(emptyList()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun snapshotToRoom(snap: DataSnapshot, code: String): OnlineRoom {
        val moves = snap.child("moves").children.mapNotNull { moveSnap ->
            val r = moveSnap.child("row").getValue(Int::class.java) ?: return@mapNotNull null
            val c = moveSnap.child("col").getValue(Int::class.java) ?: return@mapNotNull null
            val h = moveSnap.child("h").getValue(Boolean::class.java) ?: true
            OnlineMove(r, c, h)
        }
        val statusStr = snap.child("status").getValue(String::class.java) ?: "WAITING"
        val status = runCatching { RoomStatus.valueOf(statusStr) }.getOrDefault(RoomStatus.WAITING)

        return OnlineRoom(
            code      = code,
            hostUid   = snap.child("hostUid").getValue(String::class.java) ?: "",
            hostName  = snap.child("hostName").getValue(String::class.java) ?: "Host",
            guestUid  = snap.child("guestUid").getValue(String::class.java),
            guestName = snap.child("guestName").getValue(String::class.java),
            gridSize  = snap.child("gridSize").getValue(Int::class.java) ?: 4,
            status    = status,
            moves     = moves
        )
    }
}
