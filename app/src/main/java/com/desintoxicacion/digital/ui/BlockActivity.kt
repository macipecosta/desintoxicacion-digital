package com.desintoxicacion.digital.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desintoxicacion.digital.core.Store

/** Pantalla que aparece cuando algo está bloqueado. */
class BlockActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
    }

    private var blockTitle by mutableStateOf("")
    private var blockMessage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        read(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        setContent {
            DesintoxTheme {
                Column(
                    Modifier.fillMaxSize().background(Paper).systemBarsPadding().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(blockTitle, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(blockMessage, fontSize = 17.sp, color = Muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(36.dp))
                    Button(onClick = { leave() }, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(i: Intent?) {
        blockTitle = i?.getStringExtra(EXTRA_TITLE) ?: "Bloqueado"
        blockMessage = i?.getStringExtra(EXTRA_MESSAGE) ?: ""
    }

    private fun leave() {
        goHome(this)
        finish()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }
}
