package `in`.c1ph3rj.scanly.feature.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanlyLaunchActionTest {

    @Test
    fun fromActionAndExtra_readsExplicitAction() {
        assertEquals(
            ScanlyLaunchAction.Scan,
            ScanlyLaunchAction.fromActionAndExtra(
                action = ScanlyLaunchAction.Scan.intentAction,
                extra = null,
            ),
        )
    }

    @Test
    fun fromActionAndExtra_readsExtraName() {
        assertEquals(
            ScanlyLaunchAction.Qr,
            ScanlyLaunchAction.fromActionAndExtra(action = null, extra = "QR"),
        )
    }

    @Test
    fun fromActionAndExtra_readsExtraIntentAction() {
        assertEquals(
            ScanlyLaunchAction.Import,
            ScanlyLaunchAction.fromActionAndExtra(
                action = "android.intent.action.MAIN",
                extra = ScanlyLaunchAction.Import.intentAction,
            ),
        )
    }

    @Test
    fun fromActionAndExtra_returnsNullForPlainLaunch() {
        assertNull(
            ScanlyLaunchAction.fromActionAndExtra(
                action = "android.intent.action.MAIN",
                extra = null,
            ),
        )
    }

    @Test
    fun intentActions_areUnique() {
        val actions = ScanlyLaunchAction.entries.map { it.intentAction }.toSet()
        assertEquals(ScanlyLaunchAction.entries.size, actions.size)
    }
}
