package ir.irandetector.app.vpn

import ir.irandetector.app.core.Json
import ir.irandetector.app.core.TextNormalizer

/**
 * Data model for the VPN Hunter add-on.
 *
 * A "VPN app" is reported only when at least one concrete, inspectable artefact
 * proves it. Every finding carries the method that produced it and the raw
 * evidence string, so the UI can always explain itself and never guesses.
 */
enum class VpnMethod(val label: String, val explanation: String) {
    KNOWN_PACKAGE(
        "Known package",
        "Package identifier belongs to a published VPN/proxy/anti-censorship client.",
    ),
    PERMISSIONS_AND_DECLARATIONS(
        "VPN service declaration",
        "App declares the VPN service, BIND_VPN_SERVICE or a supported transport ABI in its manifest.",
    ),
    TRAFFIC_COUNTER(
        "System VPN accounting",
        "The system reports traffic accounting on a VPN interface owned by this app.",
    ),
    ROUTING_PROFILE(
        "Routing / captive-portal profile",
        "App holds a network routing profile, or is allowed to capture portals on connect.",
    ),
    NAME_AND_DEVELOPER(
        "Name & developer evidence",
        "Visible app name/publisher references VPN, proxy, tunnel or known VPN vendors.",
    ),
}

data class VpnEvidence(
    val method: VpnMethod,
    val detail: String,
    val points: Int,
)

enum class VpnConfidence(val label: String) {
    CONFIRMED("Confirmed"),
    LIKELY("Likely"),
    POSSIBLE("Possible");

    companion object {
        fun of(score: Int): VpnConfidence = when {
            score >= 70 -> CONFIRMED
            score >= 45 -> LIKELY
            else -> POSSIBLE
        }
    }
}

/** Everything the probes could observe about one installed package. */
data class VpnCandidate(
    val packageName: String,
    val label: String,
    val versionName: String? = null,
    val isSystemApp: Boolean = false,
    val installerPackage: String? = null,
    val requestedPermissions: Set<String> = emptySet(),
    val serviceNames: List<String> = emptyList(),
    val nativeLibs: List<String> = emptyList(),
    val activityNames: List<String> = emptyList(),
    val receiverNames: List<String> = emptyList(),
    val declaredVpnIntents: List<String> = emptyList(),
    val hasVpnTransportAbi: Boolean = false,
    /** First transport permission / tunnel library that produced [hasVpnTransportAbi]. */
    val transportEvidence: String? = null,
    val hasCaptivePortalBypassPermission: Boolean = false,
    val systemVpnInterface: String? = null,
    val systemVpnState: String? = null,
    val trafficBytes: Long = 0,
)

data class VpnFinding(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSystemApp: Boolean,
    val score: Int,
    val confidence: VpnConfidence,
    val evidence: List<VpnEvidence>,
    val methods: Set<VpnMethod>,
    val systemVpnInterface: String? = null,
    val systemVpnState: String? = null,
)

data class VpnScanReport(
    val findings: List<VpnFinding>,
    val scannedPackages: Int,
    val durationMs: Long,
    val methodSummary: Map<VpnMethod, Int>,
    val notes: List<String>,
)

/** One package's worth of raw data produced by the Android layer. */
interface VpnProbe {
    /**
     * Collects one [VpnCandidate] per installed package.
     *
     * [system] lets the caller reuse a snapshot it already took, so a scan does not
     * read the network state twice.
     */
    suspend fun collect(system: SystemVpnSnapshot? = null): List<VpnCandidate>

    /** Facts about the current state of the system VPN, shown as context. */
    suspend fun systemVpnState(): SystemVpnSnapshot
}

data class SystemVpnSnapshot(
    val activeInterfaces: List<String> = emptyList(),
    val connectedState: String? = null,
    val usingVpn: Boolean = false,
    /** Package Android is configured to keep connected (always-on VPN), if readable. */
    val alwaysOnPackage: String? = null,
    val alwaysOnLockDown: Boolean = false,
    /** Human readable explanations of what could not be inspected. */
    val limitations: List<String> = emptyList(),
)

