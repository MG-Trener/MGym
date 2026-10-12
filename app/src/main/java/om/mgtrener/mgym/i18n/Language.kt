package om.mgtrener.mgym.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import org.json.JSONObject
import java.util.Locale

/** Presentation only: canonical exercise names, saved plans and user input are never rewritten. */
object Language {
    val tags = listOf("ru", "en", "de", "fr", "es", "pt-BR", "zh-Hans", "kk")
    val names = listOf("Русский", "English", "Deutsch", "Français", "Español", "Português (Brasil)", "简体中文", "Қазақша")
    var tag: String = "ru"; private set
    val locale: Locale get() = Locale.forLanguageTag(tag)
    private var entries = emptyMap<String, List<String>>()
    private var patterns = emptyList<Pair<Regex, List<String>>>()
    fun normalize(value: String): String = when(value.substringBefore('-')) {
        "pt" -> "pt-BR"; "zh" -> "zh-Hans"; "ru", "en", "de", "fr", "es", "kk" -> value.substringBefore('-'); else -> "en"
    }
    fun selected(context: Context): String {
        if(Build.VERSION.SDK_INT >= 33) {
            val languages = context.getSystemService(LocaleManager::class.java).applicationLocales
            if(!languages.isEmpty) return normalize(languages[0].toLanguageTag())
            return if(context.packageName.endsWith(".uitest") && !context.getSharedPreferences("language",0).contains("tag")) "ru" else "system"
        }
        return context.getSharedPreferences("language", 0).getString("tag", if(context.packageName.endsWith(".uitest")) "ru" else "system")!!
    }
    fun wrap(context: Context): Context {
        val selected = selected(context)
        val resolved = if(selected == "system") normalize(android.content.res.Resources.getSystem().configuration.locales[0].toLanguageTag()) else selected
        tag = resolved
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(resolved)) })
    }
    fun load(context: Context) {
        val data = JSONObject(context.assets.open("i18n/catalog.json").bufferedReader().use { it.readText() })
        entries = data.keys().asSequence().associateWith { key -> data.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } } }
        patterns = entries.filterKeys { it.contains(Regex("\\{\\d+\\}")) }.map { (source, translations) ->
            val parts = source.split(Regex("\\{\\d+\\}"))
            Regex(parts.joinToString("(.+?)") { Regex.escape(it) }, RegexOption.DOT_MATCHES_ALL) to translations
        }.sortedByDescending { it.first.pattern.length }
    }
    fun choose(context: Context, value: String) {
        require(value == "system" || value in tags)
        check(context.getSharedPreferences("language",0).edit().putString("tag",value).commit())
        if(Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales =
            if(value == "system") LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(value)
    }
    fun text(source: String, depth: Int = 0): String {
        if(tag == "ru" || depth > 12) return source
        val index = tags.indexOf(tag) - 1
        entries[source]?.let { return it[index] }
        if(source.contains("; ")) return source.split("; ").joinToString("; ") { text(it,depth+1) }
        for((pattern,translations) in patterns) {
            val match=pattern.matchEntire(source) ?: continue
            var result=translations[index]
            match.groupValues.drop(1).forEachIndexed { n,v -> result=result.replace("{$n}",text(v,depth+1)) }
            return result
        }
        val trimmed=source.trim()
        if(trimmed != source && trimmed.isNotEmpty()) return source.replace(trimmed,text(trimmed,depth+1))
        // Compound summaries are assembled from independent translated labels and numeric values.
        if(source.contains('·')) return source.split('·').joinToString("·") { text(it,depth+1) }
        val sentences=source.split(Regex("(?<=[.!?])\\s+(?=[А-ЯЁ])"))
        if(sentences.size>1) return sentences.joinToString(" ") { text(it,depth+1) }
        if(source.contains('\n')) return source.split('\n').joinToString("\n") { text(it,depth+1) }
        if(source.contains(": ")) return source.split(": ").joinToString(": ") { text(it,depth+1) }
        if(source.endsWith('.')) return text(source.dropLast(1),depth+1)+"."
        return source
    }
}
