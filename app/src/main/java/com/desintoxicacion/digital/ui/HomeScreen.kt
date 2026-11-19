package com.desintoxicacion.digital.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desintoxicacion.digital.core.DayUsage
import com.desintoxicacion.digital.core.Disconnect
import com.desintoxicacion.digital.core.GuardService
import com.desintoxicacion.digital.core.Life
import com.desintoxicacion.digital.core.Profiles
import com.desintoxicacion.digital.core.Rules
import com.desintoxicacion.digital.core.Specials
import com.desintoxicacion.digital.core.Stats
import com.desintoxicacion.digital.core.Store
import com.desintoxicacion.digital.core.T
import com.desintoxicacion.digital.core.TimeRange
import com.desintoxicacion.digital.core.UpdateInfo
import com.desintoxicacion.digital.core.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun HomeScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(T.now()) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = T.now()
        }
    }
    val slowKey = now / 30_000 + refresh
    val usage by produceState<Pair<DayUsage, DayUsage>?>(null, slowKey) {
        value = withContext(Dispatchers.IO) { Stats.todayVsYesterday(ctx.applicationContext) }
    }
    val streak by produceState(0, slowKey / 10) {
        value = withContext(Dispatchers.IO) { Stats.streakDecreasing(ctx.applicationContext) }
    }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) { Updater.check { update = it } }

    val p = Profiles.active()
    val d = Store.day()
    val dcActive = Disconnect.isActive(now)

    var dialog by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fechaLarga(T.today()), color = Muted, fontSize = 13.sp)
                Text("¡Hola, ${Store.userName.ifBlank { "Maci" }}!", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (streak > 0) Pill("Racha · $streak ${if (streak == 1) "día" else "días"}")
        }

        update?.let { u -> UpdateCard(u) }

        if (!GuardService.running) {
            ColorCard(NewsTint) {
                Text("El vigilante está apagado", color = NewsTint.fg, fontWeight = FontWeight.Bold)
                MutedText("Sin el servicio de accesibilidad no se aplican los límites.", NewsTint.fg)
                OutlinedButton(onClick = { Perms.openAccessibility(ctx) }) { Text("Activar") }
            }
        }

        if (dcActive) DisconnectActiveCard(now, onChanged = { refresh++ }, onInfo = { info = it })

        // ----- Resumen oscuro -----
        ColorCard(DarkTint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Móvil hoy", color = Color(0xFFC9CBD3), fontSize = 13.sp)
                    val u = usage
                    Text(if (u == null) "…" else T.mins(u.first.totalMs), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    if (u != null) {
                        val diff = u.first.totalMs - u.second.totalMs
                        Text(
                            when {
                                u.second.totalMs == 0L -> "${u.first.unlocks} desbloqueos"
                                diff <= 0 -> "${T.mins(-diff)} menos que ayer · ${u.first.unlocks} desbl."
                                else -> "${T.mins(diff)} más que ayer · ${u.first.unlocks} desbl."
                            },
                            color = if (u.second.totalMs > 0 && diff > 0) Color(0xFFFFB38A) else DarkTint.strong,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                val u = usage
                if (u != null && Life.daysLeft(p) > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(String.format(T.ES, "%.1f", Life.yearsOnPhone(p, u.first.totalMs)), color = Color(0xFFFFD27A), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text("años de vida\na este ritmo", color = Color(0xFFC9CBD3), fontSize = 11.sp)
                    }
                }
            }
        }

        if (d.special || d.carteleraFree || Rules.wildcardActive(d, now) || Rules.newsWildcardActive(d, now)) {
            ActiveSpecials(d.special, d.carteleraFree, if (Rules.wildcardActive(d, now)) d.wildcardUntil else 0, if (Rules.newsWildcardActive(d, now)) d.newsWildcardUntil else 0)
        }

        // ----- Apartados -----
        val waLimit = if (d.special) p.specialWaLimitMin else p.waDailyLimitMin
        CategoryCard(WaTint, d.waMs.toFloat() / (waLimit * 60_000f), "WhatsApp", "${T.clock(d.waMs)} de $waLimit min", Rules.waStatus(p, d, now))
        if (!d.special) {
            MutedText("Hasta las ${T.fmtMinute(p.waSplitTime)}: ${T.mins(d.waBeforeMs)} de ${p.waBeforeSplitLimitMin} · después: ${T.mins(d.waAfterMs)} de ${p.waAfterSplitLimitMin}")
        }
        CategoryCard(NewsTint, d.newsTimedMs.toFloat() / (p.newsTimedLimitMin * 60_000f), "Periódicos", Rules.newsStatus(p, d, now), null)
        CategoryCard(MailTint, null, "Correo", Rules.mailStatus(p, d, now), Rules.webmailStatus(p, d, now))
        if (p.carteleraPackages.isNotEmpty()) {
            CategoryCard(CartTint, d.carteleraMs.toFloat() / (p.carteleraDailyLimitMin * 60_000f), "Cartelera", "${T.mins(d.carteleraMs)} de ${p.carteleraDailyLimitMin} min", Rules.carteleraStatus(p, d, now))
        }

        // ----- Botones -----
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(
                if (d.wildcardsUsed < p.wildcardsPerDay) "Comodín" else "Comodín usado",
                { dialog = "wildcard" }, Modifier.weight(1f), bg = YellowBg, fg = YellowFg,
                enabled = d.wildcardsUsed < p.wildcardsPerDay && !dcActive,
            )
            BigButton("Desconexión total", { dialog = "disconnect" }, Modifier.weight(2f), enabled = !dcActive)
        }
        Spacer(Modifier.height(14.dp))
        Text("Días especiales", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        SpecialRow("Día especial de conexión total", "${Store.usesThisWeek("special")}/${p.specialDaysPerWeek} esta semana", WaTint, !d.special && !dcActive) { dialog = "special" }
        SpecialRow("Comodín de periódicos (${p.newsWildcardMin} min)", "${Store.usesThisWeek("news")}/${p.newsWildcardPerWeek} esta semana", NewsTint, !Rules.newsWildcardActive(d, now) && !dcActive) { dialog = "news" }
        if (p.carteleraPackages.isNotEmpty()) {
            SpecialRow("Día libre de Cartelera", "${Store.usesThisWeek("cartelera")}/${p.carteleraFreeDaysPerWeek} esta semana", CartTint, !d.carteleraFree && !dcActive) { dialog = "cartelera" }
        }
        Spacer(Modifier.height(28.dp))
    }

    when (dialog) {
        "wildcard" -> ConfirmDialog(
            title = "Comodín de urgencia",
            text = "Durante ${p.wildcardMin} min podrás entrar en WhatsApp y el correo sin esperas entre entradas, siempre con la cuenta atrás de ${p.pauseSeconds} s. No amplía el límite diario de ${p.waDailyLimitMin} min. Solo tienes ${p.wildcardsPerDay} al día.",
            needCode = false,
            onDismiss = { dialog = null },
            onConfirm = { info = Specials.wildcard(p) ?: "Comodín activo hasta las ${T.hhmm(T.now() + p.wildcardMin * 60_000L)}."; dialog = null; refresh++ },
        )
        "special" -> ConfirmDialog(
            title = "Día especial de conexión total",
            text = "Hasta las ${T.fmtMinute(p.dayStartMinute)}: WhatsApp sin franjas ni esperas (máximo ${p.specialWaLimitMin} min en el día y pausa si vuelves en menos de ${p.specialWaRecentMin} min) y correo libre. Periódicos y Cartelera mantienen sus reglas.",
            needCode = true,
            onDismiss = { dialog = null },
            onConfirm = { info = Specials.specialDay(p) ?: "Día especial activado hasta las ${T.fmtMinute(p.dayStartMinute)}."; dialog = null; refresh++ },
        )
        "news" -> ConfirmDialog(
            title = "Comodín de periódicos",
            text = "Durante ${p.newsWildcardMin} min podrás entrar en todos los periódicos las veces que quieras, también en las franjas bloqueadas. Tienes ${p.newsWildcardPerWeek} por semana.",
            needCode = true,
            onDismiss = { dialog = null },
            onConfirm = { info = Specials.newsWildcard(p) ?: "Periódicos libres hasta las ${T.hhmm(T.now() + p.newsWildcardMin * 60_000L)}."; dialog = null; refresh++ },
        )
        "cartelera" -> ConfirmDialog(
            title = "Día libre de Cartelera",
            text = "Hasta las ${T.fmtMinute(p.dayStartMinute)}, la Cartelera no tendrá ninguna limitación. Tienes ${p.carteleraFreeDaysPerWeek} por semana.",
            needCode = true,
            onDismiss = { dialog = null },
            onConfirm = { info = Specials.carteleraFree(p) ?: "Cartelera libre hasta las ${T.fmtMinute(p.dayStartMinute)}."; dialog = null; refresh++ },
        )
        "disconnect" -> DisconnectDialog(
            onDismiss = { dialog = null },
            onStarted = { msg -> dialog = null; info = msg; refresh++ },
        )
    }
    info?.let { msg ->
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = { TextButton(onClick = { info = null }) { Text("Vale") } },
            text = { Text(msg) },
        )
    }
}

