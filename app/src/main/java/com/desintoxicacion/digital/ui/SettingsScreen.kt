package com.desintoxicacion.digital.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.desintoxicacion.digital.BuildConfig
import com.desintoxicacion.digital.core.Disconnect
import com.desintoxicacion.digital.core.Profile
import com.desintoxicacion.digital.core.Profiles
import com.desintoxicacion.digital.core.Scheduler
import com.desintoxicacion.digital.core.Server
import com.desintoxicacion.digital.core.Store
import com.desintoxicacion.digital.core.T
import com.desintoxicacion.digital.core.TimeRange
import com.desintoxicacion.digital.core.UpdateInfo
import com.desintoxicacion.digital.core.Updater
import java.time.LocalDate

@Composable
fun SettingsScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var unlocked by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    if (editing) {
        ProfileEditor(modifier, onClose = {
            editing = false
            refresh++
            Scheduler.scheduleDaily(ctx)
        })
        return
    }
    val pk = picking
    if (pk != null) {
        val p = Profiles.active()
        val (title, sub, initial) = when (pk) {
            "cartelera" -> Triple("App Cartelera", "La app con límite de 30 min y esperas por entradas seguidas.", p.carteleraPackages)
            "free" -> Triple("Apps siempre libres", "Sin pausas, esperas ni límites, ni siquiera en la desconexión total.", p.freePackages)
            else -> Triple("Apps permitidas", "Se pueden usar durante la desconexión total.", p.whitelist)
        }
        AppListPicker(title, sub, initial.toSet(), emptyList(), onSave = { sel ->
            Profiles.update {
                when (pk) {
                    "cartelera" -> it.copy(carteleraPackages = sel.toList())
                    "free" -> it.copy(freePackages = sel.toList())
                    else -> it.copy(whitelist = sel.toList())
                }
            }
            picking = null
        }, onCancel = { picking = null })
        return
    }

    val dcActive = Disconnect.isActive()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ScreenTitle("Ajustes", "${Store.userName} · versión ${BuildConfig.VERSION_NAME}")

        PermissionsCard(refresh)
        UpdatesCard()

        when {
            dcActive -> SectionCard {
                Text("Ajustes bloqueados", fontWeight = FontWeight.Bold)
                MutedText("No se pueden cambiar durante la desconexión total.")
            }
            !unlocked -> SectionCard("Cambiar ajustes") {
                MutedText("Para cambiar horarios, límites o listas de apps, escribe tu código de activación.")
                Field("Código", code, { code = it; codeError = false }, number = true)
                if (codeError) Text("Código incorrecto.", color = Warn)
                BigButton("Desbloquear ajustes", {
                    if (code.trim() == Profiles.active().disconnectCode) {
                        unlocked = true
                        code = ""
                    } else codeError = true
                })
            }
            else -> {
                SectionCard("Horarios y límites") {
                    MutedText("Todas las reglas: WhatsApp, correo, periódicos, Cartelera, comodines, pausas y desconexión.")
                    Spacer(Modifier.height(6.dp))
                    BigButton("Editar", { editing = true })
                }
                SectionCard("Listas de apps") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { picking = "whitelist" }) { Text("Permitidas") }
                        OutlinedButton(onClick = { picking = "cartelera" }) { Text("Cartelera") }
                        OutlinedButton(onClick = { picking = "free" }) { Text("Libres") }
                    }
                }
                EmailsCard()
                SectionCard("Web") {
                    var url by remember { mutableStateOf(Store.serverUrl) }
                    Field("Dirección de la web", url, { url = it; Store.serverUrl = it })
                }
                TextButton(onClick = { unlocked = false }) { Text("Volver a bloquear los ajustes") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmailsCard() {
    var emails by remember { mutableStateOf(Store.unlockEmails) }
    var status by remember { mutableStateOf<String?>(null) }
    SectionCard("Persona de confianza") {
        MutedText("Emails que reciben los avisos y el código para terminar antes una desconexión.")
        Field("Emails (separados por comas)", emails, { emails = it }, singleLine = false)
        BigButton("Guardar", {
            status = "Guardando…"
            Server.call("setEmails", mapOf("emails" to emails)) { _, err ->
                if (err == null) Store.unlockEmails = emails
                status = err ?: "Guardado."
            }
        })
        status?.let { MutedText(it) }
    }
}

@Composable
private fun UpdatesCard() {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    SectionCard("Actualizaciones") {
        MutedText("Versión instalada: ${BuildConfig.VERSION_NAME}")
        val i = info
        if (i == null) {
            OutlinedButton(onClick = {
                status = "Comprobando…"
                Updater.check { u -> info = u; status = if (u == null) "Tienes la última versión." else null }
            }) { Text("Buscar actualizaciones") }
        } else {
            Text("Hay una versión nueva.", fontWeight = FontWeight.Bold)
            BigButton("Actualizar", {
                if (!Updater.canInstall(ctx)) {
                    status = "Permite a la app instalar actualizaciones y vuelve a pulsar."
                    Updater.openInstallPermission(ctx)
                } else {
                    status = "Descargando…"
                    Updater.downloadAndInstall(ctx, i) { err -> status = err }
                }
            })
        }
        status?.let { MutedText(it) }
    }
}

@Composable
private fun PermissionsCard(refresh: Int) {
    val ctx = LocalContext.current
    val items = remember(refresh) {
        listOf(
            Triple("Servicio de accesibilidad", Perms.accessibility(ctx)) { Perms.openAccessibility(ctx) },
            Triple("Acceso al uso de apps", Perms.usage(ctx)) { Perms.openUsage(ctx) },
            Triple("Acceso a notificaciones", Perms.notificationListener(ctx)) { Perms.openNotificationListener(ctx) },
            Triple("Avisos de la app", Perms.postNotifications(ctx)) { Perms.openAppDetails(ctx) },
            Triple("Instalar actualizaciones", Updater.canInstall(ctx)) { Updater.openInstallPermission(ctx) },
        )
    }
    val gray = remember(refresh) { Perms.grayscale(ctx) }
    SectionCard("Permisos") {
        items.forEach { (label, ok, open) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f))
                if (ok) Text("Activo ✓", color = Good) else TextButton(onClick = open) { Text("Activar", color = Warn) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Blanco y negro", modifier = Modifier.weight(1f))
            Text(if (gray) "Activo ✓" else "Sin permiso", color = if (gray) Good else Warn)
        }
        if (!gray) SelectionContainer {
            Text(GRAYSCALE_ADB, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

// ---------------------------------------------------------------------------------- editor

/** Estado editable del perfil: todo como texto para escribir libremente. */
private class ProfileForm(p: Profile) {
    private fun r(l: List<TimeRange>) = TimeRange.listToString(l)
    private fun l(x: List<String>) = x.joinToString(", ")

    var dayStart by mutableStateOf(T.fmtMinute(p.dayStartMinute))
    var waFree by mutableStateOf(r(p.waFreeWindows))
    var waInterval by mutableStateOf(r(p.waIntervalWindows))
    var waIntervalMin by mutableStateOf(p.waIntervalMin.toString())
    var waFreeEntries by mutableStateOf(p.waFreeEntries.toString())
    var waDaily by mutableStateOf(p.waDailyLimitMin.toString())
    var waSplit by mutableStateOf(T.fmtMinute(p.waSplitTime))
    var waBefore by mutableStateOf(p.waBeforeSplitLimitMin.toString())
    var waAfter by mutableStateOf(p.waAfterSplitLimitMin.toString())
    var waPackages by mutableStateOf(l(p.waPackages))
    var mailFree by mutableStateOf(r(p.mailFreeWindows))
    var mailFreeEntries by mutableStateOf(p.mailFreeEntries.toString())
    var mailRecent by mutableStateOf(p.mailRecentMin.toString())
    var mailRecentWait by mutableStateOf(p.mailRecentWaitSec.toString())
    var mailPackages by mutableStateOf(l(p.mailPackages))
    var webmailDomains by mutableStateOf(l(p.webmailDomains))
    var webmailInterval by mutableStateOf(p.webmailIntervalMin.toString())
    var newsBlocked by mutableStateOf(r(p.newsBlockedWindows))
    var newsCounted by mutableStateOf(l(p.newsCountedSites))
    var newsEntries by mutableStateOf(p.newsEntriesPerSite.toString())
    var newsTimed by mutableStateOf(l(p.newsTimedDomains))
    var newsTimedLimit by mutableStateOf(p.newsTimedLimitMin.toString())
    var newsWildWeek by mutableStateOf(p.newsWildcardPerWeek.toString())
    var newsWildMin by mutableStateOf(p.newsWildcardMin.toString())
    var cartDaily by mutableStateOf(p.carteleraDailyLimitMin.toString())
    var cartFreeHour by mutableStateOf(p.carteleraFreeEntriesPerHour.toString())
    var cartWaits by mutableStateOf(p.carteleraWaitsSec.joinToString(", "))
    var cartFreeDays by mutableStateOf(p.carteleraFreeDaysPerWeek.toString())
    var wildPerDay by mutableStateOf(p.wildcardsPerDay.toString())
    var wildMin by mutableStateOf(p.wildcardMin.toString())
    var specialWeek by mutableStateOf(p.specialDaysPerWeek.toString())
    var specialWa by mutableStateOf(p.specialWaLimitMin.toString())
    var specialRecent by mutableStateOf(p.specialWaRecentMin.toString())
    var pauseSec by mutableStateOf(p.pauseSeconds.toString())
    var grace by mutableStateOf(p.graceSeconds.toString())
    var waInt by mutableStateOf(l(p.waIntentions))
    var mailInt by mutableStateOf(l(p.mailIntentions))
    var newsInt by mutableStateOf(l(p.newsIntentions))
    var cartInt by mutableStateOf(l(p.carteleraIntentions))
    var gray by mutableStateOf(r(p.grayscaleWindows))
    var grayDc by mutableStateOf(p.grayscaleDuringDisconnect)
    var overlay by mutableStateOf(p.overlayEnabled)
    var dcCode by mutableStateOf(p.disconnectCode)
    var allDayEnd by mutableStateOf(T.fmtMinute(p.allDayEndTime))
    var emergencyMin by mutableStateOf(p.emergencyWaitMin.toString())
    var autoHours by mutableStateOf(p.autoReplyMinHours.toString())
    var autoText by mutableStateOf(p.autoReplyText)
    var deleteNotif by mutableStateOf(p.deleteWaNotifications)
    var summary by mutableStateOf(T.fmtMinute(p.summaryTime))
    var birth by mutableStateOf(p.birthDate)
    var lifeYears by mutableStateOf(p.lifeYears.toString())

    fun build(base: Profile): Pair<Profile?, String?> {
        fun list(s: String) = s.split(",", "\n", ";").map { it.trim() }.filter { it.isNotEmpty() }
        fun num(s: String, what: String): Int = s.trim().toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("\"$what\" tiene que ser un número.")
        fun nums(s: String, what: String): List<Int> = list(s).map { num(it, what) }
        fun time(s: String, what: String): Int = TimeRange.parseTime(s)
            ?: throw IllegalArgumentException("\"$what\" tiene que ser una hora como 20:00.")
        fun ranges(s: String, what: String): List<TimeRange> = TimeRange.parseList(s)
            ?: throw IllegalArgumentException("\"$what\" tiene que ser como 09:30-13:30, 16:00-19:00.")

        return try {
            if (dcCode.trim().length !in 4..8) throw IllegalArgumentException("El código de activación debe tener entre 4 y 8 cifras.")
            if (birth.isNotBlank()) try { LocalDate.parse(birth.trim()) } catch (e: Exception) {
                throw IllegalArgumentException("La fecha de nacimiento tiene que ser como 1990-01-31.")
            }
            base.copy(
                dayStartMinute = time(dayStart, "Hora de inicio del día"),
                waFreeWindows = ranges(waFree, "Franjas libres de WhatsApp"),
                waIntervalWindows = ranges(waInterval, "Franjas con espera de WhatsApp"),
                waIntervalMin = num(waIntervalMin, "Espera entre entradas"),
                waFreeEntries = num(waFreeEntries, "Entradas sin pausa"),
                waDailyLimitMin = num(waDaily, "Límite diario de WhatsApp"),
                waSplitTime = time(waSplit, "Hora de corte"),
                waBeforeSplitLimitMin = num(waBefore, "Límite antes del corte"),
                waAfterSplitLimitMin = num(waAfter, "Límite después del corte"),
                waPackages = list(waPackages),
                mailFreeWindows = ranges(mailFree, "Franjas libres de Gmail"),
                mailFreeEntries = num(mailFreeEntries, "Entradas sin pausa en Gmail"),
                mailRecentMin = num(mailRecent, "Minutos de Gmail"),
                mailRecentWaitSec = num(mailRecentWait, "Espera de Gmail"),
                mailPackages = list(mailPackages),
                webmailDomains = list(webmailDomains),
                webmailIntervalMin = num(webmailInterval, "Espera de Hotmail"),
                newsBlockedWindows = ranges(newsBlocked, "Franjas bloqueadas de periódicos"),
                newsCountedSites = list(newsCounted),
                newsEntriesPerSite = num(newsEntries, "Entradas por periódico"),
                newsTimedDomains = list(newsTimed),
                newsTimedLimitMin = num(newsTimedLimit, "Límite del resto de periódicos"),
                newsWildcardPerWeek = num(newsWildWeek, "Comodines de periódicos por semana"),
                newsWildcardMin = num(newsWildMin, "Minutos del comodín de periódicos"),
                carteleraDailyLimitMin = num(cartDaily, "Límite diario de Cartelera"),
                carteleraFreeEntriesPerHour = num(cartFreeHour, "Entradas libres por hora"),
                carteleraWaitsSec = nums(cartWaits, "Esperas de Cartelera"),
                carteleraFreeDaysPerWeek = num(cartFreeDays, "Días libres de Cartelera"),
                wildcardsPerDay = num(wildPerDay, "Comodines al día"),
                wildcardMin = num(wildMin, "Minutos del comodín"),
                specialDaysPerWeek = num(specialWeek, "Días especiales por semana"),
                specialWaLimitMin = num(specialWa, "Máximo de WhatsApp en día especial"),
                specialWaRecentMin = num(specialRecent, "Minutos para la pausa en día especial"),
                pauseSeconds = num(pauseSec, "Segundos de pausa"),
                graceSeconds = num(grace, "Margen para volver"),
                waIntentions = list(waInt),
                mailIntentions = list(mailInt),
                newsIntentions = list(newsInt),
                carteleraIntentions = list(cartInt),
                grayscaleWindows = ranges(gray, "Franjas en blanco y negro"),
                grayscaleDuringDisconnect = grayDc,
                overlayEnabled = overlay,
                disconnectCode = dcCode.trim(),
                allDayEndTime = time(allDayEnd, "Fin de \"Todo el día\""),
                emergencyWaitMin = num(emergencyMin, "Espera de la salida de emergencia"),
                autoReplyMinHours = num(autoHours, "Horas para responder en WhatsApp"),
                autoReplyText = autoText.trim(),
                deleteWaNotifications = deleteNotif,
                summaryTime = time(summary, "Hora del resumen"),
                birthDate = birth.trim(),
                lifeYears = num(lifeYears, "Edad para el cálculo"),
            ) to null
        } catch (e: IllegalArgumentException) {
            null to e.message
        }
    }
}

@Composable
private fun ProfileEditor(modifier: Modifier, onClose: () -> Unit) {
    val base = remember { Profiles.active() }
    val f = remember { ProfileForm(base) }
    var error by remember { mutableStateOf<String?>(null) }
    val franjas = "Como 09:30-13:30, 16:00-19:00."

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ScreenTitle("Horarios y límites", base.name)

        ColorCard(WaTint) {
            Text("WhatsApp", fontWeight = FontWeight.ExtraBold, color = WaTint.fg)
            Field("Franjas libres", f.waFree, { f.waFree = it }, help = franjas)
            Field("Entradas sin pausa en cada franja libre", f.waFreeEntries, { f.waFreeEntries = it }, number = true)
            Field("Franjas con espera entre entradas", f.waInterval, { f.waInterval = it })
            Field("Espera entre entradas (min)", f.waIntervalMin, { f.waIntervalMin = it }, number = true)
            Field("Límite diario (min)", f.waDaily, { f.waDaily = it }, number = true)
            Field("Hora de corte", f.waSplit, { f.waSplit = it })
            Field("Límite antes del corte (min)", f.waBefore, { f.waBefore = it }, number = true)
            Field("Límite después del corte (min)", f.waAfter, { f.waAfter = it }, number = true)
            Field("Intenciones", f.waInt, { f.waInt = it }, singleLine = false, help = "Separadas por comas.")
            Field("Paquetes", f.waPackages, { f.waPackages = it }, help = "Avanzado. com.whatsapp es WhatsApp.")
        }
        ColorCard(MailTint) {
            Text("Correo", fontWeight = FontWeight.ExtraBold, color = MailTint.fg)
            Field("Franjas libres de Gmail", f.mailFree, { f.mailFree = it })
            Field("Entradas sin pausa en cada franja", f.mailFreeEntries, { f.mailFreeEntries = it }, number = true)
            Field("Fuera de franja: si vuelves antes de (min)", f.mailRecent, { f.mailRecent = it }, number = true)
            Field("…espera de (segundos)", f.mailRecentWait, { f.mailRecentWait = it }, number = true)
            Field("Hotmail: una entrada cada (min)", f.webmailInterval, { f.webmailInterval = it }, number = true)
            Field("Webs de Hotmail", f.webmailDomains, { f.webmailDomains = it }, singleLine = false)
            Field("Intenciones", f.mailInt, { f.mailInt = it }, singleLine = false)
            Field("Paquetes de Gmail", f.mailPackages, { f.mailPackages = it }, help = "Avanzado.")
        }
        ColorCard(NewsTint) {
            Text("Periódicos", fontWeight = FontWeight.ExtraBold, color = NewsTint.fg)
            Field("Franjas bloqueadas", f.newsBlocked, { f.newsBlocked = it })
            Field("Periódicos con límite de entradas", f.newsCounted, { f.newsCounted = it }, singleLine = false,
                help = "Separados por comas; dominios del mismo periódico unidos con | (elpais.com|elpais.es).")
            Field("Entradas al día por periódico", f.newsEntries, { f.newsEntries = it }, number = true)
            Field("Periódicos con límite de tiempo", f.newsTimed, { f.newsTimed = it }, singleLine = false)
            Field("Tiempo diario entre todos (min)", f.newsTimedLimit, { f.newsTimedLimit = it }, number = true)
            Field("Comodines de periódicos por semana", f.newsWildWeek, { f.newsWildWeek = it }, number = true)
            Field("Duración del comodín (min)", f.newsWildMin, { f.newsWildMin = it }, number = true)
            Field("Intenciones", f.newsInt, { f.newsInt = it }, singleLine = false)
        }
        ColorCard(CartTint) {
            Text("Cartelera", fontWeight = FontWeight.ExtraBold, color = CartTint.fg)
            Field("Límite diario (min)", f.cartDaily, { f.cartDaily = it }, number = true)
            Field("Entradas sin espera por hora", f.cartFreeHour, { f.cartFreeHour = it }, number = true)
            Field("Esperas siguientes (segundos)", f.cartWaits, { f.cartWaits = it }, help = "Como 30, 60, 300. La última se repite.")
            Field("Días libres por semana", f.cartFreeDays, { f.cartFreeDays = it }, number = true)
            Field("Intenciones", f.cartInt, { f.cartInt = it }, singleLine = false)
        }
        ColorCard(YellowTintCard) {
            Text("Comodines y días especiales", fontWeight = FontWeight.ExtraBold, color = YellowFg)
            Field("Comodines de urgencia al día", f.wildPerDay, { f.wildPerDay = it }, number = true)
            Field("Duración del comodín (min)", f.wildMin, { f.wildMin = it }, number = true)
            Field("Días especiales por semana", f.specialWeek, { f.specialWeek = it }, number = true)
            Field("WhatsApp máximo en día especial (min)", f.specialWa, { f.specialWa = it }, number = true)
            Field("Pausa si vuelves antes de (min)", f.specialRecent, { f.specialRecent = it }, number = true)
        }
        SectionCard("Pausa, marcador y día") {
            Field("Segundos de pausa", f.pauseSec, { f.pauseSec = it }, number = true)
            Field("Margen para volver sin nueva entrada (s)", f.grace, { f.grace = it }, number = true)
            Field("El día empieza a las", f.dayStart, { f.dayStart = it })
            Field("Franjas en blanco y negro", f.gray, { f.gray = it })
            SwitchRow("Blanco y negro en la desconexión total", f.grayDc) { f.grayDc = it }
            SwitchRow("Marcador en pantalla", f.overlay) { f.overlay = it }
        }
        SectionCard("Desconexión total") {
            Field("Código de activación", f.dcCode, { f.dcCode = it }, number = true,
                help = "También sirve para los días especiales y para entrar aquí.")
            Field("\"Todo el día\" termina a las", f.allDayEnd, { f.allDayEnd = it })
            Field("Salida de emergencia tras (min)", f.emergencyMin, { f.emergencyMin = it }, number = true)
            Field("Responder en WhatsApp desde (horas)", f.autoHours, { f.autoHours = it }, number = true)
            Field("Mensaje automático", f.autoText, { f.autoText = it }, singleLine = false)
            SwitchRow("Borrar siempre las notificaciones de WhatsApp", f.deleteNotif) { f.deleteNotif = it }
        }
        SectionCard("Resumen diario") {
            Field("Hora del resumen", f.summary, { f.summary = it })
            Field("Fecha de nacimiento (AAAA-MM-DD)", f.birth, { f.birth = it })
            Field("Edad para el cálculo", f.lifeYears, { f.lifeYears = it }, number = true)
        }

        error?.let { Text(it, color = Warn, modifier = Modifier.padding(vertical = 8.dp)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Cancelar") }
            BigButton("Guardar", {
                val (p, err) = f.build(Profiles.active())
                if (p == null) error = err else {
                    Profiles.save(p)
                    onClose()
                }
            })
        }
        Spacer(Modifier.height(28.dp))
    }
}

private val YellowTintCard = Tint(YellowBg, YellowFg, YellowFg, YellowBg)
