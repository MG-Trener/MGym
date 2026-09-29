package om.mgtrener.mgym.achievements

import om.mgtrener.mgym.analytics.Analytics
import om.mgtrener.mgym.domain.*

data class Trophy(val title: String, val description: String, val unlockedAt: Long?)
object Achievements {
    fun evaluate(data: GymData): List<Trophy> {
        val history = data.benchOnly().completed.sortedBy { it.finishedAt }
        val byWorkout = data.sets.groupBy { it.workoutId }
        return buildList {
            listOf(1, 10, 25, 50, 100, 250, 500, 1000).forEach { count ->
                add(Trophy(if (count == 1) "Первый шаг" else "$count тренировок", "Заверши $count тренировок", history.getOrNull(count - 1)?.finishedAt))
            }
            listOf(50, 75, 100, 125, 150, 175, 200).forEach { target ->
                val day = history.firstOrNull { workout -> byWorkout[workout.id].orEmpty().any {
                    it.exercise == Exercise.BENCH && (Analytics.estimate(it)?.aggregate ?: it.weight) >= target
                } }
                add(Trophy("$target CLUB", "Жим: фактический вес или расчётный e1RM $target кг", day?.finishedAt))
            }
        }
    }
}
