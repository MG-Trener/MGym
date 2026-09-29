package om.mgtrener.mgym.services

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

data class UpdateInfo(val available: String?, val message: String, val apkUrl: String? = null)
interface UpdateProvider { fun check(): UpdateInfo }

object AppVersion {
    fun parts(value: String): List<Int>? {
        val match=Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value) ?: return null
        return match.groupValues.drop(1).map {it.toIntOrNull() ?: return null}
    }
    fun newer(candidate:String,installed:String):Boolean {
        val a=parts(candidate) ?: return false
        val b=parts(installed) ?: return false
        for(i in a.indices) if(a[i]!=b[i]) return a[i]>b[i]
        return false
    }
}

class GitHubUpdateProvider(private val installed: String) : UpdateProvider {
    override fun check(): UpdateInfo {
        val connection=URI("https://api.github.com/repos/MG-Trener/MGym/releases/latest").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=10000; connection.readTimeout=10000
            connection.setRequestProperty("Accept","application/vnd.github+json")
            connection.setRequestProperty("User-Agent","MGym/$installed")
            val status=connection.responseCode
            if(status==404) return UpdateInfo(null,"Опубликованных обновлений пока нет.")
            require(status==200) { if(status==403 || status==429) "GitHub временно ограничил запросы. Попробуй позже." else "GitHub вернул ошибку $status. Повтори проверку позже." }
            val bytes=connection.inputStream.use {input ->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(true) {
                    val count=input.read(buffer)
                    if(count<0) break
                    require(output.size()+count<=1024*1024) {"Слишком большой ответ сервера обновлений"}
                    output.write(buffer,0,count)
                }
                output.toByteArray()
            }
            require(bytes.size<=1024*1024) {"Слишком большой ответ сервера обновлений"}
            return parse(String(bytes,Charsets.UTF_8),installed)
        } finally {connection.disconnect()}
    }
    companion object {
        fun parse(json:String,installed:String):UpdateInfo {
            val release=JSONObject(json)
            require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) {"Стабильный выпуск пока не опубликован"}
            val version=release.getString("tag_name")
            require(AppVersion.parts(version)!=null) {"Непонятная версия выпуска. Проверь страницу GitHub."}
            if(!AppVersion.newer(version,installed)) return UpdateInfo(null,"Установлена актуальная версия $installed.")
            val assets=release.getJSONArray("assets")
            val url=(0 until assets.length()).map {assets.getJSONObject(it)}.firstOrNull {
                it.optString("name").equals("MGym-${version.removePrefix("v")}.apk",ignoreCase=true) && it.optLong("size")>0
            }?.getString("browser_download_url")
            require(url!=null && url.startsWith("https://github.com/MG-Trener/MGym/releases/download/") && URI(url).host=="github.com") {"У новой версии пока нет установочного APK. Попробуй позже."}
            return UpdateInfo(version.removePrefix("v"),"Доступна версия ${version.removePrefix("v")}. Скачай APK и открой его для установки поверх приложения. Дневник сохранится.",url)
        }
    }
}
