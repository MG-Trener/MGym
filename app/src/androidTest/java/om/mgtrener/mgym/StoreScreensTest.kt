package om.mgtrener.mgym

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import om.mgtrener.mgym.database.GymDatabase
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.repositories.GymRepository
import om.mgtrener.mgym.services.BenchPlannerStorage
import om.mgtrener.mgym.services.Backup
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StoreScreensTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun find(v:View):WebView? = if(v is WebView)v else if(v is ViewGroup)(0 until v.childCount).firstNotNullOfOrNull {find(v.getChildAt(it))} else null
    private fun js(s:ActivityScenario<MainActivity>,script:String):String {
        val latch=CountDownLatch(1);var result="null"
        s.onActivity {a->requireNotNull(find(a.window.decorView)).evaluateJavascript(script){result=it;latch.countDown()}}
        check(latch.await(10,TimeUnit.SECONDS));return result
    }
    private fun capture(name:String){
        compose.waitForIdle();InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null),"store-$name.jpg").outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,94,it)};bitmap.recycle()
    }
    @Test fun movementNavigationFactsSourceFilterSettingsAndStoreScreens(){
        check(context.packageName.endsWith(".uitest"));context.deleteDatabase("mgym.db");BenchPlannerStorage.clear(context)
        val now=System.currentTimeMillis();val workouts=mutableListOf<Workout>();val sets=mutableListOf<LiftSet>()
        for((index,ex) in trainingExercises.withIndex())for(n in 1..4){
            val id=(index*4+n).toLong();val time=now-(5-n)*7*86400000L
            workouts+=Workout(id,time,time+3600000,listOf(ex));sets+=LiftSet(id,id,ex,60.0+index*30+n*5,5,rpe=7.0+n*.25)
        }
        GymRepository(GymDatabase(context)).let {it.replace(Backup(GymData(workouts,sets),Draft(weight="",reps=""),false));it.close()}
        ActivityScenario.launch(MainActivity::class.java).use {s->
            compose.waitUntil(15000){compose.onAllNodesWithText("План жима лёжа").fetchSemanticsNodes().isNotEmpty()}
            capture("home")
            for((movement,title,section) in listOf(Triple("squat","План приседа","Приседания"),Triple("deadlift","План становой тяги","Становая тяга"))){
                compose.onNodeWithContentDescription("Раздел: $section").performScrollTo().performClick()
                compose.onNodeWithText(title).performScrollTo().performClick()
                compose.waitUntil(15000){runCatching{js(s,"!!window.Planner && document.querySelector('#discipline-select').value==='$movement' && !document.querySelector('#empty-plan').hidden")=="true"}.getOrDefault(false)}
                assertEquals("\"$movement\"",js(s,"document.querySelector('#discipline-select').value"))
                val start=java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                js(s,"(()=>{document.querySelector('#new-plan').click();const f=document.querySelector('#generator-form');const p={name:'Подготовка',age:35,bodyweight:90,goal:200,start:'$start',target:'${start.plusDays(56)}',experience:'trained',recovery:'normal',resultType:'max',resultWeight:160,reps:1,technique:'paused',resultDate:'${start.minusDays(2)}',bar:20,step:2.5,equipment:'gym',pain:'no'};for(const [k,v] of Object.entries(p))f.elements[k].value=v;f.elements.resultType.dispatchEvent(new Event('change'));for(const e of f.querySelectorAll('[name=weekday]'))e.checked=e.value==='1';f.requestSubmit();document.querySelector('#save-plan').click();selectSession('$start');})()")
                assertTrue(js(s,"document.querySelector('.main-exercise h3').innerText").contains(section))
                compose.waitUntil(10000){js(s,"[...document.querySelectorAll('#detail img')].every(i=>i.complete&&i.naturalWidth>0)")=="true"}
                val drawn=CountDownLatch(1)
                s.onActivity { a->requireNotNull(find(a.window.decorView)).postVisualStateCallback(1,object:WebView.VisualStateCallback(){
                    override fun onComplete(requestId:Long){drawn.countDown()}
                }) }
                assertTrue(drawn.await(10,TimeUnit.SECONDS))
                // Allow the compositor to present the WebView frame before the system screenshot.
                android.os.SystemClock.sleep(400)
                capture(movement)
                js(s,"document.querySelector('#detail button').click();(()=>{const f=document.querySelector('#log-form');f.elements['kg-0'].value=20;f.elements['reps-0'].value=10;f.elements.status.value='done';f.elements.verified.checked=true;f.requestSubmit();})()")
                assertEquals("true",js(s,"document.querySelector('#review-dialog').open"))
                js(s,"document.querySelector('#review-dialog').close()")
                compose.onNodeWithText("← Назад").performClick()
                compose.waitUntil(10000){GymRepository(GymDatabase(context)).let {r->val found=r.read().completed.any{it.sourceKey!=null&&(if(movement=="squat")Exercise.SQUAT else Exercise.DEADLIFT) in it.exercises};r.close();found}}
            }
            compose.onNodeWithText("Прогресс").performClick()
            compose.onNodeWithText("RPE",useUnmergedTree=true).performClick();capture("progress")
            compose.onNodeWithText("По плану",useUnmergedTree=true).performClick()
            compose.onNodeWithText("ИТОГО ЗА ПЕРИОД · 1 тренировок").assertIsDisplayed()
            compose.onNodeWithText("Настройки").performClick()
            compose.onNodeWithText("Отзывы и предложения · WhatsApp ↗").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Любое вознаграждение разработчику за эту программу — исключительно по желанию пользователя. Все функции доступны бесплатно.").assertExists()
            capture("settings")
        }
    }
}
