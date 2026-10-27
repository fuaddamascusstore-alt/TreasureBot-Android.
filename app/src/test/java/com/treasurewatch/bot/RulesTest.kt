package com.treasurewatch.bot

import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test fun findsArabicTurkishAndEnglishBoxes() {
        assertTrue(Rules.isBox("صندوق الكنز"))
        assertTrue(Rules.isBox("Hazine Kutusu"))
        assertTrue(Rules.isBox("TREASURE BOX"))
        assertFalse(Rules.isBox("Send a gift"))
    }

    @Test fun parsesTimersAndEasternDigits() {
        assertEquals(65, Rules.timeSeconds(listOf("01:05")))
        assertEquals(12, Rules.timeSeconds(listOf("باقي ١٢ ثانية")))
        assertEquals(25, Rules.timeSeconds(listOf("۲۵ sn")))
        assertEquals(600, Rules.timeSeconds(listOf("00:10:00")))
        assertNull(Rules.timeSeconds(listOf("12:59:59")))
        assertNull(Rules.timeSeconds(listOf("no countdown")))
    }

    @Test fun liveRequiresMoreThanLiveLabel() {
        assertFalse(Rules.isLiveScreen(listOf("LIVE")))
        assertTrue(Rules.isLiveScreen(listOf("LIVE", "Send a gift")))
        assertTrue(Rules.isLiveScreen(listOf("مباشر", "إرسال هدية")))
    }

    @Test fun openRulesDoNotMatchOtherElements() {
        assertTrue(Rules.isOpen("فتح"))
        assertFalse(Rules.isOpen("Open profile"))
    }
}
