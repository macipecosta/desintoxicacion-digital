package com.desintoxicacion.digital.core

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

/**
 * Marcador flotante: una pastilla pequeña y semitransparente, siempre visible y movible,
 * con el tiempo de móvil de hoy, los desbloqueos y, encima de WhatsApp, su cronómetro.
 * Usa una ventana de accesibilidad, así que no necesita el permiso "mostrar sobre otras apps".
 */
class Overlay(private val service: AccessibilityService) {
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private val wm: WindowManager get() = service.getSystemService(WindowManager::class.java)

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, service.resources.displayMetrics).toInt()

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null) return
        val tv = TextView(service).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            background = GradientDrawable().apply {
                cornerRadius = dp(16f).toFloat()
                setColor(Color.argb(170, 28, 29, 33))
            }
            text = "…"
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Store.overlayX
            y = Store.overlayY
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - downX).toInt()
                    lp.y = startY + (e.rawY - downY).toInt()
                    try { wm.updateViewLayout(tv, lp) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    Store.overlayX = lp.x
                    Store.overlayY = lp.y
                    true
                }
                else -> false
            }
        }
        try {
            wm.addView(tv, lp)
            view = tv
            params = lp
        } catch (_: Exception) {
        }
    }

    fun hide() {
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
    }

    fun setText(text: String) {
        view?.let { if (it.text.toString() != text) it.text = text }
    }
}