/**
 * The detection engine. Pure Kotlin — no Android imports — so that every scoring
 * rule below is covered by JVM unit tests.
 *
 * Design rules:
 *  - A VPN-sounding name alone never reaches CONFIRMED (max 40 points).
 *  - Declaring the VPN service is strong but not conclusive (many security/firewall
 *    suites also declare it), so it is combined with other evidence.
 *  - Evidence is additive with diminishing returns and capped at 100.
 */
class VpnDetector {

    /**
     * @param includeSystemApps when false, pre-installed packages are skipped (the
     *   user can switch this in Settings).
     * @param includePossible when false, only LIKELY / CONFIRMED findings are kept.
     */
    fun analyse(
        candidates: List<VpnCandidate>,
        system: SystemVpnSnapshot,
        includeSystemApps: Boolean = true,
        includePossible: Boolean = true,
    ): VpnScanReport {
        val findings = ArrayList<VpnFinding>(8)
        var withPackageRule = 0
        var withManifestRule = 0
        var withSystemRule = 0
        var withProfileRule = 0
        var withNameRule = 0

        val considered = if (includeSystemApps) candidates else candidates.filterNot { it.isSystemApp }
        for (candidate in considered) {
            val evidence = buildList {
                addAll(packageEvidence(candidate))
                addAll(manifestEvidence(candidate))
                addAll(systemEvidence(candidate))
                addAll(profileEvidence(candidate))
                addAll(routingProfileEvidence(candidate, system))
                addAll(nameEvidence(candidate))
            }.distinctBy { it.method to it.detail }
            if (evidence.isEmpty()) continue

            val methods = evidence.map { it.method }.toSet()
            if (VpnMethod.KNOWN_PACKAGE in methods) withPackageRule++
            if (VpnMethod.PERMISSIONS_AND_DECLARATIONS in methods) withManifestRule++
            if (VpnMethod.TRAFFIC_COUNTER in methods) withSystemRule++
            if (VpnMethod.ROUTING_PROFILE in methods) withProfileRule++
            if (VpnMethod.NAME_AND_DEVELOPER in methods) withNameRule++

            val score = score(evidence, methods)
            val confidence = VpnConfidence.of(score)
            if (!includePossible && confidence == VpnConfidence.POSSIBLE) continue
            findings.add(
                VpnFinding(
                    packageName = candidate.packageName,
                    label = candidate.label,
                    versionName = candidate.versionName,
                    isSystemApp = candidate.isSystemApp,
                    score = score,
                    confidence = confidence,
                    evidence = evidence.sortedByDescending { it.points },
                    methods = methods,
                    systemVpnInterface = candidate.systemVpnInterface,
                    systemVpnState = candidate.systemVpnState,
                ),
            )
        }

        val sorted = findings.sortedWith(
            compareByDescending<VpnFinding> { it.score }.thenBy { it.label.lowercase() },
        )
        return VpnScanReport(
            findings = sorted,
            scannedPackages = considered.size,
            durationMs = 0,
            methodSummary = mapOf(
                VpnMethod.KNOWN_PACKAGE to withPackageRule,
                VpnMethod.PERMISSIONS_AND_DECLARATIONS to withManifestRule,
                VpnMethod.TRAFFIC_COUNTER to withSystemRule,
                VpnMethod.ROUTING_PROFILE to withProfileRule,
                VpnMethod.NAME_AND_DEVELOPER to withNameRule,
            ),
            notes = system.limitations,
        )
    }

    // ------------------------------------------------------------- methods

    /** Method 1: curated database of published VPN / proxy / anti-censorship clients. */
    private fun packageEvidence(c: VpnCandidate): List<VpnEvidence> {
        val pkg = c.packageName.lowercase()
        val hit = VpnSignatures.knownPackages[pkg]
            ?: VpnSignatures.knownPackagePrefixes.entries
                .firstOrNull { (prefix, _) -> pkg.startsWith(prefix) }
                ?.value
        if (hit == null) return emptyList()
        return listOf(
            VpnEvidence(
                method = VpnMethod.KNOWN_PACKAGE,
                detail = "Package id ${c.packageName} is ${hit.vendor}'s ${hit.product}.",
                points = 62,
            ),
        )
    }

