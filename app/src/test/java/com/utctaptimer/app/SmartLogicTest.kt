package com.utctaptimer.app

import org.junit.Assert.*
import org.junit.Test

class SmartLogicTest {
    @Test fun countdownParsesArabicAndLatinDigits() {
        assertEquals(34, SmartLogic.seconds("00:34"))
        assertEquals(34, SmartLogic.seconds("٠٠:٣٤"))
        assertEquals(95, SmartLogic.seconds("01:35"))
        assertEquals(9, SmartLogic.seconds("00 09"))
    }
    @Test fun rejectNonsensicalCounts() {
        assertNull(SmartLogic.seconds("00:99"))
        assertNull(SmartLogic.seconds("101 coins"))
        assertNull(SmartLogic.seconds("hour 20:60"))
    }
    @Test fun transitionRequiresVisualConfirmation() {
        assertFalse(SmartLogic.canTapAfterTransition(2, 1200, 2, true, true, false))
        assertFalse(SmartLogic.canTapAfterTransition(1, 1200, 1, true, true, false))
        assertFalse(SmartLogic.canTapAfterTransition(1, 1200, 2, false, true, false))
        assertFalse(SmartLogic.canTapAfterTransition(1, 1200, 2, true, false, false))
        assertTrue(SmartLogic.canTapAfterTransition(1, 1200, 2, true, true, false))
        assertFalse(SmartLogic.canTapAfterTransition(1, 1200, 2, true, true, true))
        assertFalse(SmartLogic.canTapAfterTransition(1, 5200, 2, true, true, false))
    }
    @Test fun detectKnownOpenActions() {
        assertTrue(SmartLogic.hasOpenText("فتح"))
        assertTrue(SmartLogic.hasOpenText("Open"))
        assertTrue(SmartLogic.hasOpenText("Aç"))
        assertFalse(SmartLogic.hasOpenText("Open your profile".replace("Open ", "Opened ")))
    }
}

