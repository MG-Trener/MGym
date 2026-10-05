package om.mgtrener.mgym
import android.Manifest
import android.app.job.JobScheduler
import androidx.test.platform.app.InstrumentationRegistry
import om.mgtrener.mgym.services.BackgroundUpdates
import om.mgtrener.mgym.services.GitHubUpdateProvider
import org.junit.Assert.*
import org.junit.Test
class UpdateTest {
    @Test fun backgroundChecksCanBeScheduledAndDisabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".uitest"))
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        BackgroundUpdates.setEnabled(context, true)
        try {
            val jobs=context.getSystemService(JobScheduler::class.java).allPendingJobs
            assertTrue(jobs.any {it.isPeriodic && it.isPersisted})
            assertTrue(jobs.all {it.networkType==android.app.job.JobInfo.NETWORK_TYPE_ANY})
        } finally {
            BackgroundUpdates.setEnabled(context, false)
        }
        assertTrue(context.getSystemService(JobScheduler::class.java).allPendingJobs.none {it.service.className.endsWith("UpdateCheckJob")})
    }
    private fun release(version:String="v0.3.0",url:String="https://github.com/MG-Trener/MGym/releases/download/v0.3.0/MGym-0.3.0.apk") = """{"tag_name":"$version","draft":false,"prerelease":false,"assets":[{"name":"MGym-0.3.0.apk","size":12345,"browser_download_url":"$url"}]}"""
    @Test fun offersNewApkAndRecognizesCurrentVersion() {
        assertNotNull(GitHubUpdateProvider.parse(release(),"0.2.0").apkUrl)
        assertNull(GitHubUpdateProvider.parse(release(),"0.3.0").apkUrl)
    }
    @Test fun rejectsMissingAndUntrustedAssetsAndMalformedResponses() {
        assertThrows(IllegalArgumentException::class.java) {GitHubUpdateProvider.parse(release(url="https://evil.example/MGym.apk"),"0.2.0")}
        assertThrows(IllegalArgumentException::class.java) {GitHubUpdateProvider.parse(release().replace("12345","0"),"0.2.0")}
        assertThrows(Exception::class.java) {GitHubUpdateProvider.parse("{}","0.2.0")}
        assertThrows(IllegalArgumentException::class.java) {GitHubUpdateProvider.parse(release().replace("\"prerelease\":false","\"prerelease\":true"),"0.2.0")}
    }
}
