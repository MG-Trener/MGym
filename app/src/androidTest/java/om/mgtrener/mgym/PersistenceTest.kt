package om.mgtrener.mgym

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import om.mgtrener.mgym.database.GymDatabase
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.repositories.GymRepository
import om.mgtrener.mgym.services.BackupCodec
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: GymRepository
    @Before fun prepare() {
        check(context.packageName.endsWith(".uitest")) { "Use -PmgymUiTestInstall=true; tests must not touch the personal diary" }
        context.deleteDatabase("mgym.db")
        repository = GymRepository(GymDatabase(context))
    }
    @After fun cleanup() { if (::repository.isInitialized) { repository.close(); context.deleteDatabase("mgym.db") } }
    @Test fun reopenRestoresDraftAndSingleActiveWorkout() {
        val id = repository.start(listOf(Exercise.BENCH,Exercise.CURL))
        val draft = Draft(exercise=Exercise.CURL,weight="42,5",reps="8",rir="2")
        repository.saveDraft(draft)
        repository.saveSet(draft.toSet(id))
        repository.close()
        repository = GymRepository(GymDatabase(context))
        assertEquals(id,repository.start(listOf(Exercise.BENCH)))
        assertEquals(draft,repository.draft())
        assertEquals(1,repository.read().sets.size)
        repository.finish(id)
        assertNull(repository.read().active)
        assertEquals(1,repository.read().completed.size)
        assertEquals(listOf(Exercise.CURL),repository.read().completed.single().exercises)
    }
    @Test fun backupRoundTripRetainsDetailsAndActiveDraft() {
        val id = repository.start(listOf(Exercise.CURL))
        val draft = Draft(exercise=Exercise.CURL,weight="35",reps="10", technique=Technique.STRICT,comment="строка\nтекст")
        repository.saveDraft(draft)
        repository.saveSet(draft.toSet(id))
        val original = repository.backup()
        val decoded = BackupCodec.decode(BackupCodec.encode(original))
        repository.clear()
        repository.replace(decoded)
        assertEquals(original,repository.backup())
    }
    @Test fun rejectsOrphanImportAndPreservesData() {
        val id = repository.start(listOf(Exercise.BENCH))
        repository.saveSet(Draft().toSet(id))
        val original = repository.backup()
        val invalid = original.copy(data=original.data.copy(sets=original.data.sets.map { it.copy(workoutId=999) }))
        try { repository.replace(invalid); fail("Must reject foreign key violation") } catch(_: Exception) { }
        assertEquals(original,repository.backup())
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(invalid)) }
    }
    @Test fun completedWorkoutCannotBeChanged() {
        val id = repository.start(listOf(Exercise.BENCH))
        repository.saveSet(Draft().toSet(id)); repository.finish(id)
        assertThrows(IllegalArgumentException::class.java) { repository.saveSet(Draft().toSet(id)) }
    }
    @Test fun importRejectsFractionalAndOverflowingRepetitions() {
        val id = repository.start(listOf(Exercise.BENCH))
        repository.saveSet(Draft().toSet(id))
        listOf(2.5, 4294967297L).forEach { invalid ->
            val json = org.json.JSONObject(BackupCodec.encode(repository.backup()))
            json.getJSONArray("sets").getJSONObject(0).put("reps", invalid)
            assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(json.toString()) }
        }
    }
    @Test fun schemaVersionAndCsvEscaping() {
        val helper = GymDatabase(context)
        assertEquals(2,helper.readableDatabase.version)
        helper.close()
        val id = repository.start(listOf(Exercise.BENCH))
        repository.saveSet(Draft(comment="=HYPERLINK(\"x\")\nline").toSet(id))
        val csv = BackupCodec.csv(repository.read())
        assertTrue(csv.contains("'=HYPERLINK"))
        assertTrue(csv.contains("\"\"x\"\""))
    }
    @Test fun reorderedSetsSurviveReopenAndBackup() {
        val id=repository.start(listOf(Exercise.BENCH))
        listOf("20","50","80").forEach {repository.saveSet(Draft(weight=it).toSet(id))}
        val ids=repository.read().sets.map {it.id}.reversed()
        repository.reorder(id,Exercise.BENCH,ids)
        repository.saveSet(Draft(weight="100").toSet(id))
        assertEquals(listOf(80.0,50.0,20.0,100.0),repository.read().sets.map {it.weight})
        repository.finish(id)
        repository.reorder(id,Exercise.BENCH,repository.read().sets.map {it.id}.reversed())
        val expected=repository.backup()
        assertThrows(IllegalArgumentException::class.java) {repository.reorder(id,Exercise.BENCH,listOf(ids[0],ids[0]))}
        assertEquals(expected,repository.backup())
        repository.close(); repository=GymRepository(GymDatabase(context))
        assertEquals(expected,repository.backup())
        repository.replace(BackupCodec.decode(BackupCodec.encode(expected)))
        assertEquals(expected,repository.backup())
    }
    @Test fun upgradeVersionOnePreservesHistoricalData() {
        repository.close()
        context.openOrCreateDatabase("mgym.db",0,null).use {db ->
            db.execSQL("CREATE TABLE workouts (id INTEGER PRIMARY KEY, started_at INTEGER NOT NULL, finished_at INTEGER, exercises TEXT NOT NULL)")
            db.execSQL("CREATE TABLE sets (id INTEGER PRIMARY KEY, workout_id INTEGER NOT NULL, exercise TEXT NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL, kind TEXT NOT NULL, technique TEXT NOT NULL, rir INTEGER, rpe REAL, comment TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY,value TEXT NOT NULL)")
            db.execSQL("INSERT INTO workouts VALUES (1,1000,2000,'BENCH')")
            db.execSQL("INSERT INTO sets VALUES (10,1,'BENCH',20,10,'WARMUP','NORMAL',NULL,NULL,'old note')")
            db.execSQL("INSERT INTO sets VALUES (20,1,'BENCH',80,8,'WORK','NORMAL',2,8,'')")
            db.version=1
        }
        repository=GymRepository(GymDatabase(context))
        val old=repository.read()
        assertEquals(listOf(10L,20L),old.sets.map {it.id})
        assertEquals(SetKind.WARMUP,old.sets.first().kind)
        assertEquals("old note",old.sets.first().comment)
        repository.reorder(1,Exercise.BENCH,listOf(20L,10L))
        assertEquals(listOf(20L,10L),repository.read().sets.map {it.id})
    }
    @Test fun firstDatabaseHasNoSeededWorkoutsAndArchivesDoNotEnterBenchView() {
        repository.prepareBenchDiary()
        assertTrue(repository.read().workouts.isEmpty())
        assertTrue(repository.read().sets.isEmpty())
        val id=repository.start(listOf(Exercise.CURL))
        repository.saveSet(Draft(exercise=Exercise.CURL).toSet(id))
        repository.saveDraft(Draft(exercise=Exercise.CURL,weight="35"))
        repository.prepareBenchDiary()
        assertNull(repository.read().active)
        assertTrue(repository.read().benchOnly().workouts.isEmpty())
        assertEquals(1,repository.backup().data.sets.size)
        assertEquals(Exercise.BENCH,repository.draft().exercise)
        val benchId=repository.start(listOf(Exercise.BENCH))
        assertEquals(benchId,repository.read().benchOnly().active!!.id)
    }
    @Test fun soundPreferenceSurvivesBackupAndOldBackupsStillImport() {
        repository.setSetting("sounds","false")
        val copy=BackupCodec.decode(BackupCodec.encode(repository.backup()))
        repository.clear();repository.replace(copy)
        assertEquals("false",repository.setting("sounds"))
        val old=org.json.JSONObject(BackupCodec.encode(copy)).apply {remove("sounds")}
        assertTrue(BackupCodec.decode(old.toString()).sounds)
    }
    @Test fun rowsResetAtomicallyAndFinishIncludesLastFilledRow() {
        val id=repository.start(listOf(Exercise.BENCH))
        assertEquals("",repository.draft().weight)
        assertEquals("",repository.draft().reps)
        val draft=Draft(weight="62,5",reps="5")
        repository.saveDraft(draft)
        repository.appendRow(draft.toSet(id))
        assertEquals("",repository.draft().weight)
        repository.close();repository=GymRepository(GymDatabase(context))
        assertEquals(1,repository.read().sets.size)
        assertEquals("",repository.draft().reps)
        repository.finishWithRow(id,Draft(weight="80",reps="8").toSet(id))
        assertEquals(2,repository.read().sets.size)
        assertNull(repository.read().active)
        assertEquals("",repository.draft().weight)
        assertThrows(IllegalArgumentException::class.java) {repository.finishWithRow(id,null)}
        assertEquals(2,repository.read().sets.size)
    }
}
