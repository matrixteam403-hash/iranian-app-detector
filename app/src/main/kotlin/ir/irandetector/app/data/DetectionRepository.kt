package ir.irandetector.app.data

import ir.irandetector.app.core.Json
import ir.irandetector.app.core.Json.arrayOrEmpty
import ir.irandetector.app.core.Json.intOrNull
import ir.irandetector.app.core.Json.longOrNull
import ir.irandetector.app.core.Json.string
import ir.irandetector.app.core.Json.stringOrNull
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.irandetector.app.database.AppDatabase
import ir.irandetector.app.database.KnownAppDao
import ir.irandetector.app.database.KnownAppEntity
import ir.irandetector.app.database.MetadataEntity
import ir.irandetector.app.database.MetadataDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the detection database: seeding from the bundled asset, in-memory caching
 * for the detection engine, and replacement by a remotely fetched payload.
 *
 * The cache is a plain immutable List that is rebuilt only when the database
 * changes, so scanning never re-reads SQLite per package.
 */
@Singleton
class DetectionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val knownAppDao: KnownAppDao,
    private val metadataDao: MetadataDao,
) {
    private val mutex = Mutex()

    private val _records = MutableStateFlow<List<KnownApp>>(emptyList())
    val records: StateFlow<List<KnownApp>> = _records.asStateFlow()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _updatedAt = MutableStateFlow(0L)
    val updatedAt: StateFlow<Long> = _updatedAt.asStateFlow()

    private val _index = MutableStateFlow<ir.irandetector.app.detection.DetectionIndex?>(null)
    val index: StateFlow<ir.irandetector.app.detection.DetectionIndex?> = _index.asStateFlow()

    /** Loads (once) the bundled dataset when the database is still empty. */
    suspend fun ensureSeeded(): Int = mutex.withLock {
        if (knownAppDao.count() > 0) {
            refreshCache()
            return knownAppDao.count()
        }
        val seeded = withContext(Dispatchers.IO) { seedFromAsset() }
        refreshCache()
        Log.i(TAG, "Seeded detection database with $seeded records")
        seeded
    }

    private suspend fun seedFromAsset(): Int {
        val payload = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrElse {
            Log.e(TAG, "Bundled detection database missing", it)
            return 0
        }
        val parsed = parsePayload(payload)
        if (parsed.apps.isEmpty()) return 0
        knownAppDao.replaceShipped(parsed.apps.map { it.toEntity() })
        persistMetadata(parsed.version, parsed.updatedAt, "bundled")
        return parsed.apps.size
    }

    /** Parses either `{ "apps": [...] }` or a bare `[...]` payload. */
    fun parsePayload(json: String): Payload {
        val root = Json.parse(json)
        val version = root.intOrNull("version") ?: 0
        val updatedAt = root.longOrNull("updatedAt") ?: 0L
        val appsArray = when (root) {
            is Json.Value.Arr -> root.items
            else -> root.arrayOrEmpty("apps")
        }
        val apps = appsArray.mapNotNull { KnownApp.fromJson(it) }
            .distinctBy { it.packageName.lowercase() }
        return Payload(version, updatedAt, apps)
    }

    /**
     * Replaces the shipped records with a downloaded payload.
     * User-visible behaviour is transactional: either all rows are swapped or
     * nothing changes.
     */
    suspend fun applyRemotePayload(json: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = parsePayload(json)
            require(payload.apps.isNotEmpty()) { "Remote database contained no valid records" }
            knownAppDao.replaceShipped(payload.apps.map { it.toEntity() })
            persistMetadata(payload.version, payload.updatedAt, "remote")
            refreshCache()
            payload.apps.size
        }
    }

    suspend fun recordFromUser(app: KnownApp) = withContext(Dispatchers.IO) {
        knownAppDao.insertAll(listOf(app.copy(fromDatabase = false).toEntity()))
        refreshCache()
    }

    private suspend fun persistMetadata(version: Int, updatedAt: Long, source: String) {
        metadataDao.put(MetadataEntity(AppDatabase.META_DB_VERSION, version.toString()))
        metadataDao.put(MetadataEntity(AppDatabase.META_DB_UPDATED_AT, updatedAt.toString()))
        metadataDao.put(MetadataEntity(AppDatabase.META_DB_SOURCE, source))
        _version.value = version
        _updatedAt.value = updatedAt
    }

    private suspend fun refreshCache() {
        val entities = knownAppDao.getAll()
        val apps = entities.map { it.toDomain() }
        _records.value = apps
        _index.value = ir.irandetector.app.detection.DetectionIndex.build(apps)
        if (_version.value == 0) {
            _version.value = metadataDao.get(AppDatabase.META_DB_VERSION)?.toIntOrNull() ?: 0
        }
        if (_updatedAt.value == 0L) {
            _updatedAt.value = metadataDao.get(AppDatabase.META_DB_UPDATED_AT)?.toLongOrNull() ?: 0L
        }
    }

    suspend fun databaseMeta(): Triple<Int, Long, String> = Triple(
        metadataDao.get(AppDatabase.META_DB_VERSION)?.toIntOrNull() ?: 0,
        metadataDao.get(AppDatabase.META_DB_UPDATED_AT)?.toLongOrNull() ?: 0L,
        metadataDao.get(AppDatabase.META_DB_SOURCE) ?: "bundled",
    )

    data class Payload(val version: Int, val updatedAt: Long, val apps: List<KnownApp>)

    companion object {
        private const val TAG = "DetectionRepository"
        const val ASSET_NAME = "detection_db.json"
    }
}

// ---------------------------------------------------------------- mappers

fun KnownAppEntity.toDomain(): KnownApp = KnownApp(
    id = id,
    packageName = packageName,
    alternativePackages = decodeStringList(alternativePackageList),
    nameEn = nameEn,
    nameFa = nameFa,
    developer = developer,
    developerAliases = decodeStringList(developerAliases),
    website = website,
    domains = decodeStringList(domains),
    certificateSha256 = decodeStringList(certificateSha256),
    certificateSha1 = decodeStringList(certificateSha1),
    knownInstallers = decodeStringList(knownInstallers),
    category = category,
    verification = Verification.fromWire(verification),
    iconHash = iconHash,
    iconReference = iconReference,
    nameAliases = decodeStringList(nameAliases),
    notes = notes,
    updatedAt = updatedAt,
    fromDatabase = fromDatabase,
)

fun KnownApp.toEntity(): KnownAppEntity = KnownAppEntity(
    id = id,
    packageName = packageName,
    alternativePackageList = Json.stringArray(alternativePackages),
    nameEn = nameEn,
    nameFa = nameFa,
    developer = developer,
    developerAliases = Json.stringArray(developerAliases),
    website = website,
    domains = Json.stringArray(domains),
    certificateSha256 = Json.stringArray(certificateSha256),
    certificateSha1 = Json.stringArray(certificateSha1),
    knownInstallers = Json.stringArray(knownInstallers),
    category = category,
    verification = verification.name,
    iconHash = iconHash,
    iconReference = iconReference,
    nameAliases = Json.stringArray(nameAliases),
    notes = notes,
    updatedAt = updatedAt,
    fromDatabase = fromDatabase,
)

/** Tolerant decoder: legacy rows may contain a plain comma separated value. */
internal fun decodeStringList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val trimmed = raw.trim()
    if (!trimmed.startsWith("[")) {
        return trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
    return runCatching {
        (Json.parse(trimmed) as? Json.Value.Arr)?.items
            ?.mapNotNull { (it as? Json.Value.Str)?.value }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }.getOrDefault(emptyList())
}
