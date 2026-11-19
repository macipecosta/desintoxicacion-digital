package com.desintoxicacion.digital.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Contadores del día lógico en curso. Se reinician solos a la hora de inicio del día (2:00). */
class DayState(
    val date: String,
    var waMs: Long = 0,
    var waBeforeMs: Long = 0,
    var waAfterMs: Long = 0,
    var newsTimedMs: Long = 0,
    var carteleraMs: Long = 0,
    var avoided: Int = 0,
    var wildcardsUsed: Int = 0,
    var wildcardUntil: Long = 0,
    var newsWildcardUntil: Long = 0,
    var special: Boolean = false,
    var carteleraFree: Boolean = false,
    var summarySent: Boolean = false,
    /** Entradas por clave: "WHATSAPP@570" (franja libre), "news:elpais.com|elpais.es"… */
    val counts: MutableMap<String, Int> = mutableMapOf(),
    /** Veces que se eligió cada intención hoy. */
    val intentions: MutableMap<String, Int> = mutableMapOf(),
) {
    fun count(key: String): Int = counts[key] ?: 0
    fun inc(key: String) { counts[key] = count(key) + 1 }

    fun toJson(): JSONObject = JSONObject().apply {
        put("date", date); put("waMs", waMs); put("waBeforeMs", waBeforeMs); put("waAfterMs", waAfterMs)
        put("newsTimedMs", newsTimedMs); put("carteleraMs", carteleraMs); put("avoided", avoided)
        put("wildcardsUsed", wildcardsUsed); put("wildcardUntil", wildcardUntil)
        put("newsWildcardUntil", newsWildcardUntil); put("special", special)
        put("carteleraFree", carteleraFree); put("summarySent", summarySent)
        put("counts", JSONObject(counts as Map<*, *>)); put("intentions", JSONObject(intentions as Map<*, *>))
    }

    companion object {
        private fun JSONObject.intMap(k: String): MutableMap<String, Int> {
            val out = mutableMapOf<String, Int>()
            optJSONObject(k)?.let { o -> o.keys().forEach { key -> out[key] = o.optInt(key) } }
            return out
        }

        fun fromJson(o: JSONObject) = DayState(
            date = o.optString("date"),
            waMs = o.optLong("waMs"), waBeforeMs = o.optLong("waBeforeMs"), waAfterMs = o.optLong("waAfterMs"),
            newsTimedMs = o.optLong("newsTimedMs"), carteleraMs = o.optLong("carteleraMs"),
            avoided = o.optInt("avoided"), wildcardsUsed = o.optInt("wildcardsUsed"),
            wildcardUntil = o.optLong("wildcardUntil"), newsWildcardUntil = o.optLong("newsWildcardUntil"),
            special = o.optBoolean("special"), carteleraFree = o.optBoolean("carteleraFree"),
            summarySent = o.optBoolean("summarySent"),
            counts = o.intMap("counts"), intentions = o.intMap("intentions"),
        )
    }
}

data class DayUsage(val totalMs: Long, val unlocks: Int, val perApp: Map<String, Long>) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("total", totalMs); put("unlocks", unlocks)
        put("apps", JSONObject().apply { perApp.forEach { (k, v) -> put(k, v) } })
    }

    companion object {
        fun fromJson(o: JSONObject): DayUsage {
            val apps = mutableMapOf<String, Long>()
            o.optJSONObject("apps")?.let { a -> a.keys().forEach { k -> apps[k] = a.optLong(k) } }
            return DayUsage(o.optLong("total"), o.optInt("unlocks"), apps)
        }
    }
}

/** Uso de un comodín, día especial, etc. type: wildcard, news, special, cartelera, emergency, disconnect. */
data class HistoryEvent(val time: Long, val type: String, val detail: String = "")