private fun fechaLarga(d: LocalDate): String {
    val dias = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    return "${dias[d.dayOfWeek.value - 1]} ${d.dayOfMonth} de ${meses[d.monthValue - 1]}"
}

@Composable
private fun CategoryCard(tint: Tint, fraction: Float?, title: String, line1: String, line2: String?) {
    ColorCard(tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fraction != null) {
                Ring(fraction, tint.strong, tint.track)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold, color = tint.fg, fontSize = 16.sp)
                Text(line1, color = tint.fg, fontSize = 14.sp)
                if (line2 != null) Text(line2, color = tint.fg, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveSpecials(special: Boolean, cartelera: Boolean, wildcardUntil: Long, newsUntil: Long) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        if (special) Pill("Día especial activo", WaTint.strong)
        if (wildcardUntil > 0) Pill("Comodín hasta las ${T.hhmm(wildcardUntil)}", YellowBg, YellowFg)
        if (newsUntil > 0) Pill("Periódicos libres hasta las ${T.hhmm(newsUntil)}", NewsTint.strong)
        if (cartelera) Pill("Cartelera libre hoy", CartTint.strong)
    }
}

@Composable
private fun SpecialRow(title: String, sub: String, tint: Tint, enabled: Boolean, onClick: () -> Unit) {
    ColorCard(tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = tint.fg)
                Text(sub, fontSize = 13.sp, color = tint.fg)
            }
            OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp)) {
                Text("Activar", color = if (enabled) tint.fg else Muted)
            }
        }
    }
}

