package ir.irandetector.app

import ir.irandetector.app.data.KnownApp
import ir.irandetector.app.data.Verification
import ir.irandetector.app.detection.DetectionEngine
import ir.irandetector.app.detection.DetectionIndex
import ir.irandetector.app.domain.ConfidenceTier
import ir.irandetector.app.domain.InstalledApp
import ir.irandetector.app.domain.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Detection scoring tests. These exercise the real engine (no mocks) so the
 * published confidence numbers are the ones the app will actually show.
 */
class DetectionEngineTest {

    private lateinit var engine: DetectionEngine
    private lateinit var database: List<KnownApp>
    private lateinit var index: DetectionIndex

    // A trimmed version of the shipped dataset with a certificate for Divar.
    private val divar = KnownApp(
        id = 1,
        packageName = "ir.divar",
        nameEn = "Divar",
        nameFa = "دیوار",
        developer = "Divar (Congenial Mobile)",
        website = "https://divar.ir",
        domains = listOf("divar.ir"),
        certificateSha1 = listOf(DIVAR_SHA1),
        category = "SHOPPING",
        verification = Verification.VERIFIED,
    )

    private val digikala = KnownApp(
        id = 2,
        packageName = "com.digikala",
        nameEn = "Digikala",
        nameFa = "دیجی‌کالا",
        nameAliases = listOf("دیجی کالا"),
        developer = "Digikala Group",
        domains = listOf("digikala.com"),
        category = "SHOPPING",
        verification = Verification.VERIFIED,
    )

    private val snapp = KnownApp(
        id = 3,
        packageName = "cab.snapp.passenger",
        alternativePackages = listOf("cab.snapp.passenger.play"),
        nameEn = "Snapp",
        nameFa = "اسنپ",
        developer = "Snapp Group",
        domains = listOf("snapp.ir"),
        verification = Verification.VERIFIED,
    )

    @Before
    fun setUp() {
        engine = DetectionEngine()
        database = listOf(divar, digikala, snapp)
        index = DetectionIndex.build(database)
    }

    private fun app(
        packageName: String,
        label: String,
        installer: String? = "com.android.vending",
        sha1: List<String> = listOf("00:11:22"),
        system: Boolean = false,
    ) = InstalledApp(
        packageName = packageName,
        label = label,
        versionName = "1.0",
        versionCode = 1,
        firstInstallTime = 1_600_000_000_000,
        lastUpdateTime = 1_600_000_000_000,
        installerPackage = installer,
        isSystemApp = system,
        isUpdatedSystemApp = false,
        targetSdk = 34,
        isEnabled = true,
        signatureSha256 = emptyList(),
        signatureSha1 = sha1,
        candidateInstallers = emptyList(),
        permissionCount = 3,
        category = null,
    )

    @Test
    fun `exact package match without certificate is verified`() {
        val assessment = engine.detect(app("com.digikala", "دیجی‌کالا"), database, index)
        assertEquals(ConfidenceTier.VERIFIED, assessment.tier)
        assertTrue(assessment.score >= 80)
        assertTrue(assessment.signals.any { it.type == SignalType.PACKAGE_EXACT })
        assertEquals("com.digikala", assessment.matchedRecord?.packageName)
    }

    @Test
    fun `exact package match alone is still enough for verified`() {
        // Even with a totally unrelated label and no certificate.
        val assessment = engine.detect(app("com.digikala", "Zzz", installer = null), database, index)
        assertTrue(assessment.score >= 80)
        assertEquals(ConfidenceTier.VERIFIED, assessment.tier)
    }

    @Test
    fun `alternative package id matches with slight penalty`() {
        val assessment = engine.detect(app("cab.snapp.passenger.play", "Snapp"), database, index)
        assertTrue(assessment.signals.any { it.type == SignalType.PACKAGE_ALTERNATIVE })
        assertTrue(assessment.score >= 80)
        assertEquals("cab.snapp.passenger", assessment.matchedRecord?.packageName)
    }

    @Test
    fun `certificate match on an unknown package is capped below verified`() {
        val assessment = engine.detect(
            app("com.some.clone.app", "Random App", sha1 = listOf(DIVAR_SHA1)),
            database,
            index,
        )
        assertTrue(assessment.signals.any { it.type == SignalType.CERTIFICATE })
        assertTrue("score=${assessment.score}", assessment.score <= 72)
        assertTrue(assessment.tier != ConfidenceTier.VERIFIED)
    }

    @Test
    fun `persian name only match is uncertain`() {
        val assessment = engine.detect(app("com.example.unknown", "دیجی‌کالا"), database, index)
        assertTrue(assessment.signals.any { it.type == SignalType.NAME_FA })
        assertEquals(ConfidenceTier.UNCERTAIN, assessment.tier)
    }

