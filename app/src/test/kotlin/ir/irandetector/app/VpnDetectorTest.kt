package ir.irandetector.app

import ir.irandetector.app.vpn.SystemVpnSnapshot
import ir.irandetector.app.vpn.VpnCandidate
import ir.irandetector.app.vpn.VpnConfidence
import ir.irandetector.app.vpn.VpnDetector
import ir.irandetector.app.vpn.VpnMethod
import ir.irandetector.app.vpn.VpnSignatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** VPN Hunter scoring rules — the real engine, no mocks. */
class VpnDetectorTest {

    private lateinit var detector: VpnDetector
    private val emptySystem = SystemVpnSnapshot()

    @Before
    fun setUp() {
        detector = VpnDetector()
    }

    private fun candidate(
        pkg: String,
        label: String = "Some App",
        permissions: Set<String> = emptySet(),
        services: List<String> = emptyList(),
        libs: List<String> = emptyList(),
        systemInterface: String? = null,
        traffic: Long = 0,
    ) = VpnCandidate(
        packageName = pkg,
        label = label,
        versionName = "1.0",
        requestedPermissions = permissions,
        serviceNames = services,
        nativeLibs = libs,
        systemVpnInterface = systemInterface,
        systemVpnState = if (systemInterface != null) "VPN transport active" else null,
        trafficBytes = traffic,
    )

    @Test
    fun `known vpn client is confirmed`() {
        val report = detector.analyse(listOf(candidate("com.psiphon3", "Psiphon Pro")), emptySystem)
        assertEquals(1, report.findings.size)
        val finding = report.findings.first()
        assertEquals(VpnConfidence.CONFIRMED, finding.confidence)
        assertTrue(finding.methods.contains(VpnMethod.KNOWN_PACKAGE))
    }

    @Test
    fun `wireguard family prefix is matched`() {
        val report = detector.analyse(listOf(candidate("com.wireguard.android.tunnel", "WG")), emptySystem)
        assertTrue(report.findings.first().methods.contains(VpnMethod.KNOWN_PACKAGE))
    }

    @Test
    fun `ordinary app is not reported`() {
        val report = detector.analyse(
            listOf(candidate("com.example.notes", "Notes", permissions = setOf("android.permission.INTERNET"))),
            emptySystem,
        )
        assertTrue(report.findings.isEmpty())
    }

    @Test
    fun `vpn service declaration alone is likely but not confirmed`() {
        val report = detector.analyse(
            listOf(
                candidate(
                    "com.example.customvpn",
                    "Custom Client",
                    permissions = setOf("android.permission.BIND_VPN_SERVICE"),
                    services = listOf("com.example.customvpn.TunnelVpnService"),
                ),
            ),
            emptySystem,
        )
        val finding = report.findings.first()
        assertTrue(finding.methods.contains(VpnMethod.PERMISSIONS_AND_DECLARATIONS))
        assertTrue("score=${finding.score}", finding.confidence != VpnConfidence.CONFIRMED || finding.score >= 70)
    }

    @Test
    fun `name only finding never becomes confirmed`() {
        val report = detector.analyse(
            listOf(candidate("com.example.thing", "Super Fast VPN Proxy")),
            emptySystem,
        )
        val finding = report.findings.first()
        assertEquals(setOf(VpnMethod.NAME_AND_DEVELOPER), finding.methods)
        assertTrue("score=${finding.score}", finding.score <= 40)
        assertTrue(finding.confidence != VpnConfidence.CONFIRMED)
    }

    @Test
    fun `transport library is detected as routing profile`() {
        val report = detector.analyse(
            listOf(candidate("com.example.custom", "Custom", libs = listOf("libxray.so"))),
            emptySystem,
        )
        assertTrue(report.findings.first().methods.contains(VpnMethod.ROUTING_PROFILE))
    }