    /** Method 2: manifest declarations that only VPN/transport clients carry. */
    private fun manifestEvidence(c: VpnCandidate): List<VpnEvidence> = buildList {
        val perms = c.requestedPermissions
        if ("android.permission.BIND_VPN_SERVICE" in perms) {
            add(
                VpnEvidence(
                    VpnMethod.PERMISSIONS_AND_DECLARATIONS,
                    "Requests android.permission.BIND_VPN_SERVICE (required to establish a VPN tunnel).",
                    45,
                ),
            )
        }
        if (c.hasVpnTransportAbi) {
            val what = c.transportEvidence?.let { "Detected $it: " }.orEmpty()
            add(
                VpnEvidence(
                    VpnMethod.PERMISSIONS_AND_DECLARATIONS,
                    what + "the app ships a proxy/tunnel transport core (Xray/V2Ray, WireGuard, " +
                        "OpenVPN, shadowsocks or Amnezia).",
                    32,
                ),
            )
        }
        val vpnServiceClasses = c.serviceNames.filter { name ->
            val n = name.lowercase()
            VpnSignatures.serviceNameKeywords.any { n.contains(it) }
        }
        if (vpnServiceClasses.isNotEmpty()) {
            add(
                VpnEvidence(
                    VpnMethod.PERMISSIONS_AND_DECLARATIONS,
                    "Declares VPN/tunnel service ${vpnServiceClasses.first().substringAfterLast('.')}.",
                    30,
                ),
            )
        }
        if (c.hasCaptivePortalBypassPermission) {
            add(
                VpnEvidence(
                    VpnMethod.PERMISSIONS_AND_DECLARATIONS,
                    "Holds CAPTIVE_PORTAL_APP: it can be launched by Android when a captive portal is detected.",
                    12,
                ),
            )
        }
    }

    /** Method 3+4: what the *system* reports about a live VPN owned by this app. */
    private fun systemEvidence(c: VpnCandidate): List<VpnEvidence> {
        if (c.systemVpnInterface == null) return emptyList()
        return listOf(
            VpnEvidence(
                method = VpnMethod.TRAFFIC_COUNTER,
                detail = "System VPN accounting reports ${formatBytes(c.trafficBytes)} on " +
                    "${c.systemVpnInterface} for this app (state: ${c.systemVpnState ?: "unknown"}).",
                points = 34,
            ),
        )
    }

    /** Method 5: routing profiles / captive-portal capture rights. */
    private fun profileEvidence(c: VpnCandidate): List<VpnEvidence> = buildList {
        if (c.systemVpnInterface != null && c.hasVpnTransportAbi) {
            add(
                VpnEvidence(
                    VpnMethod.ROUTING_PROFILE,
                    "Routing profile: a system VPN interface (${c.systemVpnInterface}) is up while this " +
                        "app supplies the transport, so the tunnel is very likely routed through it.",
                    22,
                ),
            )
        }
        val libs = c.nativeLibs.filter { lib -> VpnSignatures.nativeLibKeywords.any { lib.contains(it) } }
        if (libs.isNotEmpty()) {
            add(
                VpnEvidence(
                    VpnMethod.ROUTING_PROFILE,
                    "Ships tunnel engine ${libs.first()} (proxy/tunnel core).",
                    26,
                ),
            )
        }
    }

    /**
     * Method 4 (system side): Android's own always-on / lock-down VPN configuration.
     * When the configured always-on package is this app, that is a routing-profile fact,
     * not an inference.
     */
    private fun routingProfileEvidence(c: VpnCandidate, system: SystemVpnSnapshot): List<VpnEvidence> {
        val configured = system.alwaysOnPackage ?: return emptyList()
        if (!configured.equals(c.packageName, ignoreCase = true)) return emptyList()
        val lockDown = if (system.alwaysOnLockDown) " with lock-down enabled" else ""
        return listOf(
            VpnEvidence(
                VpnMethod.ROUTING_PROFILE,
                "Android is configured to always keep this app's VPN active$lockDown " +
                    "(Settings > Network > VPN > always-on VPN).",
                30,
            ),
        )
    }

