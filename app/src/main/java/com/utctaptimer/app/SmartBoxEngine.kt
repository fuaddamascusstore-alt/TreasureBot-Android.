package com.utctaptimer.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * A user-started, on-device vision assistant for TikTok LIVE treasure-box dialogs.
 * No network requests, stream search, CAPTCHA handling, repeated clicking, or reward claim guarantee.
 * Autonomous gestures are gated on a visible box, a detected countdown and a verified button transition.
 */
class SmartBoxEngine(private val service: AccessibilityService,
                     private val minimumCoins: Int,
                     private val autoOpenIcon: Boolean,
                     private val autoTap: Boolean,
                     private val tapDurationMs: Int,
                     private val finished: () -> Unit) {
    private enum class Phase { SEARCHING, OPENING, COUNTING, COMPLETED }
    private var phase = Phase.SEARCHING
    private val handler = Handler(Looper.getMainLooper())
    private val window = service.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var ui: LinearLayout? = null
    private var timerUi: TextView? = null
    private var statusUi: TextView? = null
    private var startNs = SystemClock.elapsedRealtimeNanos()
    private var lastTimerNs = 0L
    private var lastTimerSeconds: Int? = null
    private var firstTimerSeen = false
    private var missingNearZero = 0
    private var frames = 0
    private var coins: Int? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var pending = false
    private var stopped = false
    private var iconClicked = false
    private var tapped = false
    private var lastOpenAttemptNs = 0L
    private var expectedEndNs = 0L
    private var lastEventPackage: String? = null
    private val securePackages = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    private val onScan = Runnable { grab() }
    private val onUiTick = object: Runnable {
        override fun run() {
            if (stopped) return
            if (phase == Phase.COUNTING && expectedEndNs != 0L) {
                val ms = max(0L, (expectedEndNs - SystemClock.elapsedRealtimeNanos()) / 1_000_000L)
                timerUi?.text = "≈" + String.format(Locale.US,"%02d:%02d.%03d", ms / 60000, ms / 1000 % 60, ms % 1000)
            }
            handler.postDelayed(this, 50L)
        }
    }
    private val dp get() = service.resources.displayMetrics.density
    private fun pixels(v: Int) = (v * dp + 0.5f).toInt()
    private val viewParams: WindowManager.LayoutParams get() = WindowManager.LayoutParams(
        pixels(190), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.START or Gravity.TOP; x = 5; y = pixels(300)
    }

    fun start() {
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pixels(9), pixels(8), pixels(9), pixels(8))
            background = GradientDrawable().apply { setColor(0xe8112435.toInt()); cornerRadius = dp * 15 }
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val title = TextView(service).apply { text = "صندوق الكنز الذكي"; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        root.addView(title)
        timerUi = TextView(service).apply {
            text = "—"; textSize = 23f; typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER; setTextColor(0xffa4f5dd.toInt())
        }
        root.addView(timerUi)
        statusUi = TextView(service).apply {
            text = "افتح بث تيك توك"; textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        }
        root.addView(statusUi)
        root.addView(Button(service).apply {
            text = "إيقاف فوري"; isAllCaps = false; textSize = 12f
            setOnClickListener { stop("إيقاف المستخدم") }
        })
        try { window.addView(root, viewParams); ui = root }
        catch (e: Exception) { stop("تعذر عرض لوحة المراقبة"); return }
        handler.post(onUiTick)
        schedule(350)
    }
    fun onEvent(event: AccessibilityEvent?) {
        if (stopped || event == null) return
        event.packageName?.toString()?.let { lastEventPackage = it }
        if (phase != Phase.COUNTING || tapped || !autoTap) return
        if (!isTikTokActive()) return
        val root = service.rootInActiveWindow ?: return
        val now = SystemClock.elapsedRealtimeNanos()
        if (lastTimerSeconds == null || lastTimerSeconds!! > 1 || now - lastTimerNs > 2_800_000_000L) return
        if (findActionNode(root, 0) != null) {
            // An accessibility text alone is insufficient: the screenshot loop also verifies
            // that the observed button area remains present before issuing a gesture.
            accessibleOpenSeen = true
        }
    }
    private var accessibleOpenSeen = false
    private fun findActionNode(node: AccessibilityNodeInfo?, depth: Int): Rect? {
        if (node == null || depth > 12) return null
        val label = listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" ")
        val rect = Rect().also { node.getBoundsInScreen(it) }
        if (SmartLogic.hasOpenText(label) && node.isClickable && rect.width() > 35 && rect.centerY() > service.resources.displayMetrics.heightPixels * .83f) return rect
        for (i in 0 until min(40, node.childCount)) {
            val child = node.getChild(i) ?: continue
            val found = findActionNode(child, depth + 1)
            child.recycle()
            if (found != null) return found
        }
        return null
    }
    private fun isTikTokActive(): Boolean {
        val current = service.rootInActiveWindow?.packageName?.toString()
        return if (current != null) current in securePackages else lastEventPackage in securePackages
    }
    private fun schedule(delayMs: Long) {
        if (stopped) return
        handler.removeCallbacks(onScan)
        handler.postDelayed(onScan, delayMs)
    }
    private fun report(s: String) { statusUi?.text = s }
    private fun grab() {
        if (stopped || pending) return
        if (SystemClock.elapsedRealtimeNanos() - startNs > 5L * 60L * 1_000_000_000L) { stop("انتهت جلسة الخمس دقائق"); return }
        if (!isTikTokActive()) { report("افتح بث تيك توك"); schedule(1000); return }
        pending = true
        service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    if (stopped) { pending = false; return }
                    var copy: Bitmap? = null
                    try {
                        val hw = result.hardwareBuffer
                        val bitmap = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                        copy = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        bitmap?.recycle()
                        hw.close()
                    } catch (_: Throwable) { }
                    if (copy == null) { pending = false; schedule(900); return }
                    process(copy)
                }
                override fun onFailure(errorCode: Int) {
                    pending = false
                    if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                        schedule(750)
                    } else {
                        report("تعذر التقاط الشاشة ($errorCode)")
                        schedule(1250)
                    }
                }
            })
    }
    private fun process(bitmap: Bitmap) {
        val w = bitmap.width; val h = bitmap.height
        lastWidth = w; lastHeight = h
        if (w < 300 || h < 600) { bitmap.recycle(); pending = false; schedule(1100); return }
        val buttonPink = hasPinkBar(bitmap)
        // Read the large timer in the lower 20% of the modal; ignore LIVE battle timers.
        val lower = crop(bitmap, 0.05, 0.79, 0.95, 0.94, 2)
        if (lower == null) { bitmap.recycle(); pending = false; schedule(800); return }
        recognizer.process(InputImage.fromBitmap(lower, 0))
            .addOnSuccessListener { result ->
                val seconds = timerFrom(result)
                handleLower(seconds, result.text, buttonPink, bitmap)
            }
            .addOnFailureListener { report("تعذر قراءة الأرقام"); finishFrame(bitmap, 850) }
            .addOnCompleteListener { lower.recycle() }
    }
    private fun handleLower(seconds: Int?, rawText: String, pink: Boolean, bitmap: Bitmap) {
        if (stopped) { finishFrame(bitmap, 900); return }
        val now = SystemClock.elapsedRealtimeNanos()
        if (pink && seconds != null && seconds in 0..3600) {
            if (phase != Phase.COUNTING) {
                firstTimerSeen = true
                phase = Phase.COUNTING
                report("تم اكتشاف عداد الصندوق")
            }
            if (lastTimerSeconds == null || seconds != lastTimerSeconds) {
                lastTimerSeconds = seconds
                lastTimerNs = now
                // An approximate visual alignment, not millisecond-precise server time.
                expectedEndNs = now + seconds.toLong() * 1_000_000_000L
            }
            missingNearZero = 0
            if (coins == null && minimumCoins > 0) { readCoins(bitmap) }
            else finishFrame(bitmap, if (seconds <= 3) 370 else 740)
            return
        }
        if (phase == Phase.COUNTING && lastTimerSeconds != null) {
            val lastMs = (now - lastTimerNs) / 1_000_000
            if (lastTimerSeconds!! <= 1 && pink) missingNearZero++ else missingNearZero = 0
            if ((accessibleOpenSeen && pink && lastTimerSeconds!! <= 1 && lastMs >= (if (lastTimerSeconds == 1) 850 else 450) && lastMs <= 2800) ||
                SmartLogic.canTapAfterTransition(lastTimerSeconds, lastMs, missingNearZero, pink, autoTap, tapped)) {
                if (minimumCoins > 0 && (coins == null || coins!! < minimumCoins)) {
                    report("لا ضغط: لم نتأكد أن العملات ≥ $minimumCoins")
                } else if (autoTap && !tapped) {
                    tapped = true
                    phase = Phase.COMPLETED
                    val x = bitmap.width * .50f
                    val y = bitmap.height * .883f
                    report("ظهر زر الفتح — إرسال ضغطة واحدة")
                    finishFrame(bitmap, 0, rescan = false)
                    sendGesture(x, y, "زر فتح الصندوق")
                    return
                }
            }
            if (lastMs > 3100 && lastTimerSeconds!! <= 1) {
                phase = Phase.COMPLETED
                report("لم يُؤكد زر الفتح — لن نضغط")
                finishFrame(bitmap, 0, rescan = false)
                return
            }
        }
        // Look for the small gift countdown in the top-right only if there is no verified dialog.
        if (phase == Phase.SEARCHING && autoOpenIcon && !iconClicked && giftColors(bitmap)) {
            scanTopIcon(bitmap)
            return
        }
        if (phase == Phase.OPENING && now - lastOpenAttemptNs > 5_000_000_000L) {
            phase = Phase.SEARCHING
            report("تعذر التحقق من النافذة — افتح الصندوق يدوياً")
        } else if (phase == Phase.SEARCHING) report("البحث عن عداد الصندوق…")
        finishFrame(bitmap, if (phase == Phase.COUNTING) 420 else 800)
    }
    private fun scanTopIcon(bitmap: Bitmap) {
        val top = crop(bitmap, .77, .115, .99, .26, 3)
        if (top == null) { finishFrame(bitmap, 900); return }
        recognizer.process(InputImage.fromBitmap(top, 0))
            .addOnSuccessListener { text ->
                if (phase == Phase.SEARCHING && timerFrom(text) != null && giftColors(bitmap)) {
                    iconClicked = true; phase = Phase.OPENING
                    lastOpenAttemptNs = SystemClock.elapsedRealtimeNanos()
                    report("صندوق محتمل — فتح التفاصيل")
                    val tx = bitmap.width * .90f
                    val ty = bitmap.height * .17f
                    finishFrame(bitmap, 0, rescan = false)
                    sendGesture(tx, ty, "أيقونة الصندوق")
                    schedule(850)
                } else finishFrame(bitmap, 850)
            }
            .addOnFailureListener { finishFrame(bitmap, 900) }
            .addOnCompleteListener { top.recycle() }
    }
    private fun readCoins(bitmap: Bitmap) {
        val roi = crop(bitmap, .22, .60, .77, .77, 2)
        if (roi == null) { finishFrame(bitmap, 800); return }
        recognizer.process(InputImage.fromBitmap(roi, 0))
            .addOnSuccessListener { text ->
                val numbers = Regex("(?<![0-9])[0-9]{1,6}(?![0-9])").findAll(text.text.replace(",", ""))
                    .mapNotNull { it.value.toIntOrNull() }.filter { it > 0 }.toList()
                if (numbers.isNotEmpty()) coins = numbers.maxOrNull()
                if (coins != null) report("العداد معروف · العملات المقروءة: $coins")
                else report("العداد معروف · قيمة العملات غير مؤكدة")
                finishFrame(bitmap, 740)
            }
            .addOnFailureListener { finishFrame(bitmap, 850) }
            .addOnCompleteListener { roi.recycle() }
    }
    private fun timerFrom(text: Text): Int? {
        val candidates = text.textBlocks.flatMap { it.lines }.mapNotNull { SmartLogic.seconds(it.text) }
        return candidates.firstOrNull { it in 0..3600 } ?: SmartLogic.seconds(text.text)
    }
    private fun crop(source: Bitmap, left: Double, top: Double, right: Double, bottom: Double, scale: Int): Bitmap? {
        return try {
            val l = (source.width * left).toInt().coerceIn(0, source.width-1)
            val t = (source.height * top).toInt().coerceIn(0, source.height-1)
            val w = ((right-left) * source.width).toInt().coerceIn(1, source.width-l)
            val h = ((bottom-top) * source.height).toInt().coerceIn(1, source.height-t)
            val cropped = Bitmap.createBitmap(source, l, t, w, h)
            val enlarged = Bitmap.createScaledBitmap(cropped, w*scale, h*scale, false)
            if (cropped !== enlarged) cropped.recycle()
            enlarged
        } catch (_: Exception) { null }
    }
    private fun hasPinkBar(bm: Bitmap): Boolean {
        // Background-color evidence prevents accidental tapping on unrelated LIVE labels.
        val y = (bm.height * .883).toInt().coerceIn(0, bm.height-1)
        var samples = 0; var pink = 0
        for (i in 15..85 step 3) {
            val x = (bm.width * i / 100).coerceIn(0, bm.width-1)
            val c = bm.getPixel(x, y)
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            samples++
            if (r > 175 && g in 65..210 && b in 70..220 && r > g + 32 && r > b + 18) pink++
        }
        return pink * 100 / max(1, samples) >= 55
    }
    private fun giftColors(bm: Bitmap): Boolean {
        var yellow = 0; var reddish = 0
        val x1 = (bm.width * .79).toInt(); val x2 = (bm.width * .97).toInt()
        val y1 = (bm.height * .13).toInt(); val y2 = (bm.height * .22).toInt()
        for (y in y1 until y2 step 4) for (x in x1 until x2 step 4) {
            val c = bm.getPixel(x, y); val r=Color.red(c); val g=Color.green(c); val b=Color.blue(c)
            if (r > 170 && g > 105 && b < 160 && r > b + 60) yellow++
            if (r > 140 && g < 130 && b < 150 && r > g + 50) reddish++
        }
        return yellow >= 8 && reddish >= 7
    }
    private fun sendGesture(x: Float, y: Float, purpose: String) {
        if (stopped) return
        // Hide the overlay temporarily so its window does not steal the touch.
        ui?.let { try { window.removeView(it) } catch (_: Exception) {} }
        val old = ui
        ui = null
        val p = Path().apply { moveTo(x,y) }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(p, 0L, tapDurationMs.coerceIn(1,1000).toLong())).build()
        val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { handler.post { report("أُرسلت ضغطة $purpose (النتيجة غير مضمونة)") } }
            override fun onCancelled(gestureDescription: GestureDescription?) { handler.post { report("ألغى أندرويد ضغطة $purpose") } }
        }, handler)
        handler.postDelayed({
            if (!stopped && old != null && ui == null) {
                try { window.addView(old, viewParams); ui = old } catch (_: Exception) {}
            }
            if (!accepted) report("رفض النظام إرسال الضغطة")
        }, 400L)
    }
    private fun finishFrame(bitmap: Bitmap, intervalMs: Long, rescan: Boolean = true) {
        bitmap.recycle()
        frames++
        pending = false
        if (rescan && !stopped) schedule(intervalMs)
    }
    fun stop(reason: String) {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        try { ui?.let { window.removeView(it) } } catch (_: Exception) {}
        ui = null
        try { recognizer.close() } catch (_: Exception) {}
        finished()
    }
}

