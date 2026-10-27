import com.treasurewatch.bot.Rules
import com.treasurewatch.bot.SessionGuard
import com.treasurewatch.bot.SessionLimits

/** Offline check: kotlinc Rules.kt SessionGuard.kt tools/LocalChecks.kt -include-runtime -d /tmp/check.jar && java -jar /tmp/check.jar */
fun main() {
    fun requireCheck(value: Boolean, message: String) = check(value) { message }
    requireCheck(Rules.isBox("صندوق الكنز"), "arabic box")
    requireCheck(Rules.isBox("Hazine Kutusu"), "turkish box")
    requireCheck(Rules.isBox("Treasure Box"), "english box")
    requireCheck(!Rules.isBox("welcome to live"), "box false positive")
    requireCheck(Rules.timeSeconds(listOf("٠١:٢٣")) == 83, "arabic digits")
    requireCheck(Rules.timeSeconds(listOf("12 seconds")) == 12, "second unit")
    requireCheck(Rules.timeSeconds(listOf("00:10:00")) == 600, "hh:mm:ss")
    requireCheck(Rules.timeSeconds(listOf("12:59:59")) == null, "reject large countdown")
    requireCheck(!Rules.isLiveScreen(listOf("LIVE")), "live context requirement")
    requireCheck(Rules.isLiveScreen(listOf("LIVE", "Send a gift")), "live context")
    val g = SessionGuard(SessionLimits(8, 2, 5), 1_000L)
    requireCheck(g.recordBox() && !g.recordBox(), "dedupe")
    requireCheck(!g.scanExpired(8_999L) && g.scanExpired(9_000L), "scan bound")
    requireCheck(g.nextRoom(9_000L), "next room")
    requireCheck(g.recordBox(), "room reset")
    requireCheck(!g.nextRoom(12_000L), "room limit")
    requireCheck(g.timedOut(301_000L), "session limit")
    println("PASS: 16 offline Kotlin assertions")
}
