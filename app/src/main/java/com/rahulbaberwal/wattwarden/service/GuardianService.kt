package com.rahulbaberwal.wattwarden.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.rahulbaberwal.wattwarden.WattWardenApp
import com.rahulbaberwal.wattwarden.data.Analytics
import com.rahulbaberwal.wattwarden.data.BatteryReader
import com.rahulbaberwal.wattwarden.data.BatterySnapshot
import com.rahulbaberwal.wattwarden.data.ChargeSessionTracker
import com.rahulbaberwal.wattwarden.data.DrainEntity
import com.rahulbaberwal.wattwarden.data.Settings
import com.rahulbaberwal.wattwarden.widget.BatteryWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs

/**
 * Foreground service that:
 *  - polls hardware telemetry every few seconds while plugged in (slower with screen off)
 *  - sleeps until a battery/power/screen broadcast arrives while on battery
 *  - records charge sessions, fires charge/heat/trickle alerts and smart-plug webhooks
 */
class GuardianService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null

    private lateinit var app: WattWardenApp
    private lateinit var tracker: ChargeSessionTracker
    private lateinit var pm: PowerManager

    // Alert state
    private var alarmFired = false
    private var lastTempAlert = 0L
    private var fullSince = 0L
    private var overnightFired = false
    private var lowFired = false
    private var lastWidgetUpdate = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> logDrain(false)
                Intent.ACTION_SCREEN_ON -> logDrain(true)
            }
            wake.trySend(Unit)
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = application as WattWardenApp
        tracker = ChargeSessionTracker(app.db.dao())
        pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                app.settings.update { it.copy(guardianEnabled = false) }
                stopForegroundCompat()
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val first = BatteryReader.read(this)
        ServiceCompat.startForeground(
            this, Notifier.ID_LIVE, Notifier.live(this, first, null, null),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (loop?.isActive != true) loop = scope.launch { runLoop() }
        return START_STICKY
    }

    private fun stopForegroundCompat() =
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

    private suspend fun runLoop() {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        while (scope.isActive) {
            val s = app.settings.flow.first()
            val snap = BatteryReader.read(this, s.calibration)
            val capacity = Analytics.effectiveCapacity(app.db.dao().recentEstimates(), s.designCapacityMah)

            if (snap.isPluggedIn) {
                tracker.onCharging(snap, s.designCapacityMah)
                handleChargingAlerts(snap, s)
                lowFired = false
            } else {
                if (tracker.active) tracker.finish(snap)
                resetChargingState()
                handleLowLevel(snap, s)
            }

            val eta80 = if (snap.isPluggedIn) Analytics.etaMinutes(snap, capacity, 80) else null
            val eta100 = if (snap.isPluggedIn) Analytics.etaMinutes(snap, capacity, 100) else null
            nm.notify(Notifier.ID_LIVE, Notifier.live(this, snap, eta80, eta100))

            val now = System.currentTimeMillis()
            if (now - lastWidgetUpdate > 60_000) {
                lastWidgetUpdate = now
                runCatching { BatteryWidget().updateAll(applicationContext) }
            }

            if (snap.isPluggedIn) {
                delay(if (pm.isInteractive) 3_000 else 15_000)
            } else {
                // Sleep until a broadcast (battery change, plug, screen) or a 5-minute safety tick.
                withTimeoutOrNull(300_000) { wake.receive() }
            }
        }
    }

    private suspend fun handleChargingAlerts(snap: BatterySnapshot, s: Settings) {
        val now = snap.time

        // 1) Charge-limit alarm + smart-plug "off" webhook
        if (s.alarmEnabled && snap.level >= s.alarmLevel && !alarmFired) {
            alarmFired = true
            Notifier.alert(
                this, Notifier.ID_ALARM, "Charge limit reached: ${snap.level}%",
                "Unplug now to reduce lithium-ion wear.",
            )
            if (s.webhookOffUrl.isNotBlank()) {
                val ok = Webhook.fire(
                    s.webhookOffUrl,
                    mapOf("event" to "charge_limit", "level" to snap.level, "temp_c" to snap.tempC),
                )
                Notifier.alert(
                    this, Notifier.ID_WEBHOOK,
                    if (ok) "Smart plug: power cut sent" else "Smart plug webhook failed",
                    if (ok) "Charge-limit webhook delivered." else "Check the URL and your network.",
                )
            }
        }
        if (snap.level < s.alarmLevel - 3) alarmFired = false

        // 2) Thermal alert
        if (snap.tempC >= s.tempLimitC && now - lastTempAlert > 10 * 60_000L) {
            lastTempAlert = now
            Notifier.alert(
                this, Notifier.ID_TEMP,
                String.format(Locale.US, "Battery hot: %.1f°C", snap.tempC),
                "Stop heavy use or unplug the fast charger to cool down.",
            )
        }

        // 3) Overnight trickle-charge guard
        if (snap.level >= 100) {
            if (fullSince == 0L) fullSince = now
            if (s.overnightEnabled && !overnightFired && now - fullSince >= s.overnightMinutes * 60_000L) {
                overnightFired = true
                Notifier.alert(
                    this, Notifier.ID_OVERNIGHT, "Pinned at 100%",
                    "Plugged in at full charge for ${s.overnightMinutes}+ minutes. Unplug to avoid trickle-charge wear.",
                )
                if (s.webhookOffUrl.isNotBlank()) {
                    Webhook.fire(s.webhookOffUrl, mapOf("event" to "trickle_guard", "level" to snap.level))
                }
            }
        } else {
            fullSince = 0L
            overnightFired = false
        }
    }

    /** Smart-plug "on" webhook when the battery drops to the low threshold. */
    private suspend fun handleLowLevel(snap: BatterySnapshot, s: Settings) {
        if (s.webhookOnUrl.isNotBlank() && snap.level <= s.lowLevel && !lowFired) {
            lowFired = true
            val ok = Webhook.fire(s.webhookOnUrl, mapOf("event" to "low_level", "level" to snap.level))
            Notifier.alert(
                this, Notifier.ID_WEBHOOK,
                if (ok) "Smart plug: power on sent" else "Smart plug webhook failed",
                "Battery is at ${snap.level}%.",
            )
        }
        if (snap.level > s.lowLevel + 5) lowFired = false
    }

    private fun resetChargingState() {
        alarmFired = false
        fullSince = 0L
        overnightFired = false
    }

    private fun logDrain(screenOn: Boolean) {
        scope.launch {
            val snap = BatteryReader.read(this@GuardianService)
            app.db.dao().insertDrain(
                DrainEntity(
                    time = System.currentTimeMillis(), level = snap.level,
                    screenOn = screenOn, charging = snap.isPluggedIn,
                )
            )
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.rahulbaberwal.wattwarden.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GuardianService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardianService::class.java))
        }
    }
}
