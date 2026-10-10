package om.mgtrener.mgym

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import om.mgtrener.mgym.database.GymDatabase
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.repositories.GymRepository
import om.mgtrener.mgym.services.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class WorkoutUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun capture(name: String, dialog:Boolean=false) {
        compose.waitForIdle()
        val file=java.io.File(context.getExternalFilesDir(null),"$name.png")
        file.outputStream().use { (if(dialog) compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun read() = GymRepository(GymDatabase(context)).let {r->try {r.read()} finally {r.close()} }
    @Test fun deadliftAndSquatStartFromHomeAndHaveSeparateProgress() {
        check(context.packageName.endsWith(".uitest"))
        context.deleteDatabase("mgym.db")
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Начать: Становая тяга").fetchSemanticsNodes().isNotEmpty()}
            capture("home-three")
            compose.onNodeWithContentDescription("Начать: Становая тяга").performClick()
            compose.waitUntil(10000) {read().active?.exercises == listOf(Exercise.DEADLIFT)}
            compose.onNodeWithContentDescription("Вес, кг").performTextReplacement("150")
            compose.onNodeWithContentDescription("Повторы").performTextReplacement("5")
            compose.onNodeWithText("Завершить тренировку").performClick()
            compose.onNodeWithText("Завершить",useUnmergedTree=true).performClick()
            compose.waitUntil(10000) {read().completed.size==1}
            compose.onNodeWithText("← Сегодня").performClick()
            compose.onNodeWithContentDescription("Начать: Приседания").performClick()
            compose.waitUntil(10000) {read().active?.exercises == listOf(Exercise.SQUAT)}
            compose.onNodeWithContentDescription("Вес, кг").performTextReplacement("120")
            compose.onNodeWithContentDescription("Повторы").performTextReplacement("5")
            compose.onNodeWithText("Завершить тренировку").performClick()
            compose.onNodeWithText("Завершить",useUnmergedTree=true).performClick()
            compose.waitUntil(10000) {read().completed.size==2}
            compose.onNodeWithText("← Сегодня").performClick()
            compose.onNodeWithText("Прогресс").performClick()
            compose.onNodeWithText("Жим",useUnmergedTree=true).performClick()
            compose.onNodeWithText("ИТОГО ЗА ПЕРИОД · 0 тренировок").assertIsDisplayed()
            compose.onNodeWithText("Тяга",useUnmergedTree=true).performClick()
            compose.onNodeWithText("ИТОГО ЗА ПЕРИОД · 1 тренировок").assertIsDisplayed()
            compose.onNodeWithText("750").assertIsDisplayed()
            compose.onNodeWithText("Присед",useUnmergedTree=true).performClick()
            compose.onNodeWithText("600").assertIsDisplayed()
            capture("three-movements-progress")
            assertEquals(Exercise.DEADLIFT,read().completed.first {read().setsFor(it.id).single().weight==150.0}.exercises.single())
            assertEquals(Exercise.SQUAT,read().completed.first {read().setsFor(it.id).single().weight==120.0}.exercises.single())
        }
    }
    @Test fun compactScreensDragCopyCalendarAndProgress() {
        check(context.packageName.endsWith(".uitest"))
        context.deleteDatabase("mgym.db")
        val now=System.currentTimeMillis()
        val workouts=(1L..6L).map {Workout(it,now-(7-it)*7*86400000L,now-(7-it)*7*86400000L+60000,listOf(Exercise.BENCH))} + Workout(7,now,null,listOf(Exercise.BENCH,Exercise.CURL))
        val weights=listOf(20.0,50.0,80.0,110.0,120.0,125.0,130.0,100.0,80.0,120.0)
        val sets=(1L..6L).map {LiftSet(it,it,Exercise.BENCH,70+it*5.0,6)} + weights.mapIndexed {i,w->LiftSet(100L+i,7,Exercise.BENCH,w,if(i==0)20 else 6)}
        GymRepository(GymDatabase(context)).let {r->r.replace(Backup(GymData(workouts,sets),Draft(weight="",reps=""),false));r.close()}
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            compose.waitUntil(15000) {compose.onAllNodesWithText("Сохранено: 10").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Разминка").assertDoesNotExist()
            compose.onNodeWithText("Завершить тренировку").assertIsDisplayed()
            capture("active")
            compose.onNodeWithContentDescription("Переместить подход 10").assertIsDisplayed()
            val handle=compose.onNodeWithContentDescription("Переместить подход 1")
            val delta=compose.onNodeWithContentDescription("Переместить подход 2").fetchSemanticsNode().boundsInRoot.center.y-handle.fetchSemanticsNode().boundsInRoot.center.y
            handle.performTouchInput {down(center);advanceEventTime(650);moveBy(Offset(0f,delta),100);up()}
            compose.waitUntil(10000) {read().setsFor(7).first().weight==50.0}
            scenario.recreate()
            compose.waitUntil(10000) {compose.onAllNodesWithText("Сохранено: 10").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(50.0,read().setsFor(7).first().weight,0.0)
            capture("active-list")
            compose.onNodeWithText("Завершить тренировку").performClick()
            compose.onNodeWithText("Завершить",useUnmergedTree=true).performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithText("ТРЕНИРОВКА ЗАВЕРШЕНА").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("Переместить подход 10").assertIsDisplayed()
            compose.onNodeWithText("Копировать").performClick()
            compose.onNodeWithText("Скопировано").assertIsDisplayed()
            compose.runOnIdle {
                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                assertEquals(WorkoutText.format(read(),7),clipboard.primaryClip!!.getItemAt(0).text.toString())
            }
            capture("history")
            compose.onNodeWithText("← Сегодня").performClick()
            compose.onNodeWithText("Календарь").performClick()
            compose.onNodeWithContentDescription("Месяц 12, ${LocalDate.now().year}").assertIsDisplayed()
            capture("calendar-year")
            compose.onNodeWithContentDescription("Месяц ${LocalDate.now().monthValue}, ${LocalDate.now().year}").performClick()
            compose.onNode(isDialog()).assertDoesNotExist()
            capture("calendar-month")
            compose.onNodeWithContentDescription("Дата ${LocalDate.now()}, тренировок 1").performClick()
            compose.onNodeWithText("Копировать").assertIsDisplayed()
            compose.onNodeWithContentDescription("Переместить подход 10").assertIsDisplayed()
            capture("calendar-day",true)
            compose.onNodeWithText("Закрыть").performClick()
            compose.onNodeWithText("Прогресс").performClick()
            compose.onNodeWithText("ИТОГО ЗА ПЕРИОД · 7 тренировок").assertIsDisplayed()
            val graph=compose.onNodeWithContentDescription("График прогресса")
            graph.performTouchInput {swipeRight()}
            compose.onNodeWithText("Вес",useUnmergedTree=true).performClick()
            capture("progress")
            compose.onNodeWithText("Рекорды").performClick()
            capture("records")
            compose.onNodeWithText("ИТОГО ЗА ПЕРИОД · 7 тренировок").assertIsDisplayed()
        }
    }
    @Test fun completedEditsPersistAndCalendarCanEdit() {
        check(context.packageName.endsWith(".uitest"))
        context.deleteDatabase("mgym.db")
        val now=System.currentTimeMillis()
        GymRepository(GymDatabase(context)).let { r ->
            r.replace(Backup(GymData(listOf(Workout(1,now,now+60000,listOf(Exercise.BENCH))),
                listOf(LiftSet(1,1,Exercise.BENCH,80.0,8),LiftSet(2,1,Exercise.BENCH,100.0,5))),Draft(weight="",reps=""),false))
            r.close()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15000) {compose.onAllNodesWithText("1140 кг").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("1140 кг").performClick()
            compose.onNodeWithText("Редактировать").performClick()
            compose.onNodeWithText("80").performClick()
            compose.onNodeWithContentDescription("Вес, кг").performTextReplacement("82,5")
            compose.onNodeWithContentDescription("Повторы").performTextReplacement("7")
            compose.onNodeWithText("Сохранить").performClick()
            compose.waitUntil(10000) {read().setsFor(1).first().weight==82.5}
            compose.onNodeWithText("+ Добавить подход").performClick()
            compose.onNodeWithContentDescription("Вес, кг").assertTextEquals("")
            compose.onNodeWithContentDescription("Вес, кг").performTextReplacement("105")
            compose.onNodeWithContentDescription("Повторы").performTextReplacement("3")
            compose.onNodeWithText("Добавить",useUnmergedTree=true).performClick()
            compose.waitUntil(10000) {read().setsFor(1).size==3}
            compose.onNodeWithContentDescription("Действия с подходом 2").performClick()
            compose.onNodeWithText("Удалить").performClick()
            compose.onNodeWithText("Отмена").performClick()
            assertEquals(3,read().setsFor(1).size)
            compose.onNodeWithContentDescription("Действия с подходом 2").performClick()
            compose.onNodeWithText("Удалить").performClick()
            compose.onNodeWithText("Удалить",useUnmergedTree=true).performClick()
            compose.waitUntil(10000) {read().setsFor(1).size==2}
            assertEquals(listOf(82.5,105.0),read().setsFor(1).map {it.weight})
            capture("completed-edit")
            compose.onNodeWithText("Готово").performClick()
            compose.onNodeWithText("Копировать").performClick()
            compose.runOnIdle {
                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                assertEquals(WorkoutText.format(read(),1),clipboard.primaryClip!!.getItemAt(0).text.toString())
            }
            scenario.recreate()
            compose.waitUntil(10000) {compose.onAllNodesWithText("82,5 × 7").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("82.5 × 7").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("← Назад").performClick()
            compose.onNodeWithText("Календарь").performClick()
            compose.onNodeWithContentDescription("Месяц ${LocalDate.now().monthValue}, ${LocalDate.now().year}").performClick()
            compose.onNodeWithContentDescription("Дата ${LocalDate.now()}, тренировок 1").performClick()
            compose.onNodeWithText("Редактировать").performClick()
            compose.onNodeWithText("105").performClick()
            compose.onNodeWithContentDescription("Повторы").performTextReplacement("4")
            compose.onNodeWithText("Сохранить").performClick()
            compose.waitUntil(10000) {read().setsFor(1).last().reps==4}
            capture("completed-calendar-edit",true)
            assertEquals(now+60000,read().completed.single().finishedAt)
        }
    }
    @Test fun emptyRowsValidatePersistAndFinishWithoutExtraPlus() {
        check(context.packageName.endsWith(".uitest"))
        context.deleteDatabase("mgym.db")
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Начать: Жим штанги лёжа").fetchSemanticsNodes().isNotEmpty()}
            assertTrue(read().sets.isEmpty());assertTrue(read().workouts.isEmpty())
            compose.onNodeWithContentDescription("Начать: Жим штанги лёжа").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Добавить строку").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("00:00").assertIsDisplayed()
            compose.onNodeWithContentDescription("Запустить таймер").performClick()
            compose.onNodeWithContentDescription("Пауза таймера").assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithContentDescription("Пауза таймера").assertIsDisplayed()
            compose.onNodeWithContentDescription("Пауза таймера").performClick()
            compose.onNodeWithContentDescription("Запустить таймер").assertIsDisplayed()
            compose.onNodeWithContentDescription("Сбросить таймер").performClick()
            compose.onNodeWithText("00:00").assertIsDisplayed()
            compose.onNodeWithContentDescription("Вес, кг").assertTextEquals("")
            compose.onNodeWithContentDescription("Повторы").assertTextEquals("")
            compose.onNodeWithContentDescription("Добавить строку").assertIsNotEnabled()
            capture("row-empty")
            compose.onNodeWithContentDescription("Вес, кг").performTextReplacement("62,5")
            compose.onNodeWithContentDescription("Добавить строку").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Повторы").performTextReplacement("5")
            val firstPlus=compose.onNodeWithContentDescription("Добавить строку").fetchSemanticsNode().boundsInRoot.top
            compose.onNodeWithContentDescription("Добавить строку").performClick()
            compose.waitUntil(10000) {read().sets.size==1}
            compose.onNodeWithContentDescription("Вес, кг").assertTextEquals("")
            compose.onNodeWithContentDescription("Повторы").assertTextEquals("")
            compose.onNodeWithContentDescription("Добавить строку").assertIsNotEnabled()
            assertTrue(compose.onNodeWithContentDescription("Добавить строку").fetchSemanticsNode().boundsInRoot.top>firstPlus)
            compose.onNodeWithContentDescription("Вес, кг").performTextReplacement("70")
            scenario.recreate()
            compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Добавить строку").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("Вес, кг").assertTextEquals("70")
            compose.onNodeWithContentDescription("Повторы").assertTextEquals("")
            compose.onNodeWithContentDescription("Добавить строку").assertIsNotEnabled()
            compose.onNodeWithText("Завершить тренировку").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Повторы").performTextReplacement("6")
            compose.onNodeWithContentDescription("Добавить строку").performClick()
            compose.waitUntil(10000) {read().sets.size==2}
            compose.onNodeWithContentDescription("Вес, кг").performTextReplacement("0")
            compose.onNodeWithContentDescription("Повторы").performTextReplacement("8")
            compose.onNodeWithContentDescription("Добавить строку").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Вес, кг").performTextReplacement("80")
            compose.onNodeWithContentDescription("Повторы").performTextReplacement("201")
            compose.onNodeWithContentDescription("Добавить строку").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Повторы").performTextReplacement("8")
            compose.onNodeWithText("Завершить тренировку").assertIsDisplayed()
            capture("row-filled")
            compose.onNodeWithText("Завершить тренировку").performClick()
            compose.onNodeWithText("Продолжить").performClick()
            assertEquals(2,read().sets.size)
            compose.onNodeWithText("Завершить тренировку").performClick()
            compose.onNodeWithText("Завершить",useUnmergedTree=true).performClick()
            compose.waitUntil(10000) {read().completed.size==1}
            assertEquals(listOf(62.5,70.0,80.0),read().sets.map {it.weight})
            assertEquals(listOf(5,6,8),read().sets.map {it.reps})
        }
    }
    @Test fun restTimerResetsWithKeyboardOpenAndWhileRunning() {
        check(context.packageName.endsWith(".uitest"))
        context.deleteDatabase("mgym.db")
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Начать: Жим штанги лёжа").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("Начать: Жим штанги лёжа").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Вес, кг").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("Вес, кг").performTextReplacement("130")
            compose.onNodeWithContentDescription("Запустить таймер").performTouchInput { click() }
            compose.waitUntil(5000) {
                compose.onAllNodesWithContentDescription("Таймер отдыха").fetchSemanticsNodes()
                    .any { it.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] != "00:00" }
            }
            compose.onNodeWithContentDescription("Сбросить таймер").performTouchInput { click() }
            capture("timer-reset-keyboard")
            compose.onNodeWithText("00:00").assertIsDisplayed()
            compose.onNodeWithContentDescription("Запустить таймер").assertIsDisplayed()
            compose.onNodeWithContentDescription("Вес, кг").assertTextEquals("130")
            compose.onNodeWithContentDescription("Запустить таймер").performTouchInput { click() }
            compose.onNodeWithContentDescription("Пауза таймера").performTouchInput { click() }
            compose.onNodeWithContentDescription("Сбросить таймер").performTouchInput { click() }
            compose.onNodeWithText("00:00").assertIsDisplayed()
        }
    }
}
