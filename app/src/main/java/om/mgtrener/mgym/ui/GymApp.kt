package om.mgtrener.mgym.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import om.mgtrener.mgym.domain.*

@Composable
fun GymApp(model: GymViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var workoutOpen by rememberSaveable { mutableStateOf(false) }
    var selectedWorkout by rememberSaveable { mutableStateOf<Long?>(null) }
    var planOpen by rememberSaveable { mutableStateOf(false) }


    val active = model.data.active
    val result = model.completedId
    val fixedScreen = planOpen || workoutOpen || selectedWorkout != null || result != null || tab == 1 || tab == 2
    val contentScroll = key(tab, workoutOpen, selectedWorkout, result) { rememberScrollState() }
    LaunchedEffect(model.ready) { if(model.ready && active != null) workoutOpen = true }
    LaunchedEffect(active?.id) { if(active != null) workoutOpen = true else workoutOpen = false }
    BackHandler(planOpen || workoutOpen || selectedWorkout != null || result != null) {
        planOpen = false; workoutOpen = false; selectedWorkout = null; model.closeResult()
    }
    Scaffold(
        bottomBar = {
            if(!planOpen && !workoutOpen && selectedWorkout == null && result == null) GymNavigation(tab) { tab = it }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!model.ready) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("MGym", style = MaterialTheme.typography.displayMedium)
                    CircularProgressIndicator()
                    Text("Открываем локальный дневник…")
                }
            } else Column(Modifier.weight(1f).then(if(fixedScreen) Modifier else Modifier.verticalScroll(contentScroll)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    planOpen -> Bench150Screen { planOpen = false }
                    result != null -> {
                        TextButton(onClick = { model.closeResult() }) { Text("← Сегодня") }
                        WorkoutDetail(model.data, result, true, model::reorder, !model.busy, model::saveCompleted, model::removeCompleted)
                    }
                    selectedWorkout != null -> {
                        TextButton(onClick = { selectedWorkout = null }) { Text("← Назад") }
                        WorkoutDetail(model.data, selectedWorkout!!, false, model::reorder, !model.busy, model::saveCompleted, model::removeCompleted)
                    }
                    workoutOpen && active != null -> {

                        WorkoutScreen(model) { workoutOpen = false }
                    }
                    tab == 0 -> TodayScreen(model, { exercise ->
                        if(active != null) workoutOpen = true else model.start(listOf(exercise))
                    }, { selectedWorkout = it }, { planOpen = true })
                    tab == 1 -> CalendarScreen(model)
                    tab == 2 -> ProgressScreen(model.data)
                    tab == 3 -> AchievementsScreen(model.data)
                    else -> SettingsScreen(model)
                }
                if(!fixedScreen) Spacer(Modifier.height(12.dp))
            }
        }
    }
    model.message?.let { message ->
        AlertDialog(onDismissRequest = { model.dismissMessage() }, title = { Text("MGym") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { model.dismissMessage() }) { Text("Понятно") } })
    }
}