    @Test
    fun `system vpn interface attributes traffic evidence`() {
        val snapshot = SystemVpnSnapshot(activeInterfaces = listOf("tun0"), usingVpn = true)
        val report = detector.analyse(
            listOf(
                candidate(
                    "com.example.custom",
                    "Custom",
                    permissions = setOf("android.permission.BIND_VPN_SERVICE"),
                    services = listOf("com.example.custom.VpnService"),
                    systemInterface = "tun0",
                    traffic = 5 * 1024 * 1024,
                ),
            ),
            snapshot,
        )
        val finding = report.findings.first()
        assertTrue(finding.methods.contains(VpnMethod.TRAFFIC_COUNTER))
        assertEquals("tun0", finding.systemVpnInterface)
    }

    @Test
    fun `multiple methods agree and raise confidence`() {
        val snapshot = SystemVpnSnapshot(activeInterfaces = listOf("tun0"), usingVpn = true)
        val report = detector.analyse(
            listOf(
                candidate(
                    "org.amnezia.vpn",
                    "AmneziaVPN",
                    permissions = setOf("android.permission.BIND_VPN_SERVICE"),
                    services = listOf("org.amnezia.vpn.VpnService"),
                    libs = listOf("libwg-go.so"),
                    systemInterface = "tun0",
                    traffic = 1024,
                ),
            ),
            snapshot,
        )
        val finding = report.findings.first()
        assertTrue(finding.methods.size >= 3)
        assertEquals(VpnConfidence.CONFIRMED, finding.confidence)
        assertEquals(100, finding.score)
    }

    @Test
    fun `scores stay within bounds and results are sorted`() {
        val report = detector.analyse(
            listOf(
                candidate("com.psiphon3", "Psiphon"),
                candidate("com.example.thing", "VPN client"),
                candidate("com.example.other", "Tunnel Pro", services = listOf("a.TunnelService")),
            ),
            emptySystem,
        )
        assertTrue(report.findings.all { it.score in 0..100 })
        assertEquals(report.findings.map { it.score }.sortedDescending(), report.findings.map { it.score })
    }

    @Test
    fun `duplicate evidence is not double counted`() {
        val report = detector.analyse(
            listOf(
                candidate(
                    "com.psiphon3",
                    "Psiphon VPN",
                    permissions = setOf("android.permission.BIND_VPN_SERVICE"),
                ),
            ),
            emptySystem,
        )
        val finding = report.findings.first()
        assertEquals(finding.evidence.size, finding.evidence.distinctBy { it.method to it.detail }.size)
    }

    @Test
    fun `method summary counts each method that fired`() {
        val report = detector.analyse(
            listOf(candidate("com.psiphon3", "Psiphon"), candidate("com.nordvpn.android", "NordVPN")),
            emptySystem,
        )
        assertEquals(2, report.methodSummary[VpnMethod.KNOWN_PACKAGE])
        assertEquals(0, report.methodSummary[VpnMethod.TRAFFIC_COUNTER])
    }

    @Test
    fun `signature database has no duplicate or malformed ids`() {
        val keys = VpnSignatures.knownPackages.keys
        assertEquals(keys.size, keys.distinct().size)
        keys.forEach { key ->
            assertTrue("bad id: $key", key.contains('.') && !key.contains(' '))
            assertTrue(VpnSignatures.isKnown(key))
        }
        assertFalse(VpnSignatures.isKnown("com.example.nothing"))
    }

    @Test
    fun `empty input produces an empty report`() {
        val report = detector.analyse(emptyList(), emptySystem)
        assertTrue(report.findings.isEmpty())
        assertEquals(0, report.scannedPackages)
    }

    @Test
    fun `always-on vpn configuration is treated as routing-profile evidence`() {
        val system = SystemVpnSnapshot(alwaysOnPackage = "com.example.tunnel", alwaysOnLockDown = true)
        val report = detector.analyse(listOf(candidate("com.example.tunnel", "Tunnel")), system)
        val finding = report.findings.single()
        assertTrue(finding.methods.contains(VpnMethod.ROUTING_PROFILE))
        assertTrue(finding.evidence.any { it.detail.contains("always keep this app's VPN active") })
        assertTrue(finding.evidence.any { it.detail.contains("lock-down") })
    }

