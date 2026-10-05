package om.mgtrener.mgym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.ui.theme.Lime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WorkoutScreen(model: GymViewModel, collapse: () -> Unit = {}) {
    val active = model.data.active ?: return
    val focus = LocalFocusManager.current
    var editing by remember { mutableStateOf<LiftSet?>(null) }
    var removing by remember { mutableStateOf<LiftSet?>(null) }
    var finish by remember { mutableStateOf(false) }
    val draft = model.draft
    val sets = model.data.setsFor(active.id).filter { it.exercise == Exercise.BENCH }
    val valid = runCatching {draft.toSet(active.id)}.isSuccess
    val empty = draft.weight.isBlank() && draft.reps.isBlank()
    Column(Modifier.fillMaxSize().imePadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = collapse, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(36.dp).semantics { contentDescription = "Свернуть тренировку" }) { Text("‹", fontSize = 28.sp) }
            Column(Modifier.weight(1f)) {
                Text("В работе", style = MaterialTheme.typography.titleLarge)
                Text("MGYM / BENCH PRESS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Instant.ofEpochMilli(active.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.labelLarge, color = Lime)
        }
        Text("Жим штанги лёжа",style=MaterialTheme.typography.titleMedium,color=Lime)
        SummaryCard(sets)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Подходы",style=MaterialTheme.typography.titleMedium)
            Text("Сохранено: ${sets.size}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(start=56.dp,end=36.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Вес, кг",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
            Text("Повторы",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
        }
        SetTable(sets,Modifier.weight(1f),!model.busy,
            reorder={model.reorder(active.id,Exercise.BENCH,it)},edit={editing=it},delete={removing=it},
            footer={
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("${sets.size+1}",Modifier.width(48.dp),textAlign=TextAlign.End,style=MaterialTheme.typography.bodyMedium,color=Lime)
                        RowField(draft.weight,"Вес, кг",KeyboardType.Decimal,{if(it.length<=12) model.updateDraft(draft.copy(weight=it))},!model.busy,Modifier.weight(1f))
                        RowField(draft.reps,"Повторы",KeyboardType.Number,{if(it.length<=3) model.updateDraft(draft.copy(reps=it))},!model.busy,Modifier.weight(1f))
                        Spacer(Modifier.width(28.dp))
                    }
                    TextButton(onClick={focus.clearFocus();model.add {}},enabled=valid && !model.busy,
                        modifier=Modifier.padding(start=48.dp).size(48.dp).semantics {contentDescription="Добавить строку"},contentPadding=PaddingValues(0.dp)) {
                        Text("+",fontSize=28.sp)
                    }
                    if(!empty && !valid) Text("Укажи вес больше 0 и повторы от 1 до 200",Modifier.padding(start=56.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
        OutlinedButton(onClick = { finish = true }, enabled = !model.busy && (valid || (empty && sets.isNotEmpty())),
            shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)) { Text("Завершить тренировку") }
    }
    if(finish) AlertDialog(onDismissRequest = { finish = false }, title = { Text("Завершить тренировку?") },
        text = { Text("Последняя заполненная строка тоже сохранится. Пустая строка не добавляется в историю.") },
        confirmButton = { TextButton(onClick = { finish = false; model.finish() }) { Text("Завершить") } },
        dismissButton = { TextButton(onClick = { finish = false }) { Text("Продолжить") } })
    removing?.let { s ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Удалить подход?") },
            text = { Text("${number(s.weight)} кг × ${s.reps}") },
            confirmButton = { TextButton(onClick = { model.remove(s); removing = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Отмена") } })
    }
    editing?.let { s -> EditSetDialog(s, { editing = null }) { updated -> model.save(updated); editing = null } }
}

@Composable
private fun RowField(value:String,label:String,keyboard:KeyboardType,change:(String)->Unit,enabled:Boolean,modifier:Modifier) {
    BasicTextField(value,onValueChange=change,enabled=enabled,singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=keyboard),cursorBrush=SolidColor(Lime),
        textStyle=MaterialTheme.typography.titleMedium.copy(color=MaterialTheme.colorScheme.onSurface),
        modifier=modifier.heightIn(min=44.dp).border(1.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(7.dp))
            .padding(horizontal=10.dp,vertical=10.dp).semantics {contentDescription=label},
        decorationBox={input->Box {if(value.isEmpty()) Text("—",Modifier.clearAndSetSemantics {},color=MaterialTheme.colorScheme.onSurfaceVariant);input()}})
}

@Composable
private fun NumberInput(label: String, value: String, enabled: Boolean, keyboard: KeyboardType,
                        change: (String) -> Unit, modifier: Modifier = Modifier, controls: @Composable RowScope.() -> Unit) {
    Column(modifier.background(MaterialTheme.colorScheme.background, RoundedCornerShape(9.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        BasicTextField(value = value, onValueChange = change, enabled = enabled, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard), cursorBrush = SolidColor(Lime),
            textStyle = MaterialTheme.typography.headlineLarge.copy(color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).semantics { contentDescription = label },
            decorationBox = { input -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { input() } })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, content = controls)
    }
}

@Composable
fun SetEditor(draft: Draft, enabled: Boolean, change: (Draft) -> Unit) {
    var step by rememberSaveable { mutableDoubleStateOf(2.5) }
    var stepMenu by remember { mutableStateOf(false) }
    fun adjustWeight(direction: Int) {
        val value = draft.weight.replace(',', '.').toDoubleOrNull() ?: 0.0
        change(draft.copy(weight = number((value + direction * step).coerceIn(0.0, 1500.0))))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberInput("Вес, кг", draft.weight, enabled, KeyboardType.Decimal, { if(it.length <= 12) change(draft.copy(weight = it)) }, Modifier.weight(1.2f)) {
            StepButton("−", "Уменьшить вес", enabled) { adjustWeight(-1) }
            Box {
                TextButton(onClick = { stepMenu = true }, enabled = enabled, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = 36.dp).widthIn(min = 40.dp).semantics { contentDescription = "Шаг веса" }) {
                    Text(number(step) + "⌄", style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(expanded = stepMenu, onDismissRequest = { stepMenu = false }) {
                    listOf(1.0,2.5,5.0).forEach { value ->
                        DropdownMenuItem(text = { Text("Шаг ${number(value)} кг") }, onClick = { step = value; stepMenu = false })
                    }
                }
            }
            StepButton("+", "Увеличить вес", enabled) { adjustWeight(1) }
        }
        NumberInput("Повторы", draft.reps, enabled, KeyboardType.Number, { if(it.length <= 3) change(draft.copy(reps = it)) }, Modifier.weight(1f)) {
            StepButton("−", "Уменьшить повторы", enabled) { change(draft.copy(reps = ((draft.reps.toIntOrNull() ?: 1)-1).coerceAtLeast(1).toString())) }
            StepButton("+", "Увеличить повторы", enabled) { change(draft.copy(reps = ((draft.reps.toIntOrNull() ?: 0)+1).coerceAtMost(200).toString())) }
        }
    }
    var details by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).clickable { details = !details },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if(details) "Детали подхода" else buildList {
            draft.rir.takeIf { it.isNotBlank() }?.let { add("RIR $it") }
            draft.rpe.takeIf { it.isNotBlank() }?.let { add("RPE $it") }
            if(draft.comment.isNotBlank()) add("Комментарий")
        }.joinToString(" · ").ifEmpty { "RIR / RPE · заметка" }, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(if(details) "−" else "+", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(details) Dialog(onDismissRequest={details=false}) { Surface(shape=RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft.rir, { if(it.length <= 2) change(draft.copy(rir = it)) },
                label = { Text("RIR, 0–10") }, enabled = enabled, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(draft.rpe, { if(it.length <= 4) change(draft.copy(rpe = it)) },
                label = { Text("RPE, 1–10") }, enabled = enabled, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
        OutlinedTextField(draft.comment, { if(it.length <= 1000) change(draft.copy(comment = it)) },
            label = { Text("Комментарий") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        TextButton(onClick={details=false},modifier=Modifier.align(Alignment.End)) {Text("Готово")}
    } }
    }
}

@Composable
private fun StepButton(text: String, description: String, enabled: Boolean, click: () -> Unit) {
    TextButton(onClick = click, enabled = enabled, contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(36.dp).semantics { contentDescription = description }) { Text(text, fontSize = 22.sp) }
}

@Composable
private fun EditSetDialog(set: LiftSet, dismiss: () -> Unit, save: (LiftSet) -> Unit) {
    var draft by remember(set.id) { mutableStateOf(Draft(set.exercise, number(set.weight), set.reps.toString(),
        set.kind, set.technique, set.rir?.toString().orEmpty(), set.rpe?.toString().orEmpty(), set.comment)) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(12.dp).heightIn(max = 600.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Изменить подход", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                SetEditor(draft, true) { draft = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("Отмена") }
                    Button(onClick = {
                        try { save(draft.toSet(set.workoutId, set.id)) }
                        catch(e: IllegalArgumentException) { error = e.message }
                    }, shape = RoundedCornerShape(8.dp)) { Text("Сохранить") }
                }
            }
        }
    }
}
