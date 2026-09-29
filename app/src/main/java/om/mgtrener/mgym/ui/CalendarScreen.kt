package om.mgtrener.mgym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.ui.theme.Lime
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")
@Composable
fun CalendarScreen(model: GymViewModel) {
    val data=model.data
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var month by rememberSaveable { mutableIntStateOf(LocalDate.now().monthValue) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    val days=remember(data) { data.completed.groupBy {day(it.startedAt)} }
    val ym=YearMonth.of(year,month)
    val workouts=remember(data,year,month,mode) {
        data.completed.filter { day(it.startedAt).year==year && (mode==0 || day(it.startedAt).monthValue==month) }
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Календарь",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
            Row(Modifier.width(150.dp)) { CompactTabs(listOf("Год","Месяц"),mode) {mode=it} }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={
                if(mode==0) year-- else ym.minusMonths(1).let {year=it.year;month=it.monthValue}
            },enabled=if(mode==0) year>1970 else ym>YearMonth.of(1970,1)) {Text("←")}
            Text(if(mode==0) "$year" else "${ym.month.getDisplayName(TextStyle.FULL_STANDALONE,ru)} $year",style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={
                if(mode==0) year++ else ym.plusMonths(1).let {year=it.year;month=it.monthValue}
            },enabled=if(mode==0) year<2099 else ym<YearMonth.of(2099,12)) {Text("→")}
        }
        Text("${workouts.size} тренировок · жим лёжа",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(mode==0) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                repeat(4) { row ->
                    Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        repeat(3) { col ->
                            val m=row*3+col+1
                            MiniMonth(YearMonth.of(year,m),days,Modifier.weight(1f).fillMaxHeight()) {month=m;mode=1}
                        }
                    }
                }
            }
            Text("Нажми месяц · ● — день тренировки",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row { listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach {
                        Text(it,Modifier.weight(1f),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                    val offset=ym.atDay(1).dayOfWeek.value-1
                    repeat(6) { week ->
                        Row {
                            repeat(7) { weekday ->
                                val d=week*7+weekday-offset+1
                                if(d !in 1..ym.lengthOfMonth()) Spacer(Modifier.weight(1f).height(46.dp))
                                else {
                                    val date=ym.atDay(d)
                                    val sessions=days[date].orEmpty()
                                    val today=date==LocalDate.now()
                                    Column(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if(sessions.isNotEmpty()) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                        .clickable {selectedDay=date}
                                        .semantics {contentDescription="Дата $date, тренировок ${sessions.size}"},
                                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                        Text("$d",style=MaterialTheme.typography.titleSmall,color=if(today || sessions.isNotEmpty()) Lime else MaterialTheme.colorScheme.onSurface)
                                        Text(mark(sessions),style=MaterialTheme.typography.labelSmall,color=Lime)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text("Нажми дату, чтобы увидеть и скопировать тренировку.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    selectedDay?.let { date ->
        val sessions=days[date].orEmpty().sortedBy {it.startedAt}
        var selected by remember(date) {mutableIntStateOf(0)}
        val workout=sessions.getOrNull(selected)
        val rows=workout?.let {w->data.setsFor(w.id).groupBy {it.exercise}.values.maxOfOrNull {it.size}} ?: 0
        val maxHeight=(LocalConfiguration.current.screenHeightDp*0.82f).dp
        val height=(270 + rows.coerceAtMost(14)*37 + if(sessions.size>1) 42 else 0).dp.coerceAtMost(maxHeight)
        Dialog(onDismissRequest={selectedDay=null},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxWidth().padding(12.dp),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.height(if(workout==null) 140.dp else height).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text(date.toString(),style=MaterialTheme.typography.titleSmall)
                        TextButton(onClick={selectedDay=null},contentPadding=PaddingValues(0.dp),modifier=Modifier.height(36.dp)) {Text("Закрыть")}
                    }
                    if(workout==null) Text("В этот день тренировок нет",style=MaterialTheme.typography.bodyMedium)
                    else {
                        if(sessions.size>1) CompactTabs(sessions.map {date(it.startedAt).substringAfter("· ")},selected) {selected=it}
                        Box(Modifier.weight(1f)) {WorkoutDetail(data,workout.id,reorder=model::reorder,enabled=!model.busy)}
                    }
                }
            }
        }
    }
}
private fun mark(sessions:List<Workout>):String {
    return if(sessions.isEmpty()) "" else "●"
}
@Composable
private fun MiniMonth(ym:YearMonth,days:Map<LocalDate,List<Workout>>,modifier:Modifier,open:()->Unit) {
    Column(modifier.clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick=open)
        .semantics(mergeDescendants=true) {contentDescription="Месяц ${ym.monthValue}, ${ym.year}"}
        .padding(5.dp)) {
        Text(ym.month.getDisplayName(TextStyle.SHORT_STANDALONE,ru).uppercase(),fontSize=10.sp,lineHeight=12.sp,color=Lime)
        Row {listOf("П","В","С","Ч","П","С","В").forEach {Text(it,Modifier.weight(1f),fontSize=8.sp,lineHeight=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        val offset=ym.atDay(1).dayOfWeek.value-1
        repeat(6) {week ->
            Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
                repeat(7) {weekday ->
                    val d=week*7+weekday-offset+1
                    val sessions=if(d in 1..ym.lengthOfMonth()) days[ym.atDay(d)].orEmpty() else emptyList()
                    Text(if(d !in 1..ym.lengthOfMonth()) " " else mark(sessions).ifEmpty {d.toString()},
                        Modifier.weight(1f),fontSize=9.sp,lineHeight=11.sp,color=if(sessions.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Lime)
                }
            }
        }
    }
}
