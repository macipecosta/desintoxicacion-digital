package com.desintoxicacion.digital.core

/** Tipos de uso que la app controla. */
enum class Target(val label: String) {
    WHATSAPP("WhatsApp"),
    MAIL_APP("Gmail"),
    WEBMAIL("Hotmail"),
    NEWS("el periódico"),
    CARTELERA("Cartelera"),
}

/** Lo que está abierto: tipo y, en periódicos, qué web (grupo de dominios o "timed"). */
data class Session(val target: Target, val site: String = "") {
    /** Clave para recordar la última salida. */
    val key: String get() = if (target == Target.NEWS) "news:$site" else target.name
}

sealed class Gate {
    object Allow : Gate()
    data class Pause(val seconds: Int) : Gate()
    /** [skippable] = bloqueo por espera entre entradas de WhatsApp, que el comodín puede saltar. */
    data class Block(val title: String, val message: String, val skippable: Boolean = false) : Gate()
}

object Domains {
    /** Extrae el dominio de lo que muestra la barra de direcciones de Chrome. */
    fun host(text: String): String? {
        var s = text.trim().lowercase()
        if (s.isEmpty()) return null
        s = s.removePrefix("https://").removePrefix("http://")
        val cut = s.indexOfFirst { it == '/' || it == '?' || it == '#' || it == ' ' }
        if (cut >= 0) s = s.substring(0, cut)
        s = s.substringBefore(':').removePrefix("www.")
        return s.ifEmpty { null }
    }

    fun matchesOne(host: String?, domain: String): Boolean {
        if (host == null) return false
        val d = domain.trim().lowercase().removePrefix("www.")
        return d.isNotEmpty() && (host == d || host.endsWith(".$d"))
    }

    fun matches(host: String?, list: List<String>): Boolean = list.any { matchesOne(host, it) }

    /** Grupo de periódico con límite de entradas ("elpais.com|elpais.es"), o null. */
    fun countedSite(host: String?, groups: List<String>): String? =
        groups.firstOrNull { g -> g.split("|").any { matchesOne(host, it) } }

    /** Nombre corto para mostrar: el primer dominio del grupo. */
    fun siteLabel(site: String): String = if (site == "timed") "periódicos" else site.split("|").first()
}

/** Reglas del perfil: si se puede entrar, con qué pausa, y cuándo hay que cortar. */
object Rules {
    private const val MIN = 60_000L

    private fun window(list: List<TimeRange>, minute: Int): TimeRange? = list.firstOrNull { it.contains(minute) }
    private fun inAny(list: List<TimeRange>, minute: Int) = window(list, minute) != null

    fun wildcardActive(d: DayState, now: Long) = now < d.wildcardUntil
    fun newsWildcardActive(d: DayState, now: Long) = now < d.newsWildcardUntil

    /** Clave de la franja libre en la que estamos, para contar sus 3 entradas libres. */
    fun freeWindowKey(t: Target, p: Profile, now: Long): String? {
        val list = when (t) {
            Target.WHATSAPP -> p.waFreeWindows
            Target.MAIL_APP -> p.mailFreeWindows
            else -> return null
        }
        val w = window(list, T.minuteOfDay(now)) ?: return null
        return "${t.name}@${w.start}"
    }

    // ---------------------------------------------------------------- límites de tiempo

    private fun untilReset(now: Long) = "Se desbloquea ${T.whenText(T.nextReset(now))}."