    /** Method 6: name + developer evidence (supporting only). */
    private fun nameEvidence(c: VpnCandidate): List<VpnEvidence> = buildList {
        val label = TextNormalizer.normalize(c.label)
        val keyword = VpnSignatures.nameKeywords.firstOrNull { k -> label.contains(k) }
        if (keyword != null) {
            add(
                VpnEvidence(
                    VpnMethod.NAME_AND_DEVELOPER,
                    "Application name \"${c.label}\" contains \"$keyword\".",
                    26,
                ),
            )
        }
        val vendor = VpnSignatures.vendors.firstOrNull { v ->
            label.contains(v) || c.packageName.lowercase().contains(v.replace(" ", ""))
        }
        if (vendor != null && keyword == null) {
            add(
                VpnEvidence(
                    VpnMethod.NAME_AND_DEVELOPER,
                    "Application references known VPN vendor \"$vendor\".",
                    22,
                ),
            )
        }
        // Declared VPN intents (e.g. a client exposing a ".tunnel" deep link).
        c.declaredVpnIntents.firstOrNull()?.let { intent ->
            add(
                VpnEvidence(
                    VpnMethod.NAME_AND_DEVELOPER,
                    "Exposes VPN deep link $intent.",
                    14,
                ),
            )
        }
    }

    // ------------------------------------------------------------- scoring

    private fun score(evidence: List<VpnEvidence>, methods: Set<VpnMethod>): Int {
        var total = evidence.sumOf { it.points }

        // Diminishing returns: additional evidence beyond the two strongest items
        // contributes half, so several vague hints cannot fake a confirmation.
        if (evidence.size > 2) {
            val extras = evidence.sortedByDescending { it.points }.drop(2)
            total -= (extras.sumOf { it.points }) / 2
        }

        // A name-only finding can never be reported as confirmed.
        if (methods == setOf(VpnMethod.NAME_AND_DEVELOPER)) total = minOf(total, 40)

        // Agreement of two independent methods is worth a small bonus.
        if (methods.size >= 2) total += 6
        if (methods.size >= 3) total += 4

        return total.coerceIn(0, 100)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes <= 0 -> "no measurable"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
    }
}

/**
 * Static signature data. Package ids are limited to entries that are publicly
 * documented (project repositories / store listings); the list is intentionally
 * conservative and extensible — unknown brands are still caught by the manifest and
 * system methods.
 */
object VpnSignatures {

    data class Vendor(val vendor: String, val product: String)

