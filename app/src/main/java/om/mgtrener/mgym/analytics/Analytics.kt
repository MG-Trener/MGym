package om.mgtrener.mgym.analytics

import om.mgtrener.mgym.domain.*
import kotlin.math.pow

data class Estimate(val aggregate: Double, val epley: Double, val brzycki: Double, val lombardi: Double)
data class Summary(val volume: Double, val workSets: Int, val reps: Int, val best: LiftSet?, val e1rm: Double?, val estimateSource: LiftSet?)
object Analytics {
    // All saved sets count; strength estimates use 1–12 repetitions.
    fun estimate(set: LiftSet): Estimate? {
        if (set.reps !in 1..12 || !set.weight.isFinite() || set.weight <= 0) return null
        val w = set.weight
        if (set.reps == 1) return Estimate(w, w, w, w)
        val e = w * (1 + set.reps / 30.0)
        val b = w * 36 / (37 - set.reps)
        val l = w * set.reps.toDouble().pow(0.1)
        return Estimate(listOf(e, b, l).sorted()[1], e, b, l)
    }
    fun summarize(sets: List<LiftSet>): Summary {
        val work = sets
        val source = work.filter { estimate(it) != null }.maxByOrNull { estimate(it)!!.aggregate }
        return Summary(work.sumOf { it.weight * it.reps }, work.size, work.sumOf { it.reps },
            work.maxByOrNull { estimate(it)?.aggregate ?: it.weight }, source?.let { estimate(it)!!.aggregate }, source)
    }
    fun records(sets: List<LiftSet>): List<Pair<String, LiftSet>> {
        val work = sets
        return buildList {
            work.maxByOrNull { it.weight }?.let { add("Максимальный вес" to it) }
            work.filter { estimate(it) != null }.maxByOrNull { estimate(it)!!.aggregate }?.let { add("Лучший e1RM" to it) }
            listOf(1, 2, 3, 5, 8, 10, 12).forEach { reps ->
                work.filter { it.reps == reps }.maxByOrNull { it.weight }?.let { add("Рекорд ×$reps" to it) }
            }
        }
    }
}
