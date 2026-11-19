package com.desintoxicacion.digital.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import java.time.LocalDate

/** Modo DESCONEXIÓN TOTAL: impide abrir las apps que usan internet, sin cortar los datos. */
object Disconnect {
    private var essentialsCache: Set<String>? = null

    fun isActive(now: Long = T.now()): Boolean {
        val end = Store.dcEnd
        if (end <= 0) return false
        val emergency = Store.dcEmergencyAt
        if (now >= end || (emergency > 0 && now >= emergency)) {
            finish()
            return false
        }
        return true
    }

    fun endTime(): Long = Store.dcEnd

    /** Final para los botones de duración. [preset]: "1h", "2h", "3h", "day", "2days". */
    fun presetEnd(preset: String, p: Profile, now: Long = T.now()): Long {
        val hour = 3_600_000L
        fun endOfDayAt(daysAhead: Long): Long {
            val today = T.dateOf(now)
            var t = T.atMinute(today, p.allDayEndTime)
            if (t <= now) t = T.atMinute(today.plusDays(1), p.allDayEndTime)
            return T.atMinute(T.dateOf(t).plusDays(daysAhead), p.allDayEndTime)
        }
        return when (preset) {
            "1h" -> now + hour
            "2h" -> now + 2 * hour
            "3h" -> now + 3 * hour
            "day" -> endOfDayAt(0)
            "2days" -> endOfDayAt(1)
            else -> now + hour
        }
    }

    /** Final para "Personalizar" hasta un día y hora concretos. */
    fun customEnd(date: LocalDate, minute: Int): Long = T.atMinute(date, minute)

    fun start(endMs: Long, done: (String?) -> Unit) {
        val now = T.now()
        Store.dcStart = now
        Store.dcEnd = endMs
        Store.dcEmergencyAt = 0L
        Store.dcReplied = emptySet()
        Store.addHistory(HistoryEvent(now, "disconnect", "hasta ${T.whenText(endMs)}"))
        if (Store.registered) {
            Server.call("dcStart", mapOf("until" to endMs.toString())) { _, err -> done(err) }
        } else done(null)
    }

    fun finish() {
        Store.dcEnd = 0L
        Store.dcStart = 0L
        Store.dcEmergencyAt = 0L
        Store.dcReplied = emptySet()
    }

    /** La respuesta automática solo funciona en desconexiones largas (12 h o más en el Perfil Maci). */
    fun autoReplyActive(p: Profile): Boolean =
        isActive() && (Store.dcEnd - Store.dcStart) >= p.autoReplyMinHours * 3_600_000L

    fun alreadyReplied(key: String) = key in Store.dcReplied
    fun markReplied(key: String) {
        Store.dcReplied = Store.dcReplied + key
    }

    /** Pide a la web que envíe un código a la persona de confianza. */
    fun requestUnlock(done: (String?) -> Unit) {
        Server.call("requestCode", mapOf("until" to Store.dcEnd.toString())) { _, err -> done(err) }
    }

    /** Comprueba el código con la web. done(null) = desbloqueado. */
    fun tryUnlock(code: String, done: (String?) -> Unit) {
        Server.call("verifyCode", mapOf("code" to code.trim())) { _, err ->
            if (err == null) finish()
            done(err)
        }
    }

    /** Salida de emergencia: la desconexión termina dentro de [Profile.emergencyWaitMin] minutos. */
    fun emergency(reason: String, p: Profile, done: (String?) -> Unit) {
        val at = T.now() + p.emergencyWaitMin * 60_000L
        Store.dcEmergencyAt = at
        Store.addHistory(HistoryEvent(T.now(), "emergency", reason))
        Server.call("emergency", mapOf("reason" to reason, "endsAt" to at.toString())) { _, err -> done(err) }
    }

    fun cancelEmergency() {
        Store.dcEmergencyAt = 0L
        Server.call("emergencyCancel") { _, _ -> }
    }

    fun blocksPackage(ctx: Context, pkg: String): Boolean {
        if (pkg == ctx.packageName) return false
        val p = Profiles.active()
        if (pkg in p.whitelist || pkg in p.freePackages) return false
        if (pkg in essentials(ctx)) return false
        return ctx.packageManager.checkPermission(Manifest.permission.INTERNET, pkg) == PackageManager.PERMISSION_GRANTED
    }

    /** Apps del sistema que nunca se bloquean: inicio, teléfono, SMS, contactos, ajustes, teclado. */
    fun essentials(ctx: Context): Set<String> {
        essentialsCache?.let { return it }
        val s = mutableSetOf(
            "android", "com.android.systemui", "com.android.settings",
            "com.google.android.dialer", "com.android.dialer", "com.android.phone", "com.android.server.telecom",
            "com.google.android.apps.messaging", "com.android.mms",
            "com.google.android.contacts", "com.android.contacts",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.android.emergency", "com.google.android.apps.safetyhub",
        )
        val pm = ctx.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(home, 0).forEach { s.add(it.activityInfo.packageName) }
        try {
            ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { s.add(it) }
        } catch (_: Exception) {
        }
        Telephony.Sms.getDefaultSmsPackage(ctx)?.let { s.add(it) }
        imePackages(ctx).forEach { s.add(it) }
        essentialsCache = s
        return s
    }

    fun imePackages(ctx: Context): Set<String> = try {
        ctx.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.toSet()
            ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }
}
