package com.desintoxicacion.digital.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.desintoxicacion.digital.core.Notifier
import com.desintoxicacion.digital.core.Scheduler
import com.desintoxicacion.digital.core.Stats
import com.desintoxicacion.digital.core.Store

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        Notifier.ensureChannels(this)
        Scheduler.scheduleDaily(this)
        val app = applicationContext
        Thread { try { Stats.backfill(app) } catch (_: Exception) {} }.start()

        setContent {
            DesintoxTheme {
                var setupDone by remember { mutableStateOf(Store.setupDone && Store.registered) }
                if (!setupDone) {
                    SetupScreen(onDone = {
                        Store.setupDone = true
                        setupDone = true
                    })
                } else {
                    MainScaffold()
                }
            }
        }
    }
}

@Composable
private fun MainScaffold() {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = buildList {
        add("Hoy")
        add("Estadísticas")
        if (Store.isAdmin) add("Invitar")
        add("Ajustes")
    }
    Scaffold(
        containerColor = Paper,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {},
                        label = { Text(label, fontWeight = if (tab == i) FontWeight.ExtraBold else FontWeight.Medium, fontSize = 13.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = Ink,
                            unselectedTextColor = Muted,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tabs.getOrNull(tab)) {
            "Hoy" -> HomeScreen(m)
            "Estadísticas" -> StatsScreen(m)
            "Invitar" -> InviteScreen(m)
            else -> SettingsScreen(m)
        }
    }
}
