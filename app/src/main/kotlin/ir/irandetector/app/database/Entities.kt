package ir.irandetector.app.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Detection database row.
 *
 * List-valued fields are stored as JSON arrays (text) to keep the schema small
 * and the queries trivial; the dataset is a few hundred rows at most, and every
 * lookup that matters is a primary-key hit on [packageName] / [alternativePackageList].
 */
@Entity(
    tableName = "known_apps",
    indices = [Index(value = ["packageName"], unique = true)],
)
data class KnownAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    @ColumnInfo(name = "alternativePackages") val alternativePackageList: String = "[]",
    val nameEn: String,
    val nameFa: String = "",
    val developer: String = "",
    val developerAliases: String = "[]",
    val website: String? = null,
    val domains: String = "[]",
    val certificateSha256: String = "[]",
    val certificateSha1: String = "[]",
    val knownInstallers: String = "[]",
    val category: String = "OTHER",
    val verification: String = "PROBABLE",
    val iconHash: String? = null,
    val iconReference: String? = null,
    val nameAliases: String = "[]",
    val notes: String? = null,
    val updatedAt: Long = 0L,
    val fromDatabase: Boolean = true,
)

/** A package the user explicitly asked us to never flag again. */
@Entity(tableName = "exclusions", primaryKeys = ["packageName"])
data class ExclusionEntity(
    val packageName: String,
    val appLabel: String = "",
    val excludedAt: Long = System.currentTimeMillis(),
)

/**
 * Cached scan results. Icons are deliberately not stored — they are re-read from
 * PackageManager on demand so the table stays tiny and cache invalidation is cheap.
 */
@Entity(tableName = "scan_cache", indices = [Index(value = ["packageName"], unique = true)])
data class ScanCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val versionName: String?,
    val confidence: Int,
    val verification: String,
    val signals: String,          // JSON array of signal identifiers
    val reasons: String,          // JSON array of human readable explanations
    val matchedRecordId: Long?,
    val scannedAt: Long,
)

@Dao
interface KnownAppDao {
    @Query("SELECT * FROM known_apps ORDER BY nameEn COLLATE NOCASE")
    suspend fun getAll(): List<KnownAppEntity>

    @Query("SELECT * FROM known_apps ORDER BY nameEn COLLATE NOCASE")
    fun observeAll(): Flow<List<KnownAppEntity>>

    @Query("SELECT * FROM known_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun findByPackage(packageName: String): KnownAppEntity?

    @Query("SELECT COUNT(*) FROM known_apps")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<KnownAppEntity>)

    @Query("DELETE FROM known_apps WHERE fromDatabase = 1")
    suspend fun deleteShippedRows()

    @Query("DELETE FROM known_apps")
    suspend fun clear()

    @Transaction
    suspend fun replaceShipped(rows: List<KnownAppEntity>) {
        deleteShippedRows()
        insertAll(rows)
    }
}

@Dao
interface ExclusionDao {
    @Query("SELECT * FROM exclusions ORDER BY excludedAt DESC")
    fun observeAll(): Flow<List<ExclusionEntity>>

    @Query("SELECT packageName FROM exclusions")
    suspend fun allPackageNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: ExclusionEntity)

    @Query("DELETE FROM exclusions WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM exclusions")
    suspend fun clear()
}

@Dao
interface ScanCacheDao {
    @Query("SELECT * FROM scan_cache")
    suspend fun getAll(): List<ScanCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ScanCacheEntity>)

    @Query("DELETE FROM scan_cache WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM scan_cache")
    suspend fun clear()
}

/** Metadata key/value table (database version, last update time, ETag...). */
@Entity(tableName = "metadata")
data class MetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface MetadataDao {
    @Query("SELECT value FROM metadata WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: MetadataEntity)

    @Query("DELETE FROM metadata")
    suspend fun clear()
}
