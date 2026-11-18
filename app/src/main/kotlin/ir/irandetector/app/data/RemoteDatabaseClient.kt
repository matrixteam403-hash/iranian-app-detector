package ir.irandetector.app.data

import ir.irandetector.app.core.Json
import ir.irandetector.app.core.Json.arrayOrEmpty
import ir.irandetector.app.core.Json.intOrNull
import ir.irandetector.app.settings.AppSettings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional database updater.
 *
 * Deliberately implemented on top of [HttpURLConnection] instead of OkHttp/Retrofit:
 * a one-shot, user triggered GET does not justify ~1 MB of libraries in an app
 * whose primary optimisation goal is APK size and memory use.
 *
 * The endpoint is user configurable (Settings → Database → Update source) and the
 * app ships with **no** hardcoded URL, so there is no pretending that a server
 * exists. Users can paste the URL of a published `detection_db.json`.
 */
@Singleton
class RemoteDatabaseClient @Inject constructor(
    private val settings: AppSettings,
) {
    sealed interface UpdateResult {
        data class Success(val recordCount: Int, val version: Int) : UpdateResult
        data class Failure(val message: String) : UpdateResult
    }

    suspend fun fetch(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.startsWith("https://") || url.startsWith("http://")) {
                "URL must start with http:// or https://"
            }
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "IranianAppDetector/1.0 (database update)")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IOException("Server responded with HTTP $code")
                }
                val length = connection.contentLengthLong
                if (length > MAX_BYTES) {
                    throw IOException("Payload too large ($length bytes, limit $MAX_BYTES)")
                }
                val body = connection.inputStream.bufferedReader().use { reader ->
                    val sb = StringBuilder(8 * 1024)
                    val buffer = CharArray(8 * 1024)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read <= 0) break
                        sb.append(buffer, 0, read)
                        if (sb.length > MAX_BYTES) throw IOException("Payload exceeds limit")
                    }
                    sb.toString()
                }
                if (body.isBlank()) throw IOException("Server returned an empty body")
                body
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Convenience helper used by the settings screen. */
    suspend fun updateFrom(configuredUrl: String? = null): UpdateResult {
        val url = (configuredUrl ?: settings.databaseUrl.first())?.trim()
        if (url.isNullOrBlank()) {
            return UpdateResult.Failure(
                "No update URL configured. Add one under Settings → Detection database.",
            )
        }
        val payload = fetch(url).getOrElse { t ->
            Log.w(TAG, "Database download failed", t)
            return UpdateResult.Failure(t.message ?: "Download failed")
        }
        // Validate before touching the database: a malformed payload must never
        // be able to wipe the bundled dataset.
        val parsed = runCatching {
            ir.irandetector.app.data.DetectionRepository::class.java // keep reference explicit
            Json.parse(payload)
        }.getOrElse {
            return UpdateResult.Failure("Payload is not valid JSON: ${it.message}")
        }
        val apps = when (parsed) {
            is Json.Value.Arr -> parsed.items
            else -> parsed.arrayOrEmpty("apps")
        }
        if (apps.isEmpty()) return UpdateResult.Failure("Payload contains no applications")
        val version = parsed.intOrNull("version") ?: 0
        return UpdateResult.Success(recordCount = apps.size, version = version)
    }

    private companion object {
        const val TAG = "RemoteDatabaseClient"
        const val MAX_BYTES = 4 * 1024 * 1024
    }
}
