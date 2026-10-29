package com.utctaptimer.app

/** Pure, deterministic parsing and guards. Screen recognition is intentionally conservative. */
object SmartLogic {
    private val countdown = Regex("(?<![0-9])([0-9]{1,2})\\s*[:：]\\s*([0-9]{2})(?![0-9])")
    private val loose = Regex("(?<![0-9])([0-9]{1,2})\\s+([0-9]{2})(?![0-9])")
    private fun ascii(s: String): String = buildString(s.length) {
        for (c in s) append(when (c) {
            in '٠'..'٩' -> ('0'.code + c.code - '٠'.code).toChar()
            in '۰'..'۹' -> ('0'.code + c.code - '۰'.code).toChar()
            else -> c
        })
    }
    fun seconds(text: String): Int? {
        val normalized = ascii(text)
        val match = countdown.find(normalized) ?: loose.find(normalized) ?: return null
        val mm = match.groupValues[1].toIntOrNull() ?: return null
        val ss = match.groupValues[2].toIntOrNull() ?: return null
        if (ss !in 0..59 || mm !in 0..59) return null
        return mm * 60 + ss
    }
    fun canTapAfterTransition(lastSeconds: Int?, millisecondsSinceReading: Long, consecutiveMissing: Int, pinkButton: Boolean, userAuthorized: Boolean, alreadyTapped: Boolean): Boolean =
        userAuthorized && !alreadyTapped && pinkButton && consecutiveMissing >= 2 &&
            lastSeconds != null && lastSeconds in 0..1 && millisecondsSinceReading in (if (lastSeconds == 1) 850L else 450L)..2800L

    fun hasOpenText(text: String): Boolean {
        val normalized = ascii(text).lowercase().trim()
        return listOf("فتح", "افتح", "open", "claim", "collect", "aç", "ödülü al").any {
            normalized == it || normalized.startsWith("$it ")
        }
    }
}

