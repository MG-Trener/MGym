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
        compose.waitUntil(20000) { runCatching { js(scenario,"!!window.Planner && (!!document.querySelector('#detail table') || !document.querySelector('#empty-plan').hidden)") == "true" }.getOrDefault(false) }
    }
    private fun createPlan(scenario: ActivityScenario<MainActivity>, name: String, discipline: String = "bench"): String {
        val start=java.time.LocalDate.now().plusDays(7)
        js(scenario,"(()=>{document.querySelector('#new-plan').click();const f=document.querySelector('#generator-form');const p={name:${JSONObject.quote(name)},discipline:'$discipline',age:35,bodyweight:80,goal:100,start:'$start',target:'${start.plusDays(56)}',experience:'novice',trainingContinuity:'regular',recovery:'normal',resultType:'max',resultWeight:75,reps:1,technique:'paused',resultDate:'$start',bar:20,step:2.5,equipment:'gym',pain:'no'};for(const [k,v] of Object.entries(p))f.elements[k].value=v;f.elements.resultType.dispatchEvent(new Event('change'));for(const e of f.querySelectorAll('[name=weekday]'))e.checked=[1,4].includes(Number(e.value));f.requestSubmit();document.querySelector('#save-plan').click();})()")
        assertEquals("false",js(scenario,"document.querySelector('#detail').hidden"))
        return JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!).getString("active")
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
        // Dialog contents scroll independently; a physical tap needs the control in view.
        js(scenario,"document.querySelector(${JSONObject.quote(selector)}).scrollIntoView({block:'center',behavior:'instant'})")
        val painted=CountDownLatch(1)
        scenario.onActivity { a -> requireNotNull(find(a.window.decorView)).postVisualStateCallback(2L,object: WebView.VisualStateCallback(){
            override fun onComplete(requestId: Long) { painted.countDown() }
        }) }
        check(painted.await(10,TimeUnit.SECONDS))
        val coordinates=JSONObject(js(scenario,"(()=>{const r=document.querySelector(${JSONObject.quote(selector)}).getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,width:innerWidth};})()"))
        onView(isAssignableFrom(WebView::class.java)).perform(GeneralClickAction(Tap.SINGLE, { view ->
            val location=IntArray(2);view.getLocationOnScreen(location)
            val scale=view.width/coordinates.getDouble("width")
            floatArrayOf((location[0]+coordinates.getDouble("x")*scale).toFloat(),(location[1]+coordinates.getDouble("y")*scale).toFloat())
        }, Press.FINGER))
    }
    private fun usableDialog(scenario: ActivityScenario<MainActivity>): Boolean = js(scenario,"(()=>{const d=document.querySelector('#log-dialog'),b=d.querySelector('.mobile-dialog-body'),s=d.querySelector('button[form=log-form]'),v=visualViewport;const r=d.getBoundingClientRect(),br=b.getBoundingClientRect(),sr=s.getBoundingClientRect();return r.height>(v.height*.8)&&r.height<=v.height&&br.height>80&&sr.bottom<=v.offsetTop+v.height&&d.scrollWidth<=d.clientWidth+1;})()") == "true"
    @Test fun novicePlanShowsProgressiveWeeksAndFlagsUnsupportedGoal() {
        check(context.packageName.endsWith(".uitest"))
        BenchPlannerStorage.clear(context)
        context.deleteDatabase("mgym.db")
        val start = java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15000) { compose.onAllNodesWithText("План жима лёжа").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("План жима лёжа").performScrollTo().performClick()
            ready(scenario)
            assertEquals("true",js(scenario,"(()=>{document.querySelector('#new-plan').click();const f=document.querySelector('#generator-form');const p={name:'Прогрессия 75–100',age:35,bodyweight:80,goal:100,start:'${start}',target:'${start.plusDays(56)}',experience:'novice',recovery:'normal',resultType:'max',resultWeight:75,reps:1,technique:'paused',resultDate:'${start.minusDays(1)}',bar:20,step:2.5,equipment:'gym',pain:'no'};for(const [k,v] of Object.entries(p))f.elements[k].value=v;f.elements.resultType.dispatchEvent(new Event('change'));for(const e of f.querySelectorAll('[name=weekday]'))e.checked=[1,4].includes(Number(e.value));f.requestSubmit();return !document.querySelector('#preview').hidden;})()"))
            assertEquals("true",js(scenario,"document.querySelector('#preview').innerText.includes('выходит за')"))
            js(scenario,"document.querySelector('#save-plan').click()")
            val saved=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!).getJSONArray("plans").getJSONObject(0)
            assertEquals(5,saved.getJSONObject("meta").getInt("templateVersion"))
            assertEquals("unsupported",saved.getJSONObject("meta").getJSONObject("goalAssessment").getString("status"))
            assertTrue(saved.getJSONArray("sessions").length()>10)
            assertEquals("true",js(scenario,"document.querySelector('#plan-summary').textContent.includes('Ориентир нагрузки (не прогноз)')"))
            val old=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!)
            old.getJSONArray("plans").getJSONObject(0).getJSONObject("meta").remove("templateVersion")
            assertTrue(BenchPlannerStorage(context,{},{}).write("mg150-planner-v1",old.toString()))
            scenario.recreate();ready(scenario)
            val migrated=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!).getJSONArray("plans").getJSONObject(0)
            assertEquals(5,migrated.getJSONObject("meta").getInt("templateVersion"))
            assertNotNull(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1-before-periodization"))
        }
    }
    @Test fun longPlanShowsStrengthTrajectoryAndCurrentTrainingInput() {
        check(context.packageName.endsWith(".uitest"))
        BenchPlannerStorage.clear(context)
        context.deleteDatabase("mgym.db")
        val start=java.time.LocalDate.now().plusDays(2)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15000) { compose.onAllNodesWithText("План жима лёжа").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("План жима лёжа").performScrollTo().performClick()
            ready(scenario)
            assertEquals("true",js(scenario,"!!document.querySelector('[name=trainingContinuity]')"))
            js(scenario,"document.querySelector('#new-plan').click();const f=document.querySelector('#generator-form');const p={name:'Длинная прогрессия',age:35,bodyweight:80,goal:100,start:'${start}',target:'${start.plusDays(136)}',experience:'novice',trainingContinuity:'regular',recovery:'normal',resultType:'max',resultWeight:75,reps:1,technique:'touch',resultDate:'${start.minusDays(106)}',bar:20,step:2.5,equipment:'gym',pain:'no'};for(const [k,v] of Object.entries(p))f.elements[k].value=v;f.elements.resultType.dispatchEvent(new Event('change'));for(const e of f.querySelectorAll('[name=weekday]'))e.checked=[1,4].includes(Number(e.value));f.requestSubmit()")
            assertEquals("true",js(scenario,"document.querySelector('#preview-summary').textContent.includes('Рабочие подходы:')"))
            js(scenario,"document.querySelector('#save-plan').click()")
            val saved=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!).getJSONArray("plans").getJSONObject(0)
            val progression=saved.getJSONObject("meta").getJSONObject("progression")
            val trajectory=progression.getJSONObject("trajectory")
            assertTrue(trajectory.getJSONObject("last").getDouble("weight")>trajectory.getJSONObject("first").getDouble("weight")+10)
            assertTrue(saved.getJSONObject("meta").getJSONObject("goalAssessment").getJSONObject("checkpoint").getDouble("weight")>60)
            assertEquals("false",js(scenario,"document.querySelector('#progression-overview').hidden"))
            capture(scenario,"planner-progression-060.png")
        }
    }
    @Test fun personalPlanLogDialogHasUsableHeight() {
        check(context.packageName.endsWith(".uitest"))
        BenchPlannerStorage.clear(context)
        context.deleteDatabase("mgym.db")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15000) { compose.onAllNodesWithText("План жима лёжа").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("План жима лёжа").performScrollTo().performClick()
            ready(scenario)
            val planId=createPlan(scenario,"Проверка полей")
            val date=js(scenario,"document.querySelector('#sessions .active').getAttribute('aria-current');plan.sessions.find(s=>s.id===selected).date").trim('"')
            js(scenario,"document.querySelector('#detail button').click()")
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
            try { compose.waitUntil(10000) { js(scenario,"document.querySelector('#log-dialog').open") == "false" } }
            catch(e:Exception) { capture(scenario,"bench-save-failed.png");throw AssertionError(js(scenario,"JSON.stringify({error:document.querySelector('#log-error').textContent,active:document.activeElement.outerHTML,invalid:[...document.querySelectorAll('#log-form :invalid')].map(e=>e.outerHTML)})"),e) }
            val state=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!)
            assertEquals(20.0,state.getJSONObject("logs").getJSONObject("$planId|$date").getJSONArray("sets").getJSONObject(0).getDouble("kg"),0.0)
            scenario.recreate();ready(scenario)
            js(scenario,"selectSession('$date');document.querySelector('#detail button').click()")
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
            js(scenario,"selectSession('$start')")
            val initial=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!).getJSONArray("plans").getJSONObject(0)
            val main=initial.getJSONArray("sessions").getJSONObject(0).getJSONArray("exercises").getJSONObject(0).getJSONArray("sets")
            val workIndex=(0 until main.length()).first { main.getJSONObject(it).getBoolean("rpe") }
            val prescribed=main.getJSONObject(workIndex)
            assertEquals(main.length().toString(),js(scenario,"document.querySelectorAll('#detail tbody tr').length"))
            js(scenario,"document.querySelector('#detail button').click()")
            js(scenario,"(()=>{const f=document.querySelector('#log-form');f.elements['kg-$workIndex'].value='${prescribed.getDouble("weight")}';f.elements['reps-$workIndex'].value='${prescribed.getInt("reps")}';f.elements['rpe-$workIndex'].value='9';f.elements['kg-${main.length()}'].value='40';f.elements['reps-${main.length()}'].value='10';f.elements.status.value='done';f.elements.feedback.value='hard';f.elements.verified.checked=true;f.requestSubmit();})()")
            // Real UI correction must preserve the entered set and change only later appointments.
            assertEquals("true",js(scenario,"document.querySelector('#apply-review').hidden"))
            js(scenario,"document.querySelector('#review-dialog').close()")
            val before=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!)
            assertEquals(1,before.getJSONArray("plans").length())
            assertTrue(before.getJSONArray("plans").getJSONObject(0).getJSONObject("profile").getBoolean("progressionPaused"))
            assertEquals(9.0,before.getJSONObject("logs").getJSONObject(before.getString("active")+"|$start").getJSONArray("sets").getJSONObject(workIndex).getDouble("rpe"),0.0)
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
        assertTrue(store.write("mg150-planner-v1-before-progression","{\"version\":1}"))
        assertEquals("{\"version\":1}",store.read("mg150-planner-v1-before-progression"))
        assertTrue(store.write("mg150-planner-v1-before-progression-v3","{\"version\":2}"))
        assertEquals("{\"version\":2}",store.read("mg150-planner-v1-before-progression-v3"))
        assertTrue(store.write("mg150-planner-v1-before-periodization","{\"version\":3}"))
        assertEquals("{\"version\":3}",store.read("mg150-planner-v1-before-periodization"))
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
    @Test fun emptyStartDeleteCancelAndRestoreKeepOtherPlansAndDiary() {
        check(context.packageName.endsWith(".uitest"))
        BenchPlannerStorage.clear(context);context.deleteDatabase("mgym.db")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15000) { compose.onAllNodesWithText("План жима лёжа").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("План жима лёжа").performScrollTo().performClick();ready(scenario)
            compose.onNodeWithText("Прежний план и записи").assertDoesNotExist()
            assertEquals("\"MGym\"",js(scenario,"document.querySelector('.brand').textContent"))
            assertEquals("true",js(scenario,"document.querySelector('#empty-plan').hidden===false && document.querySelector('#plan-select').options.length===1 && document.querySelector('#plan-select').value===''"))
            val bench=createPlan(scenario,"Жим для удаления")
            js(scenario,"document.querySelector('#detail button').click();(()=>{const f=document.querySelector('#log-form');f.elements['kg-0'].value='20';f.elements['reps-0'].value='10';f.elements.status.value='done';f.requestSubmit()})();document.querySelector('#review-dialog').close()")
            val squat=createPlan(scenario,"Присед сохранить","squat")
            js(scenario,"document.querySelector('#discipline-select').value='bench';document.querySelector('#discipline-select').dispatchEvent(new Event('change'))")
            val before=BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!
            tapWeb(scenario,"#delete-plan");tapWeb(scenario,"#delete-dialog [data-close]")
            assertEquals(before,BenchPlannerStorage(context,{},{}).read("mg150-planner-v1"))
            tapWeb(scenario,"#delete-plan");tapWeb(scenario,"#confirm-delete")
            val after=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!)
            assertEquals(1,after.getJSONArray("plans").length());assertEquals(squat,after.getJSONArray("plans").getJSONObject(0).getString("id"));assertEquals(0,after.getJSONObject("logs").length())
            assertEquals(before,BenchPlannerStorage(context,{},{}).read("mg150-planner-v1-before-delete"))
            om.mgtrener.mgym.database.GymDatabase(context).use { helper ->
                helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM workouts WHERE source_key LIKE ?",arrayOf("$bench|%")).use { it.moveToFirst();assertEquals(1,it.getInt(0)) }
            }
            assertEquals("true",js(scenario,"!document.querySelector('#empty-plan').hidden && document.querySelector('#delete-plan').hidden"))
            scenario.recreate();ready(scenario)
            js(scenario,"document.querySelector('#discipline-select').value='squat';document.querySelector('#discipline-select').dispatchEvent(new Event('change'))")
            assertEquals("true",js(scenario,"!!document.querySelector('#detail table')"))
            tapWeb(scenario,"#delete-plan");tapWeb(scenario,"#confirm-delete")
            assertEquals("",JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!).getString("active"))
            scenario.recreate();ready(scenario)
            assertEquals("true",js(scenario,"!document.querySelector('#empty-plan').hidden"))
            js(scenario,"window.Planner.receiveImport(${JSONObject.quote(before)});document.querySelector('#confirm-import').click();document.querySelector('#data-dialog').close()")
            val restored=JSONObject(BenchPlannerStorage(context,{},{}).read("mg150-planner-v1")!!)
            assertEquals(2,restored.getJSONArray("plans").length());assertEquals(1,restored.getJSONObject("logs").length())
            assertEquals("true",js(scenario,"!!document.querySelector('#detail table')"))
            tapWeb(scenario,"#delete-plan")
            assertEquals("true",js(scenario,"document.querySelector('#delete-dialog').getBoundingClientRect().height<=441"))
            capture(scenario,"planner-delete-061.png")
            tapWeb(scenario,"#delete-dialog [data-close]")
        }
    }
}
