package com.desintoxicacion.digital.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.desintoxicacion.digital.R
import com.desintoxicacion.digital.ui.MainActivity

object Notifier {
    private const val CH_SUMMARY = "resumen"
    private const val CH_SUCCESS = "exito"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SUMMARY, "Resumen diario", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_SUCCESS, "Logros", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun post(ctx: Context, id: Int, channel: String, title: String, text: String) {
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            val nmc = NotificationManagerCompat.from(ctx)
            if (nmc.areNotificationsEnabled()) nmc.notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    fun summary(ctx: Context, title: String, text: String) = post(ctx, 1, CH_SUMMARY, title, text)
    fun success(ctx: Context, title: String, text: String) = post(ctx, 2, CH_SUCCESS, title, text)
}

/** Resumen diario (20:00 en el Perfil Maci) y notificación de éxito. */
object Summary {
    /** Comprueba si toca enviar el resumen de hoy y lo envía (en segundo plano). */
    fun runIfDue(ctx: Context, background: Boolean = true) {
        val p = Profiles.active()
        val due = synchronized(this) {
            val d = Store.day()
            val isDue = T.minuteOfDay() >= p.summaryTime && !d.summarySent
            if (isDue) Store.updateDay { it.summarySent = true }
            isDue
        }
        if (!due) return
        if (background) Thread { send(ctx.applicationContext) }.start() else send(ctx.applicationContext)
    }

    fun send(ctx: Context) {
        val p = Profiles.active()
        val (today, yesterday) = Stats.todayVsYesterday(ctx)
        val top = today.perApp.entries.sortedByDescending { it.value }.take(3)
            .joinToString(", ") { "${Stats.label(ctx, it.key)} (${T.mins(it.value)})" }
        val text = buildString {
            append("Desbloqueos: ${today.unlocks}.")
            if (top.isNotEmpty()) append(" Más usadas: $top.")
            append("\n\n")
            append(Life.sentence(p, today.totalMs))
        }
        Notifier.summary(ctx, "Hoy llevas ${T.mins(today.totalMs)} de móvil", text)
        if (yesterday.totalMs > 0 && today.totalMs < yesterday.totalMs) {
            Notifier.success(
                ctx, "¡Lo estás consiguiendo!",
                "Hoy llevas ${T.mins(yesterday.totalMs - today.totalMs)} menos de móvil que ayer a esta misma hora."
            )
        }
        Stats.backfill(ctx)
    }
}

object Scheduler {
    fun scheduleDaily(ctx: Context) {
        val p = Profiles.active()
        val am = ctx.getSystemService(AlarmManager::class.java)
        var t = T.atMinute(T.today(), p.summaryTime)
        if (t <= T.now() + 30_000) t = T.atMinute(T.today().plusDays(1), p.summaryTime)
        val pi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, DailyReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
    }
}

class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val pending = goAsync()
        Thread {
            try {
                Summary.runIfDue(context, background = false)
            } finally {
                Scheduler.scheduleDaily(context)
                pending.finish()
            }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Store.init(context)
        Scheduler.scheduleDaily(context)
    }
}
