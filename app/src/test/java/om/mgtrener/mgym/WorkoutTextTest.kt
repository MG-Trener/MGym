package om.mgtrener.mgym
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.services.WorkoutText
import org.junit.Assert.*
import org.junit.Test
class WorkoutTextTest {
    @Test fun exportsBenchInSavedOrderWithoutLegacyLabels() {
        val data=GymData(listOf(Workout(1,1000,2000,Exercise.entries)),listOf(
            LiftSet(9,1,Exercise.BENCH,80.0,6,comment="контроль"),
            LiftSet(2,1,Exercise.BENCH,62.5,8,rir=2,rpe=8.5),
            LiftSet(3,1,Exercise.CURL,35.0,10,technique=Technique.STRICT)))
        val text=WorkoutText.format(data,1)
        assertTrue(text.contains("1. 80 кг × 6 · контроль"))
        assertTrue(text.contains("2. 62,5 кг × 8 · RIR 2 · RPE 8,5"))
        assertFalse(text.contains("Строгая"))
        assertFalse(text.contains(Exercise.CURL.title))
        assertFalse(text.contains("Рабочий"))
    }
}
