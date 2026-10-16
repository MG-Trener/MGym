package om.mgtrener.mgym

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import om.mgtrener.mgym.services.BackgroundUpdates
import om.mgtrener.mgym.services.BenchPlannerStorage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayDistributionTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun releaseHasOnlyPlayUpdatesAndNoInstallationPermissions() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".uitest"))
        check(BuildConfig.STORE_DISTRIBUTION) { "Run connectedReleaseAndroidTest with -PmgymStoreUiTest=true" }
        assertEquals(0,context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        @Suppress("DEPRECATION")
        val permissions=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        for(permission in listOf("REQUEST_INSTALL_PACKAGES","POST_NOTIFICATIONS","RECEIVE_BOOT_COMPLETED","ACCESS_NETWORK_STATE"))
            assertFalse(permission,permissions.contains("android.permission.$permission"))
        assertFalse(BackgroundUpdates.enabled(context))
        BenchPlannerStorage.clear(context);context.deleteDatabase("mgym.db")
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15000) { compose.onAllNodesWithText("Настройки").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Настройки").performClick()
            compose.onNodeWithText("Открыть Google Play ↗").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Проверять в фоне").assertDoesNotExist()
            compose.onNodeWithText("Проверить обновления").assertDoesNotExist()
            compose.onNodeWithText("Поддержка · info@mg-trener.com").performScrollTo().assertIsDisplayed()
        }
    }
}
