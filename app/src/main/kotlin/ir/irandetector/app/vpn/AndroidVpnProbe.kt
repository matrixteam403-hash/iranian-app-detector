package ir.irandetector.app.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import java.net.SocketException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects the raw facts the [VpnDetector] reasons about.
 *
 * Everything here uses public, documented APIs only — no root, no hidden APIs, no
 * accessibility abuse. Where the platform legitimately hides information (the
 * system keeps the VPN owner package private), we report the limitation instead of
 * inventing a result.
 *
 * Methods implemented:
 *  1. package id    → curated signature list, matched against every installed package
 *  2. manifest      → VPN service declarations, BIND_VPN_SERVICE, transport ABI
 *  3. system state  → TrafficStats / VPN interfaces + "always-on VPN" settings entry
 *  4. transport ABI → proxy cores shipped as native libraries or declared services
 *  5. name/brand    → label + package text against VPN vocabulary and vendors
 *  6. deep links    → VPN intents a client exposes
 */
@Singleton
class AndroidVpnProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) : VpnProbe {

    private val pm: PackageManager get() = context.packageManager

    override suspend fun collect(system: SystemVpnSnapshot?): List<VpnCandidate> =
        withContext(Dispatchers.IO) {
            val packages = try {
                pm.getInstalledPackages(
                    PackageManager.GET_PERMISSIONS or
                        PackageManager.GET_SERVICES or
                        PackageManager.GET_ACTIVITIES or
                        PackageManager.GET_RECEIVERS,
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Package enumeration failed for VPN scan", t)
                emptyList()
            }
            val snapshot = system ?: systemSnapshot()
            packages.mapNotNull { info ->
                runCatching { toCandidate(info, snapshot) }
                    .onFailure { Log.w(TAG, "Skipping ${info.packageName} in VPN scan", it) }
                    .getOrNull()
            }
        }

    override suspend fun systemVpnState(): SystemVpnSnapshot = withContext(Dispatchers.IO) {
        systemSnapshot()
    }

    private fun systemSnapshot(): SystemVpnSnapshot {
        val limitations = mutableListOf<String>()
        val interfaces = activeNetworkInterfaces()
        val vpnInterfaces = interfaces.filter { name ->
            name.startsWith("tun", ignoreCase = true) ||
                name.startsWith("ppp", ignoreCase = true) ||
                name.startsWith("utun", ignoreCase = true) ||
                name.startsWith("wg", ignoreCase = true) ||
                name.startsWith("ipsec", ignoreCase = true)
        }

        var connected = false
        var stateLabel: String? = null
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            stateLabel = when {
                caps == null -> "no active network"
                connected -> "VPN transport active"
                else -> "VPN inactive"
            }
        } catch (t: Throwable) {
            limitations += "ConnectivityManager reported no VPN transport information."
        }

        // "Always-on VPN" is a user setting; the per-user secure setting is world readable,
        // so the owner package may be available. If it is not, say so honestly.
        val alwaysOnApp = runCatching {
            Settings.Secure.getString(context.contentResolver, "always_on_vpn_app")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val alwaysOnLockDown = runCatching {
            Settings.Secure.getInt(context.contentResolver, "always_on_vpn_lockdown") == 1
        }.getOrDefault(false)
        if (alwaysOnApp == null) {
            limitations += "No always-on VPN package is readable (either none is configured or " +
                "this Android build restricts the setting)."
        }
        if (vpnInterfaces.isNotEmpty() && !connected) {
            limitations += "VPN-style interfaces (${vpnInterfaces.joinToString()}) exist without an " +
                "active VPN transport — usually a stale tunnel."
        }

        // Android deliberately hides which package owns the VPN profile.
        limitations += "Android does not expose the package that owns the system VPN; " +
            "ownership is inferred from app declarations and traffic accounting."

        return SystemVpnSnapshot(
            activeInterfaces = vpnInterfaces,
            connectedState = stateLabel,
            usingVpn = connected,
            alwaysOnPackage = alwaysOnApp,
            alwaysOnLockDown = alwaysOnLockDown,
            limitations = limitations.distinct(),
        )
    }

    private fun activeNetworkInterfaces(): List<String> = try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter { runCatching { it.isUp }.getOrDefault(false) }
            ?.map { it.name }
            ?: emptyList()
    } catch (t: SocketException) {
        emptyList()
    } catch (t: Throwable) {
        emptyList()
    }

