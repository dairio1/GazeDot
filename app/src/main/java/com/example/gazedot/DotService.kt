package com.example.gazedot

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.sqrt

class DotService : AccessibilityService() {

    companion object {
        /** La pantalla de ajustes lo activa mientras espera que presiones una tecla nueva. */
        @Volatile
        var remapping = false
    }

    private lateinit var wm: WindowManager
    private var dot: CircleView? = null
    private var params: WindowManager.LayoutParams? = null

    private var cx = 0f
    private var cy = 0f
    private var lastW = 0
    private var lastH = 0
    private var sizePx = 0
    private var speed = Prefs.DEF_SPEED
    private var density = 1f

    private var keyMap: Map<Int, Dir> = emptyMap()
    private val held = mutableSetOf<Dir>()
    private var holdStart = 0L
    private var lastTick = 0L

    private val handler = Handler(Looper.getMainLooper())

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> loadPrefs() }

    private val screenChanged = Runnable { onScreenChanged() }

    private val ticker = object : Runnable {
        override fun run() {
            if (remapping) {
                held.clear()
                return
            }
            if (held.isEmpty()) return

            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastTick) / 1000f).coerceAtMost(0.05f)
            lastTick = now

            val holdSec = (now - holdStart) / 1000f
            val accel = 1f + minOf(holdSec, 1.5f) * 1.5f
            val pxPerSec = (100f + speed * 6f) * density * accel

            var dx = 0f
            var dy = 0f
            if (Dir.LEFT in held) dx -= 1f
            if (Dir.RIGHT in held) dx += 1f
            if (Dir.UP in held) dy -= 1f
            if (Dir.DOWN in held) dy += 1f

            val len = sqrt(dx * dx + dy * dy)
            if (len > 0f) {
                cx += dx / len * pxPerSec * dt
                cy += dy / len * pxPerSec * dt
                clampPosition()
                updatePosition()
            }
            handler.postDelayed(this, 16)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        density = resources.displayMetrics.density

        val view = CircleView(this)
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        dot = view
        params = lp

        loadPrefs()

        val (w, h) = screenSize()
        lastW = w
        lastH = h
        cx = w / 2f
        cy = h / 2f
        lp.x = (cx - sizePx / 2f).toInt()
        lp.y = (cy - sizePx / 2f).toInt()

        try {
            wm.addView(view, lp)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Prefs.get(this).registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Se llama dos veces: a veces las medidas nuevas tardan unos milisegundos en actualizarse.
        onScreenChanged()
        handler.removeCallbacks(screenChanged)
        handler.postDelayed(screenChanged, 300)
    }

    private fun onScreenChanged() {
        if (dot == null) return
        val (w, h) = screenSize()
        if (lastW > 0 && lastH > 0 && (w != lastW || h != lastH)) {
            cx = cx / lastW * w
            cy = cy / lastH * h
        }
        lastW = w
        lastH = h
        clampPosition()
        updatePosition()
    }

    private fun loadPrefs() {
        val color = Prefs.color(this)
        val opacity = Prefs.opacity(this)
        speed = Prefs.speed(this)
        sizePx = (Prefs.size(this) * density).toInt().coerceAtLeast(4)

        keyMap = Dir.values().associateBy { Prefs.keyFor(this, it) }

        val view = dot ?: return
        val lp = params ?: return
        view.setStyle(color, opacity)
        lp.width = sizePx
        lp.height = sizePx
        lp.x = (cx - sizePx / 2f).toInt()
        lp.y = (cy - sizePx / 2f).toInt()
        try {
            if (view.isAttachedToWindow) wm.updateViewLayout(view, lp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(m)
        return Pair(m.widthPixels, m.heightPixels)
    }

    private fun clampPosition() {
        val (w, h) = screenSize()
        cx = cx.coerceIn(0f, w.toFloat())
        cy = cy.coerceIn(0f, h.toFloat())
    }

    private fun updatePosition() {
        val view = dot ?: return
        val lp = params ?: return
        lp.x = (cx - sizePx / 2f).toInt()
        lp.y = (cy - sizePx / 2f).toInt()
        try {
            if (view.isAttachedToWindow) wm.updateViewLayout(view, lp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (remapping) return false
        val dir = keyMap[event.keyCode] ?: return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> pressed(dir)
            KeyEvent.ACTION_UP -> released(dir)
        }
        return true
    }

    private fun pressed(dir: Dir) {
        if (held.isEmpty()) {
            val now = SystemClock.uptimeMillis()
            holdStart = now
            lastTick = now
            held.add(dir)
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        } else {
            held.add(dir)
        }
    }

    private fun released(dir: Dir) {
        held.remove(dir)
        if (held.isEmpty()) handler.removeCallbacks(ticker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No necesitamos leer el contenido de la pantalla.
    }

    override fun onInterrupt() {
        held.clear()
        handler.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(screenChanged)
        held.clear()
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        val view = dot
        if (view != null) {
            try {
                wm.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        dot = null
        super.onDestroy()
    }
}
