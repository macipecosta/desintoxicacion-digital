package com.desintoxicacion.digital.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.desintoxicacion.digital.core.Profile
import com.desintoxicacion.digital.core.Profiles
import com.desintoxicacion.digital.core.Server
import com.desintoxicacion.digital.core.Store
import com.desintoxicacion.digital.core.Updater

const val GRAYSCALE_ADB = "adb shell pm grant com.desintoxicacion.digital android.permission.WRITE_SECURE_SETTINGS"

@Composable
fun SetupScreen(onDone: () -> Unit) {
    var registered by remember { mutableStateOf(Store.registered && Profiles.active().disconnectCode.isNotEmpty()) }
    if (!registered) {
        RedeemScreen(onRegistered = { registered = true })
    } else {
        PermissionsSetup(onDone)
    }
}

/** Primer paso: canjear el código de invitación. */
@Composable
private fun RedeemScreen(onRegistered: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(Store.serverUrl) }
    var showServer by remember { mutableStateOf(!Server.configured()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var needCode by remember { mutableStateOf(Store.registered && Profiles.active().disconnectCode.isEmpty()) }
    var newCode by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(24.dp))
        Pill("Desintoxicación Digital", bg = WaTint.bg, fg = WaTint.fg)
        Spacer(Modifier.height(16.dp))
        Text(if (needCode) "Elige tu código" else "¡Bienvenida!", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))

        if (needCode) {
            MutedText("Tu perfil ya existe. Elige un código de activación (4 a 8 cifras): lo usarás para la desconexión total, los días especiales y los ajustes.")
            ColorCard {
                Field("Código de activación", newCode, { newCode = it.filter(Char::isDigit) }, number = true)
                error?.let { Text(it, color = Warn) }
                Spacer(Modifier.height(8.dp))
                BigButton("Guardar", {
                    if (newCode.length !in 4..8) error = "Debe tener entre 4 y 8 cifras."
                    else {
                        Profiles.update { it.copy(disconnectCode = newCode) }
                        onRegistered()
                    }
                }, Modifier.fillMaxWidth())
            }
        } else {
        MutedText("Escribe el código de invitación que te dio la página de alta (por ejemplo, K7Q2-M9XD).")
        ColorCard {
            Field("Código de invitación", code, { code = it.uppercase() })
            if (showServer) {
                Field("Dirección de la web", server, { server = it },
                    help = "La dirección que termina en /exec. Te la da la administradora.")
            } else {
                TextButton(onClick = { showServer = true }) { Text("Cambiar la dirección de la web") }
            }
            error?.let { Text(it, color = Warn, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(8.dp))
            BigButton(if (busy) "Comprobando…" else "Continuar", {
                error = null
                busy = true
                Store.serverUrl = server
                Server.call("redeem", mapOf("code" to code), auth = false) { o, err ->
                    busy = false
                    if (o == null) {
                        error = err
                        return@call
                    }
                    Store.userId = o.optString("userId")
                    Store.secret = o.optString("secret")
                    Store.userName = o.optString("name")
                    Store.isAdmin = o.optBoolean("isAdmin")
                    val emails = o.optJSONArray("unlockEmails")
                    Store.unlockEmails = if (emails == null) "" else (0 until emails.length()).joinToString(", ") { emails.getString(it) }
                    val dc = o.optString("disconnectCode")
                    Profiles.save(
                        Profile(
                            name = "Perfil " + o.optString("name"),
                            birthDate = o.optString("birthDate"),
                            disconnectCode = dc,
                        )
                    )
                    if (dc.isEmpty()) needCode = true else onRegistered()
                }
            }, Modifier.fillMaxWidth(), enabled = !busy && code.isNotBlank())
        }
        }
    }
}

