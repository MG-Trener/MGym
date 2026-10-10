package om.mgtrener.mgym.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import om.mgtrener.mgym.ui.theme.Lime
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class PlannedSet(val weight: Double?, val reps: Int, val rest: Int, val kind: String, val rpe: Boolean)
private data class PlannedExercise(val name: String, val load: String, val sets: List<PlannedSet>)
private data class PlannedDay(val id: String, val week: Int, val title: String, val note: String, val exercises: List<PlannedExercise>)
private data class BenchPlan(val baseline: String, val rules: List<String>, val days: List<PlannedDay>)

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
private fun loadBenchPlan(context: Context): BenchPlan {
    val root = JSONObject(context.assets.open("bench150-plan.json").bufferedReader().use { it.readText() })
    val days = root.getJSONArray("sessions")
    return BenchPlan(root.getString("baseline"), root.getJSONArray("rules").strings(), (0 until days.length()).map { i ->
        val day = days.getJSONObject(i)
        val exercises = day.getJSONArray("exercises")
        PlannedDay(day.getString("id"), day.getInt("week"), day.getString("title"), day.getString("note"),
            (0 until exercises.length()).map { j ->
                val exercise = exercises.getJSONObject(j)
                val sets = exercise.getJSONArray("sets")
                PlannedExercise(exercise.getString("name"), exercise.getString("load"), (0 until sets.length()).map { k ->
                    val set = sets.getJSONObject(k)
                    PlannedSet(if(set.isNull("weight")) null else set.getDouble("weight"), set.getInt("reps"),
                        set.getInt("rest"), set.getString("kind"), set.getBoolean("rpe"))
                })
            })
    })
}

private fun plannedWeight(value: Double?): String = value?.let {
    if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString().replace('.', ',')
} ?: "—"
private fun plannedRest(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
private fun dayLabel(id: String): String = LocalDate.parse(id).format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")))
private fun entryKey(day: PlannedDay, exercise: Int, set: Int): String = "${day.id}/$exercise/$set"

@Composable
fun Bench150Screen(onBack: () -> Unit) {
    val context = LocalContext.current
    val plan = remember { loadBenchPlan(context) }
    val preferences = remember { context.getSharedPreferences("bench150-progress-v1", Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf<String?>(null) }
    var savedVersion by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = { if(selected == null) onBack() else selected = null }) { Text("← ${if(selected == null) "Сегодня" else "Календарь"}") }
        if (selected == null) {
            Text("Путь к 150 кг", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("12 октября — 5 декабря 2026", color = Lime, style = MaterialTheme.typography.titleMedium)
            Text(plan.baseline, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Как менять нагрузку", fontWeight = FontWeight.Bold)
                    plan.rules.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
            plan.days.groupBy { it.week }.forEach { (week, days) ->
                Text("НЕДЕЛЯ $week", style = MaterialTheme.typography.labelLarge, color = Lime,
                    modifier = Modifier.padding(top = 10.dp))
                days.forEach { day ->
                    val count = remember(day.id, savedVersion) { completedCount(preferences, day) }
                    val total = day.exercises.sumOf { it.sets.size }
                    Surface(onClick = { selected = day.id }, color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(dayLabel(day.id), fontWeight = FontWeight.Bold)
                                Text(day.title, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("$count/$total  ›", color = Lime, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        } else {
            val day = plan.days.first { it.id == selected }
            Text(dayLabel(day.id), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(day.title, color = Lime, style = MaterialTheme.typography.titleLarge)
            Text(day.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
            day.exercises.forEachIndexed { exerciseIndex, exercise ->
                Text(exercise.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Text(exercise.load, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                exercise.sets.forEachIndexed { setIndex, set ->
                    PlannedSetCard(day, exerciseIndex, setIndex, set, preferences) { savedVersion++ }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun completedCount(preferences: SharedPreferences, day: PlannedDay): Int = day.exercises.indices.sumOf { e ->
    day.exercises[e].sets.indices.count { s -> preferences.contains(entryKey(day, e, s)) }
}

@Composable
private fun PlannedSetCard(day: PlannedDay, exerciseIndex: Int, setIndex: Int, set: PlannedSet,
                           preferences: SharedPreferences, onSaved: () -> Unit) {
    val key = entryKey(day, exerciseIndex, setIndex)
    val previous = remember(key) { preferences.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() } }
    var actualWeight by remember(key) { mutableStateOf(previous?.optString("weight") ?: plannedWeight(set.weight).takeUnless { it == "—" }.orEmpty()) }
    var actualReps by remember(key) { mutableStateOf(previous?.optString("reps") ?: set.reps.toString()) }
    var actualRpe by remember(key) { mutableStateOf(previous?.optString("rpe") ?: "") }
    var saved by remember(key) { mutableStateOf(previous != null) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${setIndex + 1}. ${set.kind}", fontWeight = FontWeight.Bold, color = if(set.rpe) Lime else MaterialTheme.colorScheme.onSurface)
                if(saved) Text("✓ Сохранено", color = Lime, style = MaterialTheme.typography.labelSmall)
            }
            Text("План: ${plannedWeight(set.weight)} × ${set.reps} · отдых ${plannedRest(set.rest)} после подхода",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(actualWeight, { actualWeight = it; saved = false }, label = { Text("Кг") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.weight(1f))
                OutlinedTextField(actualReps, { actualReps = it; saved = false }, label = { Text("Повт.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    modifier = Modifier.weight(1f))
                if(set.rpe) OutlinedTextField(actualRpe, { actualRpe = it; saved = false }, label = { Text("RPE") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.weight(1f))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = {
                val weight = actualWeight.replace(',', '.').toDoubleOrNull()
                val reps = actualReps.toIntOrNull()
                val rpe = actualRpe.replace(',', '.').toDoubleOrNull()
                error = when {
                    weight == null || !weight.isFinite() || weight <= 0 || weight > 1500 -> "Укажите фактический вес от 0 до 1500 кг"
                    reps == null || reps !in 1..200 -> "Укажите от 1 до 200 повторений"
                    set.rpe && actualRpe.isNotBlank() && (rpe == null || !rpe.isFinite() || rpe !in 1.0..10.0) -> "RPE должен быть от 1 до 10"
                    else -> null
                }
                if(error == null) {
                    val value = JSONObject().put("weight", weight).put("reps", reps).put("rpe", if(set.rpe) actualRpe else "")
                    preferences.edit().putString(key, value.toString()).apply()
                    saved = true
                    onSaved()
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Сохранить подход") }
        }
    }
}

