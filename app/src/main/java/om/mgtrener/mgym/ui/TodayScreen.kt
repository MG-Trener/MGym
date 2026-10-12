package om.mgtrener.mgym.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import om.mgtrener.mgym.analytics.Analytics
import om.mgtrener.mgym.domain.*

@Composable
fun TodayScreen(model: GymViewModel, selected: Int, onSelect: (Int)->Unit, start: (Exercise)->Unit, open: (Long)->Unit, openPlan: ()->Unit) {
    val data=model.data
    LocalizedText("Тренировки",style=MaterialTheme.typography.headlineSmall)
    LocalizedText("Выбери движение: план, тренировка и история в одном разделе.",style=MaterialTheme.typography.bodyMedium)
    trainingExercises.forEachIndexed { index, ex ->
        val workouts=data.completed.filter {ex in it.exercises}
        Surface(onClick={onSelect(index)},shape=RoundedCornerShape(12.dp),color=if(index==selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().semantics {contentDescription=om.mgtrener.mgym.i18n.Language.text("Раздел: ${ex.title}")}) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                LocalizedText(ex.title,style=MaterialTheme.typography.titleLarge)
                LocalizedText("${workouts.size} тренировок"+(workouts.firstOrNull()?.let {" · последняя ${date(it.startedAt)}"} ?: " · начни с плана или своей тренировки"),style=MaterialTheme.typography.bodySmall)
                if(index==selected) {
                    Button(onClick=openPlan,modifier=Modifier.fillMaxWidth()) {LocalizedText(ex.planTitle)}
                    OutlinedButton(onClick={start(ex)},enabled=!model.busy,modifier=Modifier.fillMaxWidth().semantics {contentDescription=om.mgtrener.mgym.i18n.Language.text("Начать: ${ex.title}")}) {LocalizedText(if(data.active!=null) "Продолжить тренировку · ${data.active!!.exercises.first().short}" else "Тренироваться самостоятельно")}
                }
            }
        }
    }
    val ex=trainingExercises[selected]
    val workouts=data.completed.filter {ex in it.exercises}
    LocalizedText("История · ${ex.title}",style=MaterialTheme.typography.titleLarge)
    if(workouts.isEmpty()) LocalizedText("Сохранённые тренировки и факты из плана появятся здесь.")
    workouts.forEach {w ->
        Surface(onClick={open(w.id)},shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                LocalizedText(date(w.startedAt),style=MaterialTheme.typography.titleMedium)
                LocalizedText(w.exercises.joinToString(" + ") {it.short},style=MaterialTheme.typography.bodySmall)
                val sets=data.setsFor(w.id).filter {it.exercise==ex};val stats=Analytics.summarize(sets)
                LocalizedText("${sets.size} подходов · ${number(stats.volume)} кг · "+if(w.sourceKey!=null) "По плану" else "Самостоятельно",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
