package om.mgtrener.mgym
import om.mgtrener.mgym.services.GitHubUpdateProvider
import org.junit.Assert.*
import org.junit.Test
class UpdateTest {
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
