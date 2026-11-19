package com.desintoxicacion.digital.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desintoxicacion.digital.core.Domains
import com.desintoxicacion.digital.core.GuardService
import com.desintoxicacion.digital.core.Profiles
import com.desintoxicacion.digital.core.Session
import com.desintoxicacion.digital.core.Store
import com.desintoxicacion.digital.core.T
import com.desintoxicacion.digital.core.Target
import kotlinx.coroutines.delay

/**
 * Pausa de intención: hay que elegir a qué vas y, después, esperar la cuenta atrás
 * (10 s normalmente; 1 min en Gmail; 30 s, 60 s o 5 min en Cartelera).
 */
class PauseActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TARGET = "target"
        const val EXTRA_SITE = "site"
        const val EXTRA_SECONDS = "seconds"
    }

    private var target by mutableStateOf(Target.WHATSAPP)
    private var site by mutableStateOf("")
    private var seconds by mutableIntStateOf(10)
    private var round by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        read(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = giveUp()
        })
        setContent { DesintoxTheme { PauseScreen() } }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @androidx.compose.runtime.Composable
    private fun PauseScreen() {
        val tint = tintFor(target)
        val options = remember(target) { Profiles.active().intentionsFor(target) }
        var chosen by remember(round) { mutableStateOf<String?>(null) }
        var left by remember(round) { mutableIntStateOf(seconds) }
        LaunchedEffect(round, chosen) {
            if (chosen != null) {
                while (left > 0) {
                    delay(1000)
                    left--
                }
            }
        }
        val name = if (target == Target.NEWS) Domains.siteLabel(site) else target.label
        Column(
            Modifier.fillMaxSize().background(tint.bg).systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Pill(name, bg = Color.White, fg = tint.fg)
            Spacer(Modifier.height(20.dp))
            Text("¿A qué vas?", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                if (chosen == null) "Elige una intención para seguir." else "Entra solo a eso y sal en cuanto termines.",
                fontSize = 16.sp, color = tint.fg, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                options.forEach { opt ->
                    val sel = opt == chosen
                    Button(
                        onClick = { if (chosen == null) chosen = opt },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sel) tint.fg else Color.White,
                            contentColor = if (sel) Color.White else tint.fg,
                            disabledContainerColor = if (sel) tint.fg else Color.White,
                            disabledContentColor = if (sel) Color.White else tint.fg,
                        ),
                        enabled = chosen == null || sel,
                    ) { Text(opt, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(34.dp))
            Box(contentAlignment = Alignment.Center) {
                val total = seconds.coerceAtLeast(1)
                Ring(
                    fraction = if (chosen == null) 0f else 1f - left.toFloat() / total,
                    color = tint.strong, track = tint.track, size = 170.dp, stroke = 12.dp,
                )
                Text(
                    if (chosen == null) T.secs(seconds) else if (left > 0) countdown(left) else "✓",
                    fontSize = if (left >= 60 || chosen == null) 30.sp else 52.sp,
                    fontWeight = FontWeight.ExtraBold, color = Ink,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Mejor no", { giveUp() }, Modifier.weight(1f), bg = Color.White, fg = tint.fg)
                BigButton(
                    "Entrar", { enter(chosen) }, Modifier.weight(1f),
                    bg = tint.strong, enabled = chosen != null && left == 0,
                )
            }
        }
    }

    private fun countdown(s: Int): String = if (s >= 60) "%d:%02d".format(s / 60, s % 60) else "$s"

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
        round++
    }

    private fun read(i: Intent?) {
        target = try {
            Target.valueOf(i?.getStringExtra(EXTRA_TARGET) ?: Target.WHATSAPP.name)
        } catch (_: Exception) {
            Target.WHATSAPP
        }
        site = i?.getStringExtra(EXTRA_SITE) ?: ""
        seconds = i?.getIntExtra(EXTRA_SECONDS, 10) ?: 10
    }

    private fun enter(intention: String?) {
        if (intention != null) Store.updateDay { it.intentions[intention] = (it.intentions[intention] ?: 0) + 1 }
        GuardService.approved = Session(target, site)
        GuardService.approvedAt = T.now()
        finish()
    }

    private fun giveUp() {
        Store.updateDay { it.avoided++ }
        goHome(this)
        finish()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }
}
