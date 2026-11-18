package ir.irandetector.app

import ir.irandetector.app.data.KnownApp
import ir.irandetector.app.detection.DetectionEngine
import ir.irandetector.app.detection.DetectionIndex
import ir.irandetector.app.detection.IconHash
import ir.irandetector.app.domain.InstalledApp
import ir.irandetector.app.domain.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Perceptual icon hashing (pure Kotlin) and its role as a *supporting* signal. */
class IconHashTest {

    private val side = 16

    private fun solid(color: Int): IntArray = IntArray(side * side) { color }

    private fun gradient(): IntArray = IntArray(side * side) { i ->
        val v = (i * 255) / (side * side - 1)
        0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
    }

    @Test
    fun `identical images produce identical hashes`() {
        val a = IconHash.averageHash(gradient(), side)
        val b = IconHash.averageHash(gradient(), side)
        assertEquals(a, b)
        assertEquals(0, IconHash.distance(a, b))
    }

    @Test
    fun `hash has the documented length`() {
        val hash = IconHash.averageHash(gradient(), side)
        assertEquals(IconHash.HEX_LENGTH, hash.length)
    }

    @Test
    fun `inverted icon is far from the original`() {
        val normal = IconHash.averageHash(gradient(), side)
        val inverted = IntArray(side * side) { i ->
            val v = 255 - ((i * 255) / (side * side - 1))
            0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
        }
        val hash = IconHash.averageHash(inverted, side)
        assertTrue(IconHash.distance(normal, hash) >= IconHash.DISTANCE_MAX)
        assertFalse(IconHash.isPlausible(normal, hash))
    }

    @Test
    fun `uniform image does not explode`() {
        val hash = IconHash.averageHash(solid(0xFF2EE59D.toInt()), side)
        assertEquals(IconHash.HEX_LENGTH, hash.length)
        assertEquals(0, IconHash.distance(hash, hash))
    }

    @Test
    fun `distance handles malformed hashes`() {
        assertEquals(Int.MAX_VALUE, IconHash.distance("zzzz", "0000"))
        // Only non-hex input is invalid; short hex strings are valid numbers.
        assertEquals(2, IconHash.distance("82", "00"))
    }

    @Test
    fun `engine never fires an icon signal without a database hash`() {
        val engine = DetectionEngine()
        val record = KnownApp(
            id = 1,
            packageName = "com.unknown.pkg",
            nameEn = "Unknown",
            nameFa = "",
            developer = "Unknown Dev",
            iconHash = null,
        )
        val index = DetectionIndex.build(listOf(record))
        val assessment = engine.detect(app("com.unknown.pkg", "Unknown"), listOf(record), index, "0123456789abcdef")
        assertTrue(assessment.signals.none { it.type == SignalType.ICON })
    }

    @Test
    fun `matching icon hash contributes supporting points`() {
        val engine = DetectionEngine()
        val reference = IconHash.averageHash(gradient(), side)
        val record = KnownApp(
            id = 1,
            packageName = "org.some.iranian",
            nameEn = "Some Iranian App",
            nameFa = "",
            developer = "Some Dev",
            iconHash = reference,
        )
        val index = DetectionIndex.build(listOf(record))
        // Different package id: the icon is the *only* evidence available.
        val assessment = engine.detect(
            app("com.totally.unrelated", "Totally Different Label"),
            listOf(record),
            index,
            reference,
        )
        assertTrue(assessment.signals.any { it.type == SignalType.ICON })
        // A single icon match can never leave the "uncertain" band.
        assertEquals(1, assessment.signals.size)
        assertTrue(assessment.score <= 34)
    }

    private fun app(pkg: String, label: String) = InstalledApp(
        packageName = pkg,
        label = label,
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
}
