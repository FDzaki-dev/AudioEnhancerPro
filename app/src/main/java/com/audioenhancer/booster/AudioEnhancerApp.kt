package com.audioenhancer.booster

import android.app.Application

class AudioEnhancerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        ServiceWatchdogWorker.schedule(this)
    }
}