/** Segundo paso: permisos y listas de apps. */
@Composable
private fun PermissionsSetup(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    when (picking) {
        "whitelist" -> {
            AppListPicker(
                title = "Apps permitidas",
                subtitle = "Se podrán usar siempre, también durante la desconexión total. Teléfono, SMS, Contactos y Ajustes ya lo están.",
                initial = Profiles.active().whitelist.toSet(),
                keywords = if (Profiles.active().whitelist.isEmpty()) WHITELIST_KEYWORDS else emptyList(),
                onSave = { sel -> Profiles.update { it.copy(whitelist = sel.toList()) }; picking = null },
                onCancel = { picking = null },
            )
            return
        }
        "cartelera" -> {
            AppListPicker(
                title = "App Cartelera",
                subtitle = "Marca la app de la Cartelera: máximo 30 min al día y esperas si entras más de 3 veces en una hora.",
                initial = Profiles.active().carteleraPackages.toSet(),
                keywords = if (Profiles.active().carteleraPackages.isEmpty()) CARTELERA_KEYWORDS else emptyList(),
                onSave = { sel -> Profiles.update { it.copy(carteleraPackages = sel.toList()) }; picking = null },
                onCancel = { picking = null },
            )
            return
        }
        "free" -> {
            AppListPicker(
                title = "Apps siempre libres",
                subtitle = "Nunca tendrán pausas, esperas ni límites, ni siquiera en la desconexión total.",
                initial = Profiles.active().freePackages.toSet(),
                keywords = FREE_KEYWORDS,
                onSave = { sel -> Profiles.update { it.copy(freePackages = sel.toList()) }; picking = null },
                onCancel = { picking = null },
            )
            return
        }
    }

    val usage = remember(refresh) { Perms.usage(ctx) }
    val access = remember(refresh) { Perms.accessibility(ctx) }
    val listener = remember(refresh) { Perms.notificationListener(ctx) }
    val notif = remember(refresh) { Perms.postNotifications(ctx) }
    val install = remember(refresh) { Updater.canInstall(ctx) }
    val gray = remember(refresh) { Perms.grayscale(ctx) }
    val p = remember(refresh, picking) { Profiles.active() }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ScreenTitle("Configuración", "Hola, ${Store.userName}")
        MutedText("Cada botón te lleva al ajuste de Android correspondiente; al volver, el paso aparece como hecho.")
        Spacer(Modifier.height(8.dp))

        StepCard(1, "Acceso al uso de apps", "Para el marcador, las estadísticas y los desbloqueos.", usage, "Abrir ajuste") { Perms.openUsage(ctx) }
        StepCard(
            2, "Permitir ajustes restringidos",
            "En la pantalla que se abre, pulsa ⋮ (arriba a la derecha) > \"Permitir ajustes restringidos\". Si no aparece, sáltate este paso.",
            null, "Abrir información de la app",
        ) { Perms.openAppDetails(ctx) }
        StepCard(3, "Servicio de accesibilidad", "Detecta qué app o web está abierta, aplica los límites y muestra el marcador. Accesibilidad > Apps descargadas > Desintoxicación Digital.", access, "Abrir accesibilidad") { Perms.openAccessibility(ctx) }
        StepCard(4, "Acceso a notificaciones", "Para borrar las notificaciones de WhatsApp y la respuesta automática.", listener, "Abrir ajuste") { Perms.openNotificationListener(ctx) }
        StepCard(5, "Permiso para avisarte", "Para el resumen de las 20:00 y los avisos de éxito.", notif, "Permitir") {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        StepCard(6, "Instalar actualizaciones", "Para que la app pueda actualizarse sola cuando haya versión nueva.", install, "Permitir") {
            Updater.openInstallPermission(ctx)
        }
        StepCard(7, "Apps permitidas en la desconexión", if (p.whitelist.isEmpty()) "Elige las apps que podrás usar siempre." else "${p.whitelist.size} apps elegidas.", p.whitelist.isNotEmpty(), "Elegir apps") { picking = "whitelist" }
        StepCard(8, "App Cartelera", if (p.carteleraPackages.isEmpty()) "Marca la app de la Cartelera." else "Elegida.", p.carteleraPackages.isNotEmpty(), "Elegir app") { picking = "cartelera" }
        StepCard(9, "Apps siempre libres", "WhatsApp Business y Crew Photo Flow: sin ninguna limitación. ${p.freePackages.size} elegidas.", null, "Revisar") { picking = "free" }

        SectionCard("10. Pantalla en blanco y negro (opcional)") {
            MutedText(if (gray) "Permiso concedido." else "Hace falta conectar el móvil al ordenador una vez y ejecutar este comando (está en la guía):")
            if (!gray) SelectionContainer {
                Text(GRAYSCALE_ADB, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        val ready = usage && access && listener
        BigButton("Empezar", onDone, Modifier.fillMaxWidth(), enabled = ready)
        if (!ready) MutedText("Faltan los pasos 1, 3 y 4 para poder empezar.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StepCard(n: Int, title: String, desc: String, done: Boolean?, button: String, onClick: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$n. $title", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            when (done) {
                true -> Text("Hecho ✓", color = Good, fontWeight = FontWeight.Bold)
                false -> Text("Pendiente", color = Warn, fontWeight = FontWeight.Bold)
                null -> {}
            }
        }
        Spacer(Modifier.height(4.dp))
        MutedText(desc)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onClick) { Text(button) }
    }
}

/** Lista de apps instaladas con casillas. */
@Composable
fun AppListPicker(
    title: String,
    subtitle: String,
    initial: Set<String>,
    keywords: List<String>,
    onSave: (Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    val apps = remember { launchableApps(ctx) }
    var selected by remember { mutableStateOf(initial + suggested(apps, keywords)) }
    var query by remember { mutableStateOf("") }
    val shown = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        ScreenTitle(title)
        MutedText(subtitle)
        OutlinedTextField(
            value = query, onValueChange = { query = it }, label = { Text("Buscar") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        MutedText("${selected.size} seleccionadas")
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.pkg }) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        selected = if (a.pkg in selected) selected - a.pkg else selected + a.pkg
                    }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = a.pkg in selected, onCheckedChange = { c ->
                        selected = if (c) selected + a.pkg else selected - a.pkg
                    })
                    Text(a.label)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancelar") }
            BigButton("Guardar", { onSave(selected) })
        }
    }
}