    fun waLimitBlock(p: Profile, d: DayState, now: Long): Gate.Block? {
        if (d.special) {
            return if (d.waMs >= p.specialWaLimitMin * MIN) Gate.Block(
                "Límite del día especial",
                "Has usado ${T.mins(d.waMs)} de WhatsApp hoy, el máximo del día especial. ${untilReset(now)}"
            ) else null
        }
        val m = T.minuteOfDay(now)
        if (d.waMs >= p.waDailyLimitMin * MIN) return Gate.Block(
            "Límite diario de WhatsApp", "Has llegado a los ${p.waDailyLimitMin} min de hoy. ${untilReset(now)}"
        )
        val beforeSplit = m in p.dayStartMinute until p.waSplitTime
        if (beforeSplit && d.waBeforeMs >= p.waBeforeSplitLimitMin * MIN) return Gate.Block(
            "Límite de la mañana",
            "Has usado los ${p.waBeforeSplitLimitMin} min disponibles hasta las ${T.fmtMinute(p.waSplitTime)}. Vuelve a partir de esa hora."
        )
        if (!beforeSplit && d.waAfterMs >= p.waAfterSplitLimitMin * MIN) return Gate.Block(
            "Límite de la tarde",
            "Has usado los ${p.waAfterSplitLimitMin} min disponibles desde las ${T.fmtMinute(p.waSplitTime)}. ${untilReset(now)}"
        )
        return null
    }

    fun newsLimitBlock(site: String, p: Profile, d: DayState, now: Long, entering: Boolean): Gate.Block? {
        if (newsWildcardActive(d, now)) return null
        val m = T.minuteOfDay(now)
        window(p.newsBlockedWindows, m)?.let { w ->
            return Gate.Block(
                "Periódicos bloqueados",
                "Los periódicos están bloqueados ahora. Vuelven a las ${T.fmtMinute(w.end)}."
            )
        }
        if (site == "timed") {
            if (d.newsTimedMs >= p.newsTimedLimitMin * MIN) return Gate.Block(
                "Límite de periódicos",
                "Ya has dedicado ${p.newsTimedLimitMin} min a estos periódicos hoy. ${untilReset(now)}"
            )
        } else if (entering && d.count("news:$site") >= p.newsEntriesPerSite) {
            return Gate.Block(
                "${Domains.siteLabel(site)} cerrado por hoy",
                "Ya has entrado ${p.newsEntriesPerSite} veces hoy. ${untilReset(now)}"
            )
        }
        return null
    }

    fun carteleraLimitBlock(p: Profile, d: DayState, now: Long): Gate.Block? {
        if (d.carteleraFree) return null
        return if (d.carteleraMs >= p.carteleraDailyLimitMin * MIN) Gate.Block(
            "Límite de Cartelera", "Has llegado a los ${p.carteleraDailyLimitMin} min de hoy. ${untilReset(now)}"
        ) else null
    }

    /** Límites que se vigilan mientras la app está abierta. */
    fun duringBlock(s: Session, p: Profile, d: DayState, now: Long): Gate.Block? = when (s.target) {
        Target.WHATSAPP -> waLimitBlock(p, d, now)
        Target.NEWS -> newsLimitBlock(s.site, p, d, now, entering = false)
        Target.CARTELERA -> carteleraLimitBlock(p, d, now)
        Target.MAIL_APP, Target.WEBMAIL -> null
    }

    // ---------------------------------------------------------------- entradas