    val knownPackages: Map<String, Vendor> = mapOf(
        // ------------------------------------------------------------------
        // Iranian / Iran-focused anti-censorship clients. Only ids published by
        // the project itself (GitHub releases, project docs) are listed here.
        // ------------------------------------------------------------------
        "com.hiddify.hiddify" to Vendor("Hiddify", "Hiddify"),
        "app.hiddify.com" to Vendor("Hiddify", "Hiddify"),
        "io.github.hiddify.hiddify" to Vendor("Hiddify", "Hiddify"),
        "com.hiddify.app" to Vendor("Hiddify", "Hiddify"),
        "org.nekobox.nekobox" to Vendor("NekoBox project", "NekoBox"),
        "io.nekohasekai.neko" to Vendor("NekoBox project", "NekoBox"),
        "moe.nb4a" to Vendor("NekoBox project", "NekoBox for Android"),
        "com.nekoray.nekoray" to Vendor("NekoRay", "NekoRay"),
        "io.github.nekohasekai.sfa" to Vendor("SagerNet", "SFA"),
        "dev.happproxy.happ" to Vendor("Happ", "Happ"),
        "com.lantern.lantern" to Vendor("Lantern", "Lantern"),
        "org.getlantern.lantern" to Vendor("Lantern", "Lantern"),
        "com.getlantern.lantern" to Vendor("Lantern", "Lantern"),
        "org.torproject.torbrowser" to Vendor("Tor Project", "Tor Browser"),
        "org.greatfire.tor" to Vendor("Tor Project", "Orbot (GreatFire)"),
        "com.github.eycorsican.v2ray" to Vendor("v2ray community", "V2Ray client"),
        "io.github.shecan" to Vendor("Shecan", "Shecan DNS"),
        "ir.shecan.android" to Vendor("Shecan", "Shecan DNS"),
        // Bitmask (Iranian anti-censorship, open source)
        "org.bitmask.client" to Vendor("LEAP Encryption Access Project", "Bitmask"),
        // Psiphon (open source client)
        "com.psiphon3" to Vendor("Psiphon Inc.", "Psiphon Pro"),
        "com.psiphon3.subscription" to Vendor("Psiphon Inc.", "Psiphon"),
        // Outline / Shadowsocks ecosystem
        "org.outline.android.client" to Vendor("Jigsaw (Google)", "Outline"),
        "com.github.shadowsocks" to Vendor("Shadowsocks project", "Shadowsocks"),
        "com.github.shadowsocks.tv" to Vendor("Shadowsocks project", "Shadowsocks TV"),
        // WireGuard official client
        "com.wireguard.android" to Vendor("WireGuard LLC", "WireGuard"),
        // OpenVPN official clients
        "net.openvpn.openvpn" to Vendor("OpenVPN Inc.", "OpenVPN Connect"),
        "de.blinkt.openvpn" to Vendor("Arne Schwabe", "OpenVPN for Android"),
        // V2Ray / Xray generic clients
        "com.v2ray.ang" to Vendor("v2rayNG project", "v2rayNG"),
        "io.nekohasekai.sagernet" to Vendor("SagerNet", "SagerNet"),
        "io.nekohasekai.sfa" to Vendor("SagerNet", "SFA"),
        "fr.husi" to Vendor("Husi project", "Husi"),
        "com.happproxy" to Vendor("Happ", "Happ Proxy"),
        // Amnezia (popular in Iran/Russia)
        "org.amnezia.vpn" to Vendor("Amnezia", "AmneziaVPN"),
        // Tailscale / ZeroTier (mesh VPN, still VPN)
        "com.tailscale.ipn" to Vendor("Tailscale Inc.", "Tailscale"),
        "com.zerotier.one" to Vendor("ZeroTier Inc.", "ZeroTier One"),
        "com.cloudflare.onedotonedotonedotone" to Vendor("Cloudflare", "1.1.1.1 + WARP"),
        // Proton / Mullvad / NordVPN / Surfshark / ExpressVPN / Astrill (commercial)
        "ch.protonvpn.android" to Vendor("Proton AG", "Proton VPN"),
        "net.mullvad.mullvadvpn" to Vendor("Mullvad VPN AB", "Mullvad VPN"),
        "com.nordvpn.android" to Vendor("Nord Security", "NordVPN"),
        "com.napsternetlabs.napsternetv" to Vendor("NapsternetV", "NapsternetV"),
        "com.napsternetv.app" to Vendor("NapsternetV", "NapsternetV"),
        "com.fast.free.unblock.secure.vpn" to Vendor("Fast VPN", "Fast Secure VPN"),
        "com.vpn.proxy.master.free" to Vendor("Free VPN", "Free VPN Proxy"),
        "com.purevpn.mobile" to Vendor("PureVPN", "PureVPN"),
        "com.ipvanish.android" to Vendor("IPvanish", "IPVanish"),
        "com.cyberghostvpn.cyberghostvpn" to Vendor("CyberGhost", "CyberGhost VPN"),
        "com.tunnelbear.android" to Vendor("TunnelBear", "TunnelBear"),
        "hotspotshield.android.vpn" to Vendor("Aura", "Hotspot Shield"),
        "com.hotspotshield.vpn" to Vendor("Aura", "Hotspot Shield"),
        "com.avast.android.vpn" to Vendor("Avast", "Avast SecureLine VPN"),
        "com.kaspersky.secureconnection" to Vendor("Kaspersky", "Kaspersky Secure Connection"),
        "com.hola.vpn" to Vendor("Hola", "Hola VPN"),
        "org.xvpn" to Vendor("XVPN project", "XVPN"),
        "com.xvpn.client" to Vendor("XVPN", "XVPN"),
        "com.freevpn.securevpn" to Vendor("FreeVPN", "FreeVPN"),
        "com.surfshark.vpnclient.android" to Vendor("Surfshark", "Surfshark VPN"),
        "com.expressvpn.vpn" to Vendor("ExpressVPN", "ExpressVPN"),
        "com.astrill.vpn" to Vendor("Astrill Systems", "Astrill VPN"),
        "com.privateinternetaccess.android" to Vendor("Kape Technologies", "PIA VPN"),
        "com.windscribe.vpn" to Vendor("Windscribe", "Windscribe VPN"),
        "org.torproject.android" to Vendor("Tor Project", "Orbot"),
        "net.i2p.android.router" to Vendor("The Invisible Internet Project", "I2P"),
    )

