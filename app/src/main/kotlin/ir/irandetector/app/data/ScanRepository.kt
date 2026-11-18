package ir.irandetector.app.data

import ir.irandetector.app.database.ExclusionDao
import ir.irandetector.app.database.ExclusionEntity
import ir.irandetector.app.database.ScanCacheDao
import ir.irandetector.app.database.ScanCacheEntity
import ir.irandetector.app.detection.DetectionEngine
import ir.irandetector.app.detection.DetectionIndex
import ir.irandetector.app.domain.ConfidenceTier
import ir.irandetector.app.domain.DetectionResult
import ir.irandetector.app.domain.InstalledApp
import ir.irandetector.app.domain.ScanProgress
import ir.irandetector.app.settings.AppSettings
import ir.irandetector.app.settings.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Orchestrates the scan: enumerate → detect → cache → expose results.
 *
 * Results are cached in Room so that a cold start can show the previous scan
 * instantly (the UI labels it as such) while a fresh scan runs.
 */
@Singleton
class ScanRepository @Inject constructor(
    private val scanner: ir.irandetector.app.scanner.PackageScanner,
    private val detectionEngine: DetectionEngine,
    private val detectionRepository: DetectionRepository,
    private val scanCacheDao: ScanCacheDao,
    private val exclusionDao: ExclusionDao,
    private val settings: AppSettings,
    private val iconHasher: ir.irandetector.app.scanner.IconHasher,
) {
    data class ScanOutput(
        val results: List<DetectionResult>,
        val allApps: List<InstalledApp>,
        val visibilityLimited: Boolean,
    )

    suspend fun exclusions(): Set<String> = exclusionDao.allPackageNames().toSet()

    fun observeExclusions(): Flow<List<ExclusionEntity>> = exclusionDao.observeAll()

    suspend fun exclude(app: InstalledApp) = exclusionDao.insert(
        ExclusionEntity(packageName = app.packageName, appLabel = app.label),
    )

    suspend fun unexclude(packageName: String) = exclusionDao.delete(packageName)

    /**
     * Cold flow that emits progress while scanning and finishes with [ScanOutput].
     * Cancelling the collector cancels the scan cooperatively (no orphan threads).
     */
    fun scan(): Flow<ScanProgressOrResult> = flow {
        val startedAt = System.currentTimeMillis()
        val records = detectionRepository.records.value.ifEmpty {
            detectionRepository.ensureSeeded()
            detectionRepository.records.value
        }
        val index = DetectionIndex.build(records)

        emit(ScanProgressOrResult.Progress(ScanProgress.Discovering(0)))
        val outcome = scanner.scan()
        emit(ScanProgressOrResult.Progress(ScanProgress.Discovering(outcome.apps.size)))

        val includeSystem = settings.includeSystemApps.first()
        val minConfidence = settings.minimumConfidence.first()
        val exclusions = exclusions()

        val candidates = outcome.apps.filter { app ->
            (includeSystem || !app.isSystemApp) &&
                app.packageName !in exclusions
        }

        val results = ArrayList<DetectionResult>(candidates.size)
        candidates.forEachIndexed { position, app ->
            coroutineContext.ensureActive()
            if (position % 8 == 0 || position == candidates.lastIndex) {
                emit(
                    ScanProgressOrResult.Progress(
                        ScanProgress.Analysing(position + 1, candidates.size, app.label),
                    ),
                )
            }
            var assessment = detectionEngine.detect(app, records, index)
            if (assessment.signals.isEmpty()) {
                // Second pass: an icon fingerprint can only *support* a match, so it
                // is computed lazily for candidates that already produced evidence.
                val hash = runCatching { iconHasher.hash(app.packageName) }.getOrNull()
                if (hash != null) assessment = detectionEngine.detect(app, records, index, hash)
            }
            if (assessment.score < minConfidence || assessment.signals.isEmpty()) return@forEachIndexed
            val result = detectionEngine.toResult(app, assessment)
            if (!result.isDetected) return@forEachIndexed
            results.add(result)
        }

        val duration = System.currentTimeMillis() - startedAt
        persist(results)
        settings.setLastScanAt(System.currentTimeMillis())

        emit(
            ScanProgressOrResult.Progress(
                ScanProgress.Finished(
                    total = candidates.size,
                    detected = results.size,
                    durationMs = duration,
                ),
            ),
        )
        emit(ScanProgressOrResult.Result(ScanOutput(results, outcome.apps, outcome.visibilityLimited)))
    }.flowOn(Dispatchers.Default)

    private suspend fun persist(results: List<DetectionResult>) = withContext(Dispatchers.IO) {
        scanCacheDao.clear()
        val now = System.currentTimeMillis()
        scanCacheDao.insertAll(
            results.map { result ->
                ScanCacheEntity(
                    packageName = result.app.packageName,
                    label = result.app.label,
                    versionName = result.app.versionName,
                    confidence = result.confidence,
                    verification = result.tier.name,
                    signals = ir.irandetector.app.core.Json.stringArray(
                        result.signals.map { it.type.name },
                    ),
                    reasons = ir.irandetector.app.core.Json.stringArray(result.reasons),
                    matchedRecordId = result.matchedRecord?.id,
                    scannedAt = now,
                )
            },
        )
    }

    /** Cached rows from the last session (used for the dashboard before a new scan). */
    suspend fun cachedResultCount(): Int = scanCacheDao.getAll().size

    suspend fun sortResults(results: List<DetectionResult>, mode: SortMode): List<DetectionResult> =
        when (mode) {
            SortMode.CONFIDENCE -> results.sortedByDescending { it.confidence }
            SortMode.NAME -> results.sortedBy { it.app.label.lowercase() }
            SortMode.RECENT -> results.sortedByDescending { it.app.firstInstallTime }
        }

    sealed interface ScanProgressOrResult {
        data class Progress(val progress: ScanProgress) : ScanProgressOrResult
        data class Result(val output: ScanOutput) : ScanProgressOrResult
    }
}

/** Small helper so callers can ask "did this scan find anything at all?". */
fun List<DetectionResult>.byTier(tier: ConfidenceTier): List<DetectionResult> =
    filter { it.tier == tier }
