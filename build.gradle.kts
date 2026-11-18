// Top-level build file. Plugin versions are declared in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Gradle 8 + JDK 17 on low-memory CI machines: keep the daemon modest.
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
