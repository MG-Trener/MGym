package om.mgtrener.mgym

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import om.mgtrener.mgym.database.GymDatabase
import om.mgtrener.mgym.domain.*
import om.mgtrener.mgym.repositories.GymRepository
import om.mgtrener.mgym.i18n.Language
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LanguageTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun allEightLanguagesKeepDataAcrossRecreation() {
        check(context.packageName.endsWith(".uitest"))
        val repo=GymRepository(GymDatabase(context))
        repo.clear()
        val workoutId=repo.start(listOf(Exercise.BENCH))
        repo.saveSet(Draft(weight="80",reps="5",comment="Моя заметка / 我的笔记").toSet(workoutId))
        val before=repo.read()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                for(tag in Language.tags) {
                    scenario.onActivity { Language.choose(it,tag) }
                    if(android.os.Build.VERSION.SDK_INT < 33) scenario.recreate()
                    compose.waitUntil(10000){Language.tag==tag}
                    val expected=listOf("Завершить тренировку","Finish workout","Training beenden","Terminer la séance","Finalizar entrenamiento","Concluir treino","结束训练","Жаттығуды аяқтау")[Language.tags.indexOf(tag)]
                    compose.waitUntil(10000){compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()}
                    compose.onNodeWithText(expected).assertExists()
                    assertEquals(before,repo.read())
                    compose.waitForIdle()
                    val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    java.io.File(context.getExternalFilesDir(null),"language-$tag.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                }
            }
            // Opening a fresh Activity also restores the explicit choice.
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.onNodeWithText("Жаттығуды аяқтау").assertExists()
                assertEquals("kk",Language.tag)
            }
        } finally {
            Language.choose(context,"ru"); repo.clear(); repo.close()
        }
    }
    @Test fun pickerChangesLanguageAndSupportsSystemDefault() {
        check(context.packageName.endsWith(".uitest"))
        GymRepository(GymDatabase(context)).let { it.clear(); it.close() }
        Language.choose(context,"ru")
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.onNodeWithText("Настройки").performClick()
                compose.onNodeWithText("Русский").performScrollTo().performClick()
                compose.onNodeWithText("English").performClick()
                compose.waitUntil(10000){Language.tag=="en"}
                compose.onNodeWithText("App language").assertExists()
                compose.onNodeWithText("English").performScrollTo().performClick()
                compose.onNodeWithText("System default").performClick()
                compose.waitUntil(10000){Language.selected(context)=="system"}
            }
        } finally { Language.choose(context,"ru") }
    }
}
