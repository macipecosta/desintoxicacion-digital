package com.desintoxicacion.digital.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desintoxicacion.digital.core.Target

// ---------- Paleta del estilo C · Color ----------
val Paper = Color(0xFFFAFAF7)
val Ink = Color(0xFF1C1D21)
val Muted = Color(0xFF5E6170)
val Line = Color(0xFFECECE6)
val Accent = Color(0xFF5B4FE0)
val Warn = Color(0xFFA33A0E)
val Good = Color(0xFF2E7D4F)
val YellowBg = Color(0xFFFFE7A3)
val YellowFg = Color(0xFF5C4300)

/** Colores de cada apartado: fondo, texto, color fuerte y pista del anillo. */
data class Tint(val bg: Color, val fg: Color, val strong: Color, val track: Color)

val WaTint = Tint(Color(0xFFE3F4EA), Color(0xFF1F5E3A), Color(0xFF2E9B5E), Color(0xFFC3E6D0))
val NewsTint = Tint(Color(0xFFFDEBDD), Color(0xFF8A3F12), Color(0xFFD9652A), Color(0xFFF7D2B6))
val MailTint = Tint(Color(0xFFE3ECFB), Color(0xFF1E4A8F), Color(0xFF2F62C4), Color(0xFFC9D9F6))
val CartTint = Tint(Color(0xFFEEE8FB), Color(0xFF4B2E9A), Color(0xFF6D4AD8), Color(0xFFD9CEF5))
val DarkTint = Tint(Ink, Color.White, Color(0xFF8EE3B0), Color(0xFF3A3C44))

fun tintFor(t: Target): Tint = when (t) {
    Target.WHATSAPP -> WaTint
    Target.MAIL_APP, Target.WEBMAIL -> MailTint
    Target.NEWS -> NewsTint
    Target.CARTELERA -> CartTint
}

private val Colors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E3FB),
    onPrimaryContainer = Ink,
    secondary = Muted,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1F1EC),
    onSurfaceVariant = Muted,
    error = Warn,
)

@Composable
fun DesintoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

// ---------- Piezas comunes ----------

@Composable
fun ColorCard(tint: Tint? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(tint?.bg ?: Color.White, RoundedCornerShape(22.dp))
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    ColorCard {
        if (title != null) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
            Spacer(Modifier.height(6.dp))
        }
        content()
    }
}

@Composable
fun ScreenTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        if (sub != null) Text(sub, color = Muted, fontSize = 13.sp)
        Text(text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
    }
}

@Composable
fun MutedText(text: String, color: Color = Muted) = Text(text, color = color, fontSize = 14.sp)

@Composable
fun KeyValue(label: String, value: String, valueColor: Color = Ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Ring(fraction: Float, color: Color, track: Color, size: Dp = 56.dp, stroke: Dp = 7.dp) {
    Canvas(Modifier.size(size)) {
        val sw = stroke.toPx()
        val d = this.size.minDimension - sw
        val topLeft = Offset(sw / 2, sw / 2)
        drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(sw))
        drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
    }
}

@Composable
fun Pill(text: String, bg: Color = Ink, fg: Color = Color.White) {
    Box(Modifier.background(bg, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = Accent,
    fg: Color = Color.White,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        modifier = modifier.height(54.dp),
    ) { Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    number: Boolean = false,
    help: String? = null,
    singleLine: Boolean = true,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = singleLine,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
        if (help != null) Text(help, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