@Composable
private fun UpdateCard(u: UpdateInfo) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    ColorCard(MailTint) {
        Text("Nueva versión disponible", fontWeight = FontWeight.ExtraBold, color = MailTint.fg)
        if (u.notes.isNotBlank()) MutedText(u.notes, MailTint.fg)
        Spacer(Modifier.height(8.dp))
        BigButton("Actualizar", {
            if (!Updater.canInstall(ctx)) {
                status = "Permite a la app instalar actualizaciones y vuelve a pulsar."
                Updater.openInstallPermission(ctx)
            } else {
                status = "Descargando…"
                Updater.downloadAndInstall(ctx, u) { err -> status = err }
            }
        }, Modifier.fillMaxWidth(), bg = MailTint.strong)
        status?.let { MutedText(it, MailTint.fg) }
    }
}

/** Diálogo de confirmación, con código de activación si [needCode]. */
@Composable
private fun ConfirmDialog(title: String, text: String, needCode: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text)
                if (needCode) {
                    Spacer(Modifier.height(8.dp))
                    Field("Código de activación", code, { code = it; error = false }, number = true)
                    if (error) Text("Código incorrecto.", color = Warn)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (needCode && code.trim() != Profiles.active().disconnectCode) error = true else onConfirm()
            }) { Text("Activar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisconnectDialog(onDismiss: () -> Unit, onStarted: (String) -> Unit) {
    val p = Profiles.active()
    var preset by remember { mutableStateOf("day") }
    var customMode by remember { mutableStateOf("for") }
    var hours by remember { mutableStateOf("4") }
    var minutes by remember { mutableStateOf("0") }
    var date by remember { mutableStateOf(T.dateOf(T.now()).toString()) }
    var time by remember { mutableStateOf("21:00") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun endMs(): Long? = when (preset) {
        "custom" -> if (customMode == "for") {
            val total = (hours.toIntOrNull() ?: 0) * 60L + (minutes.toIntOrNull() ?: 0)
            if (total <= 0) null else T.now() + total * 60_000L
        } else {
            val d = try { LocalDate.parse(date.trim()) } catch (_: Exception) { null }
            val m = TimeRange.parseTime(time)
            if (d == null || m == null) null else Disconnect.customEnd(d, m).takeIf { it > T.now() }
        }
        else -> Disconnect.presetEnd(preset, p)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Desconexión total") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MutedText("Se bloquean las apps que usan internet, salvo tus apps permitidas. Los datos, la zona wifi y las llamadas siguen funcionando.")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1h" to "1 h", "2h" to "2 h", "3h" to "3 h", "day" to "Todo el día", "2days" to "2 días", "custom" to "Personalizar").forEach { (k, label) ->
                        FilterChip(selected = preset == k, onClick = { preset = k }, label = { Text(label) })
                    }
                }
                if (preset == "custom") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = customMode == "for", onClick = { customMode = "for" }, label = { Text("Durante") })
                        FilterChip(selected = customMode == "until", onClick = { customMode = "until" }, label = { Text("Hasta") })
                    }
                    if (customMode == "for") {
                        Row {
                            Column(Modifier.weight(1f)) { Field("Horas", hours, { hours = it.filter(Char::isDigit) }, number = true) }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) { Field("Minutos", minutes, { minutes = it.filter(Char::isDigit) }, number = true) }
                        }
                    } else {
                        Field("Día (AAAA-MM-DD)", date, { date = it })
                        Field("Hora (HH:MM)", time, { time = it })
                    }
                }
                val end = endMs()
                Text(
                    if (end == null) "Elige una duración válida." else "Termina ${T.whenText(end)}.",
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp),
                )
                if (end != null && end - T.now() >= p.autoReplyMinHours * 3_600_000L) {
                    MutedText("WhatsApp responderá automáticamente a quien te escriba.")
                }
                Field("Código de activación", code, { code = it }, number = true)
                error?.let { Text(it, color = Warn) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val end = endMs()
                when {
                    end == null -> error = "Elige una duración válida."
                    code.trim() != p.disconnectCode -> error = "Código incorrecto."
                    else -> {
                        busy = true
                        Disconnect.start(end) { err ->
                            busy = false
                            onStarted(
                                "Desconectada hasta ${T.whenText(end)}." +
                                    (if (err != null) "\n\nNo se pudo avisar por email a tu persona de confianza: $err" else "\n\nTu persona de confianza ha recibido un aviso.")
                            )
                        }
                    }
                }
            }) { Text("Desconectar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DisconnectActiveCard(now: Long, onChanged: () -> Unit, onInfo: (String) -> Unit) {
    val p = Profiles.active()
    var code by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var askReason by remember { mutableStateOf(false) }
    val em = Store.dcEmergencyAt

    ColorCard(DarkTint) {
        Text("Desconexión total", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text("Hasta ${T.whenText(Disconnect.endTime())} · quedan ${T.mins(Disconnect.endTime() - now)}", color = Color(0xFFC9CBD3))
        if (Disconnect.autoReplyActive(p)) Text("WhatsApp está respondiendo automáticamente.", color = DarkTint.strong, fontSize = 13.sp)
        if (em > 0) {
            Spacer(Modifier.height(8.dp))
            Text("Salida de emergencia: termina a las ${T.hhmm(em)} (en ${T.mins(em - now)}).", color = Color(0xFFFFD27A), fontWeight = FontWeight.Bold)
            TextButton(onClick = { Disconnect.cancelEmergency(); onChanged() }) { Text("Anular la salida de emergencia", color = Color.White) }
        }
        Spacer(Modifier.height(10.dp))
        Text("Para terminar antes", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Pide un código a tu persona de confianza (${Store.unlockEmails.ifBlank { "sin email" }}).", color = Color(0xFFC9CBD3), fontSize = 13.sp)
        OutlinedButton(enabled = !busy, onClick = {
            busy = true
            status = "Enviando…"
            Disconnect.requestUnlock { err ->
                busy = false
                status = err ?: "Código enviado a tu persona de confianza."
            }
        }) { Text("Pedir código", color = Color.White) }
        Field("Código de desbloqueo", code, { code = it.filter(Char::isDigit) }, number = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Desbloquear", {
                busy = true
                status = "Comprobando…"
                Disconnect.tryUnlock(code) { err ->
                    busy = false
                    if (err == null) {
                        status = null
                        onChanged()
                        onInfo("Desconexión terminada.")
                    } else status = err
                }
            }, enabled = !busy && code.isNotBlank(), bg = DarkTint.strong, fg = Ink)
            if (em == 0L) {
                BigButton("Emergencia", { askReason = true }, bg = Color(0xFF3A3C44))
            }
        }
        status?.let { Text(it, color = Color(0xFFFFD27A), fontSize = 13.sp) }
    }

    if (askReason) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askReason = false },
            title = { Text("Salida de emergencia") },
            text = {
                Column {
                    Text("La desconexión terminará dentro de ${p.emergencyWaitMin} min. Tu persona de confianza recibirá un aviso con el motivo. Puedes anularla mientras tanto.")
                    Field("Motivo", reason, { reason = it }, singleLine = false)
                }
            },
            confirmButton = {
                TextButton(enabled = reason.isNotBlank(), onClick = {
                    askReason = false
                    Disconnect.emergency(reason.trim(), p) { err ->
                        onChanged()
                        if (err != null) onInfo("Salida de emergencia en marcha. No se pudo avisar por email: $err")
                    }
                    onChanged()
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { askReason = false }) { Text("Cancelar") } },
        )
    }
}