    @Test
    fun `unrelated app produces no result`() {
        val assessment = engine.detect(app("com.spotify.music", "Spotify"), database, index)
        assertEquals(0, assessment.score)
        assertTrue(assessment.signals.isEmpty())
        assertNull(assessment.matchedRecord)
        assertFalse(engine.toResult(app("com.spotify.music", "Spotify"), assessment).isDetected)
    }

    @Test
    fun `ir prefix alone is never evidence`() {
        // The brief explicitly forbids treating an `ir.` prefix as proof.
        val assessment = engine.detect(app("ir.something.random", "Random"), database, index)
        assertEquals(0, assessment.score)
        assertTrue(assessment.signals.isEmpty())
    }

    @Test
    fun `persian language alone is never evidence`() {
        val assessment = engine.detect(
            app("com.example.notiran", "برنامه نمونه", installer = null),
            database,
            index,
        )
        assertEquals(0, assessment.score)
    }

    @Test
    fun `iranian installer adds supporting points only`() {
        val withBazaar = engine.detect(
            app("com.example.unknown", "Some App", installer = "com.farsitel.bazaar"),
            database,
            index,
        )
        assertTrue(withBazaar.signals.any { it.type == SignalType.INSTALLER })
        // A single weak signal stays uncertain.
        assertEquals(ConfidenceTier.UNCERTAIN, withBazaar.tier)
    }

    @Test
    fun `null installer is not evidence`() {
        val assessment = engine.detect(app("com.example.unknown", "Some App", installer = null), database, index)
        assertEquals(0, assessment.score)
    }

    @Test
    fun `fuzzy name match catches small typos`() {
        val assessment = engine.detect(app("org.unknown.digi", "Digikalaa"), database, index)
        assertTrue(
            assessment.signals.any { it.type == SignalType.NAME_FUZZY || it.type == SignalType.NAME_EN },
        )
        assertTrue(assessment.score in 1..59)
    }

    @Test
    fun `package namespace of a known publisher is detected`() {
        // com.digikala.subapp shares the namespace but is not in the database.
        val assessment = engine.detect(app("com.digikala.reader", "Reader"), database, index)
        assertTrue(
            assessment.signals.any { it.type == SignalType.PACKAGE_NAMESPACE },
        )
    }

    @Test
    fun `scores never exceed one hundred`() {
        val richDb = listOf(
            digikala.copy(
                certificateSha1 = listOf(DIVAR_SHA1),
                nameAliases = listOf("Digikala", "دیجی کالا"),
            ),
        )
        val richIndex = DetectionIndex.build(richDb)
        val assessment = engine.detect(
            app("com.digikala", "Digikala", installer = "com.farsitel.bazaar", sha1 = listOf(DIVAR_SHA1)),
            richDb,
            richIndex,
        )
        assertTrue(assessment.score <= 100)
    }

    @Test
    fun `uncertain database record cannot produce verified tier`() {
        val weak = digikala.copy(verification = Verification.UNCERTAIN)
        val weakDb = listOf(weak)
        val weakIndex = DetectionIndex.build(weakDb)
        val assessment = engine.detect(app("com.digikala", "Digikala"), weakDb, weakIndex)
        val result = engine.toResult(app("com.digikala", "Digikala"), assessment)
        assertTrue(result.tier != ConfidenceTier.VERIFIED)
    }

    @Test
    fun `explanation strings always reference real data`() {
        val assessment = engine.detect(app("com.digikala", "دیجی‌کالا"), database, index)
        assertTrue(assessment.signals.isNotEmpty())
        assessment.signals.forEach { signal ->
            assertTrue("empty explanation for ${signal.type}", signal.explanation.isNotBlank())
            assertTrue(signal.points > 0)
        }
    }

    @Test
    fun `empty database returns nothing`() {
        val assessment = engine.detect(app("com.digikala", "Digikala"), emptyList(), DetectionIndex.build(emptyList()))
        assertEquals(0, assessment.score)
    }

    @Test
    fun `duplicate records do not double count`() {
        val duplicated = listOf(digikala, digikala.copy(id = 99))
        val duplicatedIndex = DetectionIndex.build(duplicated)
        val assessment = engine.detect(app("com.digikala", "Digikala"), duplicated, duplicatedIndex)
        assertTrue(assessment.score <= 100)
        // Only one package signal is emitted even though two records match.
        assertEquals(1, assessment.signals.count { it.type == SignalType.PACKAGE_EXACT })
    }

    @Test
    fun `index maps every package variant`() {
        assertEquals(4, index.byPackage.size) // 3 records + 1 alternative id
        assertEquals(divar, index.byPackage["ir.divar"])
        assertEquals(snapp, index.byPackage["cab.snapp.passenger.play"])
    }

    companion object {
        const val DIVAR_SHA1 = "E5:7F:7C:10:FD:02:21:F0:F1:04:8B:7B:7F:79:E8:17:7D:16:FC:BE"
    }
}
