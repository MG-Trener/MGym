package om.mgtrener.mgym.achievements

import om.mgtrener.mgym.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TrophyGroup(val title: String) {
    PATH("Путь"), MOVEMENTS("Движения"), REGULARITY("Регулярность"), STRENGTH("Сила")
}
data class Trophy(val title: String, val description: String, val unlockedAt: Long?, val group: TrophyGroup)
object Achievements {
    fun evaluate(data: GymData): List<Trophy> {
        val visible = data.trainingDiary()
        val history = visible.completed.sortedBy { it.finishedAt }
        val byWorkout = visible.sets.groupBy { it.workoutId }
        val byExercise = trainingExercises.associateWith { exercise ->
            history.filter { workout -> byWorkout[workout.id].orEmpty().any { it.exercise == exercise } }
        }
        fun sets(workout: Workout, exercise: Exercise) = byWorkout[workout.id].orEmpty().filter { it.exercise == exercise }
        fun day(workout: Workout): LocalDate = Instant.ofEpochMilli(workout.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        return buildList {
            listOf(1, 3, 5, 10, 25, 50, 100).forEach { count ->
                add(Trophy(if (count == 1) "Первый шаг" else "$count тренировок",
                    "Заверши $count тренировок", history.getOrNull(count - 1)?.finishedAt, TrophyGroup.PATH))
            }
            trainingExercises.forEach { exercise ->
                val sessions = byExercise.getValue(exercise)
                listOf(1, 3, 10).forEach { count ->
                    add(Trophy("${exercise.short} · $count", "${exercise.title}: $count тренировок",
                        sessions.getOrNull(count - 1)?.finishedAt, TrophyGroup.MOVEMENTS))
                }
            }
            val allThree = trainingExercises.mapNotNull { byExercise.getValue(it).firstOrNull()?.finishedAt }
            add(Trophy("Троеборье", "Заверши жим, тягу и приседания",
                if (allThree.size == 3) allThree.max() else null, TrophyGroup.MOVEMENTS))

            val weeks = history.groupBy { workout -> day(workout).minusDays(day(workout).dayOfWeek.value.toLong() - 1) }
                .toSortedMap().map { (week, sessions) -> week to sessions.minOf { it.finishedAt!! } }
            val streakUnlocks = mutableMapOf<Int, Long>()
            var streak = 0
            var previous: LocalDate? = null
            weeks.forEach { (week, completedAt) ->
                streak = if (previous?.plusWeeks(1) == week) streak + 1 else 1
                listOf(2, 4, 8).forEach { count -> if (streak >= count) streakUnlocks.putIfAbsent(count, completedAt) }
                previous = week
            }
            listOf(2, 4, 8).forEach { count ->
                val weeks = if(count == 8) "недель" else "недели"
                add(Trophy("$count $weeks подряд", "Тренируйся хотя бы раз в неделю $count $weeks подряд",
                    streakUnlocks[count], TrophyGroup.REGULARITY))
            }
            var cumulative = 0.0
            val volumeUnlocks = mutableMapOf<Int, Long>()
            history.forEach { workout ->
                cumulative += byWorkout[workout.id].orEmpty().sumOf { it.weight * it.reps }
                listOf(5, 20, 100).forEach { tonnes ->
                    if (cumulative >= tonnes * 1000) volumeUnlocks.putIfAbsent(tonnes, workout.finishedAt!!)
                }
            }
            listOf(5, 20, 100).forEach { tonnes ->
                add(Trophy("$tonnes тонн", "Набери $tonnes тонн общего объёма", volumeUnlocks[tonnes], TrophyGroup.REGULARITY))
            }
            listOf(Exercise.BENCH to 60.0, Exercise.DEADLIFT to 100.0, Exercise.SQUAT to 80.0).forEach { (exercise, target) ->
                val sessions = byExercise.getValue(exercise)
                add(Trophy("${exercise.short} · ${target.toInt()} кг", "Подними ${target.toInt()} кг: ${exercise.title.lowercase()}",
                    sessions.firstOrNull { workout -> sets(workout, exercise).any { it.weight >= target } }?.finishedAt,
                    TrophyGroup.STRENGTH))
                var previousBest: Double? = null
                var firstImprovement: Long? = null
                sessions.forEach { workout ->
                    val best = sets(workout, exercise).maxOf { it.weight }
                    if (previousBest != null && best > previousBest!! && firstImprovement == null)
                        firstImprovement = workout.finishedAt
                    previousBest = maxOf(previousBest ?: 0.0, best)
                }
                add(Trophy("Прогресс · ${exercise.short}", "Превзойди свой прошлый вес в новом занятии",
                    firstImprovement, TrophyGroup.STRENGTH))
            }
        }
    }
}