    private fun toCandidate(info: PackageInfo, system: SystemVpnSnapshot): VpnCandidate {
        val app = info.applicationInfo ?: error("no app info")
        val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(info.packageName)
        val perms = info.requestedPermissions?.toSet() ?: emptySet()

        val services = info.services?.mapNotNull { it.name } ?: emptyList()
        val activities = info.activities?.mapNotNull { it.name } ?: emptyList()
        val receivers = info.receivers?.mapNotNull { it.name } ?: emptyList()

        val vpnServices = services.filter { name ->
            val n = name.lowercase()
            VpnSignatures.serviceNameKeywords.any { n.contains(it) }
        }

        val nativeLibs = runCatching {
            app.nativeLibraryDir?.let { dir ->
                java.io.File(dir).list()?.toList() ?: emptyList()
            } ?: emptyList()
        }.getOrDefault(emptyList())

        val transportPermission = perms.firstOrNull { permission ->
            VpnSignatures.transportPermissions.any { permission.endsWith(it) }
        }
        val tunnelLib = nativeLibs.firstOrNull { lib ->
            VpnSignatures.nativeLibKeywords.any { lib.contains(it) }
        }
        val vpnTransportAbi = transportPermission != null || tunnelLib != null

        // Deep links a client exposes for tunnel profiles (e.g. app://tunnel?config=…).
        // Only *view* handlers whose scheme/class mentions VPN vocabulary count; this is one
        // query per installed package, so the cost stays bounded.
        val vpnIntents = runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_VIEW).setPackage(info.packageName),
                PackageManager.MATCH_DEFAULT_ONLY,
            ).mapNotNull { it.activityInfo?.name }
                .filter { name ->
                    val n = name.lowercase()
                    n.contains("tunnel") || n.contains("vpn") || n.contains("proxy")
                }
        }.getOrDefault(emptyList())

        // Traffic accounting: -1 when never used or not measurable (pre-Q only allows
        // UID lookup for our own UID, TrafficStats handles that internally).
        val uid = app.uid
        val rx = runCatching { TrafficStats.getUidRxBytes(uid) }.getOrDefault(TrafficStats.UNSUPPORTED.toLong())
        val tx = runCatching { TrafficStats.getUidTxBytes(uid) }.getOrDefault(TrafficStats.UNSUPPORTED.toLong())
        val traffic = if (rx > 0 || tx > 0) (rx.coerceAtLeast(0) + tx.coerceAtLeast(0)) else 0L

        // Attribute the live VPN interface to this app only when its own manifest
        // proves it can own one; otherwise the interface stays system-level context.
        //
        // Two independent proofs are accepted:
        //   1. it declares BIND_VPN_SERVICE *and* ships a VPN service / transport, or
        //   2. it is the package Android is configured to always keep connected.
        val declaredVpnCapability = (vpnServices.isNotEmpty() || vpnTransportAbi) &&
            "android.permission.BIND_VPN_SERVICE" in perms
        val configuredAsAlwaysOn = system.alwaysOnPackage?.equals(
            info.packageName,
            ignoreCase = true,
        ) == true
        val ownsVpnInterface = system.activeInterfaces.isNotEmpty() &&
            (declaredVpnCapability || configuredAsAlwaysOn)

        return VpnCandidate(
            packageName = info.packageName,
            label = label,
            versionName = info.versionName,
            isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            installerPackage = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pm.getInstallSourceInfo(info.packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(info.packageName)
                }
            }.getOrNull(),
            requestedPermissions = perms,
            serviceNames = services.filter { it in vpnServices || services.size <= 40 },
            nativeLibs = nativeLibs,
            activityNames = activities,
            receiverNames = receivers,
            declaredVpnIntents = vpnIntents,
            hasVpnTransportAbi = vpnTransportAbi,
            transportEvidence = listOfNotNull(transportPermission, tunnelLib).firstOrNull(),
            hasCaptivePortalBypassPermission = "android.permission.CAPTIVE_PORTAL_APP" in perms,
            systemVpnInterface = if (ownsVpnInterface) system.activeInterfaces.first() else null,
            systemVpnState = system.connectedState,
            trafficBytes = if (ownsVpnInterface) traffic else 0L,
        )
    }

    private companion object {
        const val TAG = "AndroidVpnProbe"
    }
}
