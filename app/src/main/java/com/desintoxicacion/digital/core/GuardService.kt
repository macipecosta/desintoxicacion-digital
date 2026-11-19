package com.desintoxicacion.digital.core

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.desintoxicacion.digital.ui.BlockActivity
import com.desintoxicacion.digital.ui.PauseActivity

/**
 * El "vigilante": sabe qué app (o qué web de Chrome) está delante, cuenta el tiempo,
 * muestra la pausa de intención o la pantalla de bloqueo cuando toca, y mantiene el marcador.
 */
class GuardService : AccessibilityService() {

    companion object {
        @Volatile var running = false
        /** Sesión aprobada en la pausa (se entra sin volver a preguntar). */
        @Volatile var approved: Session? = null
        @Volatile var approvedAt = 0L
        /** Hasta cuándo WhatsApp se abre sin contar como entrada (para enviar invitaciones). */
        @Volatile var inviteUntil = 0L

        val CHROME = setOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary")
        private val IGNORED = setOf("com.android.systemui")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var fgPkg: String? = null
    private var chromeHost: String? = null
    private var current: Session? = null
    private var sessionStart = 0L
    private var lastTick = 0L
    private var lastUrlRead = 0L
    private var lastSlowCheck = 0L
    private var lastUsageCheck = 0L
    private var screenOn = true
    private var ime: Set<String> = emptySet()
    private var overlay: Overlay? = null
    @Volatile private var usageToday: DayUsage? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            try { tick() } catch (_: Exception) {}
            handler.postDelayed(this, 1000)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    current?.let { exitSession(it) }
                    fgPkg = null
                }
                Intent.ACTION_SCREEN_ON -> screenOn = true
                Intent.ACTION_USER_PRESENT -> {
                    screenOn = true
                    lastUsageCheck = 0L
                    rootInActiveWindow?.packageName?.toString()?.let { onForeground(it) }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        running = true
        ime = Disconnect.imePackages(this)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        overlay = Overlay(this)
        handler.post(tickRunnable)
        Scheduler.scheduleDaily(this)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tickRunnable)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        current?.let { exitSession(it) }
        overlay?.hide()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg in IGNORED || pkg in ime) return
                onForeground(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg in CHROME && pkg == fgPkg) {
                    val now = T.now()
                    if (now - lastUrlRead > 700) {
                        lastUrlRead = now
                        if (readChromeUrl(pkg)) evaluate()
                    }
                }
            }
        }
    }

    private fun onForeground(pkg: String) {
        fgPkg = pkg
        if (pkg in CHROME) readChromeUrl(pkg)
        evaluate()
    }

    /** Lee la barra de direcciones de Chrome. Devuelve true si cambió el dominio. */
    private fun readChromeUrl(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = try {
            root.findAccessibilityNodeInfosByViewId("$pkg:id/url_bar")
        } catch (_: Exception) {
            null
        }
        if (nodes.isNullOrEmpty()) return false
        val host = Domains.host(nodes[0].text?.toString() ?: "")
        val changed = host != chromeHost
        chromeHost = host
        return changed
    }

    private fun sessionFor(pkg: String): Session? {
        val p = Profiles.active()
        return when {
            pkg in p.freePackages -> null
            pkg in p.waPackages -> Session(Target.WHATSAPP)
            pkg in p.mailPackages -> Session(Target.MAIL_APP)
            pkg in p.carteleraPackages -> Session(Target.CARTELERA)
            pkg in CHROME -> {
                val host = chromeHost
                val counted = Domains.countedSite(host, p.newsCountedSites)
                when {
                    counted != null -> Session(Target.NEWS, counted)
                    Domains.matches(host, p.newsTimedDomains) -> Session(Target.NEWS, "timed")
                    Domains.matches(host, p.webmailDomains) -> Session(Target.WEBMAIL)
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun evaluate() {
        val pkg = fgPkg ?: return
        val now = T.now()
        if (pkg == packageName) {
            current?.let { exitSession(it) }
            return
        }
        if (Disconnect.isActive(now) && Disconnect.blocksPackage(this, pkg)) {
            current?.let { exitSession(it) }
            val em = Store.dcEmergencyAt
            val extra = if (em > 0) " La salida de emergencia termina ${T.whenText(em)}." else ""
            block("Desconexión total", "Estás desconectada hasta ${T.whenText(Disconnect.endTime())}. Las llamadas y los SMS siguen funcionando.$extra")
            return
        }
        val s = sessionFor(pkg)
        if (s == current) return
        current?.let { exitSession(it) }
        if (s != null) tryEnter(s, now)
    }

    private fun tryEnter(s: Session, now: Long) {
        val p = Profiles.active()
        val d = Store.day()

        // Invitación por WhatsApp desde la app: no cuenta como entrada.
        if (s.target == Target.WHATSAPP && now < inviteUntil) {
            startSession(s, now, record = false)
            return
        }

        val last = Store.lastExit(s.key)
        val ap = approved
        val isApproved = ap == s && now - approvedAt < 90_000
        val withinGrace = last > 0 && now - last < p.graceSeconds * 1000L

        if (isApproved || withinGrace) {
            val b = Rules.duringBlock(s, p, d, now)
            if (b != null) {
                block(b.title, b.message)
                return
            }
            if (isApproved) approved = null
            startSession(s, now, record = isApproved)
            return
        }

        when (val g = Rules.entryGate(s, p, d, now)) {
            is Gate.Allow -> startSession(s, now, record = true)
            is Gate.Pause -> showPause(s, g.seconds)
            is Gate.Block -> block(g.title, g.message)
        }
    }

    private fun startSession(s: Session, now: Long, record: Boolean) {
        if (record) Rules.recordEntry(s, Profiles.active(), now)
        current = s
        sessionStart = now
        lastTick = now
    }

    private fun exitSession(s: Session) {
        val now = T.now()
        accumulate(now)
        Store.setLastExit(s.key, now)
        current = null
    }

    private fun accumulate(now: Long) {
        val s = current ?: return
        val dt = (now - lastTick).coerceIn(0L, 5000L)
        lastTick = now
        if (dt == 0L) return
        val p = Profiles.active()
        when (s.target) {
            Target.WHATSAPP -> Store.updateDay {
                it.waMs += dt
                val m = T.minuteOfDay(now)
                if (m in p.dayStartMinute until p.waSplitTime) it.waBeforeMs += dt else it.waAfterMs += dt
            }
            Target.NEWS -> if (s.site == "timed" && !Rules.newsWildcardActive(Store.day(), now)) {
                Store.updateDay { it.newsTimedMs += dt }
            }
            Target.CARTELERA -> if (!Store.day().carteleraFree) Store.updateDay { it.carteleraMs += dt }
            Target.MAIL_APP, Target.WEBMAIL -> {}
        }
    }

    private fun tick() {
        val now = T.now()
        val s = current
        if (s != null && screenOn) {
            accumulate(now)
            val b = Rules.duringBlock(s, Profiles.active(), Store.day(), now)
            if (b != null) {
                exitSession(s)
                block(b.title, b.message)
            }
        }
        updateOverlay(now)
        if (now - lastSlowCheck > 30_000) {
            lastSlowCheck = now
            Grayscale.update(this)
            Summary.runIfDue(this)
            if (screenOn) evaluate()
        }
    }

    private fun updateOverlay(now: Long) {
        val p = Profiles.active()
        val ov = overlay ?: return
        if (!p.overlayEnabled || !screenOn) {
            ov.hide()
            return
        }
        ov.show()
        if (now - lastUsageCheck > 20_000) {
            lastUsageCheck = now
            val app = applicationContext
            Thread {
                try {
                    usageToday = Stats.compute(app, T.logicalStart(T.today()), T.now())
                } catch (_: Exception) {
                }
            }.start()
        }
        val u = usageToday
        val base = if (u != null) "${T.mins(u.totalMs)} · ${u.unlocks} desbl." else "…"
        val text = if (current?.target == Target.WHATSAPP) {
            val d = Store.day()
            val limit = if (d.special) p.specialWaLimitMin else p.waDailyLimitMin
            "WA ${T.clock(d.waMs)} / $limit · $base"
        } else base
        ov.setText(text)
    }

    private fun showPause(s: Session, seconds: Int) {
        val i = Intent(this, PauseActivity::class.java)
            .putExtra(PauseActivity.EXTRA_TARGET, s.target.name)
            .putExtra(PauseActivity.EXTRA_SITE, s.site)
            .putExtra(PauseActivity.EXTRA_SECONDS, seconds)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun block(title: String, message: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val i = Intent(this, BlockActivity::class.java)
            .putExtra(BlockActivity.EXTRA_TITLE, title)
            .putExtra(BlockActivity.EXTRA_MESSAGE, message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }
}
