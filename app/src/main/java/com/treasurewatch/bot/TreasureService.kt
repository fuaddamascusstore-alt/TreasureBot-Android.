package com.treasurewatch.bot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * User-directed, read-only TikTok LIVE observer.
 * Does NOT scroll streams, click boxes, redeem coins, use private APIs or bypass prompts.
 */
class TreasureService : AccessibilityService() {
    companion object {
        const val PACKAGE = "com.zhiliaoapp.musically"
        @Volatile var current: TreasureService? = null
            private set
        @Volatile var status = "خدمة الوصول غير مفعّلة"
            private set
        @Volatile var active = false
            private set
        @Volatile var visited = 0
            private set
        @Volatile var found = 0
            private set
        @Volatile var countdown: Int? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val matcher = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "TreasureImageMatch").apply { isDaemon = true } }
    private var guard: SessionGuard? = null
    private var lastInTikTok = 0L
    private var errors = 0
    private var token = 0L
    private var screenshotInFlight = false
    private var lastScreenshotAt = 0L
    private var lastImageHit: VisualMatcher.Hit? = null
    private var lastImageHitAt = 0L
    private var overlay: View? = null
    private var overlayStatus: TextView? = null
    private val ticker = object : Runnable {
        override fun run() {
            if (!active) return
            try { tick() ; errors = 0 }
            catch (error: Exception) {
                errors++
                if (errors >= 5) stopBot("توقف آمن بعد أخطاء متكررة")
                else setStatus("تعذّر فحص الشاشة ($errors/5)", true)
            }
            if (active) handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        current = this
        if (!active) status = "خدمة الوصول جاهزة؛ الموافقة والتشغيل من التطبيق"
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = stopBot("انقطعت خدمة الوصول")
    override fun onDestroy() {
        stopBot("توقفت خدمة الوصول")
        matcher.shutdownNow()
        current = null
        super.onDestroy()
    }

    fun startBot(limits: SessionLimits) {
        stopBot("إعادة تعيين الجلسة")
        val now = SystemClock.elapsedRealtime()
        guard = SessionGuard(limits, now)
        visited = 1
        found = 0
        countdown = null
        lastInTikTok = now
        errors = 0
        lastImageHit = null
        lastScreenshotAt = 0L
        active = true
        setStatus("المراقبة مفعّلة؛ افتح بث LIVE يدوياً", true)
        showOverlay()
        handler.post(ticker)
    }

    fun stopBot(message: String = "تم الإيقاف فوراً") {
        token++ // Invalidates all pending screenshot callbacks.
        active = false
        screenshotInFlight = false
        countdown = null
        lastImageHit = null
        handler.removeCallbacks(ticker)
        removeOverlay()
        if (status != message) setStatus(message, true)
    }

    /** Called only from an explicit user click, not inferred swipes or background gestures. */
    fun nextRoom() {
        if (!active) return
        val g = guard ?: return
        val now = SystemClock.elapsedRealtime()
        if (g.timedOut(now)) { stopBot("انتهت مدة الجلسة"); return }
        if (g.atRoomLimit()) { stopBot("وصلت إلى الحد الأقصى من البثوث"); return }
        if (g.nextRoom(now)) {
            visited = g.rooms
            countdown = null
            lastImageHit = null
            setStatus("البث $visited/${g.limits.maxRooms} — انتقل يدوياً في TikTok", true)
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val g = guard ?: return
        if (g.timedOut(now)) { stopBot("انتهت مدة الجلسة المحددة"); return }
        if (now - lastInTikTok > 90_000L) { stopBot("توقف آمن: TikTok ليس في المقدمة منذ 90 ثانية"); return }
        val root = rootInActiveWindow
        if (root?.packageName?.toString() != PACKAGE) {
            setStatus("افتح TikTok وادخل إلى بث LIVE", false)
            return
        }
        lastInTikTok = now
        if (g.scanExpired(now)) {
            countdown = null
            setStatus("انتهى فحص هذا البث؛ بدّل البث يدوياً ثم اضغط «بث جديد»", false)
            return
        }
        val texts = collectTexts(root)
        val hasBox = texts.any(Rules::isBox)
        val live = Rules.isLiveScreen(texts)
        if (hasBox) onBox("ظهر نص يشير إلى صندوق كنز")
        if (hasBox) {
            countdown = Rules.timeSeconds(texts)
            setStatus(if (countdown != null) "صندوق محتمل — العد التنازلي ${countdown} ثانية" else "صندوق محتمل — لا يوجد عداد مقروء", false)
        } else if (live) {
            countdown = null
            setStatus("مراقبة LIVE — ${g.scanRemaining(now)} ثانية متبقية للفحص", false)
        } else {
            countdown = null
            setStatus("لم أتأكد من شاشة LIVE؛ افتح البث يدوياً", false)
        }
        if (!hasBox && now - lastScreenshotAt >= 3500L) scanScreenshot(now)
    }

    private fun collectTexts(root: AccessibilityNodeInfo): List<String> {
        val result = ArrayList<String>(128)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var total = 0
        while (queue.isNotEmpty() && total < 500) {
            val node = queue.removeFirst()
            total++
            node.text?.toString()?.takeIf(String::isNotBlank)?.let(result::add)
            node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(result::add)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return result
    }

    private fun onBox(reason: String) {
        if (guard?.recordBox() == true) {
            found++
            setStatus("$reason — يتطلب التحقق اليدوي", true)
        }
    }

    private fun scanScreenshot(now: Long) {
        if (screenshotInFlight) return
        val templateFile = File(filesDir, "box_template.png")
        if (!templateFile.isFile) return
        screenshotInFlight = true
        lastScreenshotAt = now
        val capturedToken = token
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, java.util.concurrent.Executor { runnable -> handler.post(runnable) },
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        try {
                            if (!active || capturedToken != token) return
                            val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return
                            val screen = (try { wrapped.copy(Bitmap.Config.ARGB_8888, false) }
                                finally { wrapped.recycle() }) ?: return
                            val pattern = BitmapFactory.decodeFile(templateFile.absolutePath)
                            if (pattern == null) { screen.recycle(); return }
                            matcher.execute {
                                val hit = try { VisualMatcher.search(screen, pattern, .03, .82) }
                                    catch (_: Exception) { null }
                                    finally { screen.recycle(); pattern.recycle() }
                                handler.post { handleImageHit(hit, capturedToken) }
                            }
                        } catch (_: Exception) { /* screenshot can be protected or unavailable */ }
                        finally { screenshotInFlight = false; buffer.close() }
                    }
                    override fun onFailure(errorCode: Int) { screenshotInFlight = false }
                })
        } catch (_: Exception) { screenshotInFlight = false }
    }

    private fun handleImageHit(hit: VisualMatcher.Hit?, capturedToken: Long) {
        if (!active || token != capturedToken) return
        if (hit == null) { lastImageHit = null; return }
        val now = SystemClock.elapsedRealtime()
        val prior = lastImageHit
        val confirmed = prior != null && now - lastImageHitAt <= 8000L &&
            abs(prior.x - hit.x) <= 35 && abs(prior.y - hit.y) <= 35
        lastImageHit = hit
        lastImageHitAt = now
        if (confirmed) onBox("تطابقت صورة الصندوق في لقطتين متتاليتين")
    }

    private fun setStatus(message: String, writeLog: Boolean) {
        status = message
        overlayStatus?.text = if (message.length > 44) message.take(42) + "…" else message
        if (writeLog) EventLog.append(this, message)
    }

    private fun overlayButton(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply { setColor(color); cornerRadius = 20f }
        setOnClickListener { action() }
        minHeight = 0; minimumHeight = 0
    }

    private fun showOverlay() {
        if (overlay != null) return
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 8, 10, 8)
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 18, 32, 53)); cornerRadius = 20f
            }
        }
        overlayStatus = TextView(this).apply {
            text = "Treasure Watch — مراقبة فقط"
            textSize = 11f
            setTextColor(Color.WHITE)
            maxLines = 2
        }
        layout.addView(overlayStatus)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(overlayButton("بث جديد", Color.rgb(12, 137, 136)) { nextRoom() }, LinearLayout.LayoutParams(0, 43, 1f))
        row.addView(overlayButton("إيقاف فوري", Color.rgb(173, 56, 61)) { stopBot() }, LinearLayout.LayoutParams(0, 43, 1f))
        layout.addView(row)
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * .76f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = (resources.displayMetrics.density * 36).toInt() }
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(layout, params)
            overlay = layout
        } catch (_: Exception) { EventLog.append(this, "تعذر عرض زر الإيقاف العائم؛ الإيقاف متاح من التطبيق") }
    }

    private fun removeOverlay() {
        val target = overlay ?: return
        overlay = null
        overlayStatus = null
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(target) } catch (_: Exception) { }
    }
}
