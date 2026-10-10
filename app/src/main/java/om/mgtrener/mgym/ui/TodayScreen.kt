package om.mgtrener.mgym.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import om.mgtrener.mgym.analytics.Analytics
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.achievements.Achievements
import om.mgtrener.mgym.ui.theme.Lime

@Composable
fun TodayScreen(model: GymViewModel, start: (Exercise) -> Unit, open: (Long) -> Unit, openPlan: () -> Unit) {
    val data = model.data
    var selectedExercise by remember { mutableIntStateOf(0) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Сегодня", style = MaterialTheme.typography.headlineMedium)
        Text("MG / 01", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Hero(data.completed.size)
    OutlinedButton(onClick = openPlan, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(9.dp)) { Text("Путь к 150 кг  ↗", fontWeight = FontWeight.Bold) }
    val active = data.active
    if(active != null) {
        Button(onClick = { start(active.exercises.first()) }, enabled = !model.busy,
            shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Продолжить · ${active.exercises.first().short}")
        }
    } else {
        Text("НАЧАТЬ ТРЕНИРОВКУ",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            trainingExercises.forEach { exercise ->
                OutlinedButton(onClick = { start(exercise) }, enabled = !model.busy,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics {contentDescription="Начать: ${exercise.title}"},
                    contentPadding=PaddingValues(horizontal=4.dp)) {
                    Text(exercise.short)
                }
            }
        }
    }
    Text("ПОСЛЕДНИЕ РЕЗУЛЬТАТЫ", style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    CompactTabs(trainingExercises.map { it.short }, selectedExercise) { selectedExercise = it }
    val ex = trainingExercises[selectedExercise]
    val lastWorkout = data.completed.firstOrNull { ex in it.exercises }
    val recent = lastWorkout?.let { data.setsFor(it.id).filter { s -> s.exercise == ex } }.orEmpty()
    val stats = Analytics.summarize(recent)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(stats.best?.let { "${number(it.weight)} × ${it.reps}" } ?: "—",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(stats.e1rm?.let { "e1RM ≈ ${number(it)} кг" } ?: "Первый подход впереди",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

