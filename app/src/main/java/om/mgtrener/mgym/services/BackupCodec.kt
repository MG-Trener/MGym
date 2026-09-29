package om.mgtrener.mgym.services

import om.mgtrener.mgym.domain.*
import org.json.JSONArray
import org.json.JSONObject

data class Backup(val data: GymData, val draft: Draft, val haptics: Boolean, val sounds: Boolean = true)
object BackupCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    private fun JSONObject.integer(key: String): Long {
        val value = get(key)
        require(value is Number) { "Поле $key должно быть целым числом" }
        val d = value.toDouble()
        require(d.isFinite() && d == kotlin.math.floor(d) && d in 0.0..9007199254740991.0) { "Некорректное целое число: $key" }
        return d.toLong()
    }
    private fun JSONObject.smallInteger(key: String): Int {
        val value = integer(key)
        require(value <= Int.MAX_VALUE) { "Число слишком велико: $key" }
        return value.toInt()
    }
    private fun JSONObject.decimal(key: String): Double {
        val value = get(key)
        require(value is Number && value.toDouble().isFinite()) { "Некорректное число: $key" }
        return value.toDouble()
    }
    fun draftJson(d: Draft) = JSONObject().put("exercise", d.exercise.name).put("weight", d.weight)
        .put("reps", d.reps).put("kind", d.kind.name).put("technique", d.technique.name)
        .put("rir", d.rir).put("rpe", d.rpe).put("comment", d.comment)
    fun draftFrom(j: JSONObject) = Draft(
        Exercise.valueOf(j.getString("exercise")), j.getString("weight"), j.getString("reps"),
        SetKind.valueOf(j.getString("kind")), Technique.valueOf(j.getString("technique")),
        j.getString("rir"), j.getString("rpe"), j.getString("comment")
    ).also { d ->
        require(listOf(d.weight, d.reps, d.rir, d.rpe).all { it.length <= 32 } && d.comment.length <= 1000)
    }
    fun encode(backup: Backup): String {
        val workouts = JSONArray()
        backup.data.workouts.forEach { w ->
            workouts.put(JSONObject().put("id", w.id).put("startedAt", w.startedAt)
                .put("finishedAt", w.finishedAt ?: JSONObject.NULL)
                .put("exercises", JSONArray(w.exercises.map { it.name })))
        }
        val sets = JSONArray()
        backup.data.sets.forEach { s ->
            sets.put(JSONObject().put("id", s.id).put("workoutId", s.workoutId)
                .put("exercise", s.exercise.name).put("weight", s.weight).put("reps", s.reps)
                .put("kind", s.kind.name).put("technique", s.technique.name)
                .put("rir", s.rir ?: JSONObject.NULL).put("rpe", s.rpe ?: JSONObject.NULL).put("comment", s.comment))
        }
        return JSONObject().put("format", "MGym").put("version", 1).put("workouts", workouts)
            .put("sets", sets).put("draft", draftJson(backup.draft)).put("haptics", backup.haptics).put("sounds",backup.sounds).toString(2)
    }
    fun decode(text: String): Backup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Файл больше 10 МБ" }
        val root = JSONObject(text)
        require(root.getString("format") == "MGym" && root.integer("version") == 1L) { "Неподдерживаемый формат резервной копии" }
        val wa = root.getJSONArray("workouts")
        val sa = root.getJSONArray("sets")
        require(wa.length() <= 20000 && sa.length() <= 100000) { "Слишком много записей" }
        val workouts = (0 until wa.length()).map { index ->
            val j = wa.getJSONObject(index)
            val ex = j.getJSONArray("exercises")
            Workout(j.integer("id"), j.integer("startedAt"),
                if (j.isNull("finishedAt")) null else j.integer("finishedAt"),
                (0 until ex.length()).map { Exercise.valueOf(ex.getString(it)) })
        }
        require(workouts.map { it.id }.distinct().size == workouts.size) { "Повторяющиеся тренировки" }
        require(workouts.count { it.finishedAt == null } <= 1) { "Несколько незавершённых тренировок" }
        workouts.forEach {
            require(it.id > 0 && it.startedAt in 0..4102444800000L && (it.finishedAt == null || it.finishedAt in it.startedAt..4102444800000L))
            require(it.exercises.isNotEmpty() && it.exercises.distinct().size == it.exercises.size)
        }
        val byId = workouts.associateBy { it.id }
        val sets = (0 until sa.length()).map { index ->
            val j = sa.getJSONObject(index)
            val d = Draft(Exercise.valueOf(j.getString("exercise")), j.decimal("weight").toString(),
                j.smallInteger("reps").toString(), SetKind.valueOf(j.getString("kind")),
                Technique.valueOf(j.getString("technique")),
                if (j.isNull("rir")) "" else j.smallInteger("rir").toString(),
                if (j.isNull("rpe")) "" else j.decimal("rpe").toString(), j.getString("comment"))
            d.toSet(j.integer("workoutId"), j.integer("id")).also {
                require(it.id > 0 && byId[it.workoutId]?.exercises?.contains(it.exercise) == true) { "Подход не связан с тренировкой" }
            }
        }
        require(sets.map { it.id }.distinct().size == sets.size) { "Повторяющиеся подходы" }
        val nonEmptyWorkouts = sets.map { it.workoutId }.toSet()
        require(workouts.filter { it.finishedAt != null }.all { it.id in nonEmptyWorkouts }) { "Пустая завершённая тренировка" }
        val draft = draftFrom(root.getJSONObject("draft"))
        val data = GymData(workouts, sets)
        require(data.active == null || draft.exercise in data.active!!.exercises)
        return Backup(data, draft, root.getBoolean("haptics"),root.optBoolean("sounds",true))
    }
    fun csv(data: GymData): String {
        fun quote(v: Any?): String {
            val raw = v?.toString().orEmpty()
            // Prevent spreadsheet formula execution in user comments.
            val safe = if (raw.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$raw" else raw
            return "\"" + safe.replace("\"", "\"\"") + "\""
        }
        val dates = data.workouts.associateBy { it.id }
        return "\uFEFFworkout_id,started_at,finished_at,exercise,weight_kg,reps,type,technique,rir,rpe,comment\r\n" +
            data.sets.joinToString("\r\n") { s ->
                listOf(s.workoutId, dates[s.workoutId]?.startedAt, dates[s.workoutId]?.finishedAt,
                    s.exercise.name, s.weight, s.reps, s.kind.name, s.technique.name, s.rir, s.rpe, s.comment)
                    .joinToString(",") { quote(it) }
            }
    }
}
