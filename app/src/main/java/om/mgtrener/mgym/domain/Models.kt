package om.mgtrener.mgym.domain

enum class Exercise(val title: String, val short: String) {
    BENCH("Жим штанги лёжа", "Жим"), CURL("Подъём штанги на бицепс", "Бицепс")
}
enum class SetKind(val title: String) {
    WARMUP("Разминка"), WORK("Рабочий"), TOP("Тяжёлый"), BACKOFF("Back-off"), TEST("Тест"), CUSTOM("Другой")
}
enum class Technique(val title: String) { STRICT("Строгая"), NORMAL("Обычная"), CHEAT("С читингом") }
data class LiftSet(
    val id: Long = 0, val workoutId: Long = 0, val exercise: Exercise,
    val weight: Double, val reps: Int, val kind: SetKind = SetKind.WORK,
    val technique: Technique = Technique.NORMAL, val rir: Int? = null,
    val rpe: Double? = null, val comment: String = ""
)
data class Workout(val id: Long, val startedAt: Long, val finishedAt: Long?, val exercises: List<Exercise>)
data class Draft(
    val exercise: Exercise = Exercise.BENCH, val weight: String = "20", val reps: String = "8",
    val kind: SetKind = SetKind.WORK, val technique: Technique = Technique.NORMAL,
    val rir: String = "", val rpe: String = "", val comment: String = ""
) {
    fun toSet(workoutId: Long, id: Long = 0): LiftSet {
        val w = weight.replace(',', '.').toDoubleOrNull()
        val r = reps.toIntOrNull()
        require(w != null && w.isFinite() && w > 0 && w <= 1500) { "Вес: больше 0 и не больше 1500 кг" }
        require(r != null && r in 1..200) { "Повторения: от 1 до 200" }
        val rirValue = rir.takeIf { it.isNotBlank() }?.toIntOrNull()
        val rpeValue = rpe.takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
        require(rir.isBlank() || rirValue != null && rirValue in 0..10) { "RIR: от 0 до 10" }
        require(rpe.isBlank() || rpeValue != null && rpeValue.isFinite() && rpeValue in 1.0..10.0) { "RPE: от 1 до 10" }
        require(comment.length <= 1000) { "Комментарий: не более 1000 символов" }
        return LiftSet(id, workoutId, exercise, w, r, kind, technique, rirValue, rpeValue, comment)
    }
}
data class GymData(val workouts: List<Workout> = emptyList(), val sets: List<LiftSet> = emptyList()) {
    val active get() = workouts.firstOrNull { it.finishedAt == null }
    val completed get() = workouts.filter { it.finishedAt != null }
    fun setsFor(id: Long) = sets.filter { it.workoutId == id }
    fun completedSets(): List<LiftSet> {
        val ids = completed.map { it.id }.toSet()
        return sets.filter { it.workoutId in ids }
    }
}


// Keep legacy exercises in backups, outside the current bench diary.
fun GymData.benchOnly(): GymData {
    val bench = sets.filter { it.exercise == Exercise.BENCH }
    val ids = bench.map { it.workoutId }.toSet()
    val visible = workouts.filter { it.id in ids || (it.finishedAt == null && Exercise.BENCH in it.exercises) }
        .map { it.copy(exercises = listOf(Exercise.BENCH)) }
    return GymData(visible, bench)
}
