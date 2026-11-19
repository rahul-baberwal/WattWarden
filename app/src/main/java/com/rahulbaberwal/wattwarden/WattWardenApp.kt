package com.rahulbaberwal.wattwarden

import android.app.Application
import com.rahulbaberwal.wattwarden.data.GuardianDatabase
import com.rahulbaberwal.wattwarden.data.SettingsStore
import com.rahulbaberwal.wattwarden.service.Notifier
import com.rahulbaberwal.wattwarden.worker.DrainWorker

class WattWardenApp : Application() {
    val db: GuardianDatabase by lazy { GuardianDatabase.build(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        DrainWorker.schedule(this)
    }
}
