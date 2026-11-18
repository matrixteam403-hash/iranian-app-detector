package ir.irandetector.app.detection

/**
 * Perceptual icon fingerprinting — average hash (aHash), pure Kotlin.
 *
 * Kept free of Android types so it is unit testable on the JVM: callers pass an
 * ARGB pixel buffer of any square size and receive a 64-bit hash as hex.
 *
 * A launcher icon is squashed to 8x8, converted to luma, and each cell is
 * compared with the mean. The result survives rescaling, re-encoding and small
 * colour shifts, which is exactly what an icon needs. Hamming distance is then
 * used to compare two hashes.
 *
 * IMPORTANT: this is a *supporting* signal. The database ships no verified icon
 * hashes today, so the engine never fires it in the shipped configuration. When
 * a future database update includes `iconHash` values produced from genuine
 * icons, the signal starts contributing with no code change and no rebuild.
 */
object IconHash {

    const val GRID = 8
    const val BITS = GRID * GRID
    const val HEX_LENGTH = 16

    /** Exact match threshold: very few bits may differ. */
    const val DISTANCE_STRONG = 6

    /** Whether two hashes are plausibly the same icon at all. */
    const val DISTANCE_MAX = 10

    /**
     * @param argb square ARGB pixel buffer, row-major, length == side*side
     * @param side width/height of the input buffer
     */
    fun averageHash(argb: IntArray, side: Int): String {
        require(side > 0) { "side must be positive" }
        require(argb.size >= side * side) { "pixel buffer smaller than side*side" }

        val cells = IntArray(BITS)
        val counts = IntArray(BITS)
        // Box-average every source cell into the 8x8 grid in a single pass.
        for (y in 0 until side) {
            val gy = (y * GRID) / side
            for (x in 0 until side) {
                val gx = (x * GRID) / side
                val index = gy * GRID + gx
                val pixel = argb[y * side + x]
                val a = (pixel ushr 24) and 0xFF
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // Fully transparent pixels carry no luminance information.
                val luma = if (a < 16) 0 else (r * 299 + g * 587 + b * 114) / 1000
                cells[index] += luma
                counts[index]++
            }
        }
        var sum = 0L
        for (i in 0 until BITS) {
            if (counts[i] > 0) {
                cells[i] /= counts[i]
                sum += cells[i]
            }
        }
        val mean = sum.toDouble() / BITS

        var hash = 0L
        for (i in 0 until BITS) {
            if (cells[i] >= mean) hash = hash or (1L shl i)
        }
        return hash.toULong().toString(16).padStart(HEX_LENGTH, '0')
    }

    /** Number of differing bits, or [Int.MAX_VALUE] when a hash cannot be parsed. */
    fun distance(a: String, b: String): Int {
        val x = a.toULongOrNull(16) ?: return Int.MAX_VALUE
        val y = b.toULongOrNull(16) ?: return Int.MAX_VALUE
        return (x xor y).countOneBits()
    }

    fun isPlausible(a: String, b: String): Boolean = distance(a, b) <= DISTANCE_MAX
}
