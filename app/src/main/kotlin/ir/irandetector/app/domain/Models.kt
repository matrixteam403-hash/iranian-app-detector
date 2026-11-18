package ir.irandetector.app.domain

import ir.irandetector.app.data.KnownApp
import ir.irandetector.app.data.Verification

/** Everything the package scanner could read about one installed application. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val installerPackage: String?,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean,
    val targetSdk: Int,
    val isEnabled: Boolean,
    val signatureSha256: List<String>,
    val signatureSha1: List<String>,
    /** Browsers / handlers visible for this package (public metadata only). */
    val candidateInstallers: List<String>,
    val permissionCount: Int,
    val category: String?,
)

/** One piece of evidence produced by the engine. Never invented: always traceable. */
data class DetectionSignal(
    val type: SignalType,
    /** Points contributed to the raw score (already weighted). */
    val points: Int,
    /** Human readable, user facing explanation, e.g. "Package id matches Digikala". */
    val explanation: String,
)

enum class SignalType(val weightLabel: String) {
    PACKAGE_EXACT("Package identifier"),
    PACKAGE_ALTERNATIVE("Historical package identifier"),
    CERTIFICATE("Signing certificate"),
    DEVELOPER("Developer identity"),
    NAME_EN("English application name"),
    NAME_FA("Persian application name"),
    NAME_FUZZY("Approximate name match"),
    INSTALLER("Installation source"),
    DOMAIN("Developer domain"),
    PACKAGE_NAMESPACE("Developer package namespace"),
    ICON("Icon fingerprint"),
}

enum class ConfidenceTier(val label: String) {
    /** Full, multi-signal verification. */
    VERIFIED("Verified"),
    /** Several consistent signals, or one strong signal. */
    PROBABLE("Probable"),
    /** A single weak signal — shown but clearly marked as uncertain. */
    UNCERTAIN("Uncertain"),
    /** Below the reporting threshold. */
    NONE("Not detected");

    companion object {
        fun of(confidence: Int): ConfidenceTier = when {
            confidence >= 80 -> VERIFIED
            confidence >= 55 -> PROBABLE
            confidence >= 30 -> UNCERTAIN
            else -> NONE
        }
    }
}

data class DetectionResult(
    val app: InstalledApp,
    val confidence: Int,
    val tier: ConfidenceTier,
    val signals: List<DetectionSignal>,
    /** The database record that produced the strongest match, when any. */
    val matchedRecord: KnownApp?,
    /** Database trust level of [matchedRecord]. */
    val recordVerification: Verification?,
) {
    /** Ordered "why" lines for the details sheet. */
    val reasons: List<String> get() = signals.sortedByDescending { it.points }.map { it.explanation }

    val isDetected: Boolean get() = tier != ConfidenceTier.NONE

    val primarySignal: SignalType? get() = signals.maxByOrNull { it.points }?.type
}

/** Progress events emitted by a scan. */
sealed interface ScanProgress {
    data class Discovering(val found: Int) : ScanProgress
    data class Analysing(val index: Int, val total: Int, val label: String) : ScanProgress
    data class Finished(val total: Int, val detected: Int, val durationMs: Long) : ScanProgress
    data object Cancelled : ScanProgress
    data class Failed(val message: String) : ScanProgress
}

/** Snapshot used by the dashboard so it never shows invented numbers. */
data class DashboardStats(
    val installedCount: Int = 0,
    val detectedCount: Int = 0,
    val verifiedCount: Int = 0,
    val probableCount: Int = 0,
    val uncertainCount: Int = 0,
    val databaseRecords: Int = 0,
    val databaseUpdatedAt: Long = 0L,
    val lastScanAt: Long = 0L,
    val hasScanned: Boolean = false,
)
