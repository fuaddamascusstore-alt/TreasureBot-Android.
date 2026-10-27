package com.treasurewatch.bot

import java.util.Locale

/** Text-only heuristics. A result is a candidate, not a confirmed TikTok event. */
object Rules {
    private val boxes = listOf("treasure box", "treasure chest", "صندوق الكنز", "صندوق", "hazine kutusu", "hazine sandığı", "sandık", "boîte au trésor", "cofre del tesoro", "宝箱")
    private val open = listOf("open", "claim", "collect", "فتح", "افتح", "استلام", "aç", "topla", "ouvrir", "abrir", "領取", "领取")
    private val context = listOf("send a gift", "send gift", "gifts", "إرسال هدية", "الهدايا", "هدايا", "canlı yayın", "hediye gönder", "live match", "rewards", "إهداء")
    private val timer = Regex("(?<![0-9:])(?:(\\d{1,2}):)?([0-5]?\\d):([0-5]\\d)(?![0-9])")
    private val seconds = Regex("(?<![0-9])([1-9]\\d?|1[01]\\d|120)\\s*(?:s|sec|secs|seconds|ث|ثانية|ثوان|sn|saniye)(?![\\p{L}])")

    private fun normalize(input: String): String = buildString(input.length) {
        input.trim().lowercase(Locale.ROOT).forEach { c ->
            append(when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                else -> c
            })
        }
    }

    fun isBox(value: String): Boolean = boxes.any { normalize(value).contains(it) }
    fun isOpen(value: String): Boolean = open.any { normalize(value) == it }

    fun isLiveScreen(values: List<String>): Boolean {
        val normalized = values.asSequence().take(400).map(::normalize).toList()
        val live = normalized.any { it == "live" || it == "مباشر" || it == "بث مباشر" || it == "canlı" || it == "canli" || it.contains("live viewers") }
        val contextual = normalized.any { text -> context.any(text::contains) }
        return live && contextual
    }

    /** Only use near a box candidate. Never treat an arbitrary video timestamp as a box timer. */
    fun timeSeconds(values: List<String>): Int? = values.asSequence().take(400)
        .map(::normalize)
        .mapNotNull { text ->
            val match = timer.find(text)
            if (match != null) {
                val hours = match.groupValues[1].toIntOrNull() ?: 0
                val mins = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val sec = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                (hours * 3600 + mins * 60 + sec).takeIf { it in 0..600 }
            } else seconds.find(text)?.groupValues?.get(1)?.toIntOrNull()
        }.minOrNull()
}
