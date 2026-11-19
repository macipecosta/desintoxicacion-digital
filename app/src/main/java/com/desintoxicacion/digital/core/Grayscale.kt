package com.desintoxicacion.digital.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Pantalla en blanco y negro (corrección de color "monocromo" de Android).
 * Necesita el permiso WRITE_SECURE_SETTINGS, que se concede una vez desde el ordenador.
 */
object Grayscale {
    private const val ENABLED = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun shouldBeOn(p: Profile, now: Long = T.now()): Boolean {
        val m = T.minuteOfDay(now)
        return p.grayscaleWindows.any { it.contains(m) } || (p.grayscaleDuringDisconnect && Disconnect.isActive(now))
    }

    fun update(ctx: Context) {
        if (!hasPermission(ctx)) return
        val want = shouldBeOn(Profiles.active())
        val cr = ctx.contentResolver
        try {
            val on = Settings.Secure.getInt(cr, ENABLED, 0) == 1
            if (want && !on) {
                Settings.Secure.putInt(cr, MODE, 0) // 0 = monocromo
                Settings.Secure.putInt(cr, ENABLED, 1)
                Store.grayByApp = true
            } else if (!want && on && Store.grayByApp) {
                Settings.Secure.putInt(cr, ENABLED, 0)
                Store.grayByApp = false
            }
        } catch (_: SecurityException) {
        }
    }
}
