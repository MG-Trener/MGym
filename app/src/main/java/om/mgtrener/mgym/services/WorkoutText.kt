package om.mgtrener.mgym.services

import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.analytics.Analytics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object WorkoutText {
    private fun number(value: Double) = String.format(Locale.forLanguageTag("ru"), "%.1f",value).removeSuffix(",0")
    fun format(data: GymData, id: Long): String {
        val workout = data.workouts.first { it.id == id }
        return buildString {
            appendLine("MGym · " + Instant.ofEpochMilli(workout.startedAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
            workout.exercises.filter {it in trainingExercises}.forEach { exercise ->
                val sets = data.setsFor(id).filter { it.exercise == exercise }
                if(sets.isNotEmpty()) {
                    appendLine(); appendLine(exercise.title)
                    sets.forEachIndexed { index,s ->
                        append("${index+1}. ${number(s.weight)} кг × ${s.reps}")
                        s.rir?.let { append(" · RIR $it") }; s.rpe?.let { append(" · RPE ${number(it)}") }
                        if(s.comment.isNotBlank()) append(" · ${s.comment}")
                        appendLine()
                    }
                    val stats=Analytics.summarize(sets)
                    appendLine("Объём: ${number(stats.volume)} кг · Повторы: ${stats.reps}")
                    stats.e1rm?.let { appendLine("e1RM ≈ ${number(it)} кг") }
                }
            }
        }.trimEnd()
    }
}
