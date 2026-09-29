package om.mgtrener.mgym.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import om.mgtrener.mgym.analytics.Analytics
import om.mgtrener.mgym.achievements.Achievements
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.ui.theme.Lime
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun ProgressScreen(data: GymData) {
    val exercise = Exercise.BENCH
    var period by rememberSaveable {mutableIntStateOf(0)}
    var metric by rememberSaveable {mutableIntStateOf(0)}
    var view by rememberSaveable {mutableIntStateOf(0)}
    var periodsOpen by remember {mutableStateOf(false)}
    val periods=listOf("Всё время","1 месяц","3 месяца","6 месяцев","1 год")
    val workouts=remember(data,period) {
        val since=LocalDate.now().minusMonths(listOf(0L,1L,3L,6L,12L)[period])
        data.completed.filter {period==0 || !day(it.startedAt).isBefore(since)}.sortedBy {it.startedAt}
    }
    val grouped=remember(data,exercise) {
        data.sets.filter {it.exercise==exercise}.groupBy {it.workoutId}
    }
    val summaries=remember(workouts,grouped) {workouts.map {it to Analytics.summarize(grouped[it.id].orEmpty())}.filter {it.second.workSets>0}}
    val points=remember(summaries,metric) {
        summaries.mapNotNull {(w,s) ->
            val value=when(metric) {0->s.e1rm;1->grouped[w.id]?.maxOfOrNull {it.weight};2->s.volume;else->s.reps.toDouble()}
            value?.let {Triple(w,it,s.estimateSource)}
        }
    }
    var selected by remember(points) {mutableIntStateOf((points.size-1).coerceAtLeast(0))}
    val periodSets=remember(workouts,grouped) {workouts.flatMap {grouped[it.id].orEmpty()}}
    val records=remember(periodSets) {Analytics.records(periodSets)}
    var record by remember(records) {mutableIntStateOf(0)}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Твой прогресс",style=MaterialTheme.typography.titleLarge)
            Box {
                TextButton(onClick={periodsOpen=true},contentPadding=PaddingValues(horizontal=4.dp)) {Text(periods[period]+" ⌄")}
                DropdownMenu(periodsOpen,{periodsOpen=false}) {periods.forEachIndexed {i,label ->
                    DropdownMenuItem(text={Text(label)},onClick={period=i;periodsOpen=false})
                }}
            }
        }
        Text("Жим штанги лёжа",style=MaterialTheme.typography.bodySmall,color=Lime)
        CompactTabs(listOf("График","Рекорды"),view) {view=it}
        if(view==0) {
            CompactTabs(listOf("e1RM","Вес","Объём","Повт."),metric) {metric=it}
            if(points.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                Text("Здесь появится твой прогресс.\nДобавь подходы и заверши тренировку.",style=MaterialTheme.typography.bodyMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            } else {
                val p=points[selected.coerceAtMost(points.lastIndex)]
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${number(p.second)} ${if(metric==3) "повт." else "кг"}",style=MaterialTheme.typography.headlineMedium)
                        Text(date(p.first.startedAt),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val delta=points.last().second-points.first().second
                    Text("${if(delta>=0) "+" else ""}${number(delta)} за период",style=MaterialTheme.typography.labelSmall,color=Lime)
                }
                val xs=remember(points) {
                    val start=points.first().first.startedAt
                    val span=points.last().first.startedAt-start
                    points.mapIndexed {i,point -> if(span>0) (point.first.startedAt-start).toFloat()/span else if(points.size==1) 0.5f else i.toFloat()/points.lastIndex}
                }
                fun selectAt(x:Float,width:Float) {
                    val f=((x-12)/(width-24).coerceAtLeast(1f)).coerceIn(0f,1f)
                    selected=xs.indices.minByOrNull {abs(xs[it]-f)} ?: 0
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(number(points.maxOf {it.second}),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Canvas(Modifier.fillMaxWidth().weight(1f)
                        .semantics {contentDescription="График прогресса";stateDescription="${date(p.first.startedAt)}: ${number(p.second)}"}
                        .pointerInput(points) {detectTapGestures {selectAt(it.x,size.width.toFloat())}}
                        .pointerInput(points) {detectDragGestures {change,_->change.consume();selectAt(change.position.x,size.width.toFloat())}}) {
                        val min=points.minOf {it.second};val max=points.maxOf {it.second}
                        val positions=points.mapIndexed {i,point ->
                            Offset(12+xs[i]*(size.width-24),
                                if(max==min) size.height/2 else size.height-12-((point.second-min)/(max-min)*(size.height-24)).toFloat())
                        }
                        repeat(4) {drawLine(Color(0xFF303945),Offset(0f,it*size.height/3),Offset(size.width,it*size.height/3),1f)}
                        positions.zipWithNext().forEach {(a,b)->drawLine(Lime,a,b,3f)}
                        val current=positions[selected.coerceAtMost(positions.lastIndex)]
                        drawLine(Color(0xFF627047),Offset(current.x,0f),Offset(current.x,size.height),1.5f)
                        positions.forEachIndexed {i,pos->drawCircle(if(i==selected) Color.White else Lime,if(i==selected) 7f else 3f,pos)}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(day(points.first().first.startedAt).toString(),style=MaterialTheme.typography.labelSmall)
                        Text(day(points.last().first.startedAt).toString(),style=MaterialTheme.typography.labelSmall)
                    }
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={selected--},enabled=selected>0,contentPadding=PaddingValues(0.dp),modifier=Modifier.width(40.dp)) {Text("←")}
                    Text(if(metric==0) p.third?.let {"Подход: ${number(it.weight)} × ${it.reps}"} ?: "—" else "Тренировка ${selected+1} из ${points.size}",
                        Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={selected++},enabled=selected<points.lastIndex,contentPadding=PaddingValues(0.dp),modifier=Modifier.width(40.dp)) {Text("→")}
                }
            }
        } else {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                if(records.isEmpty()) Text("Рекорды появятся после первой тренировки.")
                records.chunked(3).forEachIndexed {row,chunk ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        chunk.forEachIndexed {col,(title,s) ->
                            val index=row*3+col
                            Surface(onClick={record=index},shape=RoundedCornerShape(8.dp),modifier=Modifier.weight(1f),
                                color=if(record==index) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface) {
                                Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(title.replace("Максимальный вес","Макс. вес").replace("Лучший e1RM","e1RM").replace("Рекорд ",""),
                                        style=MaterialTheme.typography.labelSmall)
                                    Text(number(if(title=="Лучший e1RM") Analytics.estimate(s)!!.aggregate else s.weight)+" кг",
                                        style=MaterialTheme.typography.titleSmall,color=Lime)
                                }
                            }
                        }
                        repeat(3-chunk.size) {Spacer(Modifier.weight(1f))}
                    }
                }
                records.getOrNull(record)?.second?.let {s ->
                    Text("${number(s.weight)} кг × ${s.reps} · ${workouts.firstOrNull {it.id==s.workoutId}?.let {date(it.startedAt)} ?: ""}",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("ИТОГО ЗА ПЕРИОД · ${summaries.size} тренировок",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        SummaryCard(periodSets)
        Text("e1RM — оценка по 1–12 повторам. Веди пальцем по графику.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
fun AchievementsScreen(data: GymData) {
    val trophies=remember(data) {Achievements.evaluate(data)}
    SectionTitle("Твои достижения","${trophies.count {it.unlockedAt!=null}} из ${trophies.size}")
    if(data.completed.isEmpty()) Text("Первый трофей уже близко.")
    trophies.forEach {t ->
        Panel {
            Text("${if(t.unlockedAt!=null) "◆" else "◇"}  ${t.title}",color=if(t.unlockedAt!=null) Lime else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.titleMedium)
            Text(t.description,style=MaterialTheme.typography.bodySmall)
            Text(t.unlockedAt?.let {date(it)} ?: "Ещё впереди",style=MaterialTheme.typography.bodySmall)
        }
    }
}