    /** Family ids where every package under the namespace is VPN related. */
    val knownPackagePrefixes: Map<String, Vendor> = mapOf(
        "com.hiddify." to Vendor("Hiddify", "Hiddify"),
        "app.hiddify." to Vendor("Hiddify", "Hiddify"),
        "io.nekohasekai." to Vendor("SagerNet / NekoBox", "Neko client"),
        "moe.nb4a" to Vendor("NekoBox project", "NekoBox for Android"),
        "org.getlantern." to Vendor("Lantern", "Lantern"),
        "com.getlantern." to Vendor("Lantern", "Lantern"),
        "org.nekoray." to Vendor("NekoRay", "NekoRay"),
        "ir.shecan." to Vendor("Shecan", "Shecan DNS"),
        "com.wireguard.android" to Vendor("WireGuard LLC", "WireGuard"),
        "com.napsternetlabs." to Vendor("NapsternetV", "NapsternetV"),
        "com.napsternetv." to Vendor("NapsternetV", "NapsternetV"),
        "com.wireguard." to Vendor("WireGuard LLC", "WireGuard"),
        "org.amnezia." to Vendor("Amnezia", "AmneziaVPN"),
        "ch.protonvpn." to Vendor("Proton AG", "Proton VPN"),
        "com.v2ray." to Vendor("v2rayNG project", "v2rayNG"),
        "io.nekohasekai." to Vendor("SagerNet", "SagerNet"),
        "org.torproject." to Vendor("Tor Project", "Tor client"),
        "com.github.shadowsocks" to Vendor("Shadowsocks project", "Shadowsocks"),
        "org.outline." to Vendor("Jigsaw (Google)", "Outline"),
        "net.mullvad." to Vendor("Mullvad VPN AB", "Mullvad VPN"),
    )

    val serviceNameKeywords = listOf(
        "vpns", "vpnservice", "tunnel", "openvpns", "wireguard", "proxy", "xray", "v2ray",
    )

    /** Permissions of VPN transport engines (Xray, WireGuard, OpenVPN, Amnezia…). */
    val transportPermissions = listOf(
        ".VPN_SERVICE", "BIND_VPN_SERVICE", "android.permission.QUERY_ALL_PACKAGES_VPN",
        "PERMISSION_VPN", ".TUNNEL_SERVICE",
    )

    val nativeLibKeywords = listOf(
        "wg-go", "libwg", "openvpn", "xray", "v2ray", "amnezia", "hev-sock", "libgojni",
        "libtun", "shadowsocks", "wireguard-go",
    )

    val nameKeywords = listOf(
        "vpn", "proxy", "prox", "tunnel", "tor ", "shadowsocks", "wireguard", "openvpn",
        "v2ray", "xray", "vless", "vmess", "trojan", "sing-box", "singbox", "hiddify",
        "nekobox", "nekoray", "sagernet", "shadowsocks", "outline", "psiphon", "shecan",
        "shecanvpn", "هایدیفای", "فیلترشکن", "فیلتر شکن", "ضد فیلتر", "ضدفیلتر", "پروکسی",
        "تانل", "تونل", "اینترنت آزاد", "آنتی فیلتر", "antifilter",
    ).filterNot { it.length < 3 }

    val vendors = listOf(
        "psiphon", "outline", "shadowsocks", "wireguard", "openvpn", "proton", "mullvad",
        "nordvpn", "surfshark", "expressvpn", "astrill", "windscribe", "amnezia", "tunnelbear",
        "hiddify", "nekobox", "sagernet", "v2rayng", "happ", "bitmask", "lantern", "hotspot shield",
        "shecan", "4931", "electro", "kharazmi",
    )

    /** Package-private helper used by tests to validate the curated list. */
    fun isKnown(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return knownPackages.containsKey(pkg) || knownPackagePrefixes.keys.any { pkg.startsWith(it) }
    }
}
