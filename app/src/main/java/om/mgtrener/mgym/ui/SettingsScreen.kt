package om.mgtrener.mgym.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import om.mgtrener.mgym.BuildConfig
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(model: GymViewModel) {
    val context=LocalContext.current
    var delete by remember { mutableStateOf(false) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> model.export(uri,false) } }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> model.export(uri,true) } }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::inspect) }
    SectionTitle("Настройки", "Твой дневник. Твои данные.")
    Panel {
        Text("Во время тренировки", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Тактильный отклик", Modifier.padding(top = 12.dp))
            Switch(modifier=Modifier.semantics {contentDescription="Вибрация действий"}, checked = model.haptics, onCheckedChange = model::toggleHaptics, enabled = !model.busy)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Звуковые эффекты",Modifier.padding(top=12.dp))
            Switch(model.sounds,model::toggleSounds,enabled=!model.busy,modifier=Modifier.semantics {contentDescription="Звук действий"})
        }
        Text("Короткий сигнал при добавлении, переносе и завершении. В беззвучном режиме звук выключен.",style=MaterialTheme.typography.bodySmall)
        Text("Единицы: кг · тема: графит", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Panel {
        Text("Резервная копия", style = MaterialTheme.typography.titleLarge)
        Text("JSON сохраняет тренировки, подходы, текущий ввод и настройки звука и вибрации. CSV предназначен для таблиц.")
        OutlinedButton(onClick = { exportJson.launch("MGym-backup.json") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Сохранить JSON / backup") }
        OutlinedButton(onClick = { exportCsv.launch("MGym-sets.csv") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Экспорт CSV") }
        OutlinedButton(onClick = { importJson.launch(arrayOf("application/json","text/plain","application/octet-stream")) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Восстановить из JSON") }
        Text("Файл выбираешь ты. Можно сохранить его на устройство или в выбранное тобой хранилище.", style = MaterialTheme.typography.bodySmall)
    }
    Panel {
        Text("Обновление приложения", style = MaterialTheme.typography.titleLarge)
        Text("Версия ${BuildConfig.VERSION_NAME} · жим лёжа")
        Text("Обновления из MG-Trener/MGym на GitHub. Проверка выполняется только по нажатию.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick=model::checkUpdate,enabled=!model.checkingUpdate) {Text(if(model.checkingUpdate) "Проверяем…" else "Проверить обновления")}
        model.update?.let { update ->
            Text(update.message,style=MaterialTheme.typography.bodySmall)
            update.apkUrl?.let {url ->
                Button(onClick={
                    runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,url.toUri()))}
                        .onFailure {model.notify("Не удалось открыть браузер для скачивания APK")}
                }) {Text("Скачать ${update.available}")}
                Text("После скачивания открой APK в загрузках. Android может попросить разрешить установку из браузера.",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    Panel {
        Text("О MGym", style = MaterialTheme.typography.titleLarge)
        Text("Тренировочные данные хранятся локально на устройстве.")
        Text("Без регистрации и рекламы. История не отправляется в интернет. Расчётный e1RM — статистическая оценка, а не проверенный максимум.")
        HorizontalDivider()
        Text("Разработчик", style = MaterialTheme.typography.titleMedium)
        Text("Михаил Гаврилычев · +7 701 870 93 84", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = {
                val url = "https://wa.me/77018709384".toUri()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                    .onFailure { model.notify("Не удалось открыть WhatsApp") }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Написать в WhatsApp") }
    }
    TextButton(onClick = { delete = true }, enabled = !model.busy) { Text("Удалить все данные", color = MaterialTheme.colorScheme.error) }
    if(delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Удалить весь дневник?") },
        text = { Text("Будут удалены тренировки, текущий ввод и настройки. Сначала сохрани JSON, если хочешь восстановить их позже.") },
        confirmButton = { TextButton(onClick = { model.clear(); delete = false }) { Text("Удалить всё") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Отмена") } })
    model.preview?.let { backup ->
        AlertDialog(onDismissRequest = model::cancelImport, title = { Text("Восстановить резервную копию?") },
            text = { Text("В файле: ${backup.data.workouts.size} тренировок, ${backup.data.sets.size} подходов.\n\nТекущий дневник и незавершённая тренировка будут заменены. Это действие нельзя отменить без своей резервной копии.") },
            confirmButton = { TextButton(onClick = model::importBackup, enabled = !model.busy) { Text("Заменить и восстановить") } },
            dismissButton = { TextButton(onClick = model::cancelImport) { Text("Отмена") } })
    }
}
