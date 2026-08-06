package com.audioenhancer.booster

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Batch 18: @HiltAndroidApp memicu Hilt generate Application-level dependency container
// (root semua @Inject di app ini, termasuk BoosterViewModel). WAJIB ada di Application
// class — tanpa ini, @AndroidEntryPoint di MainActivity & @HiltViewModel di
// BoosterViewModel gagal resolve saat runtime (bukan error compile, tapi crash startup).
@HiltAndroidApp
class AudioEnhancerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        ServiceWatchdogWorker.schedule(this)
    }
}
