package om.mgtrener.mgym

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.view.WindowInsets
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
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
        val painted=CountDownLatch(1)
        scenario.onActivity { a -> requireNotNull(find(a.window.decorView)).postVisualStateCallback(1L,object: WebView.VisualStateCallback(){
            override fun onComplete(requestId: Long) { painted.countDown() }
        }) }
        check(painted.await(10,TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(context.getExternalFilesDir(null),name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
    }
    private fun tapWeb(scenario: ActivityScenario<MainActivity>, selector: String) {
        val coordinates=JSONObject(js(scenario,"(()=>{const r=document.querySelector(${JSONObject.quote(selector)}).getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,width:innerWidth};})()"))
        onView(isAssignableFrom(WebView::class.java)).perform(GeneralClickAction(Tap.SINGLE, { view ->
            val location=IntArray(2);view.getLocationOnScreen(location)
            val scale=view.width/coordinates.getDouble("width")
            floatArrayOf((location[0]+coordinates.getDouble("x")*scale).toFloat(),(location[1]+coordinates.getDouble("y")*scale).toFloat())
        }, Press.FINGER))
    }
    private fun usableDialog(scenario: ActivityScenario<MainActivity>): Boolean = js(scenario,"(()=>{const d=document.querySelector('#log-dialog'),b=d.querySelector('.mobile-dialog-body'),s=d.querySelector('button[form=log-form]'),v=visualViewport;const r=d.getBoundingClientRect(),br=b.getBoundingClientRect(),sr=s.getBoundingClientRect();return r.height>(v.height*.8)&&r.height<=v.height&&br.height>80&&sr.bottom<=v.offsetTop+v.height&&d.scrollWidth<=d.clientWidth+1;})()") == "true"
    @Test fun originalPlanLogDialogHasUsableHeight() {
        check(context.packageName.endsWith(".uitest"))
        BenchPlannerStorage.clear(context)
        context.deleteDatabase("mgym.db")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15000) { compose.onAllNodesWithText("План жима лёжа").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("План жима лёжа").performScrollTo().performClick()
            ready(scenario)
            js(scenario,"selectSession('2026-10-12');document.querySelector('#detail button').click()")
            capture(scenario,"bench-log-fixed.png")
            val metrics=js(scenario,"(()=>{const d=document.querySelector('#log-dialog'),r=d.getBoundingClientRect();return {height:r.height,viewport:innerHeight,scroll:d.scrollHeight,width:r.width,client:d.clientHeight,form:document.querySelector('#log-form').getBoundingClientRect().height};})()")
            assertTrue(metrics,JSONObject(metrics).getDouble("height")>200)
            assertTrue(usableDialog(scenario))
            tapWeb(scenario,"#log-form input[name=kg-0]")
            compose.waitUntil(10000) { var visible=false;scenario.onActivity { visible=it.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true };visible }
            compose.waitUntil(10000) { usableDialog(scenario) }
            assertEquals("\"kg-0\"",js(scenario,"document.activeElement.name"))
            capture(scenario,"bench-log-keyboard.png")
            InstrumentationRegistry.getInstrumentation().sendStringSync("20")
            assertEquals("\"20\"",js(scenario,"document.querySelector('[name=kg-0]').value"))
            tapWeb(scenario,"#log-form input[name=reps-0]")
            InstrumentationRegistry.getInstrumentation().sendStringSync("12")
            assertEquals("\"12\"",js(scenario,"document.querySelector('[name=reps-0]').value"))
            // The final accessory field can be reached while the keyboard is visible.
            js(scenario,"[...document.querySelectorAll('#log-form input[name^=kg]')].at(-1).scrollIntoView({block:'center'})")
            compose.waitUntil(10000) { js(scenario,"(()=>{const e=[...document.querySelectorAll('#log-form input[name^=kg]')].at(-1).getBoundingClientRect(),b=document.querySelector('#log-dialog .mobile-dialog-body').getBoundingClientRect();return e.top>=b.top&&e.bottom<=b.bottom;})()") == "true" }
            tapWeb(scenario,"#log-dialog button[form=log-form]")
            compose.waitUntil(10000) { js(scenario,"document.querySelector('#log-dialog').open") == "false" }
            val state=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!)
            assertEquals(20.0,state.getJSONObject("logs").getJSONObject("original|2026-10-12").getJSONArray("sets").getJSONObject(0).getDouble("kg"),0.0)
            scenario.recreate();ready(scenario)
            js(scenario,"selectSession('2026-10-12');document.querySelector('#detail button').click()")
            assertEquals("\"20\"",js(scenario,"document.querySelector('[name=kg-0]').value"))
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
            compose.waitUntil(15000) { compose.onAllNodesWithText("План жима лёжа").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("План жима лёжа").performScrollTo().performClick()
            ready(scenario)
            assertEquals("true",js(scenario,"(()=>{document.querySelector('#new-plan').click();const f=document.querySelector('#generator-form');const p={name:'Проверка Android',age:35,bodyweight:90,goal:150,start:'${start}',target:'${start.plusDays(54)}',experience:'trained',recovery:'normal',resultType:'set',resultWeight:132.5,reps:3,technique:'touch',resultDate:'${start.minusDays(2)}',bar:20,step:2.5,equipment:'gym',pain:'no'};for(const [k,v] of Object.entries(p))f.elements[k].value=v;f.elements.resultType.dispatchEvent(new Event('change'));f.elements.reps.value=3;for(const e of f.querySelectorAll('[name=weekday]'))e.checked=[1,3,5].includes(Number(e.value));f.requestSubmit();return !document.querySelector('#preview').hidden;})()"))
            js(scenario,"document.querySelector('#save-plan').click()")
            assertTrue(js(scenario,"document.querySelector('#detail').innerText").contains("115"))
            assertTrue(js(scenario,"document.querySelector('#detail').innerText").contains("105"))
            assertEquals("8",js(scenario,"document.querySelectorAll('#detail tbody tr').length"))
            js(scenario,"document.querySelector('#detail button').click()")
            js(scenario,"(()=>{const f=document.querySelector('#log-form');f.elements['kg-4'].value='115';f.elements['reps-4'].value='1';f.elements['rpe-4'].value='9';f.elements['kg-8'].value='40';f.elements['reps-8'].value='10';f.elements.status.value='done';f.requestSubmit();})()")
            // Real UI correction must preserve the entered set and change only later appointments.
            assertEquals("false",js(scenario,"document.querySelector('#apply-review').hidden"))
            js(scenario,"document.querySelector('#apply-review').click()")
            val before=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!)
            assertEquals(1,before.getJSONArray("plans").length())
            assertEquals(145.75,before.getJSONArray("plans").getJSONObject(0).getJSONObject("meta").getDouble("base"),0.0)
            assertEquals(9.0,before.getJSONObject("logs").getJSONObject(before.getString("active")+"|$start").getJSONArray("sets").getJSONObject(4).getDouble("rpe"),0.0)
            scenario.recreate(); ready(scenario)
            compose.waitUntil(10000) { js(scenario,"[...document.querySelectorAll('#detail img')].every(i=>i.complete&&i.naturalWidth>0)") == "true" }
            assertEquals(before.toString(),JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!).toString())
            assertTrue(old.contains("2026-10-12/0/4"))
            capture(scenario,"bench-planner-day.png")
            // Same interchange format as the website. Native file picker only supplies text; validation stays in the shared engine.
            js(scenario,"window.Planner.receiveImport(${JSONObject.quote(before.toString())})")
            assertEquals("false",js(scenario,"document.querySelector('#import-preview').hidden"))
            js(scenario,"document.querySelector('#confirm-import').click()")
            val after=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!)
            assertEquals(2,after.getJSONArray("plans").length())
            assertEquals(2,after.getJSONObject("logs").length())
            assertNotEquals(after.getJSONArray("plans").getJSONObject(0).getString("id"),after.getJSONArray("plans").getJSONObject(1).getString("id"))
            js(scenario,"window.Planner.receiveImport('{invalid')")
            assertEquals("true",js(scenario,"document.querySelector('#import-preview').hidden"))
            assertTrue(js(scenario,"document.querySelector('#data-message').innerText").contains("Не удалось"))
            js(scenario,"document.querySelector('#data-dialog').close();document.querySelector('details.info').open=true;[...document.querySelectorAll('.info-content button')].find(b=>b.textContent==='Изменить цель и расписание').click()")
            js(scenario,"(()=>{const f=document.querySelector('#schedule-form');f.elements.target.value='${start.plusDays(118)}';for(const e of f.querySelectorAll('[name=weekday]'))e.checked=e.value==='1';f.requestSubmit();})()")
            assertEquals("false",js(scenario,"document.querySelector('#apply-schedule').hidden"))
            tapWeb(scenario,"#apply-schedule")
            val rescheduled=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!)
            assertEquals(start.plusDays(118).toString(),rescheduled.getJSONArray("plans").getJSONObject(1).getJSONObject("profile").getString("target"))
            assertEquals(2,rescheduled.getJSONObject("logs").length())
            scenario.recreate();ready(scenario)
            compose.onNodeWithText("← Назад").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Жим + Блок").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("Жим + Блок").onFirst().performScrollTo().performClick()
            compose.onNodeWithText("ТРЕНИРОВКА ПО ПЛАНУ").assertExists()
            compose.onNodeWithText("Блок",useUnmergedTree=true).performClick()
            compose.onNodeWithText("Тяга горизонтального блока").assertExists()
            compose.waitForIdle()
            val shot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null),"planner-history.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG,100,it) };shot.recycle()
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
