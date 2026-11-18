# ---------------------------------------------------------------------------
# Iranian App Detector - R8 / ProGuard rules
# ---------------------------------------------------------------------------
# The optimiser stage is deliberately disabled: this project must build on a
# 2 GB CI box where R8's optimiser alone needs ~1 GB of heap. Shrinking and
# obfuscation (the parts that actually reduce APK size) are still fully enabled,
# and R8 full mode is off in gradle.properties for the same reason.
-dontoptimize

# Keep line numbers so that crash reports remain debuggable without the mapping.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room generates implementations reflectively referenced by the database holder.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# Kotlin serialization-free JSON parsing: we hand-roll parsing, but keep
# data classes used across module boundaries intact for safety.
-keep class ir.irandetector.app.data.** { *; }
-keep class ir.irandetector.app.domain.** { *; }

# Compose: no reflection required, these avoid noisy warnings.
-dontwarn androidx.compose.**

# Coil
-dontwarn okio.**
-dontwarn okhttp3.**

# Enum values used by name in persisted settings.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
