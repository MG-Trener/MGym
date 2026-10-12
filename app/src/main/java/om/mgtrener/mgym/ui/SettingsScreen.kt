package om.mgtrener.mgym.ui

import android.content.Intent
import android.Manifest
import androidx.core.net.toUri
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import om.mgtrener.mgym.BuildConfig
import om.mgtrener.mgym.services.BackgroundUpdates
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
    var licenses by remember { mutableStateOf(false) }
    var automaticUpdates by remember { mutableStateOf(BackgroundUpdates.enabled(context)) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) BackgroundUpdates.schedule(context)
        else model.notify("Чтобы получать уведомления об обновлениях, разреши их для MGym в настройках Android")
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> model.export(uri,false) } }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> model.export(uri,true) } }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::inspect) }
    SectionTitle("Настройки", "Твой дневник. Твои данные.")
    Panel {
        LocalizedText("Язык приложения", style = MaterialTheme.typography.titleLarge)
        var expanded by remember { mutableStateOf(false) }
        val selection = om.mgtrener.mgym.i18n.Language.selected(context)
        val names = om.mgtrener.mgym.i18n.Language.names
        val tags = om.mgtrener.mgym.i18n.Language.tags
        Box {
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                LocalizedText(if(selection=="system") "Как в системе" else names[tags.indexOf(selection)])
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                (listOf("system")+tags).forEach { tag ->
                    DropdownMenuItem(text={LocalizedText(if(tag=="system") "Как в системе" else names[tags.indexOf(tag)])},onClick={
                        expanded=false
                        om.mgtrener.mgym.i18n.Language.choose(context,tag)
                        if(android.os.Build.VERSION.SDK_INT < 33) (context as? android.app.Activity)?.recreate()
                    })
                }
            }
        }
    }
    Panel {
        LocalizedText("Во время тренировки", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LocalizedText("Тактильный отклик", Modifier.padding(top = 12.dp))
            Switch(modifier=Modifier.semantics {contentDescription=om.mgtrener.mgym.i18n.Language.text("Вибрация действий")}, checked = model.haptics, onCheckedChange = model::toggleHaptics, enabled = !model.busy)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            LocalizedText("Звуковые эффекты",Modifier.padding(top=12.dp))
            Switch(model.sounds,model::toggleSounds,enabled=!model.busy,modifier=Modifier.semantics {contentDescription=om.mgtrener.mgym.i18n.Language.text("Звук действий")})
        }
        LocalizedText("Короткий сигнал при добавлении, переносе и завершении. В беззвучном режиме звук выключен.",style=MaterialTheme.typography.bodySmall)
        LocalizedText("Единицы: кг · тема: графит", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Panel {
        LocalizedText("Планы тренировок", style = MaterialTheme.typography.titleLarge)
        LocalizedText("Генератор работает внутри приложения без интернета. Программу с сайта можно перенести через «Планы тренировок → Мои данные → Загрузить копию».")
        OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://mgym.mg-trener.com/".toUri())) }
                .onFailure { model.notify("Не удалось открыть сайт в браузере") }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { LocalizedText("Открыть сайт подготовки ↗") }
    }
    Panel {
        LocalizedText("Резервная копия", style = MaterialTheme.typography.titleLarge)
        LocalizedText("JSON сохраняет дневник, текущий ввод и настройки. Для программ генератора используйте отдельную копию: «Планы тренировок → Мои данные». CSV предназначен для таблиц.")
        OutlinedButton(onClick = { exportJson.launch("MGym-backup.json") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { LocalizedText("Сохранить JSON / backup") }
        OutlinedButton(onClick = { exportCsv.launch("MGym-sets.csv") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { LocalizedText("Экспорт CSV") }
        OutlinedButton(onClick = { importJson.launch(arrayOf("application/json","text/plain","application/octet-stream")) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { LocalizedText("Восстановить из JSON") }
        LocalizedText("Файл выбираешь ты. Можно сохранить его на устройство или в выбранное тобой хранилище.", style = MaterialTheme.typography.bodySmall)
    }
    if(!BuildConfig.STORE_DISTRIBUTION) Panel {
        LocalizedText("Обновление приложения", style = MaterialTheme.typography.titleLarge)
        LocalizedText("Версия ${BuildConfig.VERSION_NAME} · жим / тяга / присед")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            LocalizedText("Проверять в фоне",Modifier.padding(top=12.dp))
            Switch(automaticUpdates,{ enabled ->
                automaticUpdates=enabled
                BackgroundUpdates.setEnabled(context,enabled)
                if (enabled && !BackgroundUpdates.canNotify(context))
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },modifier=Modifier.semantics {contentDescription=om.mgtrener.mgym.i18n.Language.text("Фоновая проверка обновлений")})
        }
        LocalizedText("Проверяем примерно раз в день при наличии сети. О новой версии сообщим уведомлением. Его нажатие откроет APK для скачивания; установку подтверждает Android.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick=model::checkUpdate,enabled=!model.checkingUpdate) {LocalizedText(if(model.checkingUpdate) "Проверяем…" else "Проверить обновления")}
        model.update?.let { update ->
            LocalizedText(update.message,style=MaterialTheme.typography.bodySmall)
            update.apkUrl?.let {url ->
                Button(onClick={
                    runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,url.toUri()))}
                        .onFailure {model.notify("Не удалось открыть браузер для скачивания APK")}
                }) {LocalizedText("Скачать ${update.available}")}
                LocalizedText("После скачивания открой APK в загрузках. Android может попросить разрешить установку из браузера.",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    if(BuildConfig.STORE_DISTRIBUTION) Panel { LocalizedText("Версия ${BuildConfig.VERSION_NAME}. Обновления — через Google Play.") }
    Panel {
        LocalizedText("О MGym", style = MaterialTheme.typography.titleLarge)
        LocalizedText("Тренировочные данные хранятся локально на устройстве.")
        LocalizedText("MGym сделан на энтузиазме спортсмена и бесплатен для всех желающих. Без регистрации и рекламы. История не отправляется в интернет. Расчётный e1RM — статистическая оценка, а не проверенный максимум.")
        LocalizedText("MGym не является медицинским изделием, не диагностирует, не лечит и не предотвращает заболевания. По вопросам здоровья и допустимости нагрузок обратитесь к медицинскому специалисту.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,("https://mgym.mg-trener.com/" + if(om.mgtrener.mgym.i18n.Language.tag=="ru") "privacy.html" else "privacy-en.html").toUri())) }.onFailure {model.notify("Не удалось открыть политику конфиденциальности")} },modifier=Modifier.fillMaxWidth()) {LocalizedText("Конфиденциальность ↗")}
        OutlinedButton(onClick={ licenses=true },modifier=Modifier.fillMaxWidth()) { LocalizedText("Лицензии компонентов") }
        HorizontalDivider()
        LocalizedText("Любое вознаграждение разработчику за эту программу — исключительно по желанию пользователя. Все функции доступны бесплатно.")
        LocalizedText("Отзывы и предложения можно отправить разработчику в WhatsApp.")
        LocalizedText("Разработчик", style = MaterialTheme.typography.titleMedium)
        LocalizedText("Михаил Гаврилычев · +7 701 870 93 84", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = {
                val url = "https://wa.me/77018709384".toUri()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                    .onFailure { model.notify("Не удалось открыть WhatsApp") }
            },
            modifier = Modifier.fillMaxWidth()
        ) { LocalizedText("Отзывы и предложения · WhatsApp ↗") }
    }
    TextButton(onClick = { delete = true }, enabled = !model.busy) { LocalizedText("Удалить все данные", color = MaterialTheme.colorScheme.error) }
    if(delete) AlertDialog(onDismissRequest = { delete = false }, title = { LocalizedText("Удалить весь дневник?") },
        text = { LocalizedText("Будут удалены тренировки, текущий ввод и настройки. Сначала сохрани JSON, если хочешь восстановить их позже.") },
        confirmButton = { TextButton(onClick = { model.clear(); delete = false }) { LocalizedText("Удалить всё") } },
        dismissButton = { TextButton(onClick = { delete = false }) { LocalizedText("Отмена") } })
    if(licenses) AlertDialog(onDismissRequest={licenses=false},title={LocalizedText("Лицензии компонентов")},text={
        val notice=remember { context.assets.open("licenses/NOTICE.txt").bufferedReader().use { it.readText() } }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=440.dp)) {
            items(notice.split("\n\n").size) { i -> Text(notice.split("\n\n")[i],style=MaterialTheme.typography.bodySmall) }
        }
    },confirmButton={TextButton(onClick={licenses=false}) {LocalizedText("Закрыть")} })
    model.preview?.let { backup ->
        AlertDialog(onDismissRequest = model::cancelImport, title = { LocalizedText("Восстановить резервную копию?") },
            text = { LocalizedText("В файле: ${backup.data.workouts.size} тренировок, ${backup.data.sets.size} подходов.\n\nТекущий дневник и незавершённая тренировка будут заменены. Это действие нельзя отменить без своей резервной копии.") },
            confirmButton = { TextButton(onClick = model::importBackup, enabled = !model.busy) { LocalizedText("Заменить и восстановить") } },
            dismissButton = { TextButton(onClick = model::cancelImport) { LocalizedText("Отмена") } })
    }
}
