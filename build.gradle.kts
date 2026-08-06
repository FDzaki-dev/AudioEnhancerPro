plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Batch 18: Hilt DI (audit High #3). Versi 2.51.1 dipilih karena kompatibel dengan
    // Kotlin 1.9.24 + AGP 8.5.2 yang sudah dipakai project ini (bukan versi terbaru
    // sembarangan — versi baru Hilt kadang butuh Kotlin/KSP lebih baru).
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}
