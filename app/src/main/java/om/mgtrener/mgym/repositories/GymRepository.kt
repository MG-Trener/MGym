package om.mgtrener.mgym.repositories

import android.content.ContentValues
import om.mgtrener.mgym.database.GymDatabase
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.services.Backup
import om.mgtrener.mgym.services.BackupCodec
import org.json.JSONObject

class GymRepository(private val helper: GymDatabase) {
    private val db get() = helper.writableDatabase
    private fun <T> transaction(block: () -> T): T {
        db.beginTransaction()
        try { val result = block(); db.setTransactionSuccessful(); return result }
        finally { db.endTransaction() }
    }
    fun read(): GymData {
        val workouts = db.rawQuery("SELECT * FROM workouts ORDER BY started_at DESC, id DESC", null).use { c ->
            buildList {
                while (c.moveToNext()) add(Workout(c.getLong(0), c.getLong(1),
                    if (c.isNull(2)) null else c.getLong(2), c.getString(3).split(",").map { Exercise.valueOf(it) }))
            }
        }
        val sets = db.rawQuery("SELECT * FROM sets ORDER BY workout_id, exercise, position, id", null).use { c ->
            buildList {
                while(c.moveToNext()) add(LiftSet(c.getLong(0), c.getLong(1), Exercise.valueOf(c.getString(2)),
                    c.getDouble(3), c.getInt(4), SetKind.valueOf(c.getString(5)), Technique.valueOf(c.getString(6)),
                    if(c.isNull(7)) null else c.getInt(7), if(c.isNull(8)) null else c.getDouble(8), c.getString(9)))
            }
        }
        return GymData(workouts, sets)
    }
    // Archive an old curl-only active session without deleting its sets.
    fun prepareBenchDiary() = transaction {
        val active = read().active
        if(active != null && Exercise.BENCH !in active.exercises) {
            if(read().setsFor(active.id).isEmpty()) db.delete("workouts","id=?",arrayOf(active.id.toString()))
            else db.execSQL("UPDATE workouts SET finished_at=MAX(started_at,?) WHERE id=?",arrayOf<Any>(System.currentTimeMillis(),active.id))
        }
        if(draft().exercise != Exercise.BENCH) saveDraft(Draft())
    }
    fun setting(key: String): String? = db.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use {
        if(it.moveToFirst()) it.getString(0) else null
    }
    fun setSetting(key: String, value: String) {
        db.execSQL("INSERT OR REPLACE INTO settings(key,value) VALUES(?,?)", arrayOf(key,value))
    }
    fun draft(): Draft = setting("draft")?.let { BackupCodec.draftFrom(JSONObject(it)) } ?: Draft()
    fun saveDraft(draft: Draft) = setSetting("draft", BackupCodec.draftJson(draft).toString())
    fun start(exercises: List<Exercise>): Long = transaction {
        require(exercises.isNotEmpty())
        db.rawQuery("SELECT id FROM workouts WHERE finished_at IS NULL", null).use {
            if(it.moveToFirst()) return@transaction it.getLong(0)
        }
        val id = db.insertOrThrow("workouts", null, ContentValues().apply {
            put("started_at", System.currentTimeMillis()); put("exercises", exercises.distinct().joinToString(",") { it.name })
        })
        saveDraft(Draft(exercise = exercises.first(), weight="", reps=""))
        id
    }
    private fun values(s: LiftSet) = ContentValues().apply {
        put("workout_id", s.workoutId); put("exercise", s.exercise.name); put("weight", s.weight)
        put("reps", s.reps); put("kind", s.kind.name); put("technique", s.technique.name)
        put("rir", s.rir); put("rpe", s.rpe); put("comment", s.comment)
    }
    private fun requireActive(id: Long, exercise: Exercise? = null) {
        db.rawQuery("SELECT exercises FROM workouts WHERE id=? AND finished_at IS NULL", arrayOf(id.toString())).use {
            require(it.moveToFirst()) { "Тренировка уже завершена" }
            require(exercise == null || exercise.name in it.getString(0).split(",")) { "Упражнение не выбрано" }
        }
    }
    fun saveSet(s: LiftSet) = transaction {
        requireActive(s.workoutId, s.exercise)
        Draft(s.exercise, s.weight.toString(), s.reps.toString(), s.kind, s.technique, s.rir?.toString().orEmpty(), s.rpe?.toString().orEmpty(), s.comment).toSet(s.workoutId)
        if(s.id == 0L) {
            val next = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM sets WHERE workout_id=? AND exercise=?", arrayOf(s.workoutId.toString(),s.exercise.name)).use { it.moveToFirst(); it.getLong(0) }
            db.insertOrThrow("sets", null, values(s).apply { put("position", next) })
        }
        else require(db.update("sets", values(s), "id=? AND workout_id=?", arrayOf(s.id.toString(),s.workoutId.toString())) == 1)
    }
    private fun requireCompleted(id: Long, exercise: Exercise? = null) {
        db.rawQuery("SELECT exercises FROM workouts WHERE id=? AND finished_at IS NOT NULL", arrayOf(id.toString())).use {
            require(it.moveToFirst()) { "Завершённая тренировка не найдена" }
            require(exercise == null || exercise.name in it.getString(0).split(",")) { "Упражнение не входит в тренировку" }
        }
    }
    fun saveCompletedSet(s: LiftSet) = transaction {
        requireCompleted(s.workoutId, s.exercise)
        Draft(s.exercise, s.weight.toString(), s.reps.toString(), s.kind, s.technique, s.rir?.toString().orEmpty(), s.rpe?.toString().orEmpty(), s.comment).toSet(s.workoutId)
        if(s.id == 0L) {
            val next = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM sets WHERE workout_id=? AND exercise=?", arrayOf(s.workoutId.toString(),s.exercise.name)).use { it.moveToFirst(); it.getLong(0) }
            db.insertOrThrow("sets", null, values(s).apply { put("position", next) })
        } else {
            require(db.update("sets", values(s), "id=? AND workout_id=?", arrayOf(s.id.toString(),s.workoutId.toString())) == 1) {
                "Подход не найден"
            }
        }
    }
    fun deleteCompletedSet(s: LiftSet) = transaction {
        requireCompleted(s.workoutId, s.exercise)
        val count = db.rawQuery("SELECT COUNT(*) FROM sets WHERE workout_id=?", arrayOf(s.workoutId.toString())).use {
            it.moveToFirst(); it.getInt(0)
        }
        require(count > 1) { "В тренировке должен остаться хотя бы один подход" }
        require(db.delete("sets", "id=? AND workout_id=?", arrayOf(s.id.toString(),s.workoutId.toString())) == 1) {
            "Подход не найден"
        }
    }
    fun appendRow(set: LiftSet) = transaction {
        saveSet(set)
        saveDraft(Draft(exercise=set.exercise,weight="",reps=""))
    }
    fun finishWithRow(id:Long, pending:LiftSet?) = transaction {
        if(pending != null) {
            require(pending.workoutId==id && pending.id==0L)
            saveSet(pending)
        }
        finish(id)
        saveDraft(Draft(weight="",reps=""))
    }
    fun deleteSet(s: LiftSet) = transaction {
        requireActive(s.workoutId)
        db.delete("sets", "id=? AND workout_id=?", arrayOf(s.id.toString(),s.workoutId.toString()))
    }
    fun finish(id: Long) = transaction {
        requireActive(id)
        db.rawQuery("SELECT COUNT(*) FROM sets WHERE workout_id=?", arrayOf(id.toString())).use {
            it.moveToFirst(); require(it.getInt(0) > 0) { "Добавь хотя бы один подход" }
        }
        val performed = db.rawQuery("SELECT DISTINCT exercise FROM sets WHERE workout_id=? ORDER BY exercise", arrayOf(id.toString())).use { c ->
            buildList { while(c.moveToNext()) add(c.getString(0)) }.joinToString(",")
        }
        db.execSQL("UPDATE workouts SET finished_at=MAX(started_at,?), exercises=? WHERE id=?", arrayOf<Any>(System.currentTimeMillis(), performed, id))
    }
    fun reorder(workoutId: Long, exercise: Exercise, ids: List<Long>) = transaction {
        val existing = db.rawQuery("SELECT id FROM sets WHERE workout_id=? AND exercise=?", arrayOf(workoutId.toString(),exercise.name)).use { c ->
            buildSet { while(c.moveToNext()) add(c.getLong(0)) }
        }
        require(ids.size == existing.size && ids.toSet() == existing) { "Список подходов изменился. Повтори перемещение." }
        ids.forEachIndexed { index, id ->
            db.execSQL("UPDATE sets SET position=? WHERE id=? AND workout_id=?", arrayOf<Any>(index,id,workoutId))
        }
    }
    fun backup() = Backup(read(), draft(), setting("haptics") != "false",setting("sounds") != "false")
    fun replace(backup: Backup) = transaction {
        db.delete("sets", null, null); db.delete("workouts", null, null)
        backup.data.workouts.forEach { w ->
            db.insertOrThrow("workouts", null, ContentValues().apply {
                put("id", w.id); put("started_at", w.startedAt); put("finished_at", w.finishedAt)
                put("exercises", w.exercises.joinToString(",") { it.name })
            })
        }
        backup.data.sets.forEachIndexed { index, s -> db.insertOrThrow("sets", null, values(s).apply { put("id",s.id); put("position",index) }) }
        saveDraft(backup.draft); setSetting("haptics",backup.haptics.toString())
        setSetting("sounds",backup.sounds.toString())
    }
    fun clear() = transaction {
        db.delete("sets", null, null); db.delete("workouts", null, null); db.delete("settings",null,null)
    }
    fun close() = helper.close()
}
