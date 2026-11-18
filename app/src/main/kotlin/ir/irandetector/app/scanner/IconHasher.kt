package ir.irandetector.app.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.irandetector.app.detection.IconHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android binding for [IconHash]: rasterises a launcher icon to a fixed 32x32
 * buffer and hashes it. Results are memoised per package for the process
 * lifetime; icons change only on update, and a scan rebuilds the cache anyway.
 */
@Singleton
class IconHasher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = HashMap<String, String?>(64)

    suspend fun hash(packageName: String): String? = withContext(Dispatchers.IO) {
        if (cache.containsKey(packageName)) return@withContext cache[packageName]
        val hash = runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = drawable.toSquareBitmap(RENDER_SIZE)
            val pixels = IntArray(RENDER_SIZE * RENDER_SIZE)
            bitmap.getPixels(pixels, 0, RENDER_SIZE, 0, 0, RENDER_SIZE, RENDER_SIZE)
            bitmap.recycle()
            IconHash.averageHash(pixels, RENDER_SIZE)
        }.onFailure {
            Log.w(TAG, "Could not hash icon of $packageName", it)
        }.getOrNull()
        cache[packageName] = hash
        hash
    }

    fun clearCache() = cache.clear()

    private fun Drawable.toSquareBitmap(size: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        val target = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return target
    }

    private companion object {
        const val TAG = "IconHasher"
        const val RENDER_SIZE = 32
    }
}
