package com.desintoxicacion.digital.core

import org.json.JSONArray
import org.json.JSONObject

/** Franja horaria en minutos del día. Admite franjas que cruzan la medianoche (p. ej. 19:00-02:00). */
data class TimeRange(val start: Int, val end: Int) {
    fun contains(minute: Int): Boolean =
        if (start <= end) minute in start until end else (minute >= start || minute < end)

    override fun toString(): String = "${T.fmtMinute(start)}-${T.fmtMinute(end)}"

    companion object {
        fun parseTime(s: String): Int? {
            val p = s.trim().split(":")
            if (p.size != 2) return null
            val h = p[0].trim().toIntOrNull() ?: return null
            val m = p[1].trim().toIntOrNull() ?: return null
            if (h !in 0..24 || m !in 0..59) return null
            return (h * 60 + m).coerceAtMost(1440)
        }

        /** "09:30-13:30, 16:00-19:00" -> lista. Devuelve null si el formato es incorrecto. */
        fun parseList(s: String): List<TimeRange>? {
            if (s.isBlank()) return emptyList()
            val out = mutableListOf<TimeRange>()
            for (part in s.split(",", ";")) {
                if (part.isBlank()) continue
                val p = part.trim().split("-")
                if (p.size != 2) return null
                val a = parseTime(p[0]) ?: return null
                val b = parseTime(p[1]) ?: return null
                out.add(TimeRange(a, b))
            }
            return out
        }

        fun listToString(l: List<TimeRange>): String = l.joinToString(", ")
    }
}

/**
 * Todos los parámetros de la app. Los valores por defecto son los del Perfil Maci (v2).
 * Los tiempos van en minutos salvo que el nombre diga otra cosa.
 */
