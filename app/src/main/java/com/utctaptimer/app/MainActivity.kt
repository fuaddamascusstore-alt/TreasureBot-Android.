package com.utctaptimer.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

private const val STORE = "tap_timer"
private const val NAVY = -15589316
private const val TEAL = -16742144
private fun background(color: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = 18f }
private fun clockText(millis: Long): String {
    val m = max(0L, millis)
    return String.format(Locale.US, "%02d:%02d.%03d", m / 60000L, m / 1000L % 60L, m % 1000L)
}
private fun utcText(epoch: Long): String = SimpleDateFormat("HH:mm:ss.SSS 'UTC'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date(epoch))

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(STORE, MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xfff3f6fa.toInt())
            setPadding(28, 28, 28, 24)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        fun label(text: String): TextView = TextView(this).apply {
            this.text = text; textSize = 17f; setTextColor(NAVY); setPadding(0, 14, 0, 10)
        }
        root.addView(label("عداد UTC الذكي").apply { textSize = 25f })
        root.addView(label("عداد يدوي عائم وضغطة واحدة في الوقت المحدد"))
        fun field(title: String, value: String): EditText {
            root.addView(label(title))
            val e = EditText(this).apply {
                setText(prefs.getString(title, value))
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                textSize = 23f
                setSingleLine(true)
            }
            root.addView(e)
            return e
        }
        val seconds = field("مدة العد بالثواني", "40")
        val remaining = field("الضغطة عند بقاء (ثانية، مثل 0.500)", "0.500")
        val stroke = field("مدة الضغطة (ms، مثل 1)", "1")
        fun btn(text: String, block: () -> Unit) {
            val b = Button(this).apply { this.text = text; isAllCaps = false; textSize = 17f }
            root.addView(b)
            b.setOnClickListener { block() }
        }
        btn("1. تفعيل خدمة اللمس من إعدادات الهاتف") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btn("2. عرض العداد فوق الشاشة") {
            val s = seconds.text.toString().toDoubleOrNull()
            val r = remaining.text.toString().toDoubleOrNull()
            val d = stroke.text.toString().toDoubleOrNull()?.toInt()
            if (s == null || r == null || d == null || s <= 0.0 || s > 3600.0 || r < 0.0 || r >= s || d !in 1..1000) {
                Toast.makeText(this, "القيم غير صالحة", Toast.LENGTH_LONG).show()
                return@btn
            }
            prefs.edit().putString("مدة العد بالثواني", seconds.text.toString())
                .putString("الضغطة عند بقاء (ثانية، مثل 0.500)", remaining.text.toString())
                .putString("مدة الضغطة (ms، مثل 1)", stroke.text.toString()).apply()
            val service = TapService.active
            if (service == null) {
                Toast.makeText(this, "فعّل خدمة UTC Tap Timer من إمكانية الوصول", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                service.openPanel((s * 1000).toLong(), (r * 1000).toLong(), d)
                moveTaskToBack(true)
            }
        }
        root.addView(label("اختر نقطة الضغط من النافذة العائمة أولاً، افتح التطبيق الذي تريده، ثم اضغط ابدأ. توقيت 1ms المطلوب ليس مضموناً من أندرويد. لا يتم جمع بيانات."))
    }
}

