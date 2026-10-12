package om.mgtrener.mgym

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import om.mgtrener.mgym.database.GymDatabase
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.repositories.GymRepository
import om.mgtrener.mgym.services.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlannerDiaryTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun clean() { check(context.packageName.endsWith(".uitest"));context.deleteDatabase("mgym.db");BenchPlannerStorage.clear(context) }
    private fun state(): JSONObject {
        val original=JSONObject(context.assets.open("bench-planner/plan.json").bufferedReader().use { it.readText() })
        val day=original.getJSONArray("sessions").getJSONObject(0)
        val sets=JSONArray();val ex=day.getJSONArray("exercises")
        for(i in 0 until ex.length()) { val prescribed=ex.getJSONObject(i).getJSONArray("sets")
            for(j in 0 until prescribed.length())sets.put(JSONObject().put("kg",if(i==0) prescribed.getJSONObject(j).getDouble("weight") else 30).put("reps",5).put("rpe",JSONObject.NULL))
        }
        return JSONObject().put("version",1).put("active","original").put("plans",JSONArray()).put("logs",JSONObject().put("original|"+day.getString("id"),JSONObject().put("status","done").put("feedback","ok").put("verified",true).put("sets",sets)))
    }
    @Test fun savesAllExercisesAndUpdatesWithoutDuplicatesWhileManualWorkoutRemainsActive() {
        val repo=GymRepository(GymDatabase(context));val manual=repo.start(listOf(Exercise.SQUAT));repo.saveSet(LiftSet(workoutId=manual,exercise=Exercise.SQUAT,weight=80.0,reps=5))
        val store=BenchPlannerStorage(context,{},{});val state=state();val raw=state.toString()
        assertTrue(store.write(PlannerDiary.KEY,raw));val first=repo.read();assertEquals(manual,first.active!!.id);assertEquals(1,first.completed.size)
        val workout=first.completed.single();assertTrue(workout.exercises.containsAll(listOf(Exercise.BENCH,Exercise.CABLE_ROW,Exercise.TRICEPS)))
        val count=first.setsFor(workout.id).size;assertTrue(count>10)
        assertTrue(store.write(PlannerDiary.KEY,raw));assertEquals(count,repo.read().setsFor(workout.id).size)
        state.getJSONObject("logs").getJSONObject(workout.sourceKey!!).getJSONArray("sets").getJSONObject(0).put("reps",7)
        assertTrue(store.write(PlannerDiary.KEY,state.toString()));assertEquals(1,repo.read().completed.size);assertEquals(7,repo.read().setsFor(workout.id).first().reps)
        val backup=BackupCodec.decode(BackupCodec.encode(repo.backup()));repo.replace(backup)
        assertTrue(store.write(PlannerDiary.KEY,state.toString()));assertEquals(1,repo.read().completed.size);assertEquals(manual,repo.read().active!!.id);repo.close()
    }
    @Test fun migratesOldPreferencesAndRollsBackInvalidHistoryWrite() {
        val raw=state().toString();context.getSharedPreferences(BenchPlannerStorage.PREFS,Context.MODE_PRIVATE).edit().putString(PlannerDiary.KEY,raw).commit()
        val store=BenchPlannerStorage(context,{},{});assertEquals(raw,store.read(PlannerDiary.KEY))
        val repo=GymRepository(GymDatabase(context));assertEquals(1,repo.read().completed.size)
        val invalid=JSONObject(raw);invalid.getJSONObject("logs").getJSONObject("original|2026-10-12").getJSONArray("sets").getJSONObject(0).put("kg",9999)
        assertFalse(store.write(PlannerDiary.KEY,invalid.toString()));assertEquals(raw,store.read(PlannerDiary.KEY));assertEquals(1,repo.read().completed.size)
        repo.close()
    }
    @Test fun partialFactsAreVisibleAndEmptyFactsDoNotInventWorkouts() {
        val store=BenchPlannerStorage(context,{},{});val state=state();val log=state.getJSONObject("logs").getJSONObject("original|2026-10-12");val sets=log.getJSONArray("sets")
        log.put("status","planned");for(i in 1 until sets.length()){sets.getJSONObject(i).put("kg",JSONObject.NULL).put("reps",JSONObject.NULL)}
        assertTrue(store.write(PlannerDiary.KEY,state.toString()));val repo=GymRepository(GymDatabase(context));assertEquals(1,repo.read().completed.size);assertEquals(1,repo.read().completedSets().size)
        sets.getJSONObject(0).put("kg",JSONObject.NULL).put("reps",JSONObject.NULL);assertTrue(store.write(PlannerDiary.KEY,state.toString()));assertTrue(repo.read().workouts.isEmpty());repo.close()
    }
    @Test fun schemaThreeUpgradePreservesDiary() {
        context.deleteDatabase("mgym.db")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("mgym.db"),null).use { db ->
            db.execSQL("CREATE TABLE workouts(id INTEGER PRIMARY KEY,started_at INTEGER,finished_at INTEGER,exercises TEXT)")
            db.execSQL("CREATE TABLE sets(id INTEGER PRIMARY KEY,workout_id INTEGER,exercise TEXT,weight REAL,reps INTEGER,kind TEXT,technique TEXT,rir INTEGER,rpe REAL,comment TEXT,position INTEGER)")
            db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT)")
            db.execSQL("INSERT INTO workouts VALUES(1,100,200,'BENCH')")
            db.execSQL("INSERT INTO sets VALUES(1,1,'BENCH',100,5,'WORK','NORMAL',NULL,NULL,'',0)")
            db.version=3
        }
        val repo=GymRepository(GymDatabase(context));assertEquals(100.0,repo.read().sets.single().weight,0.0)
        assertTrue(BenchPlannerStorage(context,{},{}).write(PlannerDiary.KEY,state().toString()));assertEquals(2,repo.read().completed.size);repo.close()
    }
}