data class Profile(
    val name: String = "Perfil Maci",
    val dayStartMinute: Int = 120,

    // WhatsApp
    val waPackages: List<String> = listOf("com.whatsapp"),
    val waFreeWindows: List<TimeRange> = listOf(TimeRange(120, 570), TimeRange(810, 960), TimeRange(1140, 120)),
    val waIntervalWindows: List<TimeRange> = listOf(TimeRange(570, 810), TimeRange(960, 1140)),
    val waIntervalMin: Int = 45,
    val waFreeEntries: Int = 3,
    val waDailyLimitMin: Int = 45,
    val waSplitTime: Int = 960,
    val waBeforeSplitLimitMin: Int = 20,
    val waAfterSplitLimitMin: Int = 25,

    // Gmail
    val mailPackages: List<String> = listOf("com.google.android.gm"),
    val mailFreeWindows: List<TimeRange> = listOf(TimeRange(120, 570), TimeRange(810, 960)),
    val mailFreeEntries: Int = 3,
    val mailRecentMin: Int = 30,
    val mailRecentWaitSec: Int = 60,

    // Hotmail / Outlook (web)
    val webmailDomains: List<String> = listOf("hotmail.com", "outlook.live.com", "outlook.com"),
    val webmailIntervalMin: Int = 60,

    // Periódicos
    val newsBlockedWindows: List<TimeRange> = listOf(TimeRange(570, 810), TimeRange(960, 1170)),
    /** Webs con límite de entradas. Varios dominios del mismo periódico van unidos con "|". */
    val newsCountedSites: List<String> = listOf("elpais.com|elpais.es", "heraldo.es"),
    val newsEntriesPerSite: Int = 3,
    val newsTimedDomains: List<String> = listOf(
        "elmundo.es", "publico.es", "okdiario.com", "eldiario.es", "elperiodicodearagon.com", "abc.es"
    ),
    val newsTimedLimitMin: Int = 60,
    val newsWildcardPerWeek: Int = 1,
    val newsWildcardMin: Int = 60,

    // Cartelera
    val carteleraPackages: List<String> = emptyList(),
    val carteleraDailyLimitMin: Int = 30,
    val carteleraFreeEntriesPerHour: Int = 3,
    val carteleraWaitsSec: List<Int> = listOf(30, 60, 300),
    val carteleraFreeDaysPerWeek: Int = 3,

    // Apps siempre libres (WhatsApp Business, Crew Photo Flow…)
    val freePackages: List<String> = listOf("com.whatsapp.w4b"),

    // Comodín diario y día especial
    val wildcardsPerDay: Int = 1,
    val wildcardMin: Int = 20,
    val specialDaysPerWeek: Int = 2,
    val specialWaLimitMin: Int = 90,
    val specialWaRecentMin: Int = 5,

    // Pausa de intención
    val pauseSeconds: Int = 10,
    val graceSeconds: Int = 60,
    val waIntentions: List<String> = DEFAULT_INTENTIONS,
    val mailIntentions: List<String> = DEFAULT_INTENTIONS,
    val newsIntentions: List<String> = listOf("Una noticia concreta", "Ponerme al día", "Algo de trabajo", "Mirar por mirar"),
    val carteleraIntentions: List<String> = listOf("Ver qué hay hoy", "Algo concreto", "Mirar por mirar"),

    // Blanco y negro y marcador
    val grayscaleWindows: List<TimeRange> = listOf(TimeRange(570, 810), TimeRange(960, 1170)),
    val grayscaleDuringDisconnect: Boolean = true,
    val overlayEnabled: Boolean = true,

    // Desconexión total
    val disconnectCode: String = "",
    val allDayEndTime: Int = 1260,
    val emergencyWaitMin: Int = 60,
    val autoReplyMinHours: Int = 12,
    val autoReplyText: String = "Este es un mensaje automático, estoy en periodo de desconexión de internet, si necesitas algo urgente puedes llamarme por teléfono",
    val deleteWaNotifications: Boolean = true,
    val whitelist: List<String> = emptyList(),

    // Resumen diario y cálculo de vida
    val summaryTime: Int = 1200,
    val birthDate: String = "",
    val lifeYears: Int = 80,
) {
    fun intentionsFor(t: Target): List<String> = when (t) {
        Target.WHATSAPP -> waIntentions
        Target.MAIL_APP, Target.WEBMAIL -> mailIntentions
        Target.NEWS -> newsIntentions
        Target.CARTELERA -> carteleraIntentions
    }.ifEmpty { DEFAULT_INTENTIONS }

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("dayStartMinute", dayStartMinute)
        put("waPackages", JSONArray(waPackages))
        put("waFreeWindows", TimeRange.listToString(waFreeWindows))
        put("waIntervalWindows", TimeRange.listToString(waIntervalWindows))
        put("waIntervalMin", waIntervalMin); put("waFreeEntries", waFreeEntries)
        put("waDailyLimitMin", waDailyLimitMin); put("waSplitTime", waSplitTime)
        put("waBeforeSplitLimitMin", waBeforeSplitLimitMin); put("waAfterSplitLimitMin", waAfterSplitLimitMin)
        put("mailPackages", JSONArray(mailPackages))
        put("mailFreeWindows", TimeRange.listToString(mailFreeWindows))
        put("mailFreeEntries", mailFreeEntries); put("mailRecentMin", mailRecentMin)
        put("mailRecentWaitSec", mailRecentWaitSec)
        put("webmailDomains", JSONArray(webmailDomains)); put("webmailIntervalMin", webmailIntervalMin)
        put("newsBlockedWindows", TimeRange.listToString(newsBlockedWindows))
        put("newsCountedSites", JSONArray(newsCountedSites)); put("newsEntriesPerSite", newsEntriesPerSite)
        put("newsTimedDomains", JSONArray(newsTimedDomains)); put("newsTimedLimitMin", newsTimedLimitMin)
        put("newsWildcardPerWeek", newsWildcardPerWeek); put("newsWildcardMin", newsWildcardMin)
        put("carteleraPackages", JSONArray(carteleraPackages))
        put("carteleraDailyLimitMin", carteleraDailyLimitMin)
        put("carteleraFreeEntriesPerHour", carteleraFreeEntriesPerHour)
        put("carteleraWaitsSec", JSONArray(carteleraWaitsSec))
        put("carteleraFreeDaysPerWeek", carteleraFreeDaysPerWeek)
        put("freePackages", JSONArray(freePackages))
        put("wildcardsPerDay", wildcardsPerDay); put("wildcardMin", wildcardMin)
        put("specialDaysPerWeek", specialDaysPerWeek); put("specialWaLimitMin", specialWaLimitMin)
        put("specialWaRecentMin", specialWaRecentMin)
        put("pauseSeconds", pauseSeconds); put("graceSeconds", graceSeconds)
        put("waIntentions", JSONArray(waIntentions)); put("mailIntentions", JSONArray(mailIntentions))
        put("newsIntentions", JSONArray(newsIntentions)); put("carteleraIntentions", JSONArray(carteleraIntentions))
        put("grayscaleWindows", TimeRange.listToString(grayscaleWindows))
        put("grayscaleDuringDisconnect", grayscaleDuringDisconnect); put("overlayEnabled", overlayEnabled)
        put("disconnectCode", disconnectCode); put("allDayEndTime", allDayEndTime)
        put("emergencyWaitMin", emergencyWaitMin); put("autoReplyMinHours", autoReplyMinHours)
        put("autoReplyText", autoReplyText); put("deleteWaNotifications", deleteWaNotifications)
        put("whitelist", JSONArray(whitelist))
        put("summaryTime", summaryTime); put("birthDate", birthDate); put("lifeYears", lifeYears)
    }

    companion object {
        val DEFAULT_INTENTIONS = listOf("Responder a alguien", "Algo de trabajo", "Algo concreto que buscar", "Mirar por mirar")

        private fun JSONObject.strList(k: String, def: List<String>): List<String> {
            val a = optJSONArray(k) ?: return def
            return (0 until a.length()).map { a.getString(it) }
        }

        private fun JSONObject.intList(k: String, def: List<Int>): List<Int> {
            val a = optJSONArray(k) ?: return def
            return (0 until a.length()).map { a.getInt(it) }
        }

        private fun JSONObject.ranges(k: String, def: List<TimeRange>): List<TimeRange> =
            if (has(k)) TimeRange.parseList(optString(k)) ?: def else def

        fun fromJson(o: JSONObject): Profile {
            val d = Profile()
            return Profile(
                name = o.optString("name", d.name),
                dayStartMinute = o.optInt("dayStartMinute", d.dayStartMinute),
                waPackages = o.strList("waPackages", d.waPackages),
                waFreeWindows = o.ranges("waFreeWindows", d.waFreeWindows),
                waIntervalWindows = o.ranges("waIntervalWindows", d.waIntervalWindows),
                waIntervalMin = o.optInt("waIntervalMin", d.waIntervalMin),
                waFreeEntries = o.optInt("waFreeEntries", d.waFreeEntries),
                waDailyLimitMin = o.optInt("waDailyLimitMin", d.waDailyLimitMin),
                waSplitTime = o.optInt("waSplitTime", d.waSplitTime),
                waBeforeSplitLimitMin = o.optInt("waBeforeSplitLimitMin", d.waBeforeSplitLimitMin),
                waAfterSplitLimitMin = o.optInt("waAfterSplitLimitMin", d.waAfterSplitLimitMin),
                mailPackages = o.strList("mailPackages", d.mailPackages),
                mailFreeWindows = o.ranges("mailFreeWindows", d.mailFreeWindows),
                mailFreeEntries = o.optInt("mailFreeEntries", d.mailFreeEntries),
                mailRecentMin = o.optInt("mailRecentMin", d.mailRecentMin),
                mailRecentWaitSec = o.optInt("mailRecentWaitSec", d.mailRecentWaitSec),
                webmailDomains = o.strList("webmailDomains", d.webmailDomains),
                webmailIntervalMin = o.optInt("webmailIntervalMin", d.webmailIntervalMin),
                newsBlockedWindows = o.ranges("newsBlockedWindows", d.newsBlockedWindows),
                newsCountedSites = o.strList("newsCountedSites", d.newsCountedSites),
                newsEntriesPerSite = o.optInt("newsEntriesPerSite", d.newsEntriesPerSite),
                newsTimedDomains = o.strList("newsTimedDomains", d.newsTimedDomains),
                newsTimedLimitMin = o.optInt("newsTimedLimitMin", d.newsTimedLimitMin),
                newsWildcardPerWeek = o.optInt("newsWildcardPerWeek", d.newsWildcardPerWeek),
                newsWildcardMin = o.optInt("newsWildcardMin", d.newsWildcardMin),
                carteleraPackages = o.strList("carteleraPackages", d.carteleraPackages),
                carteleraDailyLimitMin = o.optInt("carteleraDailyLimitMin", d.carteleraDailyLimitMin),
                carteleraFreeEntriesPerHour = o.optInt("carteleraFreeEntriesPerHour", d.carteleraFreeEntriesPerHour),
                carteleraWaitsSec = o.intList("carteleraWaitsSec", d.carteleraWaitsSec),
                carteleraFreeDaysPerWeek = o.optInt("carteleraFreeDaysPerWeek", d.carteleraFreeDaysPerWeek),
                freePackages = o.strList("freePackages", d.freePackages),
                wildcardsPerDay = o.optInt("wildcardsPerDay", d.wildcardsPerDay),
                wildcardMin = o.optInt("wildcardMin", d.wildcardMin),
                specialDaysPerWeek = o.optInt("specialDaysPerWeek", d.specialDaysPerWeek),
                specialWaLimitMin = o.optInt("specialWaLimitMin", d.specialWaLimitMin),
                specialWaRecentMin = o.optInt("specialWaRecentMin", d.specialWaRecentMin),
                pauseSeconds = o.optInt("pauseSeconds", d.pauseSeconds),
                graceSeconds = o.optInt("graceSeconds", d.graceSeconds),
                waIntentions = o.strList("waIntentions", d.waIntentions),
                mailIntentions = o.strList("mailIntentions", d.mailIntentions),
                newsIntentions = o.strList("newsIntentions", d.newsIntentions),
                carteleraIntentions = o.strList("carteleraIntentions", d.carteleraIntentions),
                grayscaleWindows = o.ranges("grayscaleWindows", d.grayscaleWindows),
                grayscaleDuringDisconnect = o.optBoolean("grayscaleDuringDisconnect", d.grayscaleDuringDisconnect),
                overlayEnabled = o.optBoolean("overlayEnabled", d.overlayEnabled),
                disconnectCode = o.optString("disconnectCode", d.disconnectCode),
                allDayEndTime = o.optInt("allDayEndTime", d.allDayEndTime),
                emergencyWaitMin = o.optInt("emergencyWaitMin", d.emergencyWaitMin),
                autoReplyMinHours = o.optInt("autoReplyMinHours", d.autoReplyMinHours),
                autoReplyText = o.optString("autoReplyText", d.autoReplyText),
                deleteWaNotifications = o.optBoolean("deleteWaNotifications", d.deleteWaNotifications),
                whitelist = o.strList("whitelist", d.whitelist),
                summaryTime = o.optInt("summaryTime", d.summaryTime),
                birthDate = o.optString("birthDate", d.birthDate),
                lifeYears = o.optInt("lifeYears", d.lifeYears),
            )
        }
    }
}

/** El perfil de este móvil. */
object Profiles {
    @Volatile private var cache: Profile? = null

    fun active(): Profile {
        cache?.let { return it }
        val raw = Store.getString("profile")
        val p = if (raw == null) Profile() else try {
            Profile.fromJson(org.json.JSONObject(raw))
        } catch (e: Exception) {
            Profile()
        }
        cache = p
        return p
    }

    @Synchronized
    fun save(p: Profile) {
        cache = p
        Store.putString("profile", p.toJson().toString())
    }

    fun update(transform: (Profile) -> Profile) = save(transform(active()))
}
