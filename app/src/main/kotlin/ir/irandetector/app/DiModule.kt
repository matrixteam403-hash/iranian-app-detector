package ir.irandetector.app

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.irandetector.app.database.AppDatabase
import ir.irandetector.app.database.ExclusionDao
import ir.irandetector.app.database.KnownAppDao
import ir.irandetector.app.database.MetadataDao
import ir.irandetector.app.database.ScanCacheDao
import ir.irandetector.app.scanner.PackageScanner
import ir.irandetector.app.vpn.AndroidVpnProbe
import ir.irandetector.app.vpn.VpnDetector
import ir.irandetector.app.vpn.VpnProbe
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // The database is a disposable cache of the bundled asset: rebuilding it
            // is cheaper and safer than shipping (and shipping mistakes in) migrations.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideKnownAppDao(db: AppDatabase): KnownAppDao = db.knownAppDao()

    @Provides
    fun provideExclusionDao(db: AppDatabase): ExclusionDao = db.exclusionDao()

    @Provides
    fun provideScanCacheDao(db: AppDatabase): ScanCacheDao = db.scanCacheDao()

    @Provides
    fun provideMetadataDao(db: AppDatabase): MetadataDao = db.metadataDao()

    @Provides
    @Singleton
    fun providePackageScanner(@ApplicationContext context: Context): PackageScanner =
        PackageScanner(context)

    @Provides
    @Singleton
    fun provideVpnProbe(probe: AndroidVpnProbe): VpnProbe = probe

    @Provides
    @Singleton
    fun provideVpnDetector(): VpnDetector = VpnDetector()
}
