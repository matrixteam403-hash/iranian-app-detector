package ir.irandetector.app.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        KnownAppEntity::class,
        ExclusionEntity::class,
        ScanCacheEntity::class,
        MetadataEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knownAppDao(): KnownAppDao
    abstract fun exclusionDao(): ExclusionDao
    abstract fun scanCacheDao(): ScanCacheDao
    abstract fun metadataDao(): MetadataDao

    companion object {
        const val NAME = "iran_detector.db"
        const val META_DB_VERSION = "detection_db_version"
        const val META_DB_UPDATED_AT = "detection_db_updated_at"
        const val META_DB_SOURCE = "detection_db_source"
    }
}
