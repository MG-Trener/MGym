package om.mgtrener.mgym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.ui.theme.Lime

@Composable
fun SetTable(sets: List<LiftSet>, modifier: Modifier = Modifier, enabled: Boolean = true,
             reorder: ((List<Long>) -> Unit)? = null, edit: ((LiftSet) -> Unit)? = null,
             duplicate: ((LiftSet) -> Unit)? = null, delete: ((LiftSet) -> Unit)? = null) {
    var ordered by remember(sets) { mutableStateOf(sets) }
    val state = rememberLazyListState()
    var dragging by remember { mutableStateOf<Long?>(null) }
    var center by remember { mutableFloatStateOf(0f) }
    val onReorder by rememberUpdatedState(reorder)
    fun moveAtCenter() {
        val id = dragging ?: return
        val target = state.layoutInfo.visibleItemsInfo.firstOrNull { center in it.offset.toFloat()..(it.offset+it.size).toFloat() } ?: return
        val from = ordered.indexOfFirst { it.id == id }
        val to = ordered.indexOfFirst { it.id == target.key }
        if(from >= 0 && to >= 0 && from != to) ordered = ordered.toMutableList().apply { add(to,removeAt(from)) }
    }
    LaunchedEffect(dragging) {
        while(dragging != null) {
            withFrameMillis { }
            val layout=state.layoutInfo
            val direction = when {
                center < layout.viewportStartOffset + 36 -> -10f
                center > layout.viewportEndOffset - 36 -> 10f
                else -> 0f
            }
            if(direction != 0f) { state.scrollBy(direction); moveAtCenter() }
        }
    }
    LazyColumn(modifier, state = state) {
        itemsIndexed(ordered, key = { _,s -> s.id }) { index,s ->
            var expanded by remember(s.id) { mutableStateOf(false) }
            var menu by remember(s.id) { mutableStateOf(false) }
            val isDragging=dragging == s.id

            Column(Modifier.animateItem().fillMaxWidth().zIndex(if(isDragging) 1f else 0f)
                .graphicsLayer { val info=if(isDragging) state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == s.id } else null; translationY = if(info != null) center-info.offset-info.size/2f else 0f }
                .background(if(isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                    if(reorder != null) Box(Modifier.width(32.dp).height(36.dp)
                        .semantics {
                            contentDescription="Переместить подход ${index+1}"
                            customActions = listOf(
                                CustomAccessibilityAction("Выше") {
                                    if(index>0 && enabled) { onReorder?.invoke(ordered.toMutableList().apply { add(index-1,removeAt(index)) }.map { it.id }); true } else false
                                },
                                CustomAccessibilityAction("Ниже") {
                                    if(index<ordered.lastIndex && enabled) { onReorder?.invoke(ordered.toMutableList().apply { add(index+1,removeAt(index)) }.map { it.id }); true } else false
                                })
                        }
                        .pointerInput(s.id,enabled) {
                            if(enabled) detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == s.id }?.let { dragging=s.id; center=it.offset+it.size/2f }
                                },
                                onDrag = { change, amount -> change.consume(); center += amount.y; moveAtCenter() },
                                onDragCancel = { dragging=null; ordered=sets },
                                onDragEnd = {
                                    if(ordered.map { it.id } != sets.map { it.id }) onReorder?.invoke(ordered.map { it.id })
                                    dragging=null
                                })
                        }, contentAlignment=Alignment.Center) { Text("≡",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("${index+1}",Modifier.width(24.dp), style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.weight(1f).clickable { expanded=!expanded }.padding(vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
                        Text("${number(s.weight)} × ${s.reps}",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        Text(buildList {
                            s.rir?.let { add("RIR $it") }; s.rpe?.let { add("RPE ${number(it)}") }
                            if(s.comment.isNotBlank()) add("···")
                        }.joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(edit != null) Box {
                        TextButton(onClick={menu=true},enabled=enabled,modifier=Modifier.width(36.dp).height(36.dp),contentPadding=PaddingValues(0.dp)) {
                            Text("⋮",color=Lime,modifier=Modifier.semantics { contentDescription="Действия с подходом ${index+1}" })
                        }
                        DropdownMenu(menu,{menu=false}) {
                            DropdownMenuItem(text={Text("Изменить")},onClick={menu=false;edit(s)})
                            duplicate?.let { action -> DropdownMenuItem(text={Text("Повторить")},onClick={menu=false;action(s)}) }
                            delete?.let { action -> DropdownMenuItem(text={Text("Удалить")},onClick={menu=false;action(s)}) }
                        }
                    } else Spacer(Modifier.width(8.dp))
                }
                if(expanded && s.comment.isNotBlank()) Text(s.comment,Modifier.padding(start=56.dp,end=8.dp,bottom=6.dp),style=MaterialTheme.typography.bodySmall)
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=0.22f))
            }
        }
    }
}
