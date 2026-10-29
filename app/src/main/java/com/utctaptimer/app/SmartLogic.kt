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
    /**
     * Positive values mean milliseconds remaining before the estimated zero;
     * negative values mean milliseconds after the estimated zero.
     * Accepted formats: "0.250", "-0.050", "00:00.250", "01:05.100".
     */
    fun requestedRemainingMs(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val negative = text.startsWith("-")
        val unsigned = text.removePrefix("-").removePrefix("+")
        val seconds = if (':' in unsigned) {
            val groups = Regex("""^(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?$""").matchEntire(unsigned) ?: return null
            val minutes = groups.groupValues[1].toLongOrNull() ?: return null
            val sec = groups.groupValues[2].toLongOrNull() ?: return null
            if (sec > 59) return null
            val frac = groups.groupValues[3].padEnd(3, '0').ifEmpty { "000" }.toLongOrNull() ?: return null
            minutes * 60_000 + sec * 1000 + frac
        } else {
            val groups = Regex("""^\\d{1,5}(?:\\.\\d{1,3})?$""").matchEntire(unsigned) ?: return null
            val values = groups.value.split('.')
            val integral = values[0].toLongOrNull() ?: return null
            val fractional = values.getOrNull(1)?.padEnd(3, '0')?.toLongOrNull() ?: 0L
            integral * 1000 + fractional
        }
        return (if (negative) -seconds else seconds).takeIf { it in -5000L..60000L }
    }

}

