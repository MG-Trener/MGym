package om.mgtrener.mgym
import om.mgtrener.mgym.services.AppVersion
import org.junit.Assert.*
import org.junit.Test
class VersionTest {
    @Test fun comparesNumbersRatherThanStringsAndRejectsUnstableTags() {
        assertTrue(AppVersion.newer("v0.10.0","0.2.0"))
        assertFalse(AppVersion.newer("v0.2.0","0.2.0"))
        assertFalse(AppVersion.newer("0.1.9","0.2.0"))
        assertFalse(AppVersion.newer("v0.3.0-beta","0.2.0"))
        assertFalse(AppVersion.newer("garbage","0.2.0"))
    }
}
