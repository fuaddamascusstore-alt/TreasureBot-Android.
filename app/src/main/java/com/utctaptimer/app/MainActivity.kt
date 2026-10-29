package com.utctaptimer.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.Typeface
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
import android.widget.CheckBox
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

private const val PREFS = "utc_tap_timer"
private const val NAVY = 0xff102238.toInt()
private const val TEAL = 0xff008b8b.toInt()
private fun bg(color: Int, radius: Float = 18f) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
private fun formatted(ms: Long): String {
    val safe = max(0L, ms)
    return "%02d:%02d.%03d".format(Locale.US, safe / 60000, (safe / 1000) % 60, safe % 1000)
}
private fun utc(epoch: Long): String = SimpleDateFormat("HH:mm:ss.SSS 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epoch))

class MainActivity : Activity() {
    private lateinit var seconds: EditText
    private lateinit var target: EditText
    private lateinit var duration: EditText
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = NAVY
        val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(26, 28, 26, 20); setBackgroundColor(0xfff2f5f8.toInt()); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(root)
        setContentView(scroll)
        fun label(s: String, size: Float, color: Int = NAVY) = TextView(this).apply { text = s; textSize = size; setTextColor(color); setPadding(0, 10, 0, 10) }
        root.addView(label("UTC SMART TAP", 24f).apply { typeface = Typeface.DEFAULT_BOLD })
        root.addView(label("عداد يدوي + مراقب صناديق الكنز محلياً", 14f))
        fun field(title: String, default: String): EditText {
            root.addView(label(title, 15f))
            return EditText(this).also {
                it.setText(preferences.getString(title, default))
                it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                it.textSize = 22f
                it.setSingleLine(true)
                it.setSelectAllOnFocus(true)
                it.setBackgroundColor(Color.WHITE)
                it.setPadding(18, 14, 18, 14)
                root.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
            }
        }
        seconds = field("مدة العداد بالثواني", "40")
        target = field("اضغط عندما يتبقى (ثانية) — مثال 0.500", "0.500")
        duration = field("مدة اللمسة (ميلي ثانية) — مثال 1", "1")
        fun button(text: String, action: () -> Unit) {
            val b = Button(this).apply { this.text = text; textSize = 16f; isAllCaps = false; setTextColor(Color.WHITE); background = bg(TEAL) }
            root.addView(b, LinearLayout.LayoutParams(-1, 58).apply { topMargin = 12 })
            b.setOnClickListener { action() }
        }
        button("1 — تفعيل خدمة اللمس من إعدادات الهاتف") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("2 — إظهار العداد العائم") {
            val s = seconds.text.toString().toDoubleOrNull()
            val t = target.text.toString().toDoubleOrNull()
            val d = duration.text.toString().toIntOrNull()
            if (s == null || t == null || d == null || s <= 0 || s > 3600 || t < 0 || t >= s || d !in 1..1000) {
                Toast.makeText(this, "قيم غير صحيحة: مدة 1–3600 ثانية، ووقت الضغطة أقل من المدة، ولمسة 1–1000ms", Toast.LENGTH_LONG).show()
                return@button
            }
            preferences.edit().putString("مدة العداد بالثواني", seconds.text.toString())
                .putString("اضغط عندما يتبقى (ثانية) — مثال 0.500", target.text.toString())
                .putString("مدة اللمسة (ميلي ثانية) — مثال 1", duration.text.toString()).apply()
            val svc = TapService.active
            if (svc == null) {
                Toast.makeText(this, "فعّل خدمة UTC Tap Timer في إمكانية الوصول أولاً", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                svc.openPanel((s * 1000).toLong(), (t * 1000).toLong(), d)
                moveTaskToBack(true)
            }
        }
        status = label("", 13f)
        root.addView(status)
        root.addView(label("الوضع الذكي: يكشف عداد الصندوق من شاشة TikTok، ويراقب تبدّل زر الفتح. القراءة والضغط غير مضمونين ولا يعتمد النظام على UTC لتوقيت تيك توك.", 14f))
        root.addView(label("الحد الأدنى للعملات (0 = أي صندوق)", 15f))
        val minCoins = EditText(this).apply { setText(preferences.getInt("minCoins", 0).toString()); inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(true); textSize = 22f }
        root.addView(minCoins)
        val autoIcon = CheckBox(this).apply { text = "فتح نافذة تفاصيل الصندوق تلقائياً إذا تم كشف أيقونته"; isChecked = true }
        root.addView(autoIcon)
        val autoTap = CheckBox(this).apply { text = "أوافق على ضغطة واحدة عند التأكد بصرياً من ظهور زر الفتح"; isChecked = false }
        root.addView(autoTap)
        root.addView(label("تنبيه: إذن إمكانية الوصول يسمح بقراءة الشاشة وتنفيذ لمسة. لا نسجل صوراً أو نرسلها لخادم. التشغيل داخل تيك توك فقط، والجلسة 5 دقائق كحد أقصى.", 13f))
        button("3 — ابدأ مراقبة صندوق الكنز الذكية") {
            val min = minCoins.text.toString().toIntOrNull()
            val d = duration.text.toString().toIntOrNull()
            if (min == null || min !in 0..1000000 || d == null || d !in 1..1000) {
                Toast.makeText(this, "تحقق من العملات ومدة اللمس", Toast.LENGTH_LONG).show()
                return@button
            }
            val svc = TapService.active
            if (svc == null) {
                Toast.makeText(this, "فعّل خدمة إمكانية الوصول أولاً", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                preferences.edit().putInt("minCoins", min).apply()
                svc.openSmartPanel(min, autoIcon.isChecked, autoTap.isChecked, d)
                moveTaskToBack(true)
            }
        }
        root.addView(label("لتجربة الوضع الذكي، افتح بثاً به صندوق. إذا تعذر كشف الأيقونة افتح تفاصيل الصندوق يدوياً. اختبر أولاً دون تفعيل الضغط التلقائي.", 14f))
    }
    override fun onResume() {
        super.onResume()
        // Re-check after returning from accessibility settings.
        if (::status.isInitialized) status.text = if (TapService.active != null) "خدمة اللمس مفعّلة" else "خدمة اللمس غير مفعّلة"
    }
}

class TapService : AccessibilityService() {
    companion object { @Volatile var active: TapService? = null; private set }
    private val main = Handler(Looper.getMainLooper())
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var panel: View? = null
    private var picker: View? = null
    private var textTimer: TextView? = null
    private var textStatus: TextView? = null
    private var startButton: Button? = null
    private var totalMs = 40000L
    private var targetMs = 500L
    private var touchMs = 1
    private var startNs = 0L
    private var utcAtStart = 0L
    private var utcSource = ""
    private var running = false
    private var executed = false
    private var scheduledTap: Runnable? = null
    private var smart: SmartBoxEngine? = null
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val elapsed = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000
            textTimer?.text = formatted(totalMs - elapsed)
            val epoch = utcAtStart + elapsed
            textStatus?.text = "${utc(epoch)}  ·  $utcSource"
            if (elapsed >= totalMs) {
                running = false
                startButton?.isEnabled = true
                textTimer?.text = "00:00.000"
                if (!executed) textStatus?.text = "انتهى العد دون تنفيذ اللمس"
            } else main.postDelayed(this, 16L)
        }
    }

    override fun onServiceConnected() { super.onServiceConnected(); active = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { smart?.onEvent(event) }
    override fun onInterrupt() { cancelCountdown("أُلغيت العملية بواسطة النظام"); smart?.stop("أوقف النظام المراقبة") }
    override fun onDestroy() { cancelCountdown("أغلقت الخدمة"); smart?.stop("أغلقت الخدمة"); smart = null; removePicker(); removePanel(); active = null; super.onDestroy() }

    private fun params(w: Int, h: Int) = WindowManager.LayoutParams(w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 12; y = 140 }

    fun openPanel(total: Long, tapAtRemaining: Long, gestureMs: Int) {
        smart?.stop("تم التحويل للعداد اليدوي"); smart = null
        cancelCountdown("جاهز")
        totalMs = total; targetMs = tapAtRemaining; touchMs = gestureMs
        removePanel()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 14)
            background = bg(NAVY, 24f)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val lp = params(290.dp, WindowManager.LayoutParams.WRAP_CONTENT)
        val header = TextView(this).apply { text = "UTC TIMER  •  اسحب للتحريك"; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(5, 5, 5, 8) }
        root.addView(header)
        header.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var originX = 0; var originY = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; originX = lp.x; originY = lp.y; return true }
                    MotionEvent.ACTION_MOVE -> { lp.x = max(0, originX + (downX - event.rawX).toInt()); lp.y = max(0, originY + (event.rawY - downY).toInt()); try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}; return true }
                }; return true
            }
        })
        textTimer = TextView(this).apply { text = formatted(totalMs); setTextColor(Color.WHITE); textSize = 37f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER; setPadding(0, 2, 0, 2) }
        root.addView(textTimer)
        textStatus = TextView(this).apply { text = "اضغط ابدأ للعد • تنفيذ عند ${formatted(targetMs)}"; setTextColor(0xff96ebe6.toInt()); textSize = 11f; gravity = Gravity.CENTER; setPadding(0, 2, 0, 10) }
        root.addView(textStatus)
        fun button(t: String, cb: () -> Unit): Button = Button(this).apply { text = t; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE); background = bg(TEAL, 12f); setOnClickListener { cb() } }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startButton = button("ابدأ", ::startCountdown)
        row.addView(startButton, LinearLayout.LayoutParams(0, 50.dp, 1f))
        row.addView(button("إلغاء", { cancelCountdown("ألغيت العد"); textTimer?.text = formatted(totalMs) }), LinearLayout.LayoutParams(0, 50.dp, 1f))
        root.addView(row)
        val secondRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        secondRow.addView(button("تحديد النقطة", ::choosePoint), LinearLayout.LayoutParams(0, 48.dp, 1f))
        secondRow.addView(button("إغلاق", { cancelCountdown("أغلق العداد"); removePanel() }), LinearLayout.LayoutParams(0, 48.dp, 1f))
        root.addView(secondRow)
        try { wm.addView(root, lp); panel = root }
        catch (e: Exception) { Toast.makeText(this, "فشل عرض النافذة: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    fun openSmartPanel(minCoins: Int, openIcon: Boolean, tap: Boolean, durationMs: Int) {
        cancelCountdown("التحويل للوضع الذكي")
        removePanel()
        smart?.stop("إعادة بدء المراقبة")
        val newEngine = SmartBoxEngine(this, minCoins, openIcon, tap, durationMs) { smart = null }
        smart = newEngine
        newEngine.start()
    }

    private fun choosePoint() {
        if (running) { Toast.makeText(this, "ألغِ العداد قبل تغيير النقطة", Toast.LENGTH_SHORT).show(); return }
        removePicker()
        val cover = FrameLayout(this).apply { setBackgroundColor(0x66000000) }
        val message = TextView(this).apply { text = "المس المكان المطلوب للضغطة\nلإلغاء التحديد اضغط رجوع"; textSize = 22f; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; setPadding(18, 80, 18, 20); setTextColor(Color.WHITE) }
        cover.addView(message, FrameLayout.LayoutParams(-1, -1))
        cover.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val xx = event.rawX; val yy = event.rawY
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat("tapX", xx).putFloat("tapY", yy).apply()
                removePicker()
                textStatus?.text = "نقطة الضغط: X=${xx.toInt()} Y=${yy.toInt()}"
            }
            true
        }
        try {
            wm.addView(cover, params(-1, -1).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0 }); picker = cover
        } catch (e: Exception) { Toast.makeText(this, "تعذر تحديد النقطة", Toast.LENGTH_SHORT).show() }
    }

    private fun startCountdown() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.contains("tapX") || !prefs.contains("tapY")) {
            textStatus?.text = "حدد نقطة الضغط أولاً"; return
        }
        cancelCountdown("بدء")
        val before = SystemClock.elapsedRealtimeNanos()
        val networkUtc = if (Build.VERSION.SDK_INT >= 33) try { SystemClock.currentNetworkTimeClock().instant().toEpochMilli() } catch (_: Exception) { null } else null
        val after = SystemClock.elapsedRealtimeNanos()
        utcAtStart = (networkUtc ?: System.currentTimeMillis()) + ((after - before) / 2_000_000)
        utcSource = if (networkUtc != null) "UTC شبكة" else "UTC الجهاز (غير موثّق)"
        startNs = (before + after) / 2
        running = true; executed = false
        startButton?.isEnabled = false
        main.post(ticker)
        val triggerDelay = max(0, totalMs - targetMs)
        val task = object : Runnable {
            override fun run() {
                if (!running || executed) return
                val elapsed = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000
                if (elapsed < triggerDelay) {
                    main.postDelayed(this, max(1, triggerDelay - elapsed))
                } else {
                    executed = true
                    sendTap(elapsed - triggerDelay)
                }
            }
        }
        scheduledTap = task
        main.postDelayed(task, triggerDelay)
    }

    private fun sendTap(latenessMs: Long) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val x = prefs.getFloat("tapX", -1f)
        val y = prefs.getFloat("tapY", -1f)
        if (x < 0 || y < 0) { cancelCountdown("نقطة لمس غير صالحة"); return }
        val view = panel
        if (view != null) {
            try { wm.removeView(view) } catch (_: Exception) {}
            panel = null
        }
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, touchMs.toLong())).build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { main.post { textStatus?.text = "أرسلت الضغطة (تأخير الجدولة ~${latenessMs}ms)" } }
            override fun onCancelled(gestureDescription: GestureDescription?) { main.post { textStatus?.text = "ألغي اللمس بواسطة النظام" } }
        }, main)
        if (!accepted) textStatus?.text = "رفض النظام إرسال الضغطة"
        // Restore control after the touch event so the overlay cannot intercept the gesture.
        main.postDelayed({
            if (panel == null && view != null) {
                try { wm.addView(view, params(290.dp, -2)); panel = view }
                catch (_: Exception) {}
            }
        }, max(300L, touchMs + 120L))
    }

    private fun cancelCountdown(reason: String) {
        running = false
        main.removeCallbacks(ticker)
        scheduledTap?.let { main.removeCallbacks(it) }
        scheduledTap = null
        textStatus?.text = reason
        startButton?.isEnabled = true
    }
    private fun removePanel() { panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }; panel = null; textTimer = null; textStatus = null; startButton = null }
    private fun removePicker() { picker?.let { try { wm.removeView(it) } catch (_: Exception) {} }; picker = null }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density + 0.5f).toInt()
}

