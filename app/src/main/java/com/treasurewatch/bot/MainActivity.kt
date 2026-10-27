package com.treasurewatch.bot

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.io.File

/** Native RTL control panel with no third-party permissions or libraries. */
class MainActivity : Activity() {
    private val navy = Color.rgb(19, 35, 58)
    private val teal = Color.rgb(4, 135, 136)
    private val dark = Color.rgb(38, 57, 78)
    private val muted = Color.rgb(99, 115, 133)
    private val backgroundColor = Color.rgb(246, 248, 251)
    private val handler = Handler(Looper.getMainLooper())
    private val settings by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private var currentTab = 0
    private val tabButtons = mutableListOf<Button>()
    private var consent = false
    private var statusValue: TextView? = null
    private var statsValue: TextView? = null
    private val refresh = object : Runnable {
        override fun run() {
            statusValue?.text = TreasureService.status
            statsValue?.text = "البثوث: ${TreasureService.visited}     الصناديق المحتملة: ${TreasureService.found}" +
                (TreasureService.countdown?.let { "     العداد: ${it}ث" } ?: "")
            handler.postDelayed(this, 650)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = backgroundColor
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(backgroundColor) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(16), dp(22), dp(18))
            setBackgroundColor(navy)
        }
        header.addView(label("TREASURE WATCH  /  LIVE", 12, Color.rgb(101, 224, 215), true))
        header.addView(label("مراقبة صناديق الكنز", 24, Color.WHITE, true))
        header.addView(label("مساعد محلي للمراقبة اليدوية — نسخة تجريبية", 12, Color.rgb(215, 224, 235), false))
        shell.addView(header)
        val tabs = LinearLayout(this).apply {
            setPadding(dp(12), dp(12), dp(12), dp(4))
            orientation = LinearLayout.HORIZONTAL
        }
        listOf("الرئيسية", "الإعدادات", "السجل").forEachIndexed { index, name ->
            val b = button(name, if (currentTab == index) teal else dark, compact = true) {
                currentTab = index
                tabButtons.forEachIndexed { i, item ->
                    item.background = GradientDrawable().apply {
                        setColor(if (i == currentTab) teal else dark)
                        cornerRadius = dp(12).toFloat()
                    }
                }
                render()
            }
            tabButtons.add(b)
            tabs.addView(b, LinearLayout.LayoutParams(0, dp(46), 1f))
        }
        shell.addView(tabs)
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(28))
        }
        scroll.addView(content)
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(shell)
        render()
    }

    override fun onResume() { super.onResume(); handler.removeCallbacks(refresh); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }

    private fun render() {
        statusValue = null
        statsValue = null
        content.removeAllViews()
        when (currentTab) {
            0 -> home()
            1 -> settingsScreen()
            else -> logsScreen()
        }
    }

    private fun home() {
        content.addView(label("حالة الجلسة", 18, navy, true))
        statusValue = label(TreasureService.status, 15, navy, true)
        content.addView(card(statusValue!!))
        statsValue = label("البثوث: ${TreasureService.visited}     الصناديق المحتملة: ${TreasureService.found}", 14, dark, true)
        content.addView(card(statsValue!!))
        content.addView(label("قبل التشغيل", 18, navy, true))
        content.addView(label("1. فعّل خدمة إمكانية الوصول يدوياً.  2. اختر حدود التشغيل من الإعدادات.  3. وافق على الإفصاح ثم ابدأ المراقبة.", 14, muted, false))
        content.addView(button("فتح إعدادات إمكانية الوصول", navy) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        content.addView(button("فتح TikTok", dark) { launchTikTok() })
        val disclosure = "إفصاح عن إمكانية الوصول: يقرأ التطبيق النصوص وأوصاف عناصر واجهة TikTok الظاهرة أثناء الجلسة، وقد يفحص لقطة الشاشة محلياً إذا أضفت صورة معايرة. يستخدم ذلك فقط لتحديد صندوق محتمل وعدّاده. لا يرسل النصوص أو الصور خارج الجهاز، ولا يخزّنها. يسجل أوقات الأحداث العامة محلياً فقط. لن يضغط التطبيق زر فتح الصندوق أو يبدّل البثوث تلقائياً."
        content.addView(label("الخصوصية والتحكم", 18, navy, true))
        content.addView(card(label(disclosure, 13, dark, false)))
        val confirm = CheckBox(this).apply {
            text = "أوافق على استخدام إمكانية الوصول للمراقبة وفق الإفصاح أعلاه"
            textSize = 14f
            setTextColor(dark)
            isChecked = consent
            setOnCheckedChangeListener { _, checked -> consent = checked }
        }
        content.addView(confirm)
        content.addView(button("بدء مراقبة البث", teal) {
            when {
                !consent -> statusValue?.text = "يجب قراءة الإفصاح وتحديد الموافقة أولاً"
                TreasureService.current == null -> statusValue?.text = "فعّل خدمة إمكانية الوصول أولاً من إعدادات Android"
                else -> {
                    val limits = SessionLimits(
                        settings.getInt("scan", 15),
                        settings.getInt("rooms", 15),
                        settings.getInt("minutes", 30)
                    )
                    TreasureService.current?.startBot(limits)
                    launchTikTok()
                }
            }
        })
        content.addView(button("تسجيل الانتقال اليدوي إلى بث جديد", dark) {
            TreasureService.current?.nextRoom()
            statusValue?.text = TreasureService.status
        })
        content.addView(button("إيقاف فوري", Color.rgb(176, 53, 62)) {
            TreasureService.current?.stopBot()
            statusValue?.text = TreasureService.status
        })
        content.addView(label("أثناء التشغيل يظهر زر صغير للإيقاف الفوري وزر «بث جديد» لتسجيل انتقالك اليدوي إلى بث آخر. لا يوجد جمع تلقائي للعملات أو تجاوز للحماية. النتائج إشارات محتملة وليست جوائز مؤكدة.", 13, muted, false))
    }

    private fun settingsScreen() {
        content.addView(label("حدود الجلسة", 20, navy, true))
        content.addView(label("تُطبَّق الإعدادات عند بداية الجلسة التالية. تتوقف المراقبة تلقائياً عند انتهاء الوقت أو ترك TikTok لمدة 90 ثانية.", 13, muted, false))
        settingSlider("ثواني فحص كل بث", "scan", 5, 60, 15, "ثانية")
        settingSlider("أقصى عدد للبثوث", "rooms", 1, 100, 15, "بث")
        settingSlider("المدة القصوى للجلسة", "minutes", 5, 120, 30, "دقيقة")
        content.addView(label("التعرف البصري الاختياري", 18, navy, true))
        content.addView(card(label("إذا كانت أيقونة الكنز غير مقروءة من إمكانية الوصول، يمكنك قصّ صورتها من لقطة شاشة على جهازك. تجري المطابقة محلياً فقط. لا يلتقط التطبيق صوراً متواصلة إلا أثناء جلسة مفعّلة ومع وجود صورة معايرة.", 13, dark, false)))
        content.addView(button("استيراد صورة صندوق مقصوصة", navy) { pickTemplate() })
        content.addView(button("حذف صورة المعايرة", dark) {
            File(filesDir, "box_template.png").delete()
            render()
        })
        content.addView(label(if (File(filesDir, "box_template.png").exists()) "المعايرة: محفوظة على الجهاز" else "المعايرة: غير موجودة (الاكتشاف بالنص فقط)", 13, muted, false))
    }

    private fun logsScreen() {
        content.addView(label("سجل العمليات المحلي", 20, navy, true))
        content.addView(label("يُحتفظ بآخر 120 حدثاً فقط. لا يحتوي السجل على أسماء الحسابات أو محتوى البث.", 13, muted, false))
        content.addView(button("مسح السجل", dark) { EventLog.clear(this); render() })
        val logs = EventLog.read(this)
        if (logs.isEmpty()) content.addView(card(label("لا توجد عمليات مسجلة حتى الآن", 14, muted, false)))
        else logs.forEach { content.addView(card(label(it, 13, dark, false))) }
    }

    private fun settingSlider(title: String, key: String, min: Int, max: Int, fallback: Int, suffix: String) {
        val value = settings.getInt(key, fallback).coerceIn(min, max)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(label(title, 15, navy, true))
        val label = label("$value $suffix", 13, teal, true)
        box.addView(label)
        box.addView(SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val chosen = (progress + min).coerceIn(min, max)
                    label.text = "$chosen $suffix"
                    settings.edit().putInt(key, chosen).apply()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        })
        content.addView(card(box))
    }

    private fun launchTikTok() {
        val intent = packageManager.getLaunchIntentForPackage(TreasureService.PACKAGE)
        if (intent == null) statusValue?.text = "لم يُعثر على النسخة القياسية من TikTok"
        else try { startActivity(intent) } catch (_: Exception) { statusValue?.text = "تعذّر فتح TikTok" }
    }

    private fun pickTemplate() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }, 201)
    }

    @Deprecated("Dependency-free Android file picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != 201 || resultCode != RESULT_OK) return
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth in 16..1000 && bounds.outHeight in 16..1000) { "يجب قص أيقونة صغيرة (16 إلى 1000 بكسل)" }
            val bitmap = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: error("تعذر قراءة الصورة")
            try {
                File(filesDir, "box_template.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                EventLog.append(this, "استيراد صورة معايرة محلية")
            } finally { bitmap.recycle() }
            render()
        } catch (error: Exception) {
            content.addView(label("تعذر استيراد الصورة: ${error.message}", 13, Color.RED, false))
        }
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(null, 1)
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(6), dp(6), dp(6))
    }

    private fun card(child: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.rgb(233, 237, 242))
        }
        setPadding(dp(14), dp(12), dp(14), dp(12))
        addView(child)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6); bottomMargin = dp(7)
        }
    }

    private fun button(value: String, color: Int, compact: Boolean = false, onClick: () -> Unit): Button = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = if (compact) 12f else 14f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(12).toFloat() }
        minHeight = 0; minimumHeight = 0
        if (!compact) layoutParams = LinearLayout.LayoutParams(-1, dp(49)).apply {
            topMargin = dp(6); bottomMargin = dp(4)
        }
        setOnClickListener { onClick() }
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
