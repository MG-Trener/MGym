package om.mgtrener.mgym

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import om.mgtrener.mgym.services.BenchPlannerStorage
import om.mgtrener.mgym.ui.BenchPlannerAssets
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BenchPlannerTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun find(view: View): WebView? = if(view is WebView) view else
        if(view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
    private fun js(scenario: ActivityScenario<MainActivity>, script: String): String {
        val latch=CountDownLatch(1); var result="null"
        scenario.onActivity { activity -> val web=requireNotNull(find(activity.window.decorView))
            web.evaluateJavascript(script) { result=it; latch.countDown() }
        }
        check(latch.await(10, TimeUnit.SECONDS)); return result
    }
    private fun ready(scenario: ActivityScenario<MainActivity>) {
        compose.waitUntil(20000) { runCatching { js(scenario,"!!document.querySelector('#detail table')") == "true" }.getOrDefault(false) }
    }
    private fun capture(scenario: ActivityScenario<MainActivity>, name: String) {
        scenario.onActivity { a -> val view=requireNotNull(find(a.window.decorView)); val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            java.io.File(context.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    @Test fun generatorPersistsAcrossRecreationAndImportsWithoutReplacingDiary() {
        check(context.packageName.endsWith(".uitest"))
        BenchPlannerStorage.clear(context)
        val start = java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        context.deleteDatabase("mgym.db")
        val old=context.getSharedPreferences("bench150-progress-v1",Context.MODE_PRIVATE)
        old.edit().putString("2026-10-12/0/4","{\"weight\":115,\"reps\":1,\"rpe\":6}").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15000) { compose.onAllNodesWithText("Путь к 150 кг  ↗").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Путь к 150 кг  ↗").performScrollTo().performClick()
            ready(scenario)
            assertEquals("true",js(scenario,"(()=>{document.querySelector('#new-plan').click();const f=document.querySelector('#generator-form');const p={name:'Проверка Android',age:35,bodyweight:90,goal:150,start:'${start}',target:'${start.plusDays(54)}',experience:'trained',recovery:'normal',resultType:'set',resultWeight:132.5,reps:3,technique:'touch',resultDate:'${start.minusDays(2)}',bar:20,step:2.5,equipment:'gym',pain:'no'};for(const [k,v] of Object.entries(p))f.elements[k].value=v;f.elements.resultType.dispatchEvent(new Event('change'));f.elements.reps.value=3;for(const e of f.querySelectorAll('[name=weekday]'))e.checked=[1,3,5].includes(Number(e.value));f.requestSubmit();return !document.querySelector('#preview').hidden;})()"))
            js(scenario,"document.querySelector('#save-plan').click()")
            assertTrue(js(scenario,"document.querySelector('#detail').innerText").contains("115"))
            assertTrue(js(scenario,"document.querySelector('#detail').innerText").contains("105"))
            assertEquals("9",js(scenario,"document.querySelectorAll('#detail tbody tr').length"))
            js(scenario,"document.querySelector('#detail button').click()")
            js(scenario,"(()=>{const f=document.querySelector('#log-form');f.elements['kg-4'].value='115';f.elements['reps-4'].value='1';f.elements['rpe-4'].value='9';f.elements.status.value='done';f.requestSubmit();})()")
            // Real UI correction must preserve the entered set and change only later appointments.
            assertEquals("false",js(scenario,"document.querySelector('#apply-review').hidden"))
            js(scenario,"document.querySelector('#apply-review').click()")
            val before=JSONObject(context.getSharedPreferences(BenchPlannerStorage.PREFS,Context.MODE_PRIVATE).getString("mg150-planner-v1",null)!!)
            assertEquals(1,before.getJSONArray("plans").length())
            assertEquals(137.5,before.getJSONArray("plans").getJSONObject(0).getJSONObject("meta").getDouble("base"),0.0)
            assertEquals(9.0,before.getJSONObject("logs").getJSONObject(before.getString("active")+"|$start").getJSONArray("sets").getJSONObject(4).getDouble("rpe"),0.0)
            scenario.recreate(); ready(scenario)
            compose.waitUntil(10000) { js(scenario,"[...document.querySelectorAll('#detail img')].every(i=>i.complete&&i.naturalWidth>0)") == "true" }
            assertEquals(before.toString(),JSONObject(context.getSharedPreferences(BenchPlannerStorage.PREFS,Context.MODE_PRIVATE).getString("mg150-planner-v1",null)!!).toString())
            assertTrue(old.contains("2026-10-12/0/4"))
            capture(scenario,"bench-planner-day.png")
            // Same interchange format as the website. Native file picker only supplies text; validation stays in the shared engine.
            js(scenario,"window.Planner.receiveImport(${JSONObject.quote(before.toString())})")
            assertEquals("false",js(scenario,"document.querySelector('#import-preview').hidden"))
            js(scenario,"document.querySelector('#confirm-import').click()")
            val after=JSONObject(context.getSharedPreferences(BenchPlannerStorage.PREFS,Context.MODE_PRIVATE).getString("mg150-planner-v1",null)!!)
            assertEquals(2,after.getJSONArray("plans").length())
            assertEquals(2,after.getJSONObject("logs").length())
            assertNotEquals(after.getJSONArray("plans").getJSONObject(0).getString("id"),after.getJSONArray("plans").getJSONObject(1).getString("id"))
            js(scenario,"window.Planner.receiveImport('{invalid')")
            assertEquals("true",js(scenario,"document.querySelector('#import-preview').hidden"))
            assertTrue(js(scenario,"document.querySelector('#data-message').innerText").contains("Не удалось"))
        }
    }
    @Test fun scopedStorageBackupAndNetworkBoundary() {
        check(context.packageName.endsWith(".uitest"))
        var exportCalled=false; var importCalled=false
        val store=BenchPlannerStorage(context,{exportCalled=true},{importCalled=true})
        assertFalse(store.write("unexpected","secret")); assertNull(store.read("unexpected"))
        assertFalse(store.write("mg150-planner-v1","a".repeat(BenchPlannerStorage.MAX_BYTES+1)))
        assertTrue(store.exportBackup("{\"version\":1}")); assertTrue(exportCalled)
        assertEquals("{\"version\":1}",BenchPlannerStorage.exportFile(context).readText())
        store.importBackup(); assertTrue(importCalled)
        for(url in listOf("https://example.com/bench/index.html","file:///etc/passwd","https://appassets.androidplatform.net/bench/../index.html","https://appassets.androidplatform.net/bench/unknown.js")) {
            assertNull(BenchPlannerAssets.path(Uri.parse(url)))
            assertEquals(403,BenchPlannerAssets.response(context,Uri.parse(url)).statusCode)
        }
        val response=BenchPlannerAssets.response(context,Uri.parse(BenchPlannerAssets.HOME))
        assertEquals(200,response.statusCode); assertTrue(response.data.bufferedReader().use{it.readText()}.contains("mobile.js"))
        BenchPlannerStorage.clear(context)
        assertNull(store.read("mg150-planner-v1")); assertFalse(BenchPlannerStorage.exportFile(context).exists())
    }
}
