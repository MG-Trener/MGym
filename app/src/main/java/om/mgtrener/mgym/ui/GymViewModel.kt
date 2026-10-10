package om.mgtrener.mgym.ui

import android.app.Application
import android.net.Uri
import om.mgtrener.mgym.BuildConfig
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import om.mgtrener.mgym.database.GymDatabase
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.repositories.GymRepository
import om.mgtrener.mgym.services.*
import java.util.concurrent.Executors

class GymViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = GymRepository(GymDatabase(app))
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    var data by mutableStateOf(GymData()); private set
    var draft by mutableStateOf(Draft()); private set
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var preview by mutableStateOf<Backup?>(null); private set
    private val feedback=InteractionFeedback(app)
    private val restTimerStore=RestTimerStore(app)
    var restTimer by mutableStateOf(RestTimerState()); private set
    private val updateWorker=Executors.newSingleThreadExecutor()
    var checkingUpdate by mutableStateOf(false); private set
    var update by mutableStateOf<UpdateInfo?>(null); private set
    var sounds by mutableStateOf(true); private set
    var haptics by mutableStateOf(true); private set
    var completedId by mutableStateOf<Long?>(null); private set

    init {
        worker.execute {
            try {
                repository.prepareDiary()
                val loaded = repository.read().trainingDiary()
                val restored = repository.draft()
                val vibration = repository.setting("haptics") != "false"
                val sound = repository.setting("sounds") != "false"
                main.post {
                    data = loaded; draft = restored; haptics = vibration; sounds = sound
                    restTimer = loaded.active?.let { restTimerStore.load(it.id) } ?: run {
                        restTimerStore.clear(); RestTimerState()
                    }
                    ready = true
                }
            } catch(e: Exception) { main.post { message = "Не удалось открыть данные: ${e.message}" } }
        }
    }
    private fun action(reloadDraft: Boolean = false, success: (() -> Unit)? = null, block: () -> Unit) {
        if (busy || !ready) return
        busy = true
        worker.execute {
            try {
                block()
                val loaded = repository.read().trainingDiary()
                val restored = if(reloadDraft) repository.draft() else null
                val vibration = repository.setting("haptics") != "false"
                val sound = repository.setting("sounds") != "false"
                main.post {
                    data = loaded; restored?.let { draft = it }; haptics = vibration; sounds = sound
                    if (loaded.active?.id != restTimer.workoutId) {
                        restTimer = loaded.active?.let { restTimerStore.load(it.id) } ?: run {
                            restTimerStore.clear(); RestTimerState()
                        }
                    }
                    busy = false; success?.invoke()
                }
            } catch(e: Exception) {
                main.post { busy = false; message = e.message ?: "Не удалось сохранить данные" }
            }
        }
    }
    fun updateDraft(value: Draft) {
        if (busy || !ready) return
        draft = value
        worker.execute {
            try { repository.saveDraft(value) }
            catch(e: Exception) { main.post { message = "Ввод не сохранён: ${e.message}" } }
        }
    }
    fun start(exercises: List<Exercise>) = action(reloadDraft = true) { repository.start(exercises) }
    fun add(onSuccess: () -> Unit) {
        val active = data.active ?: return
        val set = try { draft.copy(kind = SetKind.WORK).toSet(active.id) } catch(e: IllegalArgumentException) { message = e.message; return }
        action(reloadDraft=true,success = {feedback.play(sounds,haptics);onSuccess()}) { repository.appendRow(set) }
    }
    fun reorder(workoutId: Long, exercise: Exercise, ids: List<Long>) = action(success={feedback.play(sounds,haptics)}) { repository.reorder(workoutId,exercise,ids) }
    fun save(set: LiftSet) = action { repository.saveSet(set) }
    fun remove(set: LiftSet) = action { repository.deleteSet(set) }
    fun saveCompleted(set: LiftSet) = action { repository.saveCompletedSet(set) }
    fun removeCompleted(set: LiftSet) = action { repository.deleteCompletedSet(set) }
    fun finish() {
        val id = data.active?.id ?: return
        val pending=if(draft.weight.isBlank() && draft.reps.isBlank()) null else try {
            draft.copy(kind=SetKind.WORK).toSet(id)
        } catch(e:IllegalArgumentException) {message=e.message;return}
        action(reloadDraft=true,success = { completedId = id; feedback.play(sounds,haptics,true) }) { repository.finishWithRow(id,pending) }
    }
    fun closeResult() { completedId = null }
    fun startRestTimer() { data.active?.let { restTimer = restTimerStore.start(it.id) } }
    fun pauseRestTimer() { data.active?.let { restTimer = restTimerStore.pause(it.id) } }
    fun resetRestTimer() { data.active?.let { restTimer = restTimerStore.reset(it.id) } }
    fun dismissMessage() { message = null }
    fun notify(text: String) { message = text }
    fun toggleHaptics(value: Boolean) = action { repository.setSetting("haptics",value.toString()) }
    fun toggleSounds(value: Boolean) = action { repository.setSetting("sounds",value.toString()) }
    fun clear() = action(reloadDraft = true) { repository.clear() }
    fun export(uri: Uri, csv: Boolean) = action(success = { message = "Файл сохранён" }) {
        val text = if(csv) BackupCodec.csv(repository.read().trainingDiary()) else BackupCodec.encode(repository.backup())
        val resolver = getApplication<Application>().contentResolver
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "Не удалось открыть файл" }.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }
    fun inspect(uri: Uri) = action {
        val resolver = getApplication<Application>().contentResolver
        val bytes = requireNotNull(resolver.openInputStream(uri)).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= BackupCodec.MAX_BYTES) { "Файл больше 10 МБ" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size <= BackupCodec.MAX_BYTES) { "Файл больше 10 МБ" }
        val backup = BackupCodec.decode(bytes.toString(Charsets.UTF_8))
        main.post { preview = backup }
    }
    fun cancelImport() { preview = null }
    fun importBackup() {
        val backup = preview ?: return
        action(reloadDraft = true, success = { preview = null; message = "Резервная копия восстановлена" }) { repository.replace(backup); repository.prepareDiary() }
    }
    fun checkUpdate() {
        if(checkingUpdate) return
        checkingUpdate=true; update=null
        updateWorker.execute {
            val result=try { GitHubUpdateProvider(BuildConfig.VERSION_NAME).check() }
            catch(e:Exception) {UpdateInfo(null,"Не удалось проверить обновление. Проверь интернет и повтори. ${e.message.orEmpty()}")}
            main.post {update=result;checkingUpdate=false}
        }
    }
    override fun onCleared() {
        worker.execute { repository.close() }
        worker.shutdown()
        updateWorker.shutdownNow()
        feedback.close()
    }
}