class TapService : AccessibilityService() {
    companion object { @Volatile var active: TapService? = null; private set }
    private val handler = Handler(Looper.getMainLooper())
    private val manager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var panel: View? = null
    private var picker: View? = null
    private var count: TextView? = null
    private var status: TextView? = null
    private var start: Button? = null
    private var totalMs = 40000L
    private var remainingMs = 500L
    private var gestureMs = 1
    private var startNanos = 0L
    private var utcAtStart = 0L
    private var utcSource = ""
    private var running = false
    private var fired = false
    private var scheduled: Runnable? = null
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val elapsed = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1000000L
            count?.text = clockText(totalMs - elapsed)
            status?.text = utcText(utcAtStart + elapsed) + " · " + utcSource
            if (elapsed >= totalMs) {
                running = false
                start?.isEnabled = true
                count?.text = "00:00.000"
            } else handler.postDelayed(this, 16L)
        }
    }
    override fun onServiceConnected() { super.onServiceConnected(); active = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stop("أُلغيت العملية") }
    override fun onDestroy() { stop("أُغلقت الخدمة"); removePicker(); removePanel(); active = null; super.onDestroy() }
    private fun params(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.END; x = 12; y = 120 }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density + 0.5f).toInt()
    fun openPanel(total: Long, remaining: Long, duration: Int) {
        stop("جاهز")
        removePanel()
        totalMs = total; remainingMs = remaining; gestureMs = duration
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12)
            background = background(NAVY); layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val lp = params(300.dp, -2)
        val title = TextView(this).apply {
            text = "UTC TIMER • اسحب للتحريك"; setTextColor(Color.WHITE)
            textSize = 13f; gravity = Gravity.CENTER
        }
        root.addView(title)
        title.setOnTouchListener(object : View.OnTouchListener {
            var x = 0f; var y = 0f; var oldX = 0; var oldY = 0
            override fun onTouch(v: View, ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                    x = ev.rawX; y = ev.rawY; oldX = lp.x; oldY = lp.y
                } else if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
                    lp.x = max(0, oldX + (x - ev.rawX).toInt())
                    lp.y = max(0, oldY + (ev.rawY - y).toInt())
                    try { manager.updateViewLayout(root, lp) } catch (_: Exception) {}
                }
                return true
            }
        })
        count = TextView(this).apply {
            text = clockText(totalMs); setTextColor(Color.WHITE)
            textSize = 38f; gravity = Gravity.CENTER; typeface = android.graphics.Typeface.MONOSPACE
        }
        root.addView(count)
        status = TextView(this).apply {
            text = "حدد نقطة اللمس ثم ابدأ · " + clockText(remainingMs)
            setTextColor(0xff9bebdf.toInt()); textSize = 12f; gravity = Gravity.CENTER
        }
        root.addView(status)
        fun button(text: String, click: () -> Unit) = Button(this).apply {
            this.text = text; isAllCaps = false; textSize = 12f
            setOnClickListener { click() }
        }
        val row = LinearLayout(this)
        start = button("ابدأ", ::begin)
        row.addView(start, LinearLayout.LayoutParams(0, 48.dp, 1f))
        row.addView(button("إلغاء") { stop("أُلغي العد"); count?.text = clockText(totalMs) },
            LinearLayout.LayoutParams(0, 48.dp, 1f))
        root.addView(row)
        val row2 = LinearLayout(this)
        row2.addView(button("تحديد النقطة", ::pick), LinearLayout.LayoutParams(0, 48.dp, 1f))
        row2.addView(button("إغلاق") { stop("أُغلق"); removePanel() }, LinearLayout.LayoutParams(0, 48.dp, 1f))
        root.addView(row2)
        try { manager.addView(root, lp); panel = root }
        catch (_: Exception) { Toast.makeText(this, "تعذر إظهار العداد", Toast.LENGTH_SHORT).show() }
    }
    private fun pick() {
        if (running) { status?.text = "ألغِ العد قبل تحديد النقطة"; return }
        removePicker()
        val cover = FrameLayout(this).apply { setBackgroundColor(0x77000000) }
        val message = TextView(this).apply {
            text = "المس النقطة المستهدفة"; textSize = 23f
            setTextColor(Color.WHITE); setPadding(16, 100, 16, 0); gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        cover.addView(message)
        cover.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val x = event.rawX; val y = event.rawY
                getSharedPreferences(STORE, MODE_PRIVATE).edit().putFloat("x", x).putFloat("y", y).apply()
                removePicker()
                status?.text = "تم حفظ النقطة: " + x.toInt() + ", " + y.toInt()
            }
            true
        }
        try { manager.addView(cover, params(-1, -1).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0 }); picker = cover }
        catch (_: Exception) { status?.text = "تعذر اختيار النقطة" }
    }
    private fun begin() {
        val prefs = getSharedPreferences(STORE, MODE_PRIVATE)
        if (!prefs.contains("x") || !prefs.contains("y")) { status?.text = "حدد نقطة اللمس أولاً"; return }
        stop("بدء")
        val before = SystemClock.elapsedRealtimeNanos()
        val network = if (Build.VERSION.SDK_INT >= 33) {
            try { SystemClock.currentNetworkTimeClock().instant().toEpochMilli() }
            catch (_: Exception) { null }
        } else null
        val after = SystemClock.elapsedRealtimeNanos()
        startNanos = (before + after) / 2L
        utcAtStart = network ?: System.currentTimeMillis()
        utcSource = if (network != null) "UTC شبكة" else "UTC الجهاز غير متزامن"
        running = true; fired = false
        start?.isEnabled = false
        handler.post(tick)
        val delay = totalMs - remainingMs
        val trigger = object : Runnable {
            override fun run() {
                if (!running || fired) return
                val elapsed = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1000000L
                if (elapsed < delay) handler.postDelayed(this, max(1L, delay - elapsed))
                else { fired = true; sendGesture(elapsed - delay) }
            }
        }
        scheduled = trigger
        handler.postDelayed(trigger, delay)
    }
    private fun sendGesture(late: Long) {
        val prefs = getSharedPreferences(STORE, MODE_PRIVATE)
        val x = prefs.getFloat("x", -1f)
        val y = prefs.getFloat("y", -1f)
        if (x < 0 || y < 0) { stop("إحداثيات غير صالحة"); return }
        val oldPanel = panel
        if (oldPanel != null) { try { manager.removeView(oldPanel) } catch (_: Exception) {}; panel = null }
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0L, gestureMs.toLong())
        ).build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                handler.post { status?.text = "تم إرسال اللمس · تأخير الجدولة " + late + "ms" }
            }
            override fun onCancelled(g: GestureDescription?) {
                handler.post { status?.text = "ألغى النظام اللمس" }
            }
        }, handler)
        if (!accepted) status?.text = "النظام رفض الإيماءة"
        handler.postDelayed({
            if (panel == null && oldPanel != null) {
                try { manager.addView(oldPanel, params(300.dp, -2)); panel = oldPanel }
                catch (_: Exception) {}
            }
        }, max(350L, gestureMs.toLong() + 120L))
    }
    private fun stop(text: String) {
        running = false; handler.removeCallbacks(tick)
        scheduled?.let { handler.removeCallbacks(it) }
        scheduled = null; start?.isEnabled = true; status?.text = text
    }
    private fun removePanel() { panel?.let { try { manager.removeView(it) } catch (_: Exception) {} }; panel = null; status = null; count = null; start = null }
    private fun removePicker() { picker?.let { try { manager.removeView(it) } catch (_: Exception) {} }; picker = null }
}