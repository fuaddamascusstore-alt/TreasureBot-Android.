package com.treasurewatch.bot

/** Fully deterministic session bounds, testable without Android. */
data class SessionLimits(val scanSeconds: Int, val maxRooms: Int, val maxMinutes: Int) {
    fun normalized() = SessionLimits(
        scanSeconds.coerceIn(5, 60), maxRooms.coerceIn(1, 100), maxMinutes.coerceIn(5, 120)
    )
}

class SessionGuard(requested: SessionLimits, private val startedAt: Long) {
    val limits = requested.normalized()
    var rooms: Int = 1
        private set
    private var roomStarted = startedAt
    private var foundThisRoom = false

    fun timedOut(now: Long) = now - startedAt >= limits.maxMinutes * 60_000L
    fun scanExpired(now: Long) = now - roomStarted >= limits.scanSeconds * 1000L
    fun scanRemaining(now: Long) = ((limits.scanSeconds * 1000L - (now - roomStarted) + 999) / 1000).coerceAtLeast(0).toInt()
    fun recordBox(): Boolean {
        if (foundThisRoom) return false
        foundThisRoom = true
        return true
    }
    fun nextRoom(now: Long): Boolean {
        if (rooms >= limits.maxRooms || timedOut(now)) return false
        rooms++
        roomStarted = now
        foundThisRoom = false
        return true
    }
    fun atRoomLimit() = rooms >= limits.maxRooms
}
