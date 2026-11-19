package com.desintoxicacion.digital.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desintoxicacion.digital.core.DayUsage
import com.desintoxicacion.digital.core.Stats
import com.desintoxicacion.digital.core.Specials
import com.desintoxicacion.digital.core.Store
import com.desintoxicacion.digital.core.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

private val DAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")

@Composable
fun StatsScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    var weekOffset by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val monday = T.today().with(DayOfWeek.MONDAY).minusWeeks(weekOffset.toLong())
    val days = (0..6).map { monday.plusDays(it.toLong()) }

    val week by produceState<List<DayUsage?>?>(null, weekOffset) {
        value = withContext(Dispatchers.IO) { days.map { Stats.day(ctx.applicationContext, it) } }
    }
    val prevWeek by produceState<List<DayUsage?>?>(null, weekOffset) {
        value = withContext(Dispatchers.IO) {
            days.map { Stats.day(ctx.applicationContext, it.minusWeeks(1)) }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenTitle("Estadísticas")
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { weekOffset++; selected = null }) { Text("‹ Anterior") }
            Text(
                "Semana del ${T.ddmm(days.first())} al ${T.ddmm(days.last())}",
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium,
            )
            TextButton(onClick = { if (weekOffset > 0) { weekOffset--; selected = null } }, enabled = weekOffset > 0) {
                Text("Siguiente ›")
            }
        }

        if (week == null) MutedText("Calculando…")
        val w: List<DayUsage?> = week ?: List(7) { null }

        SectionCard("Tiempo de uso por día") {
            MutedText("Toca un día para ver el uso de cada app.")
            BarChart(
                values = w.map { (it?.totalMs ?: 0L) / 60_000f },
                labels = DAY_LETTERS,
                valueText = { v -> if (v <= 0f) "" else T.mins((v * 60_000).toLong()).replace(" min", "'") },
                selected = selected,
                onSelect = { i -> selected = if (selected == i) null else i },
                todayIndex = days.indexOf(T.today()),
            )
            val known = w.filterNotNull().filter { it.totalMs > 0 }
            if (known.isNotEmpty()) {
                val avg = known.sumOf { it.totalMs } / known.size
                Spacer(Modifier.height(8.dp))
                KeyValue("Media diaria", T.mins(avg))
                val pw = prevWeek?.filterNotNull()?.filter { it.totalMs > 0 }.orEmpty()
                if (pw.isNotEmpty()) {
                    val pavg = pw.sumOf { it.totalMs } / pw.size
                    val diff = avg - pavg
                    KeyValue(
                        "Frente a la semana anterior",
                        if (diff <= 0) "${T.mins(-diff)} menos al día" else "${T.mins(diff)} más al día",
                        if (diff <= 0) Good else Warn,
                    )
                }
            }
        }

        selected?.let { i ->
            val u = w.getOrNull(i)
            SectionCard("Uso por app · ${dayName(days[i])}") {
                if (u == null || u.perApp.isEmpty()) {
                    MutedText("No hay datos de este día.")
                } else {
                    u.perApp.entries.sortedByDescending { it.value }.take(25).forEach { (pkg, ms) ->
                        KeyValue(Stats.label(ctx, pkg), T.mins(ms))
                    }
                }
            }
        }

        SectionCard("Desbloqueos por día") {
            BarChart(
                values = w.map { (it?.unlocks ?: 0).toFloat() },
                labels = DAY_LETTERS,
                valueText = { v -> if (v <= 0f) "" else v.toInt().toString() },
                selected = null,
                onSelect = {},
                todayIndex = days.indexOf(T.today()),
            )
            val known = w.filterNotNull().filter { it.unlocks > 0 }
            if (known.isNotEmpty()) KeyValue("Media diaria", "${known.sumOf { it.unlocks } / known.size}")
        }

        val pastDays = days.filter { !it.isAfter(T.today()) }
        val weekHist = pastDays.map { Store.dayHistory(it) }
        SectionCard("Pausa de intención") {
            KeyValue("Entradas evitadas esta semana", "${weekHist.sumOf { it.first }}")
            val totals = mutableMapOf<String, Int>()
            weekHist.forEach { (_, m) -> m.forEach { (k, v) -> totals[k] = (totals[k] ?: 0) + v } }
            if (totals.isEmpty()) MutedText("Todavía no has elegido ninguna intención esta semana.")
            totals.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                KeyValue(k, "$v ${if (v == 1) "vez" else "veces"}", if (k == "Mirar por mirar") Warn else Ink)
            }
        }

        SectionCard("Comodines y días especiales") {
            val from = T.logicalStart(days.first())
            val to = T.logicalStart(days.last().plusDays(1))
            val all = Store.history().filter { it.type != "disconnect" }
            val thisWeek = all.filter { it.time in from until to }
            KeyValue("Esta semana", "${thisWeek.size}")
            Spacer(Modifier.height(6.dp))
            all.sortedByDescending { it.time }.take(20).forEach {
                val extra = if (it.detail.isNotBlank()) " · ${it.detail}" else ""
                MutedText("${T.ddmm(T.dateOf(it.time))} ${T.hhmm(it.time)} · ${Specials.label(it.type)}$extra")
            }
            if (all.isEmpty()) MutedText("Todavía no has usado ninguno.")
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun dayName(d: LocalDate): String {
    val names = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    return "${names[d.dayOfWeek.value - 1]} ${T.ddmm(d)}"
}

/** Gráfica de barras sencilla, una barra por día. */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    valueText: (Float) -> String,
    selected: Int?,
    onSelect: (Int) -> Unit,
    todayIndex: Int,
) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val chartHeight = 140.dp
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            val isSel = selected == i
            Column(
                Modifier.weight(1f).clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(valueText(v), fontSize = 10.sp, color = Muted, maxLines = 1)
                Box(Modifier.height(chartHeight), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .width(22.dp)
                            .height(chartHeight * (v / max))
                            .background(
                                when {
                                    isSel -> Ink
                                    i == todayIndex -> Accent
                                    else -> Color(0xFFB9B3F2)
                                },
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            )
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(labels.getOrElse(i) { "" }, fontWeight = if (i == todayIndex) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
