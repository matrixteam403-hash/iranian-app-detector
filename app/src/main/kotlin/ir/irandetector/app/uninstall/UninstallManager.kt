package ir.irandetector.app.uninstall

import ir.irandetector.app.domain.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State machine for the sequential uninstall queue.
 *
 * Pure Kotlin and side-effect free on purpose: the UI layer turns [UninstallState.current]
 * into a platform uninstall Intent and feeds the Activity result back in with
 * [reportResult]. That split makes the tricky part — never launching the next
 * dialog before the previous result arrives, and never losing the queue when the
 * activity is recreated — fully unit testable.
 */
@Singleton
class UninstallManager @Inject constructor() {

    enum class Outcome { SUCCEEDED, CANCELLED, FAILED }

    data class Item(
        val packageName: String,
        val label: String,
        val outcome: Outcome? = null,
    )

    data class UninstallState(
        val queue: List<Item> = emptyList(),
        val currentIndex: Int = 0,
        val isRunning: Boolean = false,
        /** Set while an uninstall dialog is on screen; guards against double launches. */
        val awaitingResult: Boolean = false,
        val finished: Boolean = false,
    ) {
        val total: Int get() = queue.size
        val current: Item? get() = queue.getOrNull(currentIndex)
        val completed: List<Item> get() = queue.filter { it.outcome == Outcome.SUCCEEDED }
        val cancelled: List<Item> get() = queue.filter { it.outcome == Outcome.CANCELLED }
        val failed: List<Item> get() = queue.filter { it.outcome == Outcome.FAILED }
        val remaining: Int get() = queue.count { it.outcome == null }
        val progressFraction: Float
            get() = if (queue.isEmpty()) 0f else queue.count { it.outcome != null }.toFloat() / queue.size
    }

    private val _state = MutableStateFlow(UninstallState())
    val state: StateFlow<UninstallState> = _state.asStateFlow()

    /** Starts a new queue. Any previous run is discarded. */
    fun start(apps: List<InstalledApp>) {
        _state.value = UninstallState(
            queue = apps.map { Item(it.packageName, it.label) },
            isRunning = apps.isNotEmpty(),
            awaitingResult = false,
            finished = apps.isEmpty(),
        )
    }

    /**
     * Called by the UI right before it launches the system dialog.
     * Returns the package to uninstall, or null when there is nothing to do
     * (already awaiting a result, queue empty, queue finished).
     */
    fun requestNext(): String? {
        val snapshot = _state.value
        if (!snapshot.isRunning || snapshot.awaitingResult || snapshot.finished) return null
        val current = snapshot.current ?: run {
            _state.update { it.copy(isRunning = false, finished = true) }
            return null
        }
        _state.update { it.copy(awaitingResult = true) }
        return current.packageName
    }

    /**
     * Feeds the Activity result back.
     * [succeeded] comes from `resultCode == Activity.RESULT_OK`; a non-OK result
     * is reported as CANCELLED (the user pressed back / declined), never as a
     * system failure, so the summary stays truthful.
     */
    fun reportResult(succeeded: Boolean, failureMessage: String? = null) {
        val snapshot = _state.value
        if (!snapshot.awaitingResult) return // stale result after cancel: ignore
        val index = snapshot.currentIndex
        val updatedQueue = snapshot.queue.toMutableList()
        val current = updatedQueue.getOrNull(index) ?: return finish()
        updatedQueue[index] = current.copy(
            outcome = when {
                succeeded -> Outcome.SUCCEEDED
                failureMessage != null -> Outcome.FAILED
                else -> Outcome.CANCELLED
            },
        )
        val nextIndex = index + 1
        val done = nextIndex >= updatedQueue.size
        _state.value = snapshot.copy(
            queue = updatedQueue,
            currentIndex = nextIndex,
            awaitingResult = false,
            isRunning = !done,
            finished = done,
        )
    }

    /** Marks the current package as failed without showing a dialog (e.g. protected app). */
    fun reportPreflightFailure(reason: String) {
        val snapshot = _state.value
        if (!snapshot.awaitingResult) return
        reportResult(succeeded = false, failureMessage = reason)
    }

    /**
     * Marks the current item as skipped *without* a system request.
     *
     * Needed because the confirmation now happens in-app: the user may decline an
     * app before any intent is launched, so there is no dialog result to report.
     */
    fun skipCurrent() {
        val snapshot = _state.value
        val index = snapshot.currentIndex
        if (index >= snapshot.queue.size) return finish()
        val updated = snapshot.queue.toMutableList()
        updated[index] = updated[index].copy(outcome = Outcome.CANCELLED)
        val nextIndex = index + 1
        val done = nextIndex >= updated.size
        _state.value = snapshot.copy(
            queue = updated,
            currentIndex = nextIndex,
            awaitingResult = false,
            isRunning = !done,
            finished = done,
        )
    }

    /** User aborted the whole queue; remaining items are marked as cancelled. */
    fun cancelQueue() {
        val snapshot = _state.value
        if (!snapshot.isRunning && !snapshot.awaitingResult) return
        val updated = snapshot.queue.map { item ->
            if (item.outcome == null) item.copy(outcome = Outcome.CANCELLED) else item
        }
        _state.value = snapshot.copy(
            queue = updated,
            currentIndex = updated.size,
            isRunning = false,
            awaitingResult = false,
            finished = true,
        )
    }

    fun reset() {
        _state.value = UninstallState()
    }

    private fun finish() {
        _state.update { it.copy(isRunning = false, awaitingResult = false, finished = true) }
    }

    companion object {
        /**
         * Packages that can never be removed by their owner on a non-rooted device.
         * Reported honestly instead of pretending an uninstall was attempted.
         */
        fun preflightIssue(app: InstalledApp, ownPackage: String): String? = when {
            app.packageName == ownPackage -> "This app cannot uninstall itself"
            app.packageName == "android" -> "Android system framework cannot be uninstalled"
            !app.isEnabled -> "Application is disabled for this user"
            else -> null
        }
    }
}