    fun entryGate(s: Session, p: Profile, d: DayState, now: Long): Gate {
        val last = Store.lastExit(s.key)
        val pause = Gate.Pause(p.pauseSeconds)
        return when (s.target) {
            Target.WHATSAPP -> {
                waLimitBlock(p, d, now)?.let { return it }
                when {
                    d.special -> if (last > 0 && now - last < p.specialWaRecentMin * MIN) pause else Gate.Allow
                    wildcardActive(d, now) -> pause
                    inAny(p.waIntervalWindows, T.minuteOfDay(now)) -> {
                        val next = last + p.waIntervalMin * MIN
                        if (last > 0 && now < next) Gate.Block(
                            "Todavía no",
                            "Ahora solo puedes entrar en WhatsApp una vez cada ${p.waIntervalMin} min. La próxima entrada es a las ${T.hhmm(next)}. Si es urgente, usa el comodín desde la app.",
                            skippable = true
                        ) else pause
                    }
                    else -> freeWindowGate(s.target, p, d, now, p.waFreeEntries, pause)
                }
            }
            Target.MAIL_APP -> when {
                d.special -> Gate.Allow
                wildcardActive(d, now) -> pause
                freeWindowKey(s.target, p, now) != null -> freeWindowGate(s.target, p, d, now, p.mailFreeEntries, pause)
                last > 0 && now - last < p.mailRecentMin * MIN -> Gate.Pause(p.mailRecentWaitSec)
                else -> Gate.Allow
            }
            Target.WEBMAIL -> when {
                d.special -> Gate.Allow
                wildcardActive(d, now) -> pause
                else -> {
                    val next = last + p.webmailIntervalMin * MIN
                    if (last > 0 && now < next) Gate.Block(
                        "Todavía no",
                        "Solo puedes entrar en Hotmail una vez cada ${p.webmailIntervalMin} min. La próxima entrada es a las ${T.hhmm(next)}."
                    ) else pause
                }
            }
            Target.NEWS -> newsLimitBlock(s.site, p, d, now, entering = true) ?: pause
            Target.CARTELERA -> {
                if (d.carteleraFree) return Gate.Allow
                carteleraLimitBlock(p, d, now)?.let { return it }
                val n = Store.recentEntries("cartelera", 60 * MIN).size
                if (n < p.carteleraFreeEntriesPerHour) Gate.Allow
                else {
                    val waits = p.carteleraWaitsSec.ifEmpty { listOf(300) }
                    Gate.Pause(waits[(n - p.carteleraFreeEntriesPerHour).coerceAtMost(waits.size - 1)])
                }
            }
        }
    }

    private fun freeWindowGate(t: Target, p: Profile, d: DayState, now: Long, free: Int, pause: Gate.Pause): Gate {
        val key = freeWindowKey(t, p, now) ?: return pause
        return if (d.count(key) < free) Gate.Allow else pause
    }

    /** Apunta una entrada nueva (no las vueltas en menos de 1 minuto). */
    fun recordEntry(s: Session, p: Profile, now: Long) {
        when (s.target) {
            Target.WHATSAPP, Target.MAIL_APP -> freeWindowKey(s.target, p, now)?.let { k -> Store.updateDay { it.inc(k) } }
            Target.NEWS -> if (s.site != "timed") Store.updateDay { it.inc("news:${s.site}") }
            Target.CARTELERA -> Store.addEntry("cartelera", now)
            Target.WEBMAIL -> {}
        }
    }

    // ---------------------------------------------------------------- textos para la pantalla Hoy

    fun waStatus(p: Profile, d: DayState, now: Long): String {
        waLimitBlock(p, d, now)?.let { return it.title }
        if (d.special) return "Día especial: hasta ${p.specialWaLimitMin} min"
        if (wildcardActive(d, now)) return "Comodín activo hasta las ${T.hhmm(d.wildcardUntil)}"
        val m = T.minuteOfDay(now)
        if (inAny(p.waIntervalWindows, m)) {
            val last = Store.lastExit(Target.WHATSAPP.name)
            val next = last + p.waIntervalMin * MIN
            return if (last > 0 && now < next) "Próxima entrada a las ${T.hhmm(next)}" else "Puedes entrar (1 cada ${p.waIntervalMin} min)"
        }
        val key = freeWindowKey(Target.WHATSAPP, p, now)
        val left = if (key != null) (p.waFreeEntries - d.count(key)).coerceAtLeast(0) else 0
        return if (left > 0) "Franja libre · $left entradas sin pausa" else "Franja libre · con pausa"
    }

    fun mailStatus(p: Profile, d: DayState, now: Long): String {
        if (d.special) return "Libre (día especial)"
        val key = freeWindowKey(Target.MAIL_APP, p, now)
        if (key != null) {
            val left = (p.mailFreeEntries - d.count(key)).coerceAtLeast(0)
            return if (left > 0) "Gmail libre · $left sin pausa" else "Gmail con pausa"
        }
        val last = Store.lastExit(Target.MAIL_APP.name)
        return if (last > 0 && now - last < p.mailRecentMin * MIN) "Gmail: espera de ${T.secs(p.mailRecentWaitSec)} hasta las ${T.hhmm(last + p.mailRecentMin * MIN)}"
        else "Gmail: entrada directa"
    }

