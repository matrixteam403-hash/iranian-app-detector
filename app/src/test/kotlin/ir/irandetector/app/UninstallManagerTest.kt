package ir.irandetector.app

import ir.irandetector.app.domain.InstalledApp
import ir.irandetector.app.uninstall.UninstallManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Uninstall queue state machine tests, including the two edge cases the brief
 * calls out explicitly: cancelled confirmations and repeated dialog launches.
 */
class UninstallManagerTest {

    private lateinit var manager: UninstallManager

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

    @Before
    fun setUp() {
        manager = UninstallManager()
        manager.start(listOf(app("a.one"), app("b.two"), app("c.three")))
    }

    @Test
    fun `queue starts with the first package`() {
        val state = manager.state.value
        assertEquals(3, state.total)
        assertEquals(0, state.currentIndex)
        assertTrue(state.isRunning)
        assertFalse(state.finished)
        assertEquals("a.one", manager.requestNext())
    }

    @Test
    fun `requesting twice without a result does not relaunch`() {
        assertEquals("a.one", manager.requestNext())
        // The dialog is on screen: a second request must be ignored.
        assertNull(manager.requestNext())
        assertNull(manager.requestNext())
    }

    @Test
    fun `successful result advances to the next package`() {
        assertEquals("a.one", manager.requestNext())
        manager.reportResult(succeeded = true)
        assertEquals(1, manager.state.value.completed.size)
        assertEquals("b.two", manager.requestNext())
    }

    @Test
    fun `cancelled result is recorded as cancelled and queue continues`() {
        manager.requestNext()
        manager.reportResult(succeeded = false)
        val state = manager.state.value
        assertEquals(1, state.cancelled.size)
        assertEquals(0, state.completed.size)
        assertEquals("b.two", manager.requestNext())
    }

    @Test
    fun `failure is distinguished from cancellation`() {
        manager.requestNext()
        manager.reportResult(succeeded = false, failureMessage = "protected package")
        assertEquals(1, manager.state.value.failed.size)
        assertEquals(0, manager.state.value.cancelled.size)
    }

    @Test
    fun `queue finishes after the last item`() {
        repeat(3) {
            manager.requestNext()
            manager.reportResult(succeeded = true)
        }
        val state = manager.state.value
        assertTrue(state.finished)
        assertFalse(state.isRunning)
        assertEquals(3, state.completed.size)
        assertEquals(1f, state.progressFraction, 0.001f)
        assertNull(manager.requestNext())
    }

    @Test
    fun `cancelling the queue marks remaining items cancelled`() {
        manager.requestNext()
        manager.reportResult(succeeded = true)
        manager.requestNext()
        manager.cancelQueue()
        val state = manager.state.value
        assertTrue(state.finished)
        assertEquals(1, state.completed.size)
        assertEquals(2, state.cancelled.size)
        assertEquals(0, state.remaining)
    }

    @Test
    fun `stale result after cancellation is ignored`() {
        manager.requestNext()
        manager.cancelQueue()
        manager.reportResult(succeeded = true)
        // Still exactly the cancellation snapshot, no success recorded.
        assertEquals(3, manager.state.value.cancelled.size)
        assertEquals(0, manager.state.value.completed.size)
    }

    @Test
    fun `reset clears everything`() {
        manager.requestNext()
        manager.reset()
        val state = manager.state.value
        assertEquals(0, state.total)
        assertFalse(state.isRunning)
    }

    @Test
    fun `empty start produces a finished queue`() {
        manager.start(emptyList())
        assertTrue(manager.state.value.finished)
        assertNull(manager.requestNext())
    }

    @Test
    fun `progress fraction is monotonic`() {
        val fractions = mutableListOf<Float>()
        repeat(3) {
            manager.requestNext()
            manager.reportResult(succeeded = true)
            fractions.add(manager.state.value.progressFraction)
        }
        assertEquals(fractions.sorted(), fractions)
    }

    @Test
    fun `preflight blocks self uninstall`() {
        val issue = UninstallManager.preflightIssue(app("ir.irandetector.app"), "ir.irandetector.app")
        assertTrue(issue != null)
    }

    @Test
    fun `preflight allows a normal app`() {
        assertNull(UninstallManager.preflightIssue(app("com.digikala"), "ir.irandetector.app"))
    }
}
