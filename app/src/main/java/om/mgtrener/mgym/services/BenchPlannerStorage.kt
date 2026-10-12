package om.mgtrener.mgym.services

import android.content.Context
import android.webkit.JavascriptInterface
import java.io.File

/** Only the bundled planner can use this small, scoped interface. No arbitrary files or URLs. */
class BenchPlannerStorage(context: Context, private val onExport: () -> Unit, private val onImport: () -> Unit) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    @JavascriptInterface fun read(key: String): String? = if (key in KEYS) prefs.getString(key, null) else null
    @JavascriptInterface fun write(key: String, value: String): Boolean = runCatching {
        require(key in KEYS && value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        prefs.edit().putString(key, value).commit()
    }.getOrDefault(false)
    @JavascriptInterface fun exportBackup(text: String): Boolean = runCatching {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val atomic = android.util.AtomicFile(exportFile(app))
        val stream = atomic.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
        onExport(); true
    }.getOrDefault(false)
    @JavascriptInterface fun importBackup() { onImport() }
    companion object {
        const val PREFS = "bench-planner-v2"
        const val MAX_BYTES = 2_000_000
        val KEYS = setOf("mg150-planner-v1", "mg150-planner-v1-before-v2")
        fun exportFile(context: Context) = File(context.cacheDir, "bench-planner-export.json")
        fun clear(context: Context) {
            check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit())
            exportFile(context).delete()
            File(context.cacheDir, "bench-planner-import.json").delete()
        }
    }
}
