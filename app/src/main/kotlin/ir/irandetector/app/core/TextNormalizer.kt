package ir.irandetector.app.core

/**
 * Text normalisation and similarity helpers used by the detection engine.
 *
 * Persian/Arabic script has several code-point variants that render identically
 * (ي/ی, ك/ک, ۀ/ه), optional diacritics, tatweel and zero-width joiners. Without
 * normalisation, "اسنپ" typed with an Arabic yeh would never match a record
 * stored with a Persian yeh. Everything here is pure Kotlin so it is covered by
 * fast JVM unit tests.
 */
object TextNormalizer {

    private const val ARABIC_YEH = '\u064A'
    private const val PERSIAN_YEH = '\u06CC'
    private const val ARABIC_KAF = '\u0643'
    private const val PERSIAN_KEHEH = '\u06A9'
    private const val TATWEEL = '\u0640'
    private const val ZWNJ = '\u200C'
    private const val ARABIC_HEH_GOAL = '\u06C0' // ۀ

    /** Harakat / tanwin / shadda / sukun — removed, they carry no lexical weight. */
    private val DIACRITICS = '\u064B'..'\u0652'

    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val sb = StringBuilder(input.length)
        var lastWasSpace = false
        for (raw in input) {
            var c = raw
            if (c in DIACRITICS) continue
            c = when (c) {
                ARABIC_YEH -> PERSIAN_YEH
                ARABIC_KAF -> PERSIAN_KEHEH
                ARABIC_HEH_GOAL -> '\u0647'
                TATWEEL -> continue
                ZWNJ -> ' '
                // Arabic-Indic and extended Arabic-Indic digits -> ASCII digits.
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                else -> c
            }
            if (c.isWhitespace()) {
                if (!lastWasSpace && sb.isNotEmpty()) { sb.append(' '); lastWasSpace = true }
                continue
            }
            lastWasSpace = false
            if (c.isLetterOrDigit()) sb.append(c.lowercaseChar())
        }
        return sb.toString().trim()
    }

    /** Normalised form with all spaces removed — used for "digikala" vs "دیجی کالا". */
    fun compact(input: String?): String = normalize(input).replace(" ", "")

    fun containsPersianLetters(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        return input.any { it in '\u0600'..'\u06FF' }
    }

    /** Classic Levenshtein distance, two-row implementation (O(min(a,b)) memory). */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val (shorter, longer) = if (a.length <= b.length) a to b else b to a
        var previous = IntArray(shorter.length + 1) { it }
        var current = IntArray(shorter.length + 1)
        for (i in 1..longer.length) {
            current[0] = i
            for (j in 1..shorter.length) {
                val substitution = previous[j - 1] + if (longer[i - 1] == shorter[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[shorter.length]
    }

    /** 0.0 (unrelated) .. 1.0 (identical) after normalisation. */
    fun similarity(a: String?, b: String?): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val maxLen = maxOf(x.length, y.length)
        return 1.0 - levenshtein(x, y).toDouble() / maxLen
    }

    /**
     * Token aware similarity: rewards a full containment match ("اسنپ" inside
     * "اسنپ سوپراپ") while still penalising unrelated strings.
     */
    fun tokenContainment(a: String?, b: String?): Double {
        val x = compact(a)
        val y = compact(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        if (x.length >= 3 && y.contains(x)) return 0.92
        if (y.length >= 3 && x.contains(y)) return 0.92
        return 0.0
    }

    /**
     * Removes common noise words so that "اپلیکیشن دیجی کالا" matches "دیجی کالا".
     * Matching is per whitespace separated token (never a substring replacement),
     * otherwise "app" would be carved out of "snapp".
     */
    fun stripNoiseWords(input: String?): String {
        val value = normalize(input)
        if (value.isEmpty()) return value
        return value.split(' ')
            .filter { it.isNotEmpty() && it !in NOISE_WORDS }
            .joinToString(" ")
    }

    private val NOISE_WORDS = setOf(
        "application", "app", "official", "the",
        "اپلیکیشن", "برنامه", "رسمی", "نسخه", "جدید", "اپ",
    )
}

/** Turns a package name into human searchable parts: `com.sabaidea.filimo` -> [com, sabaidea, filimo]. */
object PackageNames {
    fun segments(packageName: String): List<String> =
        packageName.lowercase().split('.').filter { it.isNotBlank() }

    /** Top level namespace, e.g. `com.sabaidea` for `com.sabaidea.filimo`. */
    fun namespace(packageName: String): String {
        val parts = segments(packageName)
        return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else parts.joinToString(".")
    }

    /**
     * The segment that carries the brand, used for name matching.
     *
     * For `com.digikala.reader` the brand is `digikala` (index 1), not the feature
     * name `reader`; for two segment ids such as `ir.divar` it is the last segment.
     */
    fun brandSegment(packageName: String): String {
        val parts = segments(packageName)
        return when {
            parts.size >= 3 -> parts[1]
            parts.isNotEmpty() -> parts.last()
            else -> ""
        }
    }

    /**
     * True only for a syntactically valid package id. Used to reject garbage
     * coming from a remote database payload before it is allowed to influence
     * detection.
     */
    fun isValid(packageName: String?): Boolean {
        if (packageName.isNullOrBlank() || packageName.length > 127) return false
        val parts = packageName.split('.')
        if (parts.size < 2) return false
        return parts.all { part ->
            part.isNotEmpty() &&
                part.first().isLetter() &&
                part.all { it.isLetterOrDigit() || it == '_' }
        }
    }
}
