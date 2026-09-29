package om.mgtrener.mgym.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import om.mgtrener.mgym.analytics.Analytics
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.achievements.Achievements
import om.mgtrener.mgym.ui.theme.Lime

@Composable
fun TodayScreen(model: GymViewModel, start: () -> Unit, open: (Long) -> Unit) {
    val data = model.data
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Сегодня", style = MaterialTheme.typography.headlineMedium)
        Text("MG / 01", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Hero(data.completed.size)
    Button(onClick = start, enabled = !model.busy, shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(if(data.active != null) "Продолжить тренировку" else "Начать тренировку")
    }
    Text("ПОСЛЕДНИЕ РЕЗУЛЬТАТЫ", style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Exercise.BENCH).forEach { ex ->
            val lastWorkout = data.completed.firstOrNull { ex in it.exercises }
            val recent = lastWorkout?.let { data.setsFor(it.id).filter { s -> s.exercise == ex } }.orEmpty()
            val stats = Analytics.summarize(recent)
            Card(Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(ex.short, style = MaterialTheme.typography.titleSmall, color = Lime)
                    Text(stats.best?.let { "${number(it.weight)} × ${it.reps}" } ?: "—",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stats.e1rm?.let { "e1RM ≈ ${number(it)} кг" } ?: "Первый подход впереди",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("История", style = MaterialTheme.typography.titleLarge)
        Text("${data.completed.size} тренировок", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(data.completed.isEmpty()) {
        Text("Здесь будут твои тренировки. Начни с первого подхода.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    data.completed.take(30).forEach { w ->
        Surface(onClick = { open(w.id) }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(w.exercises.joinToString(" + ") { it.short }, style = MaterialTheme.typography.titleMedium)
                    Text(date(w.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${number(Analytics.summarize(data.setsFor(w.id)).volume)} кг", style = MaterialTheme.typography.labelLarge)
                Text(" ›", color = Lime)
            }
        }
    }
}