    fun webmailStatus(p: Profile, d: DayState, now: Long): String {
        if (d.special) return "Hotmail libre"
        val last = Store.lastExit(Target.WEBMAIL.name)
        val next = last + p.webmailIntervalMin * MIN
        return if (last > 0 && now < next) "Hotmail a las ${T.hhmm(next)}" else "Hotmail disponible"
    }

    fun newsStatus(p: Profile, d: DayState, now: Long): String {
        if (newsWildcardActive(d, now)) return "Comodín de periódicos hasta las ${T.hhmm(d.newsWildcardUntil)}"
        window(p.newsBlockedWindows, T.minuteOfDay(now))?.let { return "Bloqueados hasta las ${T.fmtMinute(it.end)}" }
        val counted = p.newsCountedSites.joinToString(" · ") {
            "${Domains.siteLabel(it)} ${d.count("news:$it")}/${p.newsEntriesPerSite}"
        }
        return "$counted · resto ${T.mins(d.newsTimedMs)} de ${p.newsTimedLimitMin}"
    }

    fun carteleraStatus(p: Profile, d: DayState, now: Long): String {
        if (d.carteleraFree) return "Día libre de Cartelera"
        carteleraLimitBlock(p, d, now)?.let { return it.title }
        val n = Store.recentEntries("cartelera", 60 * MIN).size
        return "$n entradas en la última hora"
    }
}

/** Comodines y días especiales. Cada función devuelve null si ha ido bien o el motivo si no. */
object Specials {
    fun wildcard(p: Profile): String? {
        val d = Store.day()
        if (d.wildcardsUsed >= p.wildcardsPerDay) return "Ya has usado el comodín de hoy."
        val until = T.now() + p.wildcardMin * 60_000L
        Store.updateDay { it.wildcardsUsed++; it.wildcardUntil = until }
        Store.addHistory(HistoryEvent(T.now(), "wildcard"))
        return null
    }

    fun newsWildcard(p: Profile): String? {
        if (Store.usesThisWeek("news") >= p.newsWildcardPerWeek) return "Ya has usado el comodín de periódicos esta semana."
        val until = T.now() + p.newsWildcardMin * 60_000L
        Store.updateDay { it.newsWildcardUntil = until }
        Store.addHistory(HistoryEvent(T.now(), "news"))
        return null
    }

    fun specialDay(p: Profile): String? {
        if (Store.day().special) return "El día especial ya está activo."
        if (Store.usesThisWeek("special") >= p.specialDaysPerWeek) return "Ya has usado los ${p.specialDaysPerWeek} días especiales de esta semana."
        Store.updateDay { it.special = true }
        Store.addHistory(HistoryEvent(T.now(), "special"))
        return null
    }

    fun carteleraFree(p: Profile): String? {
        if (Store.day().carteleraFree) return "El día libre de Cartelera ya está activo."
        if (Store.usesThisWeek("cartelera") >= p.carteleraFreeDaysPerWeek) return "Ya has usado los ${p.carteleraFreeDaysPerWeek} días libres de Cartelera de esta semana."
        Store.updateDay { it.carteleraFree = true }
        Store.addHistory(HistoryEvent(T.now(), "cartelera"))
        return null
    }

    fun label(type: String): String = when (type) {
        "wildcard" -> "Comodín de urgencia"
        "news" -> "Comodín de periódicos"
        "special" -> "Día especial de conexión"
        "cartelera" -> "Día libre de Cartelera"
        "emergency" -> "Salida de emergencia"
        "disconnect" -> "Desconexión total"
        else -> type
    }
}
