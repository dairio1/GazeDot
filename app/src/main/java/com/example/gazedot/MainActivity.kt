package com.example.gazedot

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var preview: CircleView
    private lateinit var status: TextView
    private val keyButtons = mutableMapOf<Dir, Button>()
    private var waiting: Dir? = null
    private var density = 1f

    private fun dp(v: Int): Int = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        density = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(32))
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        // --- Estado del servicio ---
        status = TextView(this).apply { textSize = 16f }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Abrir ajustes de accesibilidad"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(note("Busca «GazeDot» en Accesibilidad (puede estar en «Apps instaladas» o «Servicios descargados») y actívalo."))

        // --- Vista previa ---
        root.addView(section("Vista previa"))
        val box = FrameLayout(this).apply { setBackgroundColor(Color.rgb(224, 224, 224)) }
        preview = CircleView(this)
        box.addView(preview, FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER))
        root.addView(
            box,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220))
        )

        // --- Color ---
        root.addView(section("Color"))
        val colors = listOf(
            "#FF3B30", "#FF9500", "#FFD60A", "#34C759",
            "#00C7BE", "#0A84FF", "#BF5AF2", "#FFFFFF"
        )
        colors.chunked(4).forEach { rowColors ->
            val row = LinearLayout(this)
            rowColors.forEach { hex ->
                val c = Color.parseColor(hex)
                val swatch = View(this).apply {
                    setBackgroundColor(c)
                    setOnClickListener {
                        prefs.edit().putInt(Prefs.K_COLOR, c).apply()
                        refreshPreview()
                    }
                }
                val lp = LinearLayout.LayoutParams(0, dp(44), 1f)
                lp.setMargins(dp(4), dp(4), dp(4), dp(4))
                row.addView(swatch, lp)
            }
            root.addView(row)
        }

        // --- Sliders ---
        root.addView(section("Apariencia y velocidad"))
        addSlider(root, "Tamaño", Prefs.K_SIZE, 20, 200, Prefs.DEF_SIZE, " dp")
        addSlider(root, "Opacidad", Prefs.K_OPACITY, 10, 100, Prefs.DEF_OPACITY, " %")
        addSlider(root, "Velocidad", Prefs.K_SPEED, 1, 100, Prefs.DEF_SPEED, "")

        // --- Teclas ---
        root.addView(section("Teclas de control"))
        root.addView(note("Toca un botón y luego presiona la tecla que quieras en tu teclado. Mientras el servicio esté activo, estas teclas se usan solo para mover el círculo y no escribirán en otras apps."))
        Dir.values().forEach { dir ->
            val b = Button(this)
            b.setOnClickListener {
                if (waiting == dir) {
                    cancelWaiting()
                } else {
                    waiting = dir
                    DotService.remapping = true
                    updateKeyButtons()
                }
            }
            keyButtons[dir] = b
            root.addView(b)
        }
        root.addView(note("Para cancelar la asignación: toca el botón otra vez o presiona Atrás."))

        // --- Restablecer ---
        root.addView(Button(this).apply {
            text = "Restablecer valores"
            setOnClickListener {
                cancelWaiting()
                prefs.edit().clear().apply()
                recreate()
            }
        })

        updateKeyButtons()
        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshPreview()
    }

    override fun onPause() {
        cancelWaiting()
        super.onPause()
    }

    override fun onDestroy() {
        DotService.remapping = false
        super.onDestroy()
    }

    // ---------- Captura de la tecla nueva ----------
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val dir = waiting
        if (dir != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val code = event.keyCode
                if (code == KeyEvent.KEYCODE_BACK) {
                    cancelWaiting()
                } else if (code != KeyEvent.KEYCODE_UNKNOWN) {
                    prefs.edit().putInt(dir.prefKey, code).apply()
                    waiting = null
                    DotService.remapping = false
                    updateKeyButtons()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun cancelWaiting() {
        waiting = null
        DotService.remapping = false
        updateKeyButtons()
    }

    private fun updateKeyButtons() {
        Dir.values().forEach { dir ->
            val b = keyButtons[dir] ?: return@forEach
            b.text = if (waiting == dir) {
                "${dir.label}: presiona una tecla…"
            } else {
                "${dir.label}: ${Prefs.keyName(Prefs.keyFor(this, dir))}"
            }
        }
    }

    // ---------- Estado / vista previa ----------
    private fun isServiceEnabled(): Boolean {
        val me = ComponentName(this, DotService::class.java)
        val s = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun updateStatus() {
        status.text = if (isServiceEnabled()) {
            "Servicio ACTIVO ✔\nEl círculo se muestra sobre cualquier app."
        } else {
            "Servicio DESACTIVADO ✘\nActívalo en Accesibilidad para ver el círculo."
        }
    }

    private fun refreshPreview() {
        val size = dp(Prefs.size(this))
        preview.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        preview.setStyle(Prefs.color(this), Prefs.opacity(this))
    }

    // ---------- Helpers de UI ----------
    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(8))
    }

    private fun note(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun addSlider(
        root: LinearLayout, label: String, key: String,
        min: Int, max: Int, def: Int, unit: String
    ) {
        val cur = prefs.getInt(key, def).coerceIn(min, max)
        val tv = TextView(this).apply {
            text = "$label: $cur$unit"
            setPadding(0, dp(8), 0, 0)
        }
        val sb = SeekBar(this).apply {
            this.max = max - min
            progress = cur - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val v = p + min
                    tv.text = "$label: $v$unit"
                    if (fromUser) {
                        prefs.edit().putInt(key, v).apply()
                        refreshPreview()
                    }
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(tv)
        root.addView(sb)
    }
}
