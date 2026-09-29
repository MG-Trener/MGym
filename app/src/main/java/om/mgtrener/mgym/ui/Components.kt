package om.mgtrener.mgym.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import om.mgtrener.mgym.analytics.Analytics
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.ui.theme.Lime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun number(value: Double): String = String.format(Locale.forLanguageTag("ru"), "%.1f", value).removeSuffix(",0")
fun date(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("ru")))
fun day(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
    }
}
@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
@Composable
fun Hero(count: Int) {
    Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF24312E), Color(0xFF141A20))),
        RoundedCornerShape(14.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("MGYM / TRAINING LOG", color = Lime, style = MaterialTheme.typography.labelSmall)
            Text("Сильнее с каждым\nподходом.", style = MaterialTheme.typography.headlineMedium)
            Text(if(count == 0) "Твоя точка отсчёта — сегодня." else "$count тренировок в истории",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(16,30,40,6,40,30,16).forEach { h ->
                Box(Modifier.width(if(h == 6) 16.dp else 5.dp).height(h.dp)
                    .background(if(h == 6) Lime else Color(0xFF83928C), RoundedCornerShape(2.dp)))
            }
        }
    }
}
@Composable
fun SummaryCard(sets: List<LiftSet>) {
    val stats = Analytics.summarize(sets)
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("ОБЪЁМ, КГ" to number(stats.volume), "ПОВТОРЫ" to stats.reps.toString(),
            "e1RM, КГ ≈" to (stats.e1rm?.let { number(it) } ?: "—")).forEach { (label,value) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
