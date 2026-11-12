package com.audioenhancer.booster

import android.app.Application

// Batch 49: @HiltAndroidApp (Batch 18) DICABUT bareng seluruh Hilt/kapt — lihat
// CHANGELOG.md v1.86.0. Application class ini sekarang plain lagi, tidak perlu
// jadi root dependency container apapun (satu-satunya titik inject yang pernah
// ada, Application ke BoosterViewModel, sudah didapat gratis dari AndroidViewModel
// bawaan AndroidX — lihat BoosterViewModel.kt).
class AudioEnhancerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        ServiceWatchdogWorker.schedule(this)
    }
}
