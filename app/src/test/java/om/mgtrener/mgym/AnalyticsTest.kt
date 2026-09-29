package om.mgtrener.mgym

import om.mgtrener.mgym.analytics.Analytics
import om.mgtrener.mgym.achievements.Achievements
import om.mgtrener.mgym.domain.*
import org.junit.Assert.*
import org.junit.Test

class AnalyticsTest {
    private fun set(weight: Double = 100.0, reps: Int = 5, kind: SetKind = SetKind.WORK) =
        LiftSet(workoutId = 1, exercise = Exercise.BENCH, weight = weight, reps = reps, kind = kind)
    @Test fun singleRepIsActualWeight() {
        val e = Analytics.estimate(set(reps = 1))!!
        assertEquals(100.0, e.aggregate, 0.0001)
        assertEquals(e.epley, e.brzycki, 0.0001)
    }
    @Test fun estimateUsesMedianOfThreeFormulas() {
        val e = Analytics.estimate(set())!!
        assertEquals(116.6666667, e.epley, 0.0001)
        assertEquals(112.5, e.brzycki, 0.0001)
        assertEquals(117.4618943, e.lombardi, 0.0001)
        assertEquals(e.epley, e.aggregate, 0.0001)
    }
    @Test fun unreliableEstimatesAreExcluded() {
        assertNull(Analytics.estimate(set(reps = 13)))
        assertNotNull(Analytics.estimate(set(kind = SetKind.WARMUP)))
        assertNull(Analytics.estimate(set(weight = Double.NaN)))
    }
    @Test fun volumeAndRepetitionsIncludeAllSavedSets() {
        val stats = Analytics.summarize(listOf(set(),set(80.0,10),set(20.0,20,SetKind.WARMUP)))
        assertEquals(1700.0,stats.volume,0.0001)
        assertEquals(35,stats.reps)
        assertEquals(3,stats.workSets)
    }
    @Test fun estimateSourceAlwaysMatchesEstimatedMaximum() {
        val valid = set(100.0, 5)
        val highRep = set(130.0, 15)
        val stats = Analytics.summarize(listOf(valid, highRep))
        assertEquals(valid, stats.estimateSource)
        assertEquals(Analytics.estimate(valid)!!.aggregate, stats.e1rm!!, 0.0001)
    }
    @Test fun legacyCurlsDoNotEnterBenchDiaryOrAwards() {
        val curl=LiftSet(1,1,Exercise.CURL,50.0,8)
        val data=GymData(listOf(Workout(1,1,2,listOf(Exercise.CURL))),listOf(curl))
        assertTrue(data.benchOnly().workouts.isEmpty())
        assertTrue(data.benchOnly().sets.isEmpty())
        assertTrue(Achievements.evaluate(data).all {it.unlockedAt==null})
    }
    @Test fun achievementsRequireCompletionAndRetainEarliestDate() {
        val active = GymData(listOf(Workout(1,100,null,listOf(Exercise.BENCH))),listOf(set()))
        assertTrue(Achievements.evaluate(active).all { it.unlockedAt == null })
        val done = active.copy(workouts = listOf(Workout(1,100,200,listOf(Exercise.BENCH)),Workout(2,300,400,listOf(Exercise.BENCH))),
            sets = active.sets + set().copy(workoutId = 2))
        assertEquals(200L, Achievements.evaluate(done).first { it.title == "100 CLUB" }.unlockedAt)
        assertEquals(200L, Achievements.evaluate(done).first().unlockedAt)
    }
    @Test fun commaDecimalAndOptionalEffort() {
        val s = Draft(weight = "42,5", reps = "8").toSet(1)
        assertEquals(42.5,s.weight,0.001)
        assertNull(s.rir)
        assertNull(s.rpe)
    }
    @Test fun rejectsInvalidInputWithoutConvertingToZero() {
        listOf(Draft(weight=""),Draft(weight="NaN"),Draft(weight="-1"),Draft(reps="0"),
            Draft(reps="201"),Draft(rir="abc"),Draft(rpe="11")).forEach { draft ->
            assertThrows(IllegalArgumentException::class.java) { draft.toSet(1) }
        }
    }
}