/** Todo lo que la app guarda en el móvil (SharedPreferences privadas). */
object Store {
    private lateinit var sp: SharedPreferences
    private var dayCache: DayState? = null

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("desintox2", Context.MODE_PRIVATE)
        if (sp.getString("installDate", null) == null) {
            sp.edit().putString("installDate", LocalDate.now().toString()).apply()
        }
    }

    fun getString(k: String): String? = sp.getString(k, null)
    fun putString(k: String, v: String?) = sp.edit().putString(k, v).apply()

    private fun bool(k: String, d: Boolean = false) = sp.getBoolean(k, d)
    private fun setBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    private fun long(k: String) = sp.getLong(k, 0L)
    private fun setLong(k: String, v: Long) = sp.edit().putLong(k, v).apply()

    // ---------- Cuenta y servidor ----------
    var setupDone: Boolean
        get() = bool("setupDone")
        set(v) = setBool("setupDone", v)
    var serverUrl: String
        get() = sp.getString("serverUrl", null) ?: com.desintoxicacion.digital.BuildConfig.SERVER_URL
        set(v) = sp.edit().putString("serverUrl", v.trim()).apply()
    var userId: String
        get() = sp.getString("userId", "") ?: ""
        set(v) = sp.edit().putString("userId", v).apply()
    var secret: String
        get() = sp.getString("secret", "") ?: ""
        set(v) = sp.edit().putString("secret", v).apply()
    var userName: String
        get() = sp.getString("userName", "") ?: ""
        set(v) = sp.edit().putString("userName", v).apply()
    var isAdmin: Boolean
        get() = bool("isAdmin")
        set(v) = setBool("isAdmin", v)
    var unlockEmails: String
        get() = sp.getString("unlockEmails", "") ?: ""
        set(v) = sp.edit().putString("unlockEmails", v).apply()
    val registered: Boolean get() = userId.isNotEmpty() && secret.isNotEmpty()

    var grayByApp: Boolean
        get() = bool("grayByApp")
        set(v) = setBool("grayByApp", v)
    var overlayX: Int
        get() = sp.getInt("overlayX", 24)
        set(v) = sp.edit().putInt("overlayX", v).apply()
    var overlayY: Int
        get() = sp.getInt("overlayY", 120)
        set(v) = sp.edit().putInt("overlayY", v).apply()

    val installDate: LocalDate
        get() = LocalDate.parse(sp.getString("installDate", null) ?: LocalDate.now().toString())

    // ---------- Día en curso ----------

    @Synchronized
    fun day(): DayState {
        val today = T.today().toString()
        val d = dayCache ?: sp.getString("day", null)?.let {
            try { DayState.fromJson(JSONObject(it)) } catch (e: Exception) { null }
        }
        if (d != null && d.date == today) {
            dayCache = d
            return d
        }
        if (d != null) archiveDay(d)
        val fresh = DayState(today)
        saveDay(fresh)
        return fresh
    }

    @Synchronized
    fun saveDay(d: DayState) {
        dayCache = d
        sp.edit().putString("day", d.toJson().toString()).apply()
    }

    @Synchronized
    fun updateDay(block: (DayState) -> Unit) {
        val d = day()
        block(d)
        saveDay(d)
    }

    private fun archiveDay(d: DayState) {
        val o = JSONObject(sp.getString("dayHistory", "{}") ?: "{}")
        o.put(d.date, JSONObject().put("avoided", d.avoided).put("intentions", JSONObject(d.intentions as Map<*, *>)))
        sp.edit().putString("dayHistory", o.toString()).apply()
    }

    /** Entradas evitadas e intenciones de un día pasado (o de hoy). */
    fun dayHistory(date: LocalDate): Pair<Int, Map<String, Int>> {
        if (date == T.today()) {
            val d = day()
            return d.avoided to d.intentions.toMap()
        }
        val o = JSONObject(sp.getString("dayHistory", "{}") ?: "{}").optJSONObject(date.toString())
            ?: return 0 to emptyMap()
        val m = mutableMapOf<String, Int>()
        o.optJSONObject("intentions")?.let { i -> i.keys().forEach { k -> m[k] = i.optInt(k) } }
        return o.optInt("avoided") to m
    }

    // ---------- Última salida y entradas recientes ----------

    fun lastExit(key: String): Long = long("exit_$key")
    fun setLastExit(key: String, ms: Long) = setLong("exit_$key", ms)

    @Synchronized
    fun recentEntries(key: String, withinMs: Long): List<Long> {
        val a = JSONArray(sp.getString("entries_$key", "[]") ?: "[]")
        val from = T.now() - withinMs
        return (0 until a.length()).map { a.getLong(it) }.filter { it >= from }
    }

    @Synchronized
    fun addEntry(key: String, ms: Long) {
        val list = recentEntries(key, 24 * 3_600_000L) + ms
        sp.edit().putString("entries_$key", JSONArray(list).toString()).apply()
    }

    // ---------- Historial de comodines y días especiales ----------

    @Synchronized
    fun history(): List<HistoryEvent> {
        val a = JSONArray(sp.getString("history", "[]") ?: "[]")
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            HistoryEvent(o.optLong("t"), o.optString("type"), o.optString("detail"))
        }
    }

    @Synchronized
    fun addHistory(e: HistoryEvent) {
        val a = JSONArray(sp.getString("history", "[]") ?: "[]")
        a.put(JSONObject().put("t", e.time).put("type", e.type).put("detail", e.detail))
        sp.edit().putString("history", a.toString()).apply()
    }

    fun usesThisWeek(type: String): Int {
        val from = T.weekStart()
        return history().count { it.type == type && it.time >= from }
    }

    // ---------- Desconexión total ----------

    var dcStart: Long
        get() = long("dcStart")
        set(v) = setLong("dcStart", v)
    var dcEnd: Long
        get() = long("dcEnd")
        set(v) = setLong("dcEnd", v)
    var dcEmergencyAt: Long
        get() = long("dcEmergencyAt")
        set(v) = setLong("dcEmergencyAt", v)
    var dcReplied: Set<String>
        get() = sp.getStringSet("dcReplied", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("dcReplied", v).apply()

    // ---------- Caché de estadísticas de días cerrados ----------

    @Synchronized
    fun cachedDay(date: LocalDate): DayUsage? {
        val o = JSONObject(sp.getString("statsCache", "{}") ?: "{}")
        return o.optJSONObject(date.toString())?.let { DayUsage.fromJson(it) }
    }

    @Synchronized
    fun cacheDay(date: LocalDate, u: DayUsage) {
        val o = JSONObject(sp.getString("statsCache", "{}") ?: "{}")
        o.put(date.toString(), u.toJson())
        sp.edit().putString("statsCache", o.toString()).apply()
    }
}
