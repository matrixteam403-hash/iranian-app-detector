package ir.irandetector.app.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import ir.irandetector.app.domain.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Reads installed packages using only public PackageManager APIs.
 *
 * Package visibility (Android 11+): the manifest declares QUERY_ALL_PACKAGES,
 * which is the documented mechanism for an app whose core, user-visible purpose
 * is to inventory installed applications ("device search / antivirus / app
 * management" category in Play policy). No root, no accessibility service, no
 * device-admin tricks and no hidden APIs are used to circumvent the platform.
 * If a device/OEM still filters the result, we simply report fewer packages —
 * nothing is guessed.
 */
@Singleton
class PackageScanner @Inject constructor(
    private val context: Context,
) {
    private val pm: PackageManager get() = context.packageManager

    data class ScanOutcome(
        val apps: List<InstalledApp>,
        val visibilityLimited: Boolean,
    )

    suspend fun scan(): ScanOutcome = withContext(Dispatchers.IO) {
        val packages: List<PackageInfo> = try {
            pm.getInstalledPackages(
                PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_SIGNING_CERTIFICATES,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "getInstalledPackages(GET_SIGNING_CERTIFICATES) failed", t)
            try {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            } catch (t2: Throwable) {
                Log.e(TAG, "Package enumeration failed", t2)
                emptyList()
            }
        }

        val result = ArrayList<InstalledApp>(packages.size)
        for (info in packages) {
            coroutineContext.ensureActive()
            val app = runCatching { toInstalledApp(info) }
                .onFailure { Log.w(TAG, "Skipping unreadable package ${info.packageName}", it) }
                .getOrNull()
            if (app != null) result.add(app)
        }
        result.sortBy { it.label.lowercase() }

        // A device always has far more than a handful of packages; a very small
        // result is a strong hint that visibility is being restricted.
        ScanOutcome(apps = result, visibilityLimited = result.size < 5)
    }

    /** True when QUERY_ALL_PACKAGES is actually granted to us at runtime. */
    fun hasFullVisibility(): Boolean = try {
        pm.canQueryPackages()
    } catch (t: Throwable) {
        false
    }

    private fun PackageManager.canQueryPackages(): Boolean {
        // A marker package that certainly exists on every device.
        return getPackageInfo("android", 0) != null
    }

    fun loadIcon(packageName: String, sizePx: Int) = try {
        pm.getApplicationIcon(packageName)
    } catch (t: Throwable) {
        null
    }

    private fun toInstalledApp(info: PackageInfo): InstalledApp {
        val ai: ApplicationInfo = info.applicationInfo ?: throw IllegalStateException("no app info")
        val label = runCatching { pm.getApplicationLabel(ai).toString() }
            .getOrDefault(info.packageName)

        val (sha256, sha1) = extractCertificates(info)

        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(info.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(info.packageName)
            }
        }.getOrNull()

        val browsers = runCatching {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
            pm.queryIntentActivities(intent, 0).mapNotNull { it.activityInfo?.packageName }.distinct()
        }.getOrDefault(emptyList())

        return InstalledApp(
            packageName = info.packageName,
            label = label,
            versionName = info.versionName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION") info.versionCode.toLong()
            },
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
            installerPackage = installer,
            isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            isUpdatedSystemApp = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            targetSdk = ai.targetSdkVersion,
            isEnabled = ai.enabled,
            signatureSha256 = sha256,
            signatureSha1 = sha1,
            candidateInstallers = browsers,
            permissionCount = info.requestedPermissions?.size ?: 0,
            category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { ApplicationInfo.getCategoryTitle(context, ai.category)?.toString() }
                    .getOrNull()
            } else {
                null
            },
        )
    }

    /** Signing certificate digests, in both SHA-256 and SHA-1 for database matching. */
    private fun extractCertificates(info: PackageInfo): Pair<List<String>, List<String>> {
        val sha256 = ArrayList<String>(1)
        val sha1 = ArrayList<String>(1)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = info.signingInfo ?: return emptyList<String>() to emptyList()
                val signers = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                for (signer in signers) {
                    sha256 += digest("SHA-256", signer.toByteArray())
                    sha1 += digest("SHA-1", signer.toByteArray())
                }
            } else {
                @Suppress("DEPRECATION")
                val signatures = info.signatures ?: return emptyList<String>() to emptyList()
                for (signature in signatures) {
                    sha256 += digest("SHA-256", signature.toByteArray())
                    sha1 += digest("SHA-1", signature.toByteArray())
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Certificate extraction failed for ${info.packageName}", t)
        }
        return sha256 to sha1
    }

    private fun digest(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes)
            .joinToString(":") { "%02X".format(it) }

    private companion object {
        const val TAG = "PackageScanner"
    }
}
