package ir.irandetector.app.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.irandetector.app.data.DetectionRepository
import ir.irandetector.app.data.RemoteDatabaseClient
import ir.irandetector.app.data.ScanRepository
import ir.irandetector.app.database.AppDatabase
import ir.irandetector.app.domain.ConfidenceTier
import ir.irandetector.app.domain.DashboardStats
import ir.irandetector.app.domain.DetectionResult
import ir.irandetector.app.domain.InstalledApp
import ir.irandetector.app.domain.ScanProgress
import ir.irandetector.app.settings.AppSettings
import ir.irandetector.app.settings.SortMode
import ir.irandetector.app.settings.ThemeMode
import ir.irandetector.app.uninstall.UninstallManager
import ir.irandetector.app.vpn.VpnDetector
import ir.irandetector.app.vpn.VpnFinding
import ir.irandetector.app.vpn.VpnProbe
import ir.irandetector.app.vpn.VpnScanReport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single ViewModel for the whole app.
 *
 * The app has one coherent piece of state (the scan) shared by the dashboard,
 * the results list and the queue; splitting it across several ViewModels would
 * mean duplicating the scan job and the selection set.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val detectionRepository: DetectionRepository,
    private val remoteClient: RemoteDatabaseClient,
    private val settings: AppSettings,
    val uninstallManager: UninstallManager,
    private val vpnProbe: VpnProbe,
    private val vpnDetector: VpnDetector,
) : ViewModel() {

    data class UiState(
        val results: List<DetectionResult> = emptyList(),
        val selected: Set<String> = emptySet(),
        val query: String = "",
        val sortMode: SortMode = SortMode.CONFIDENCE,
        val tierFilter: Set<ConfidenceTier> = setOf(
            ConfidenceTier.VERIFIED,
            ConfidenceTier.PROBABLE,
            ConfidenceTier.UNCERTAIN,
        ),
        val exclusions: Set<String> = emptySet(),
        val isScanning: Boolean = false,
        val scanPhase: ScanProgress? = null,
        val lastScanAt: Long = 0L,
        val hasScanned: Boolean = false,
        val installedCount: Int = 0,
        val visibilityLimited: Boolean = false,
        val stats: DashboardStats = DashboardStats(),
        val themeMode: ThemeMode = ThemeMode.DARK,
        val hideExcluded: Boolean = true,
        val includeSystemApps: Boolean = true,
        val minimumConfidence: Int = 30,
        val databaseUrl: String? = null,
        val snackbar: String? = null,
        // ---- Uninstall confirmation (owned by us, not by the OEM) ----
        val uninstallPrompt: UninstallPrompt? = null,
        /** Walkthrough for browser downloads / package installer sideloading. */
        val showInstallHelp: Boolean = false,
        val uninstallDelayMs: Int = 350,
        // ---- VPN Hunter ----
        val vpnShowPossible: Boolean = true,
        val vpnIncludeSystemApps: Boolean = true,
        val vpnHunterEnabled: Boolean = false,
        val vpnScanning: Boolean = false,
        val vpnReport: VpnScanReport? = null,
        val vpnError: String? = null,
    ) {
        /** Results after search + tier filter + sort, ready for the list. */
        val visibleResults: List<DetectionResult>
            get() {
                val filtered = results.filter { result ->
                    (result.tier in tierFilter) &&
                        (query.isBlank() || result.app.label.contains(query, true) ||
                            result.app.packageName.contains(query, true) ||
                            (result.matchedRecord?.developer?.contains(query, true) == true))
                }
                return when (sortMode) {
                    SortMode.CONFIDENCE -> filtered.sortedByDescending { it.confidence }
                    SortMode.NAME -> filtered.sortedBy { it.app.label.lowercase() }
                    SortMode.RECENT -> filtered.sortedByDescending { it.app.firstInstallTime }
                }
            }

        val selectedResults: List<DetectionResult>
            get() = results.filter { it.app.packageName in selected }

        val allVisibleSelected: Boolean
            get() = visibleResults.isNotEmpty() && visibleResults.all { it.app.packageName in selected }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    /**
     * The in-app confirmation for one queued app.
     *
     * It exists because the OEM uninstall screen is not reliable: on several builds
     * it never appears, or appears and closes without returning a result. Owning the
     * prompt here guarantees exactly one confirmation per app, in order.
     */
    data class UninstallPrompt(
        val packageName: String,
        val label: String,
        val position: Int,
        val total: Int,
        /** Where the request came from: the results queue, or a single VPN finding. */
        val origin: UninstallOrigin = UninstallOrigin.SELECTION,
        /** True while the system dialog is (supposed to be) on screen. */
        val isWorking: Boolean = false,
        /** Set when the system uninstaller failed and we had to use app settings. */
        val systemDialogUnavailable: Boolean = false,
    )

    enum class UninstallOrigin { SELECTION, VPN_SINGLE }

    sealed interface UiEvent {
        data class Toast(val message: String) : UiEvent
        data class UninstallRequested(val packageName: String) : UiEvent
        data class DatabaseUpdated(val count: Int) : UiEvent

        /** Emitted when a VPN scan finishes so the Activity can surface a toast. */
        data class VpnScanFinished(val message: String) : UiEvent
    }

    val uninstallState: StateFlow<UninstallManager.UninstallState> = uninstallManager.state

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            detectionRepository.ensureSeeded()
            val (version, updatedAt) = detectionRepository.databaseMeta()
            _state.update {
                it.copy(
                    stats = it.stats.copy(
                        databaseRecords = detectionRepository.records.value.size,
                        databaseUpdatedAt = updatedAt,
                    ),
                )
            }
        }
        viewModelScope.launch {
            combine(
                settings.themeMode,
                settings.hideExcluded,
                settings.includeSystemApps,
                settings.minimumConfidence,
                settings.databaseUrl,
                settings.sortMode,
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                values.toList()
            }.collect { values ->
                _state.update {
                    it.copy(
                        themeMode = values[0] as ThemeMode,
                        hideExcluded = values[1] as Boolean,
                        includeSystemApps = values[2] as Boolean,
                        minimumConfidence = values[3] as Int,
                        databaseUrl = values[4] as String?,
                        sortMode = values[5] as SortMode,
                    )
                }
            }
        }
        viewModelScope.launch {
            scanRepository.observeExclusions().collect { rows ->
                _state.update { current ->
                    val names = rows.map { it.packageName }.toSet()
                    current.copy(
                        exclusions = names,
                        selected = current.selected - names,
                        results = if (current.hideExcluded) {
                            current.results.filterNot { it.app.packageName in names }
                        } else {
                            current.results
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            settings.lastScanAt.collect { timestamp ->
                _state.update { it.copy(lastScanAt = timestamp) }
            }
        }
        viewModelScope.launch {
            settings.vpnHunterEnabled.collect { enabled ->
                _state.update { it.copy(vpnHunterEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settings.uninstallDelayMs.collect { value ->
                _state.update { it.copy(uninstallDelayMs = value) }
            }
        }
        viewModelScope.launch {
            settings.vpnShowPossible.collect { value ->
                _state.update { it.copy(vpnShowPossible = value) }
            }
        }
        viewModelScope.launch {
            settings.vpnIncludeSystemApps.collect { value ->
                _state.update { it.copy(vpnIncludeSystemApps = value) }
            }
        }
    }

    // --------------------------------------------------------------- actions

    fun startScan() {
        if (_state.value.isScanning) return
        scanJob?.cancel()
        _state.update {
            it.copy(isScanning = true, scanPhase = ScanProgress.Discovering(0), snackbar = null)
        }
        scanJob = viewModelScope.launch {
            scanRepository.scan().collect { event ->
                when (event) {
                    is ScanRepository.ScanProgressOrResult.Progress -> when (val progress = event.progress) {
                        is ScanProgress.Finished -> {
                            _state.update { current ->
                                val verified = current.results.count { r -> r.tier == ConfidenceTier.VERIFIED }
                                current.copy(
                                    hasScanned = true,
                                    installedCount = progress.total,
                                    stats = current.stats.copy(
                                        installedCount = progress.total,
                                        detectedCount = progress.detected,
                                        lastScanAt = System.currentTimeMillis(),
                                        hasScanned = true,
                                    ),
                                )
                            }
                        }
                        else -> _state.update { it.copy(scanPhase = progress) }
                    }
                    is ScanRepository.ScanProgressOrResult.Result -> {
                        val output = event.output
                        val verified = output.results.count { it.tier == ConfidenceTier.VERIFIED }
                        val probable = output.results.count { it.tier == ConfidenceTier.PROBABLE }
                        val uncertain = output.results.count { it.tier == ConfidenceTier.UNCERTAIN }
                        _state.update { current ->
                            current.copy(
                                results = output.results,
                                installedCount = output.allApps.size,
                                isScanning = false,
                                scanPhase = null,
                                hasScanned = true,
                                visibilityLimited = output.visibilityLimited,
                                // Drop selections for packages that vanished.
                                selected = current.selected.intersect(
                                    output.results.map { it.app.packageName }.toSet(),
                                ),
                                stats = current.stats.copy(
                                    installedCount = output.allApps.size,
                                    detectedCount = output.results.size,
                                    verifiedCount = verified,
                                    probableCount = probable,
                                    uncertainCount = uncertain,
                                    databaseRecords = detectionRepository.records.value.size,
                                    databaseUpdatedAt = detectionRepository.updatedAt.value,
                                    lastScanAt = System.currentTimeMillis(),
                                    hasScanned = true,
                                ),
                            )
                        }
                        uninstallManager.reset()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update {
            it.copy(isScanning = false, scanPhase = ScanProgress.Cancelled)
        }
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun setSort(mode: SortMode) {
        _state.update { it.copy(sortMode = mode) }
        viewModelScope.launch { settings.setSortMode(mode) }
    }

    fun toggleTier(tier: ConfidenceTier) = _state.update { current ->
        val next = if (tier in current.tierFilter) current.tierFilter - tier else current.tierFilter + tier
        current.copy(tierFilter = next.ifEmpty { setOf(ConfidenceTier.VERIFIED) })
    }

    fun toggleSelection(packageName: String) = _state.update { current ->
        val next = if (packageName in current.selected) {
            current.selected - packageName
        } else {
            current.selected + packageName
        }
        current.copy(selected = next)
    }

    fun selectAllVisible() = _state.update { current ->
        current.copy(selected = current.selected + current.visibleResults.map { it.app.packageName })
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun exclude(packageName: String) {
        val result = _state.value.results.firstOrNull { it.app.packageName == packageName } ?: return
        viewModelScope.launch {
            scanRepository.exclude(result.app)
            _events.emit(UiEvent.Toast("${result.app.label} excluded from future scans"))
        }
    }

    fun unexclude(packageName: String) {
        viewModelScope.launch { scanRepository.unexclude(packageName) }
    }

    fun exclusions(): Set<String> = _state.value.exclusions

    /** Called by the UI when the user presses "Uninstall selected". */
    fun beginUninstall() {
        val selected = _state.value.selectedResults
        if (selected.isEmpty()) return
        val ownPackage = _state.value.results.map { it.app.packageName }
        uninstallManager.start(selected.map { it.app })
        Log.i(TAG, "Uninstall queue started with ${selected.size} app(s) of ${ownPackage.size}")
        showNextUninstallPrompt()
    }

    /** Shows the confirmation for the app at the head of the queue. */
    private fun showNextUninstallPrompt() {
        val snapshot = uninstallManager.state.value
        val current = snapshot.current
        if (snapshot.finished || current == null) {
            _state.update { it.copy(uninstallPrompt = null) }
            return
        }
        val position = snapshot.queue.count { it.outcome != null } + 1
        _state.update {
            it.copy(
                uninstallPrompt = UninstallPrompt(
                    packageName = current.packageName,
                    label = current.label,
                    position = position,
                    total = snapshot.total,
                ),
            )
        }
    }

    /** User accepted the in-app confirmation: hand this one package to Android. */
    fun confirmUninstall() {
        val prompt = _state.value.uninstallPrompt ?: return
        val next = uninstallManager.requestNext()
        if (next == null) {
            showNextUninstallPrompt()
            return
        }
        _state.update { it.copy(uninstallPrompt = prompt.copy(isWorking = true)) }
        viewModelScope.launch { _events.emit(UiEvent.UninstallRequested(next)) }
    }

    /**
     * Removes a single VPN client straight from the VPN Hunter list.
     *
     * Deliberately reuses the exact same pipeline as the bulk flow — in-app
     * confirmation -> one system request -> PackageManager verification — so a client
     * can be removed on its own without building a queue first.
     */
    fun removeFromVpnHunter(packageName: String, label: String?) {
        if (_state.value.uninstallPrompt != null || uninstallManager.state.value.awaitingResult) {
            Log.w(TAG, "VPN remove ignored: another confirmation is already pending")
            return
        }
        if (uninstallManager.state.value.isRunning) {
            Log.w(TAG, "VPN remove ignored: the uninstall queue is running")
            return
        }
        // Reuse the queue machinery for a single item: one request, one result.
        uninstallManager.start(
            listOf(
                InstalledApp(
                    packageName = packageName,
                    label = label ?: packageName,
                    versionName = null,
                    versionCode = 0,
                    firstInstallTime = 0,
                    lastUpdateTime = 0,
                    installerPackage = null,
                    isSystemApp = false,
                    isUpdatedSystemApp = false,
                    targetSdk = 0,
                    isEnabled = true,
                    signatureSha256 = emptyList(),
                    signatureSha1 = emptyList(),
                    candidateInstallers = emptyList(),
                    permissionCount = 0,
                    category = null,
                ),
            ),
        )
        _state.update {
            it.copy(
                uninstallPrompt = UninstallPrompt(
                    packageName = packageName,
                    label = label ?: packageName,
                    position = 1,
                    total = 1,
                    origin = UninstallOrigin.VPN_SINGLE,
                ),
            )
        }
    }

    /** User declined the in-app confirmation: nothing is sent to the system. */
    fun skipUninstall() {
        val prompt = _state.value.uninstallPrompt ?: return
        if (uninstallManager.state.value.awaitingResult) {
            // A request is already in flight; skipping now would desynchronise the
            // queue from the system dialog, so it is refused instead of guessed.
            Log.w(TAG, "Skip refused while a system request is pending")
            return
        }
        Log.i(TAG, "Skipped ${prompt.packageName}")
        uninstallManager.skipCurrent()
        showNextUninstallPrompt()
    }

    /** The Activity had to fall back to app settings for the current package. */
    fun onSystemDialogUnavailable() {
        _state.update { current ->
            current.uninstallPrompt?.let {
                current.copy(uninstallPrompt = it.copy(systemDialogUnavailable = true, isWorking = false))
            } ?: current
        }
    }

    fun dismissUninstallPrompt() {
        _state.update { it.copy(uninstallPrompt = null) }
    }

    // ------------------------------------------------------------ install help

    fun openInstallHelp() = _state.update { it.copy(showInstallHelp = true) }

    fun dismissInstallHelp() {
        _state.update { it.copy(showInstallHelp = false) }
        viewModelScope.launch { settings.setInstallHelpSeen(true) }
    }

    fun setUninstallDelayMs(value: Int) {
        viewModelScope.launch {
            settings.setUninstallDelayMs(value)
            _state.update { it.copy(uninstallDelayMs = value) }
        }
    }

    /**
     * Called by the Activity after a removal attempt resolves.
     *
     * [packageName] is mandatory: a late callback for an app the queue already moved
     * past must never be attributed to the *next* app. This was the second half of the
     * "queue reports the wrong result" class of bugs.
     */
    fun onUninstallResult(
        succeeded: Boolean,
        packageName: String? = null,
        failureMessage: String? = null,
    ) {
        val expected = uninstallManager.state.value.current?.packageName
        if (packageName != null && expected != null && packageName != expected) {
            Log.w(
                TAG,
                "Ignoring stale uninstall result for $packageName (queue is at $expected)",
            )
            return
        }
        uninstallManager.reportResult(succeeded = succeeded, failureMessage = failureMessage)
        if (succeeded) {
            // The app is gone: remove it from the results immediately.
            val packageToDrop = uninstallManager.state.value.queue
                .lastOrNull { it.outcome == UninstallManager.Outcome.SUCCEEDED }
                ?.packageName
            if (packageToDrop != null) {
                _state.update { current ->
                    val remaining = current.results.filterNot { it.app.packageName == packageToDrop }
                    current.copy(
                        results = remaining,
                        selected = current.selected - packageToDrop,
                        stats = rebuildStats(current.stats, remaining, current.installedCount - 1),
                    )
                }
            }
        }
        val origin = _state.value.uninstallPrompt?.origin
        if (succeeded) {
            val packageToDrop = packageName
                ?: uninstallManager.state.value.queue
                    .lastOrNull { it.outcome == UninstallManager.Outcome.SUCCEEDED }
                    ?.packageName
            if (origin == UninstallOrigin.VPN_SINGLE && packageToDrop != null) {
                _state.update { current ->
                    current.copy(
                        vpnReport = current.vpnReport?.copy(
                            findings = current.vpnReport.findings
                                .filterNot { it.packageName == packageToDrop },
                        ),
                    )
                }
                Log.i(TAG, "Removed $packageToDrop from the VPN Hunter list")
            }
        }
        // Next app is *presented*, not launched: the user confirms each one.
        showNextUninstallPrompt()
    }

    fun cancelUninstallQueue() {
        uninstallManager.cancelQueue()
        _state.update { it.copy(uninstallPrompt = null) }
    }

    fun resetUninstallQueue() {
        uninstallManager.reset()
        _state.update { it.copy(uninstallPrompt = null) }
    }

    fun requestNextUninstall() {
        showNextUninstallPrompt()
    }

    fun reportPreflightFailure(reason: String) {
        uninstallManager.reportPreflightFailure(reason)
        requestNextUninstall()
    }

    // ------------------------------------------------------------ VPN Hunter

    fun setVpnHunterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setVpnHunterEnabled(enabled)
            _state.update { it.copy(vpnHunterEnabled = enabled) }
            if (enabled) startVpnScan() else _state.update { it.copy(vpnReport = null) }
        }
    }

    /** Runs the multi-method VPN sweep. Cancellable, never blocks the UI thread. */
    fun startVpnScan() {
        if (_state.value.vpnScanning) return
        _state.update { it.copy(vpnScanning = true, vpnError = null) }
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val result = runCatching {
                // One snapshot, used by both the probe and the engine: the network
                // state cannot change underneath us mid-scan.
                val system = vpnProbe.systemVpnState()
                val candidates = vpnProbe.collect(system)
                vpnDetector.analyse(
                    candidates = candidates,
                    system = system,
                    includeSystemApps = _state.value.vpnIncludeSystemApps,
                    includePossible = _state.value.vpnShowPossible,
                ).copy(
                    durationMs = System.currentTimeMillis() - startedAt,
                )
            }
            result.onSuccess { report ->
                _state.update { it.copy(vpnScanning = false, vpnReport = report) }
                _events.emit(
                    UiEvent.VpnScanFinished(
                        if (report.findings.isEmpty()) {
                            "VPN Hunter: no VPN clients found"
                        } else {
                            "VPN Hunter: ${report.findings.size} VPN client(s) found"
                        },
                    ),
                )
            }.onFailure { t ->
                _state.update {
                    it.copy(
                        vpnScanning = false,
                        vpnError = t.message ?: "VPN scan failed",
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------- settings

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setHideExcluded(value: Boolean) {
        viewModelScope.launch {
            settings.setHideExcluded(value)
            _state.update { it.copy(hideExcluded = value) }
        }
    }

    fun setIncludeSystemApps(value: Boolean) {
        viewModelScope.launch {
            settings.setIncludeSystemApps(value)
            _state.update { it.copy(includeSystemApps = value) }
        }
    }

    fun setMinimumConfidence(value: Int) {
        viewModelScope.launch {
            settings.setMinimumConfidence(value)
            _state.update { it.copy(minimumConfidence = value) }
        }
    }

    fun setVpnShowPossible(value: Boolean) {
        viewModelScope.launch {
            settings.setVpnShowPossible(value)
            _state.update { it.copy(vpnShowPossible = value) }
        }
    }

    fun setVpnIncludeSystemApps(value: Boolean) {
        viewModelScope.launch {
            settings.setVpnIncludeSystemApps(value)
            _state.update { it.copy(vpnIncludeSystemApps = value) }
        }
    }

    fun setDatabaseUrl(url: String?) {
        viewModelScope.launch {
            settings.setDatabaseUrl(url)
            _state.update { it.copy(databaseUrl = url?.takeIf(String::isNotBlank)) }
        }
    }

    fun updateDatabase(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val url = settings.databaseUrl.first()
            if (url.isNullOrBlank()) {
                onResult("No update URL configured")
                return@launch
            }
            val payload = remoteClient.fetch(url).getOrElse {
                onResult("Download failed: ${it.message}")
                return@launch
            }
            detectionRepository.applyRemotePayload(payload)
                .onSuccess { count ->
                    _state.update {
                        it.copy(
                            stats = it.stats.copy(
                                databaseRecords = detectionRepository.records.value.size,
                                databaseUpdatedAt = detectionRepository.updatedAt.value,
                            ),
                        )
                    }
                    _events.emit(UiEvent.DatabaseUpdated(count))
                    onResult("Updated: $count records")
                }
                .onFailure { onResult("Update rejected: ${it.message}") }
        }
    }

    fun databaseInfo(): Pair<Int, Long> =
        detectionRepository.records.value.size to detectionRepository.updatedAt.value

    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    private companion object {
        const val TAG = "AppViewModel"
    }

    private fun rebuildStats(
        stats: DashboardStats,
        results: List<DetectionResult>,
        installedCount: Int,
    ): DashboardStats = stats.copy(
        installedCount = installedCount.coerceAtLeast(0),
        detectedCount = results.size,
        verifiedCount = results.count { it.tier == ConfidenceTier.VERIFIED },
        probableCount = results.count { it.tier == ConfidenceTier.PROBABLE },
        uncertainCount = results.count { it.tier == ConfidenceTier.UNCERTAIN },
        databaseRecords = detectionRepository.records.value.size,
        databaseUpdatedAt = detectionRepository.updatedAt.value,
    )
}
