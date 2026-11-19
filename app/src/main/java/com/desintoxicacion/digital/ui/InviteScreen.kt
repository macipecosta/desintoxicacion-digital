package com.desintoxicacion.digital.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.desintoxicacion.digital.core.GuardService
import com.desintoxicacion.digital.core.Server
import com.desintoxicacion.digital.core.Store
import com.desintoxicacion.digital.core.T

/** Solo para administradoras: invitar a alguien por WhatsApp. */
@Composable
fun InviteScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ScreenTitle("Invitar", "Administradora")
        ColorCard(WaTint) {
            Text("Invita por WhatsApp", fontWeight = FontWeight.ExtraBold, color = WaTint.fg)
            MutedText(
                "Se abrirá WhatsApp con un mensaje y un enlace personal de un solo uso. La persona rellenará sus datos, " +
                    "elegirá su código y el email de su persona de confianza, y descargará la app. Abrir WhatsApp para invitar no cuenta como entrada.",
                WaTint.fg,
            )
            Spacer(Modifier.height(8.dp))
            Field("Nombre de la persona", name, { name = it })
            Spacer(Modifier.height(6.dp))
            BigButton(if (busy) "Creando invitación…" else "Invitar por WhatsApp", {
                busy = true
                status = null
                Server.call("invite", mapOf("name" to name.trim())) { o, err ->
                    busy = false
                    if (o == null) {
                        status = err
                        return@call
                    }
                    val url = o.optString("url")
                    val text = "Hola ${name.trim()}, te invito a Desintoxicación Digital, una app para usar menos el móvil. " +
                        "Entra en este enlace, rellena tus datos y descarga la app (solo Android): $url"
                    GuardService.inviteUntil = T.now() + 3 * 60_000L
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    try {
                        ctx.startActivity(Intent(send).setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        ctx.startActivity(Intent.createChooser(send, "Enviar invitación").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    status = "Invitación creada para ${name.trim()}."
                    name = ""
                }
            }, Modifier.fillMaxWidth(), bg = WaTint.strong, enabled = !busy && name.isNotBlank())
            status?.let { MutedText(it, WaTint.fg) }
        }
        SectionCard("Cómo funciona") {
            MutedText("1. La persona abre el enlace y crea su perfil.\n2. Descarga la app desde esa misma página.\n3. Al abrir la app escribe su código de invitación y sigue los pasos.\n\nLos datos de las personas invitadas quedan en la hoja \"Desintoxicación Digital — usuarios\" de Google Drive de la administradora que instaló la web.")
        }
        Spacer(Modifier.height(24.dp))
        if (!Store.isAdmin) MutedText("Esta pantalla solo funciona para administradoras.")
    }
}
