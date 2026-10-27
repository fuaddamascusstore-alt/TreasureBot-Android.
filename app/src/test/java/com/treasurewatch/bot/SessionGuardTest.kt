package com.treasurewatch.bot

import org.junit.Assert.*
import org.junit.Test

class SessionGuardTest {
    @Test fun clampsUntrustedSettings() {
        assertEquals(SessionLimits(5, 100, 5), SessionLimits(-50, 999, -2).normalized())
    }

    @Test fun capsScanAndRoomProgress() {
        val g = SessionGuard(SessionLimits(10, 2, 30), 1_000L)
        assertEquals(10, g.scanRemaining(1_000L))
        assertFalse(g.scanExpired(10_999L))
        assertTrue(g.scanExpired(11_000L))
        assertTrue(g.nextRoom(11_000L))
        assertEquals(2, g.rooms)
        assertFalse(g.nextRoom(21_000L))
    }

    @Test fun deduplicatesBoxUntilManualRoomChange() {
        val g = SessionGuard(SessionLimits(5, 2, 30), 100L)
        assertTrue(g.recordBox())
        assertFalse(g.recordBox())
        assertTrue(g.nextRoom(3_000L))
        assertTrue(g.recordBox())
    }

    @Test fun stopsAtSessionDeadline() {
        val g = SessionGuard(SessionLimits(10, 100, 5), 42L)
        assertFalse(g.timedOut(300_041L))
        assertTrue(g.timedOut(300_042L))
        assertFalse(g.nextRoom(300_042L))
    }
}
