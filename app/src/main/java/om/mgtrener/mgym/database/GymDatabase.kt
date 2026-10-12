package om.mgtrener.mgym.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class GymDatabase(context: Context) : SQLiteOpenHelper(context, "mgym.db", null, VERSION) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE workouts (
            id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL,
            finished_at INTEGER, exercises TEXT NOT NULL,
            CHECK(finished_at IS NULL OR finished_at >= started_at))""")
        db.execSQL("CREATE UNIQUE INDEX one_active_workout ON workouts((1)) WHERE finished_at IS NULL")
        db.execSQL("CREATE INDEX workout_date ON workouts(started_at)")
        createSets(db, "sets")
        createSetIndexes(db)
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        createPlanner(db)
    }
    private fun createSets(db: SQLiteDatabase, name: String) {
        db.execSQL("""CREATE TABLE $name (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            workout_id INTEGER NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
            exercise TEXT NOT NULL CHECK(exercise IN ('BENCH','DEADLIFT','SQUAT','CURL','CABLE_ROW','DUMBBELL_ROW','LAT_PULLDOWN','TRICEPS','REAR_DELT','OVERHEAD_TRICEPS')),
            weight REAL NOT NULL CHECK(weight > 0 AND weight <= 1500),
            reps INTEGER NOT NULL CHECK(reps BETWEEN 1 AND 200),
            kind TEXT NOT NULL, technique TEXT NOT NULL,
            rir INTEGER CHECK(rir BETWEEN 0 AND 10), rpe REAL CHECK(rpe BETWEEN 1 AND 10),
            comment TEXT NOT NULL DEFAULT '', position INTEGER NOT NULL DEFAULT 0)""")
    }
    private fun createSetIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX sets_workout ON sets(workout_id, id)")
        db.execSQL("CREATE INDEX sets_exercise ON sets(exercise, workout_id)")
    }
    private fun createPlanner(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE workouts ADD COLUMN source_key TEXT")
        db.execSQL("ALTER TABLE workouts ADD COLUMN source_hash TEXT")
        db.execSQL("CREATE UNIQUE INDEX planner_workout_source ON workouts(source_key) WHERE source_key IS NOT NULL")
        db.execSQL("CREATE TABLE planner_documents (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sets ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE sets SET position=id")
        }
        if (oldVersion < 3) {
            createSets(db, "sets_new")
            db.execSQL("""INSERT INTO sets_new(id,workout_id,exercise,weight,reps,kind,technique,rir,rpe,comment,position)
                SELECT id,workout_id,exercise,weight,reps,kind,technique,rir,rpe,comment,position FROM sets""")
            db.execSQL("DROP TABLE sets")
            db.execSQL("ALTER TABLE sets_new RENAME TO sets")
            createSetIndexes(db)
        }
        if (oldVersion < 4) {
            createSets(db, "sets_v4")
            db.execSQL("INSERT INTO sets_v4 SELECT * FROM sets")
            db.execSQL("DROP TABLE sets")
            db.execSQL("ALTER TABLE sets_v4 RENAME TO sets")
            createSetIndexes(db)
            createPlanner(db)
        }
    }
    companion object { const val VERSION = 4 }
}
