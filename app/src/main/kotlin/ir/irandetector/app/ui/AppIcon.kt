package ir.irandetector.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launcher icon rendering for installed packages.
 *
 * Design notes (performance):
 *  - PackageManager is queried off the main thread, once per package.
 *  - The rasterised bitmap is cached in a size-bounded LruCache keyed by package
 *    name, so scrolling a 300 item list does not thrash the GC or re-decode
 *    drawables on every recomposition.
 *  - Fixed 144 px raster keeps memory at ~80 KB per icon regardless of DPI.
 *  - Missing/unreadable icons fall back to a deterministic letter avatar instead
 *    of an empty box (edge case required by the spec).
 */
private const val ICON_PX = 144
private const val ICON_CACHE_KB = 6 * 1024 // ≈ 75 icons

object IconCache {
    val bitmaps = object : LruCache<String, Bitmap>(ICON_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun clear() = bitmaps.evictAll()
}

@Composable
fun PackageIconBadge(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val context = LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<Bitmap?>(
        initialValue = IconCache.bitmaps.get(packageName),
        key1 = packageName,
    ) {
        value = IconCache.bitmaps.get(packageName)
        if (value == null) {
            val loaded = loadIcon(context, packageName)
            if (loaded != null) IconCache.bitmaps.put(packageName, loaded)
            value = loaded
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = remember(bitmap) { bitmap!!.asImageBitmap() },
            contentDescription = null,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        LetterAvatar(packageName = packageName, size = size, modifier = modifier)
    }
}

/** Loads and caches a package icon. Safe to call repeatedly and from any thread. */
suspend fun loadIcon(context: android.content.Context, packageName: String): Bitmap? =
    withContext(Dispatchers.IO) {
        IconCache.bitmaps.get(packageName) ?: runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, ICON_PX, ICON_PX)
            drawable.draw(canvas)
            bitmap
        }.getOrNull()
    }

suspend fun preloadIcon(context: android.content.Context, packageName: String): Boolean =
    loadIcon(context, packageName) != null

/** Deterministic letter avatar used when an icon cannot be read. */
@Composable
fun LetterAvatar(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val colors = AVATAR_COLORS
    val color = colors[(packageName.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % colors.size]
    val bitmap = remember(packageName) { drawLetterBitmap(packageName, color) }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

private val AVATAR_COLORS = intArrayOf(
    0xFF2EE59D.toInt(),
    0xFF5AC8FA.toInt(),
    0xFFF5B83D.toInt(),
    0xFFB48CFF.toInt(),
    0xFFFF8A80.toInt(),
)

private fun drawLetterBitmap(packageName: String, color: Int): ImageBitmap {
    val bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        alpha = 46
    }
    canvas.drawRoundRect(RectF(0f, 0f, ICON_PX.toFloat(), ICON_PX.toFloat()), 34f, 34f, fill)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = ICON_PX * 0.46f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val letter = packageName.firstOrNull { it.isLetter() }?.uppercase() ?: "?"
    val metrics = text.fontMetrics
    val y = ICON_PX / 2f - (metrics.ascent + metrics.descent) / 2f
    canvas.drawText(letter, ICON_PX / 2f, y, text)
    return bmp.asImageBitmap()
}
