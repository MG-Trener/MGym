package om.mgtrener.mgym.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import om.mgtrener.mgym.services.BenchPlannerStorage
import om.mgtrener.mgym.domain.*
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** HTTPS origin backed exclusively by bundled APK assets; never falls back to the network. */
internal object BenchPlannerAssets {
    const val HOST = "appassets.androidplatform.net"
    const val ROOT = "https://$HOST/bench/"
    const val HOME = ROOT + "index.html"
    private val files = setOf("index.html", "method.html", "style.css", "mobile.css", "app.js", "planner.js",
        "i18n.js", "i18n-catalog.js", "i18n-progress.js", "generator.js", "engine-v2.js", "engine-v3.js", "mobile.js") + listOf("cable-row", "dumbbell-row",
        "lat-pulldown", "overhead-triceps-extension", "rear-delt-fly", "triceps-pushdown").map { "assets/$it.webp" } + listOf("leg-curl", "split-squat", "hip-thrust").map { "assets/$it.png" }
    fun path(uri: Uri): String? = uri.path?.removePrefix("/bench/")?.takeIf {
        uri.scheme == "https" && uri.host == HOST && uri.port == -1 && uri.userInfo == null &&
            uri.path?.startsWith("/bench/") == true && it in files
    }
    fun response(context: Context, uri: Uri): WebResourceResponse {
        val path = path(uri)
        if (path == null) return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(byteArrayOf()))
        val mime = when (path.substringAfterLast('.')) { "js" -> "text/javascript"; "css" -> "text/css"; "json" -> "application/json"; "webp" -> "image/webp"; "png" -> "image/png"; else -> "text/html" }
        return runCatching {
            WebResourceResponse(mime, "UTF-8", 200, "OK", mapOf(
                "Content-Security-Policy" to "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self'; connect-src 'self'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'",
                "Cache-Control" to "no-store"), context.assets.open("bench-planner/$path"))
        }.getOrElse { WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf())) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BenchPlannerScreen(discipline: Exercise = Exercise.BENCH, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var pendingImport by rememberSaveable { mutableStateOf(false) }
    // Import payload stays in a scoped cache file across recreation, never in the activity bundle.
    val importFile = remember { java.io.File(context.cacheDir, "bench-planner-import.json") }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { withContext(Dispatchers.IO) {
                val text = BenchPlannerStorage.exportFile(context).readText()
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            }; "Резервная копия программ сохранена" }.getOrElse { "Не удалось сохранить копию: ${it.message}" }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) { val count = input.read(buffer); if (count < 0) break
                        require(output.size() + count <= BenchPlannerStorage.MAX_BYTES) { "Файл больше 2 МБ" }
                        output.write(buffer, 0, count)
                    }; output.toByteArray()
                }
                importFile.writeBytes(bytes)
            }; pendingImport = true }.onFailure { message = "Не удалось прочитать копию: ${it.message}" }
        }
    }
    LaunchedEffect(pendingImport, pageReady, web) {
        if (pendingImport && pageReady) {
            val text = withContext(Dispatchers.IO) { runCatching { importFile.readText() }.getOrNull() }
            if (text != null) web?.evaluateJavascript("window.Planner.receiveImport(${JSONObject.quote(text)})", null)
            else message = "Не удалось прочитать выбранную копию. Выберите файл повторно."
            pendingImport = false
            withContext(Dispatchers.IO) { importFile.delete() }
        }
    }
    val goBack: () -> Unit = {
        val view = web
        if (view == null) onBack() else view.evaluateJavascript(
            "(()=>{const d=document.querySelector('dialog[open]');if(d){d.close();return true;}return false;})()"
        ) { closed -> if (closed != "true") { if (view.canGoBack()) view.goBack() else onBack() } }
    }
    BackHandler { goBack() }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = goBack) { LocalizedText("← Назад") }
        }
        AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { ctx ->
            WebView(ctx).apply {
                web = this
                setBackgroundColor(Color.rgb(16, 22, 21))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false // Data is synchronously persisted through the scoped native store.
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                val main = Handler(Looper.getMainLooper())
                addJavascriptInterface(BenchPlannerStorage(ctx,
                    onExport = { main.post { saveFile.launch("MGym-programs.json") } },
                    onImport = { main.post { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } }
                ), "MGymNative")
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = BenchPlannerAssets.response(ctx, request.url)
                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { pageReady = false }
                    override fun onPageFinished(view: WebView, url: String) { pageReady = url.startsWith(BenchPlannerAssets.HOME) }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (BenchPlannerAssets.path(request.url) in setOf("index.html", "method.html")) return false
                        if (request.isForMainFrame && request.hasGesture() && request.url.scheme == "https") {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                                .onFailure { message = "Не удалось открыть ссылку" }
                        }
                        return true
                    }
                }
                loadUrl(BenchPlannerAssets.HOME + "?discipline=" + discipline.disciplineKey)
            }
        }, onRelease = { view -> view.stopLoading(); view.removeJavascriptInterface("MGymNative"); view.destroy(); if (web === view) web = null })
    }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, title = { LocalizedText("Планы тренировок") },
        text = { LocalizedText(text) }, confirmButton = { TextButton(onClick = { message = null }) { LocalizedText("Понятно") } }) }
}
