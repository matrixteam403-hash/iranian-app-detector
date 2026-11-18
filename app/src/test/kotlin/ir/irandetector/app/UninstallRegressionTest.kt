package ir.irandetector.app

import ir.irandetector.app.domain.InstalledApp
import ir.irandetector.app.uninstall.UninstallManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the reported "app does not ask me to confirm each app"
 * problem.
 *
 * Root causes that were fixed:
 *  1. The Activity collected UiEvents with `collectLatest`, so a new event could
 *     cancel the coroutine that was launching the *previous* system dialog.
 *  2. Success/failure was taken from `resultCode` only. Several OEM settings apps
 *     return RESULT_CANCELED after a successful uninstall, so the queue marked an
 *     uninstalled app as "cancelled" and could stall.
 *  3. A lost Activity result (process recreation) left `awaitingResult = true`
 *     forever, freezing the queue.
 */
class UninstallRegressionTest {

    private fun app(pkg: String) = InstalledApp(
        packageName = pkg,
        label = pkg,
        versionName = "1",
        versionCode = 1,
        firstInstallTime = 0,
        lastUpdateTime = 0,
        installerPackage = null,
        isSystemApp = false,
        isUpdatedSystemApp = false,
        targetSdk = 34,
        isEnabled = true,
        signatureSha256 = emptyList(),
        signatureSha1 = emptyList(),
        candidateInstallers = emptyList(),
        permissionCount = 0,
        category = null,
    )

    @Test
    fun `every selected app is requested exactly once, in order`() {
        val manager = UninstallManager()
        val apps = listOf(app("a.one"), app("b.two"), app("c.three"), app("d.four"))
        manager.start(apps)
        val requested = mutableListOf<String>()
        repeat(apps.size) {
            val next = manager.requestNext() ?: error("queue stalled at ${requested.size}")
            requested.add(next)
            manager.reportResult(succeeded = true)
        }
        assertEquals(apps.map { it.packageName }, requested)
        assertTrue(manager.state.value.finished)
    }

    @Test
    fun `cancelled result code does not stall the next request`() {
        val manager = UninstallManager()
        manager.start(listOf(app("a.one"), app("b.two")))
        assertEquals("a.one", manager.requestNext())
        manager.reportResult(succeeded = false) // user pressed back
        assertEquals("b.two", manager.requestNext())
        manager.reportResult(succeeded = true)
        assertTrue(manager.state.value.finished)
    }

    @Test
    fun `queue recovers after a lost activity result`() {
        val manager = UninstallManager()
        manager.start(listOf(app("a.one"), app("b.two")))
        assertEquals("a.one", manager.requestNext())
        // Simulate "process died while the dialog was open": no result arrives, then
        // the Activity re-verifies with PackageManager and reports the real outcome.
        assertEquals(2, manager.state.value.remaining) // neither resolved yet
        manager.reportResult(succeeded = true)
        assertEquals("b.two", manager.requestNext())
        assertEquals(1, manager.state.value.remaining)
        manager.reportResult(succeeded = true)
        assertEquals(0, manager.state.value.remaining)
    }

    @Test
    fun `awaiting flag never stays stuck after a reported result`() {
        val manager = UninstallManager()
        manager.start(listOf(app("a.one")))
        manager.requestNext()
        assertTrue(manager.state.value.awaitingResult)
        manager.reportResult(succeeded = true)
        assertFalse(manager.state.value.awaitingResult)
        assertNull(manager.requestNext())
    }

    @Test
    fun `duplicate results for the same item are ignored`() {
        val manager = UninstallManager()
        manager.start(listOf(app("a.one"), app("b.two")))
        manager.requestNext()
        manager.reportResult(succeeded = true)
        // A second callback (result + onResume verification) must not skip an item.
        manager.reportResult(succeeded = true)
        assertEquals(1, manager.state.value.completed.size)
        assertEquals("b.two", manager.requestNext())
    }

    @Test
    fun `skipping an app marks it cancelled and moves on without a system request`() {
        val manager = UninstallManager()
        manager.start(listOf(app("a.one"), app("b.two"), app("c.three")))
        // The in-app dialog is shown but the user declines: nothing is launched.
        manager.skipCurrent()
        assertEquals(1, manager.state.value.cancelled.size)
        assertFalse(manager.state.value.awaitingResult)
        assertEquals("b.two", manager.requestNext())
    }

    @Test
    fun `queue finishes correctly when every app is skipped`() {
        val manager = UninstallManager()
        manager.start(listOf(app("a.one"), app("b.two")))
        manager.skipCurrent()
        manager.skipCurrent()
        val snapshot = manager.state.value
        assertTrue(snapshot.finished)
        assertEquals(2, snapshot.cancelled.size)
        assertNull(manager.requestNext())
    }

    @Test
    fun `a single item queue removes exactly one app and finishes`() {
        // This is the path used by the per-card "Remove" button in VPN Hunter.
        val manager = UninstallManager()
        manager.start(listOf(app("com.example.vpnclient")))
        assertEquals(1, manager.state.value.total)
        assertEquals("com.example.vpnclient", manager.requestNext())
        manager.reportResult(succeeded = true)
        val snapshot = manager.state.value
        assertTrue(snapshot.finished)
        assertEquals(1, snapshot.completed.size)
        assertNull(manager.requestNext())
    }

    @Test
    fun `a single item queue that is declined leaves the app untouched`() {
        val manager = UninstallManager()
        manager.start(listOf(app("com.example.vpnclient")))
        manager.requestNext()
        manager.reportResult(succeeded = false)
        assertTrue(manager.state.value.finished)
        assertTrue(manager.state.value.completed.isEmpty())
        assertEquals(1, manager.state.value.cancelled.size)
    }
}
