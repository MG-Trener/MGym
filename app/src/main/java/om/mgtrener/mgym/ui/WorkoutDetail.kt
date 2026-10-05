package om.mgtrener.mgym.ui

import androidx.compose.animation.animateColorAsState
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
fun WorkoutDetail(
    data: GymData,
    id: Long,
    isResult: Boolean = false,
    reorder: ((Long,Exercise,List<Long>)->Unit)? = null,
    enabled: Boolean = true,
    save: ((LiftSet)->Unit)? = null,
    delete: ((LiftSet)->Unit)? = null
) {
    val workout=data.workouts.firstOrNull { it.id==id } ?: return
    var exercise by rememberSaveable(id) { mutableStateOf(workout.exercises.first()) }
    var editMode by rememberSaveable(id) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LiftSet?>(null) }
    var removing by remember { mutableStateOf<LiftSet?>(null) }
    var adding by remember { mutableStateOf(false) }
    val sets=data.setsFor(id).filter { it.exercise==exercise }
    val canEdit=save!=null && delete!=null
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(isResult) "ТРЕНИРОВКА ЗАВЕРШЕНА" else "Тренировка",style=MaterialTheme.typography.titleMedium)
                Text(date(workout.startedAt),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                CopyWorkout(data,id)
                if(canEdit) TextButton(
                    onClick={
                        editMode=!editMode
                        if(!editMode) { editing=null; removing=null; adding=false }
                    },
                    enabled=enabled,
                    contentPadding=PaddingValues(horizontal=8.dp),
                    modifier=Modifier.heightIn(min=36.dp)
                ) { Text(if(editMode) "Готово" else "Редактировать") }
            }
        }
        if(workout.exercises.size>1) CompactTabs(workout.exercises.map { it.short },workout.exercises.indexOf(exercise)) { exercise=workout.exercises[it] }
        else Text(exercise.title,style=MaterialTheme.typography.titleSmall)
        SummaryCard(sets)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Подходы · ${sets.size}",style=MaterialTheme.typography.labelMedium)
            Text(
                if(editMode) "Нажми на вес или повторы" else "Удерживай ≡ и перемещай",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SetTable(
            sets,
            Modifier.weight(1f),
            enabled,
            reorder=if(reorder!=null) { ids -> reorder(id,exercise,ids) } else null,
            edit=if(editMode && canEdit) { set -> editing=set } else null,
            delete=if(editMode && canEdit && sets.size>1) { set -> removing=set } else null,
            footer=if(editMode && canEdit) {
                {
                    TextButton(
                        onClick={adding=true},
                        enabled=enabled,
                        modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)
                    ) { Text("+ Добавить подход") }
                }
            } else null
        )
    }
    editing?.let { set ->
        EditSetDialog(set,{editing=null}) { updated ->
            save?.invoke(updated)
            editing=null
        }
    }
    if(adding) AddCompletedSetDialog(
        workoutId=id,
        exercise=exercise,
        dismiss={adding=false}
    ) { set ->
        save?.invoke(set)
        adding=false
    }
    removing?.let { set ->
        AlertDialog(
            onDismissRequest={removing=null},
            title={Text("Удалить подход?")},
            text={Text("${number(set.weight)} кг × ${set.reps}")},
            confirmButton={
                TextButton(onClick={
                    delete?.invoke(set)
                    removing=null
                }) {Text("Удалить")}
            },
            dismissButton={TextButton(onClick={removing=null}) {Text("Отмена")}}
        )
    }
}
@Composable
private fun AddCompletedSetDialog(
    workoutId: Long,
    exercise: Exercise,
    dismiss: () -> Unit,
    save: (LiftSet) -> Unit
) {
    var draft by remember(workoutId,exercise) {
        mutableStateOf(Draft(exercise=exercise,weight="",reps="",kind=SetKind.WORK))
    }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(
            Modifier.fillMaxWidth().padding(16.dp),
            shape=RoundedCornerShape(16.dp),
            color=MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.padding(12.dp).heightIn(max=600.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                Text("Добавить подход",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(vertical=8.dp))
                SetEditor(draft,true) { draft=it }
                error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=dismiss) { Text("Отмена") }
                    Button(
                        onClick={
                            try { save(draft.copy(kind=SetKind.WORK).toSet(workoutId)) }
                            catch(e:IllegalArgumentException) { error=e.message }
                        },
                        shape=RoundedCornerShape(8.dp)
                    ) { Text("Добавить") }
                }
            }
        }
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
