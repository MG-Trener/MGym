package om.mgtrener.mgym.services

import android.content.ContentValues
import android.content.Context
import om.mgtrener.mgym.database.GymDatabase
import om.mgtrener.mgym.domain.Exercise
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.security.MessageDigest

/** Planner JSON and its history projection commit in one SQLite transaction. */
internal object PlannerDiary {
    const val KEY = "mg150-planner-v1"
    fun read(context: Context, key: String): String? = GymDatabase(context).use { helper ->
        helper.readableDatabase.rawQuery("SELECT value FROM planner_documents WHERE key=?",arrayOf(key)).use { if(it.moveToFirst()) it.getString(0) else null }
    }
    fun write(context: Context, key: String, value: String) {
        GymDatabase(context).use { helper ->
            val db=helper.writableDatabase
            db.beginTransaction()
            try {
                if(key==KEY) {
                    val state=JSONObject(value)
                    require(state.getInt("version")==1)
                    val plans=state.getJSONArray("plans"); val logs=state.getJSONObject("logs")
                    require(plans.length()<=20 && logs.length()<=13000)
                    // Historical mapping only: never offered as a program or loaded by the WebView.
                    val original=JSONObject(context.assets.open("bench-planner/plan.json").bufferedReader().use { it.readText() })
                    val byId=mutableMapOf("original" to original)
                    for(i in 0 until plans.length()) { val plan=plans.getJSONObject(i);byId[plan.getString("id")]=plan }
                    for(logKey in logs.keys()) {
                        require(logKey.length<=160)
                        val date=LocalDate.parse(logKey.substringAfterLast('|'))
                        val plan=requireNotNull(byId[logKey.substringBeforeLast('|')])
                        val sessions=plan.getJSONArray("sessions")
                        val session=(0 until sessions.length()).map { sessions.getJSONObject(it) }.first { it.getString("id")==date.toString() }
                        val log=logs.getJSONObject(logKey); val actual=log.getJSONArray("sets")
                        require(actual.length()<=80)
                        val hash=MessageDigest.getInstance("SHA-256").digest((log.toString()+plan.optJSONObject("profile")?.optString("discipline","bench").orEmpty()).toByteArray()).joinToString("") { "%02x".format(it) }
                        val existing=db.rawQuery("SELECT id,source_hash FROM workouts WHERE source_key=?",arrayOf(logKey)).use { if(it.moveToFirst()) it.getLong(0) to it.getString(1) else null }
                        if(existing?.second==hash)continue
                        val movement=when(plan.optJSONObject("profile")?.optString("discipline", "bench") ?: "bench") { "bench" -> Exercise.BENCH; "squat" -> Exercise.SQUAT; "deadlift" -> Exercise.DEADLIFT; else -> error("Неизвестная дисциплина") }
                        val rows=mutableListOf<ContentValues>();val exercises=session.getJSONArray("exercises");var index=0
                        for(ei in 0 until exercises.length()) {
                            val ex=exercises.getJSONObject(ei); val name=ex.getString("name")
                            val exercise=if(ei==0) movement else requireNotNull(Exercise.entries.find { it.title==name }) { "Неизвестное упражнение: $name" }
                            val prescribed=ex.getJSONArray("sets")
                            for(si in 0 until prescribed.length()) {
                                val v=actual.optJSONObject(index++ ) ?: continue
                                if(v.isNull("kg") || v.isNull("reps")) continue
                                val kg=v.getDouble("kg"); val reps=v.getDouble("reps")
                                require(kg.isFinite()&&kg in 0.0..500.0&&reps.isFinite()&&reps in 0.0..100.0&&reps==kotlin.math.floor(reps))
                                if(kg==0.0||reps==0.0) continue
                                val t=prescribed.getJSONObject(si)
                                val rpe=if(v.isNull("rpe")) null else v.getDouble("rpe").also { require(it.isFinite()&&it in 1.0..10.0) }
                                rows.add(ContentValues().apply {
                                    put("exercise",exercise.name);put("weight",kg);put("reps",reps.toInt())
                                    put("kind",if(t.optString("kind")=="Разминка") "WARMUP" else "WORK")
                                    put("technique",if(ei==0&&log.optBoolean("verified")) "STRICT" else "NORMAL")
                                    put("rpe",rpe);put("position",si)
                                    put("comment",("План: "+plan.optString("name","Путь к 150 кг")+" · "+log.getString("status")+if(ei==0&&log.optBoolean("verified")) if(movement==Exercise.BENCH) " · пауза подтверждена" else " · техника подтверждена" else "").take(1000))
                                })
                            }
                        }
                        // No invented sets: even a partial saved workout contains only performed rows.
                        if(rows.isEmpty()) { if(existing!=null) db.delete("workouts","id=?",arrayOf(existing.first.toString()));continue }
                        val dateMillis=date.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val values=ContentValues().apply { put("started_at",dateMillis);put("finished_at",dateMillis);put("exercises",(listOf(movement.name)+rows.map { it.getAsString("exercise") }).distinct().joinToString(","));put("source_key",logKey);put("source_hash",hash) }
                        val id=existing?.first ?: db.insertOrThrow("workouts",null,values)
                        if(existing!=null){db.update("workouts",values,"id=?",arrayOf(id.toString()));db.delete("sets","workout_id=?",arrayOf(id.toString()))}
                        rows.forEach { it.put("workout_id",id);db.insertOrThrow("sets",null,it) }
                    }
                }
                db.execSQL("INSERT OR REPLACE INTO planner_documents(key,value) VALUES(?,?)",arrayOf(key,value))
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }
}
