package com.d4vram.pillmate2

import android.app.Application

class PillMateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
