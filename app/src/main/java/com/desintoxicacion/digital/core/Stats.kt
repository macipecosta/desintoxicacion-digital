package com.desintoxicacion.digital.core

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Estadísticas de uso leídas del propio Android (acceso a uso de apps). */
object Stats {
    private var excludedCache: Set<String>? = null

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    /** No cuentan como uso: la pantalla de inicio, la barra del sistema y esta misma app. */
    private fun excluded(ctx: Context): Set<String> {
        excludedCache?.let { return it }
        val s = mutableSetOf(ctx.packageName, "com.android.systemui", "android")
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        ctx.packageManager.queryIntentActivities(home, 0).forEach { s.add(it.activityInfo.packageName) }
        excludedCache = s
        return s
    }

    /** Calcula tiempo en pantalla por app y desbloqueos entre [start] y [end]. */
    fun compute(ctx: Context, start: Long, end: Long): DayUsage {
        if (end <= start || !hasAccess(ctx)) return DayUsage(0, 0, emptyMap())
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(start, end)
        val ev = UsageEvents.Event()
        val perApp = mutableMapOf<String, Long>()
        var unlocks = 0
        var curPkg: String? = null
        var curStart = 0L

        fun close(at: Long) {
            val p = curPkg ?: return
            val dur = at - curStart
            if (dur > 0) perApp[p] = (perApp[p] ?: 0L) + dur
            curPkg = null
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val t = ev.timeStamp
            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (curPkg != ev.packageName) {
                        close(t)
                        curPkg = ev.packageName
                        curStart = t
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (curPkg == ev.packageName) close(t)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> close(t)
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
            }
        }
        close(minOf(end, T.now()))

        val ex = excluded(ctx)
        val filtered = perApp.filterKeys { it !in ex }.filterValues { it >= 1000 }
        return DayUsage(filtered.values.sum(), unlocks, filtered)
    }

    /** Uso de un día entero (o de hoy hasta ahora). Los días ya cerrados se guardan en caché. */
    fun day(ctx: Context, date: LocalDate): DayUsage? {
        val today = T.today()
        if (date.isAfter(today)) return null
        val start = T.logicalStart(date)
        if (date == today) return compute(ctx, start, T.now())
        Store.cachedDay(date)?.let { return it }
        if (ChronoUnit.DAYS.between(date, today) > 9 || date.isBefore(Store.installDate.minusDays(7))) return null
        if (!hasAccess(ctx)) return null
        val u = compute(ctx, start, T.logicalStart(date.plusDays(1)))
        Store.cacheDay(date, u)
        return u
    }

    /** Guarda en caché los últimos días, antes de que Android borre el detalle. */
    fun backfill(ctx: Context, days: Int = 8) {
        val today = T.today()
        for (i in 1..days) day(ctx, today.minusDays(i.toLong()))
    }

    /** Hoy hasta ahora frente a ayer hasta la misma hora. */
    fun todayVsYesterday(ctx: Context): Pair<DayUsage, DayUsage> {
        val now = T.now()
        val today = T.today()
        val start = T.logicalStart(today)
        val ys = T.logicalStart(today.minusDays(1))
        return compute(ctx, start, now) to compute(ctx, ys, ys + (now - start))
    }

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    /** Días seguidos (hasta ayer) usando el móvil menos que el día anterior. */
    fun streakDecreasing(ctx: Context): Int {
        var count = 0
        var d = T.today().minusDays(1)
        repeat(60) {
            val a = day(ctx, d) ?: return count
            val b = day(ctx, d.minusDays(1)) ?: return count
            if (b.totalMs == 0L || a.totalMs >= b.totalMs) return count
            count++
            d = d.minusDays(1)
        }
        return count
    }

    /** Días seguidos sin usar comodín, contando hoy si aún no lo has usado. */
    fun streakNoWildcard(): Int {
        val used = Store.history().filter { it.type == "wildcard" }.map { T.logicalDate(it.time) }.toSet()
        var d = T.today()
        var count = 0
        val install = Store.installDate
        while (!d.isBefore(install) && d !in used && count < 3650) {
            count++
            d = d.minusDays(1)
        }
        return count
    }
}

/** Cálculo de vida: años que pasarías con el móvil hasta la edad del perfil, a este ritmo diario. */
object Life {
    fun daysLeft(p: Profile): Long {
        val birth = try { LocalDate.parse(p.birthDate) } catch (e: Exception) { return 0 }
        return ChronoUnit.DAYS.between(T.today(), birth.plusYears(p.lifeYears.toLong())).coerceAtLeast(0)
    }

    fun yearsOnPhone(p: Profile, msPerDay: Long): Double = msPerDay / 3_600_000.0 * daysLeft(p) / 8766.0

    fun sentence(p: Profile, msPerDay: Long): String {
        if (daysLeft(p) == 0L) return ""
        val years = String.format(T.ES, "%.1f", yearsOnPhone(p, msPerDay))
        return "Si sigues usando el móvil ${T.hoursDec(msPerDay)} al día, hasta los ${p.lifeYears} años le dedicarás $years años de tu vida."
    }
}
