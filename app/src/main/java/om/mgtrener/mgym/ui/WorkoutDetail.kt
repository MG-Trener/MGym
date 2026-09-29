package om.mgtrener.mgym.ui

import androidx.compose.animation.animateColorAsState
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.services.WorkoutText

@Composable
fun CopyWorkout(data: GymData,id:Long) {
    val context=LocalContext.current
    var copied by remember(id,data) { mutableStateOf(false) }
    TextButton(onClick={
        val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Тренировка MGym",WorkoutText.format(data,id)))
        copied=true
    },contentPadding=PaddingValues(horizontal=8.dp), modifier=Modifier.heightIn(min=36.dp)) { Text(if(copied) "Скопировано" else "Копировать") }
}
@Composable
fun WorkoutDetail(data: GymData,id:Long,isResult:Boolean=false,
                  reorder: ((Long,Exercise,List<Long>)->Unit)?=null, enabled:Boolean=true) {
    val workout=data.workouts.firstOrNull { it.id==id } ?: return
    var exercise by rememberSaveable(id) { mutableStateOf(workout.exercises.first()) }
    val sets=data.setsFor(id).filter { it.exercise==exercise }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(isResult) "ТРЕНИРОВКА ЗАВЕРШЕНА" else "Тренировка",style=MaterialTheme.typography.titleMedium)
                Text(date(workout.startedAt),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CopyWorkout(data,id)
        }
        if(workout.exercises.size>1) CompactTabs(workout.exercises.map { it.short },workout.exercises.indexOf(exercise)) { exercise=workout.exercises[it] }
        else Text(exercise.title,style=MaterialTheme.typography.titleSmall)
        SummaryCard(sets)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Подходы · ${sets.size}",style=MaterialTheme.typography.labelMedium)
            Text("Удерживай ≡ и перемещай",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SetTable(sets,Modifier.weight(1f),enabled,
            reorder=if(reorder!=null) { ids -> reorder(id,exercise,ids) } else null)
    }
}
@Composable
fun CompactTabs(labels:List<String>,selected:Int,select:(Int)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index,label ->
            val color by animateColorAsState(if(index==selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,label="tab")
            Surface(onClick={select(index)},modifier=Modifier.weight(1f),shape=MaterialTheme.shapes.small,
                color=color) {
                Box(Modifier.heightIn(min=36.dp).padding(horizontal=4.dp),contentAlignment=Alignment.Center) {
                    Text(label,style=MaterialTheme.typography.labelMedium,color=if(index==selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
