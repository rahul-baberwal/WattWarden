package com.rahulbaberwal.wattwarden

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rahulbaberwal.wattwarden.service.GuardianService
import com.rahulbaberwal.wattwarden.worker.DrainWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restores the sampler and (if the user had it on) the Guardian service after reboot/update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as WattWardenApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                DrainWorker.schedule(context)
                if (app.settings.flow.first().guardianEnabled) GuardianService.start(context)
            } catch (e: Exception) {
                // Starting a foreground service from the background can be refused on some OEMs.
            } finally {
                pending.finish()
            }
        }
    }
}
