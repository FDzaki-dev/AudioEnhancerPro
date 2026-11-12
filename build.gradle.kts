plugins {
    // Batch 314 (request user: "Evaluasi upgrade AGP/Kotlin"): 8.5.2 -> 8.6.1 = SATU variabel risiko. Gradle 8.7 (bootstrap workflow), JDK 17 dan
    // Build Tools 34 TETAP cocok (AGP 8.6: min Gradle 8.7); 8.6.1 memperbaiki 3 regresi R8 dari 8.5.x. Kotlin 1.9.24 / compose compiler 1.5.14 /
    // compileSdk 34 TIDAK diubah. Revert = Boomly_v313.zip.
    // Batch 316 (request user: pilih "AGP 8.7.x + Gradle baru"): 8.6.1 -> 8.7.3 (patch 8.7 terbaru; 8.7.2 memperbaiki bug R8 yang mengganti isi metode dengan `throw null`)
    // WAJIB berpasangan dengan Gradle 8.7 -> 8.9 (bootstrap `build.yml` + `android-artifact.yml`; AGP 8.7: min Gradle 8.9, JDK 17, Build Tools 34.0.0, compileSdk maks 35).
    // Kotlin 1.9.25 / compose compiler 1.5.15 / compileSdk 34 / detekt 1.23.6 TIDAK diubah. Revert = Boomly_v315.zip.
    id("com.android.application") version "8.7.3" apply false
    // Batch 315 (request user: pilih "Kotlin 1.9.25 + compose compiler"): 1.9.24 -> 1.9.25, WAJIB berpasangan dengan compose compiler 1.5.14 -> 1.5.15
    // (app/build.gradle.kts; peta kompatibilitas Compose-Kotlin: 1.9.25 = 1.5.15). AGP 8.6.1, Gradle 8.7, compileSdk 34 TIDAK diubah. Revert = Boomly_v314.zip.
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    // Batch 49: plugin Hilt (id("com.google.dagger.hilt.android"), dipasang Batch 18)
    // DICABUT — lihat app/build.gradle.kts & CHANGELOG.md v1.86.0 untuk rasional
    // lengkap kenapa Hilt ternyata dead weight di project ini.
}
