package com.desintoxicacion.digital.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Utilidades de fechas y horas. Los minutos del día van de 0 (00:00) a 1440 (24:00).
 * El "día lógico" empieza a la hora del perfil (2:00 en el Perfil Maci): a la 1:30 de la
 * madrugada todavía cuenta como el día anterior.
 */
object T {
    val ES: Locale = Locale.forLanguageTag("es-ES")
    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val DDMM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

    fun now(): Long = System.currentTimeMillis()
    fun zone(): ZoneId = ZoneId.systemDefault()

    fun minuteOfDay(ms: Long = now()): Int {
        val z = Instant.ofEpochMilli(ms).atZone(zone())
        return z.hour * 60 + z.minute
    }

    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone()).toInstant().toEpochMilli()
    fun atMinute(d: LocalDate, minute: Int): Long = startOfDay(d) + minute * 60_000L
    fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()

    // ----- Día lógico -----
    private fun dayStartMin(): Int = try { Profiles.active().dayStartMinute } catch (_: Exception) { 0 }

    fun logicalDate(ms: Long = now()): LocalDate {
        val ds = dayStartMin()
        val d = dateOf(ms)
        return if (minuteOfDay(ms) < ds) d.minusDays(1) else d
    }

    fun today(): LocalDate = logicalDate()

    /** Momento en que empieza el día lógico [d]. */
    fun logicalStart(d: LocalDate): Long = atMinute(d, dayStartMin())

    /** Momento en que empieza el día lógico siguiente (cuándo se reinician los límites). */
    fun nextReset(ms: Long = now()): Long = logicalStart(logicalDate(ms).plusDays(1))

    fun weekStart(ms: Long = now()): Long = logicalStart(logicalDate(ms).with(DayOfWeek.MONDAY))

    // ----- Formatos -----
    fun hhmm(ms: Long): String = HHMM.format(Instant.ofEpochMilli(ms).atZone(zone()))
    fun ddmm(d: LocalDate): String = DDMM.format(d)
    fun fmtMinute(m: Int): String = "%02d:%02d".format((m / 60) % 24, m % 60)

    /** "hoy a las 21:00", "mañana a las 21:00" o "el 21/11 a las 21:00". */
    fun whenText(ms: Long): String {
        val d = dateOf(ms)
        val cal = dateOf(now())
        val day = when (d) {
            cal -> "hoy"
            cal.plusDays(1) -> "mañana"
            else -> "el ${ddmm(d)}"
        }
        return "$day a las ${hhmm(ms)}"
    }

    /** "1 h 05 min" o "12 min". */
    fun mins(ms: Long): String {
        val m = (ms / 60_000L).coerceAtLeast(0)
        return if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)} min" else "$m min"
    }

    /** "18:32" (minutos:segundos) o "1:05:10". */
    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }

    /** "45 s", "1 min", "5 min". */
    fun secs(seconds: Int): String = if (seconds < 60) "$seconds s" else "${seconds / 60} min"

    fun hoursDec(ms: Long): String = String.format(ES, "%.1f h", ms / 3_600_000.0)
}
