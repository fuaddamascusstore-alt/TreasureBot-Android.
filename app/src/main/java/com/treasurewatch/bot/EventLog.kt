package com.treasurewatch.bot

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** On-device capped history. No screen text, usernames, screenshot bytes or network transport. */
object EventLog {
    private const val KEY = "events"
    private const val MAX = 120
    private val delimiter = "\n"

    fun append(context: Context, event: String) {
        val clean = event.replace('\n', ' ').replace('\r', ' ').take(180)
        val time = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date())
        val pref = context.getSharedPreferences("history", Context.MODE_PRIVATE)
        val previous = pref.getString(KEY, "").orEmpty().lineSequence().filter(String::isNotBlank).toList()
        pref.edit().putString(KEY, (listOf("$time — $clean") + previous).take(MAX).joinToString(delimiter)).apply()
    }

    fun read(context: Context): List<String> = context.getSharedPreferences("history", Context.MODE_PRIVATE)
        .getString(KEY, "").orEmpty().lineSequence().filter(String::isNotBlank).toList()

    fun clear(context: Context) {
        context.getSharedPreferences("history", Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
