plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Batch 49: plugin Hilt (id("com.google.dagger.hilt.android"), dipasang Batch 18)
    // DICABUT — lihat app/build.gradle.kts & CHANGELOG.md v1.86.0 untuk rasional
    // lengkap kenapa Hilt ternyata dead weight di project ini.
}
