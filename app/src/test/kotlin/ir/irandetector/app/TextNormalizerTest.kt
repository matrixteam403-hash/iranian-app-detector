package ir.irandetector.app

import ir.irandetector.app.core.Json
import ir.irandetector.app.core.PackageNames
import ir.irandetector.app.core.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `arabic yeh and kaf are folded to persian forms`() {
        // "ديجی کالا" written with Arabic yeh (U+064A) must equal the Persian form.
        assertEquals(TextNormalizer.normalize("ديجی کالا"), TextNormalizer.normalize("دیجی کالا"))
        assertEquals(TextNormalizer.normalize("ك ale"), TextNormalizer.normalize("ک ale"))
    }

    @Test
    fun `diacritics and tatweel are removed`() {
        assertEquals("اسنپ", TextNormalizer.normalize("اَسـنپ"))
    }

    @Test
    fun `zero width non joiner becomes a space`() {
        assertEquals("دیجی کالا", TextNormalizer.normalize("دیجی\u200Cکالا"))
    }

    @Test
    fun `persian and arabic digits are converted`() {
        assertEquals("12345", TextNormalizer.normalize("۱۲۳۴۵"))
        assertEquals("123", TextNormalizer.normalize("١٢٣"))
    }

    @Test
    fun `case and whitespace are normalised`() {
        assertEquals("snapp", TextNormalizer.normalize("  SnApp  "))
        assertEquals("digi kala", TextNormalizer.normalize("Digi\n\tKala"))
    }

    @Test
    fun `similarity identical strings`() {
        assertEquals(1.0, TextNormalizer.similarity("Digikala", "digikala"), 0.0001)
    }

    @Test
    fun `similarity close strings is high`() {
        assertTrue(TextNormalizer.similarity("Digikala", "Digikalaa") > 0.85)
    }

    @Test
    fun `similarity unrelated strings is low`() {
        assertTrue(TextNormalizer.similarity("Digikala", "Facebook") < 0.4)
    }

    @Test
    fun `similarity handles empty input`() {
        assertEquals(0.0, TextNormalizer.similarity("", "Digikala"), 0.0)
        assertEquals(0.0, TextNormalizer.similarity(null, "Digikala"), 0.0)
    }

    @Test
    fun `token containment rewards partial names`() {
        assertTrue(TextNormalizer.tokenContainment("اسنپ", "اسنپ سوپراپ") > 0.9)
        assertEquals(0.0, TextNormalizer.tokenContainment("Spotify", "Digikala"), 0.0)
    }

    @Test
    fun `noise words are stripped`() {
        assertEquals("دیجی کالا", TextNormalizer.stripNoiseWords("اپلیکیشن دیجی کالا"))
        assertEquals("snapp", TextNormalizer.stripNoiseWords("Official Snapp App"))
    }

    @Test
    fun `persian detection helper`() {
        assertTrue(TextNormalizer.containsPersianLetters("اسنپ"))
        assertFalse(TextNormalizer.containsPersianLetters("Snapp"))
        assertFalse(TextNormalizer.containsPersianLetters(null))
    }

    @Test
    fun `package name helpers`() {
        assertEquals("com.sabaidea", PackageNames.namespace("com.sabaidea.filimo"))
        assertEquals("rajman", PackageNames.brandSegment("org.rajman.neshan.traffic"))
        assertEquals("digikala", PackageNames.brandSegment("com.digikala.reader"))
        assertEquals(listOf("ir", "divar"), PackageNames.segments("ir.divar"))
    }

    @Test
    fun `package validation rejects garbage but keeps real ids`() {
        assertTrue(PackageNames.isValid("com.digikala"))
        assertTrue(PackageNames.isValid("net.iGap"))
        assertTrue(PackageNames.isValid("ir.resaneh1.iptv"))
        assertFalse(PackageNames.isValid("singleSegment"))
        assertFalse(PackageNames.isValid(""))
        assertFalse(PackageNames.isValid("com..double"))
        assertFalse(PackageNames.isValid("com.1leading.digit"))
        assertFalse(PackageNames.isValid(null))
        assertFalse(PackageNames.isValid("com.space id"))
    }

    @Test
    fun `json parser round trips nested structures`() {
        val source = """{"a":[1,2,"three"],"b":{"c":true,"d":null},"e":"line\nbreak"}"""
        val parsed = Json.parse(source) as Json.Value.Obj
        val a = parsed.fields["a"] as Json.Value.Arr
        assertEquals(3, a.items.size)
        assertEquals(2.0, (a.items[1] as Json.Value.Num).value, 0.0)
        val b = parsed.fields["b"] as Json.Value.Obj
        assertEquals(true, (b.fields["c"] as Json.Value.Bool).value)
        assertEquals(Json.Value.Null, b.fields["d"])
        assertEquals("line\nbreak", (parsed.fields["e"] as Json.Value.Str).value)
    }

    @Test(expected = Json.JsonException::class)
    fun `json parser rejects malformed input`() {
        Json.parse("""{"a": }""")
    }

    @Test
    fun `json string array writer escapes quotes`() {
        assertEquals("""["a\"b"]""", Json.stringArray(listOf("a\"b")))
    }
}
