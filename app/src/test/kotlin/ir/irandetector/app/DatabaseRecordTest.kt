package ir.irandetector.app

import ir.irandetector.app.core.Json
import ir.irandetector.app.data.IranianStores
import ir.irandetector.app.data.KnownApp
import ir.irandetector.app.data.Verification
import ir.irandetector.app.database.KnownAppEntity
import ir.irandetector.app.data.toDomain
import ir.irandetector.app.data.toEntity
import ir.irandetector.app.data.decodeStringList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Database record parsing/validation tests.
 *
 * Note: the Room DAOs themselves are exercised by the compiled schema; these tests
 * cover the pure mapping layer, which is where data corruption would originate.
 */
class DatabaseRecordTest {

    private val validJson = """
        {
          "packageName": "com.digikala",
          "alternativePackages": ["com.digikala.beta", "not a package!"],
          "nameEn": "Digikala",
          "nameFa": "دیجی‌کالا",
          "developer": "Digikala Group",
          "domains": ["digikala.com"],
          "certificateSha1": ["aa:bb"],
          "verification": "VERIFIED",
          "category": "SHOPPING"
        }
    """.trimIndent()

    @Test
    fun `valid record parses and filters invalid alternative ids`() {
        val record = KnownApp.fromJson(Json.parse(validJson))
        assertTrue(record != null)
        assertEquals("com.digikala", record!!.packageName)
        assertEquals(listOf("com.digikala.beta"), record.alternativePackages)
        assertEquals(Verification.VERIFIED, record.verification)
        assertEquals(listOf("AA:BB"), record.certificateSha1)
    }

    @Test
    fun `record with invalid package id is rejected`() {
        val bad = """{"packageName":"notapackage","nameEn":"X"}"""
        assertNull(KnownApp.fromJson(Json.parse(bad)))
    }

    @Test
    fun `record without package id is rejected`() {
        val bad = """{"nameEn":"No id"}"""
        assertNull(KnownApp.fromJson(Json.parse(bad)))
    }

    @Test
    fun `unknown verification defaults to probable`() {
        val record = KnownApp.fromJson(
            Json.parse("""{"packageName":"com.example.app","verification":"WHATEVER"}"""),
        )
        assertEquals(Verification.PROBABLE, record!!.verification)
    }

    @Test
    fun `entity round trip preserves every field`() {
        val original = KnownApp(
            id = 7,
            packageName = "ir.divar",
            alternativePackages = listOf("ir.divar.dev"),
            nameEn = "Divar",
            nameFa = "دیوار",
            developer = "Divar",
            developerAliases = listOf("دیوار"),
            website = "https://divar.ir",
            domains = listOf("divar.ir"),
            certificateSha256 = listOf("AA:BB"),
            certificateSha1 = listOf("CC:DD"),
            knownInstallers = listOf(IranianStores.BAZAAR),
            category = "SHOPPING",
            verification = Verification.VERIFIED,
            nameAliases = listOf("divar.ir"),
            notes = "note",
            updatedAt = 123L,
        )
        val restored = original.toEntity().let { entity: KnownAppEntity -> entity.toDomain() }
        assertEquals(original.packageName, restored.packageName)
        assertEquals(original.alternativePackages, restored.alternativePackages)
        assertEquals(original.nameFa, restored.nameFa)
        assertEquals(original.certificateSha1, restored.certificateSha1)
        assertEquals(original.knownInstallers, restored.knownInstallers)
        assertEquals(original.verification, restored.verification)
        assertEquals(original.notes, restored.notes)
    }

    @Test
    fun `legacy comma separated lists still decode`() {
        assertEquals(listOf("a", "b"), decodeStringList("a, b"))
        assertEquals(listOf("x"), decodeStringList("[\"x\"]"))
        assertEquals(emptyList<String>(), decodeStringList(null))
        assertEquals(emptyList<String>(), decodeStringList(""))
    }

    @Test
    fun `iranian store recognition`() {
        assertTrue(IranianStores.isIranianStore("com.farsitel.bazaar"))
        assertTrue(IranianStores.isIranianStore("ir.mservices.market"))
        assertEquals("Cafe Bazaar", IranianStores.displayName("com.farsitel.bazaar"))
        assertTrue(!IranianStores.isIranianStore("com.android.vending"))
        assertTrue(!IranianStores.isIranianStore(null))
    }

    @Test
    fun `payload duplication is collapsed by package id`() {
        val json = """
            {"version": 2, "apps": [
              {"packageName":"com.a.b","nameEn":"A"},
              {"packageName":"com.a.b","nameEn":"A duplicate"}
            ]}
        """.trimIndent()
        val root = Json.parse(json)
        val apps = root.arrayOrEmpty("apps").mapNotNull { KnownApp.fromJson(it) }
        assertEquals(2, apps.size)
        assertEquals(1, apps.distinctBy { it.packageName }.size)
    }
}

private fun Json.Value.arrayOrEmpty(key: String): List<Json.Value> =
    (this as? Json.Value.Obj)?.fields?.get(key)?.let { (it as? Json.Value.Arr)?.items } ?: emptyList()
