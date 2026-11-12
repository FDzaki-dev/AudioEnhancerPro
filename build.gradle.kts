plugins {
    // Batch 314 (request user: "Evaluasi upgrade AGP/Kotlin"): 8.5.2 -> 8.6.1 = SATU variabel risiko. Gradle 8.7 (bootstrap workflow), JDK 17 dan
    // Build Tools 34 TETAP cocok (AGP 8.6: min Gradle 8.7); 8.6.1 memperbaiki 3 regresi R8 dari 8.5.x. Kotlin 1.9.24 / compose compiler 1.5.14 /
    // compileSdk 34 TIDAK diubah. Revert = Boomly_v313.zip.
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Batch 49: plugin Hilt (id("com.google.dagger.hilt.android"), dipasang Batch 18)
    // DICABUT — lihat app/build.gradle.kts & CHANGELOG.md v1.86.0 untuk rasional
    // lengkap kenapa Hilt ternyata dead weight di project ini.
}
