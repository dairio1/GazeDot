package com.example.gazedot

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.view.KeyEvent

enum class Dir(val prefKey: String, val label: String, val defaultKey: Int) {
    UP("key_up", "Arriba", KeyEvent.KEYCODE_DPAD_UP),
    DOWN("key_down", "Abajo", KeyEvent.KEYCODE_DPAD_DOWN),
    LEFT("key_left", "Izquierda", KeyEvent.KEYCODE_DPAD_LEFT),
    RIGHT("key_right", "Derecha", KeyEvent.KEYCODE_DPAD_RIGHT)
}

object Prefs {
    const val K_COLOR = "color"
    const val K_SIZE = "size_dp"
    const val K_OPACITY = "opacity"
    const val K_SPEED = "speed"

    const val DEF_SIZE = 60
    const val DEF_OPACITY = 60
    const val DEF_SPEED = 40

    fun get(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("gazedot_prefs", Context.MODE_PRIVATE)

    fun color(ctx: Context): Int = get(ctx).getInt(K_COLOR, Color.parseColor("#FF3B30"))
    fun size(ctx: Context): Int = get(ctx).getInt(K_SIZE, DEF_SIZE)
    fun opacity(ctx: Context): Int = get(ctx).getInt(K_OPACITY, DEF_OPACITY)
    fun speed(ctx: Context): Int = get(ctx).getInt(K_SPEED, DEF_SPEED)

    fun keyFor(ctx: Context, dir: Dir): Int = get(ctx).getInt(dir.prefKey, dir.defaultKey)

    fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
}
