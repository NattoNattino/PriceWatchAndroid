package com.nattonattino.pricewatch

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

class PriceWatchApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, workManagerConfiguration)
    }
}
