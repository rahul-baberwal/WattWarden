package com.rahulbaberwal.wattwarden.worker

import android.content.Context
import android.os.PowerManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rahulbaberwal.wattwarden.WattWardenApp
import com.rahulbaberwal.wattwarden.data.BatteryReader
import com.rahulbaberwal.wattwarden.data.DrainAnalyzer
import com.rahulbaberwal.wattwarden.data.DrainEntity
import com.rahulbaberwal.wattwarden.service.Notifier
import com.rahulbaberwal.wattwarden.widget.BatteryWidget
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Runs every ~15 min even when the foreground service is not running.
 * Records a battery sample, detects phantom drain, and refreshes the widget.
 */
class DrainWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as WattWardenApp
        val dao = app.db.dao()
        val s = app.settings.flow.first()
        val snap = BatteryReader.read(applicationContext, s.calibration)
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val now = System.currentTimeMillis()

        dao.insertDrain(
            DrainEntity(time = now, level = snap.level, screenOn = pm.isInteractive, charging = snap.isPluggedIn)
        )
        dao.pruneDrain(now - 14 * DAY)

        val stats = DrainAnalyzer.analyze(dao.drainSince(now - 24 * HOUR))
        val idle = stats.idlePctPerHr
        if (idle != null && idle > s.phantomThreshold && now - s.lastDrainAlert > 6 * HOUR) {
            Notifier.alert(
                applicationContext, Notifier.ID_DRAIN, "Phantom drain detected",
                String.format(
                    Locale.US,
                    "Losing %.1f%%/h with the screen off (limit %.1f%%/h). Check Settings > Battery usage for a wakelock-heavy app.",
                    idle, s.phantomThreshold,
                ),
            )
            app.settings.update { it.copy(lastDrainAlert = now) }
        }

        runCatching { BatteryWidget().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val HOUR = 3_600_000L
        private const val DAY = 24 * HOUR

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "drain_sampler",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DrainWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
