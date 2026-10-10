package om.mgtrener.mgym.services

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

data class RestTimerState(
    val workoutId: Long = 0,
    val elapsedBeforeStartMs: Long = 0,
    val startedAtMs: Long? = null
) {
    val running: Boolean get() = startedAtMs != null

    fun elapsedMs(nowMs: Long): Long = elapsedBeforeStartMs +
        (startedAtMs?.let { (nowMs - it).coerceAtLeast(0) } ?: 0)

    fun display(nowMs: Long): String {
        val seconds = elapsedMs(nowMs) / 1000
        val hours = seconds / 3600
        val minutes = (seconds / 60) % 60
        val rest = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest)
        else "%02d:%02d".format(minutes, rest)
    }
}

class RestTimerStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val preferences: SharedPreferences = context.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)

    fun load(workoutId: Long): RestTimerState {
        require(workoutId > 0)
        if (preferences.getLong("workout_id", 0) != workoutId) return reset(workoutId)
        val elapsed = preferences.getLong("elapsed_ms", 0)
        val start = preferences.getLong("started_at_ms", 0).takeIf { it > 0 }
        if (elapsed < 0 || start != null && start > clock()) return reset(workoutId)
        return RestTimerState(workoutId, elapsed, start)
    }

    fun start(workoutId: Long): RestTimerState {
        val current = load(workoutId)
        return if (current.running) current else save(current.copy(startedAtMs = clock()))
    }

    fun pause(workoutId: Long): RestTimerState {
        val current = load(workoutId)
        return if (!current.running) current else save(RestTimerState(workoutId, current.elapsedMs(clock())))
    }

    fun reset(workoutId: Long): RestTimerState = save(RestTimerState(workoutId))

    fun clear() {
        preferences.edit { clear() }
    }

    private fun save(state: RestTimerState): RestTimerState {
        preferences.edit {
            putLong("workout_id", state.workoutId)
            putLong("elapsed_ms", state.elapsedBeforeStartMs)
            putLong("started_at_ms", state.startedAtMs ?: 0)
        }
        return state
    }
}