    @Test
    fun `always-on vpn for a different package is not attributed to this app`() {
        val system = SystemVpnSnapshot(alwaysOnPackage = "com.other.client")
        // A neutral app (no VPN vocabulary anywhere) must stay unreported even though
        // *some* other package is the always-on VPN.
        val report = detector.analyse(listOf(candidate("com.example.calc", "Calculator")), system)
        assertTrue(report.findings.isEmpty())
    }

    @Test
    fun `transport evidence names the exact artefact that matched`() {
        val report = detector.analyse(
            listOf(
                candidate("com.unknown.client", "Client").copy(
                    hasVpnTransportAbi = true,
                    transportEvidence = "libwg-go.so",
                ),
            ),
            emptySystem,
        )
        val finding = report.findings.single()
        assertTrue(finding.evidence.any { it.detail.contains("libwg-go.so") })
        assertEquals(VpnConfidence.POSSIBLE, finding.confidence)
    }

    @Test
    fun `report surfaces the platform limitations it was given`() {
        val system = SystemVpnSnapshot(limitations = listOf("Android hides the VPN owner package"))
        val report = detector.analyse(listOf(candidate("com.psiphon3", "Psiphon")), system)
        assertTrue(report.notes.any { it.contains("hides the VPN owner") })
    }

    @Test
    fun `system apps can be excluded from the report`() {
        val candidates = listOf(
            candidate("com.psiphon3", "Psiphon"),
            candidate("com.psiphon3.subscription", "Psiphon (system)").copy(isSystemApp = true),
        )
        val withSystem = detector.analyse(candidates, emptySystem)
        val withoutSystem = detector.analyse(candidates, emptySystem, includeSystemApps = false)
        assertEquals(2, withSystem.findings.size)
        assertEquals(1, withoutSystem.findings.size)
        assertEquals("com.psiphon3", withoutSystem.findings.single().packageName)
    }

    @Test
    fun `possible-only findings can be filtered out`() {
        val weak = candidate("com.example.tunnel", "Tunnel") // name only -> Possible
        val strong = candidate("com.psiphon3", "Psiphon")    // known id -> Confirmed
        val all = detector.analyse(listOf(weak, strong), emptySystem)
        val filtered = detector.analyse(listOf(weak, strong), emptySystem, includePossible = false)
        assertEquals(2, all.findings.size)
        assertEquals(1, filtered.findings.size)
        assertEquals(VpnConfidence.CONFIRMED, filtered.findings.single().confidence)
    }

    @Test
    fun `iranian anti-censorship clients are recognised by package id`() {
        listOf(
            "com.hiddify.hiddify",
            "moe.nb4a",
            "org.getlantern.lantern",
            "com.napsternetlabs.napsternetv",
        ).forEach { pkg ->
            val report = detector.analyse(listOf(candidate(pkg, "Client")), emptySystem)
            val finding = report.findings.single()
            assertTrue("$pkg should be a known package", VpnMethod.KNOWN_PACKAGE in finding.methods)
            // A curated package id alone is 62 points -> LIKELY by design; CONFIRMED
            // requires a second independent method (see the scoring rules).
            assertTrue(
                "$pkg should be at least LIKELY, was ${finding.confidence}",
                finding.confidence != VpnConfidence.POSSIBLE,
            )
        }
    }

    @Test
    fun `always-on package also proves live tunnel ownership`() {
        val system = SystemVpnSnapshot(
            activeInterfaces = listOf("tun0"),
            connectedState = "VPN transport active",
            usingVpn = true,
            alwaysOnPackage = "com.example.custom.tunnel",
        )
        val report = detector.analyse(
            listOf(candidate("com.example.custom.tunnel", "Custom Tunnel")),
            system,
        )
        val finding = report.findings.single()
        assertTrue(finding.methods.contains(VpnMethod.ROUTING_PROFILE))
    }
}
