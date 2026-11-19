package com.rahulbaberwal.wattwarden.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rahulbaberwal.wattwarden.MainActivity
import com.rahulbaberwal.wattwarden.R
import com.rahulbaberwal.wattwarden.data.BatterySnapshot
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Notifier {
    const val CH_LIVE = "live"
    const val CH_ALERT = "alerts"

    const val ID_LIVE = 1
    const val ID_ALARM = 2
    const val ID_TEMP = 3
    const val ID_OVERNIGHT = 4
    const val ID_DRAIN = 5
    const val ID_WEBHOOK = 6

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_LIVE, "Live status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing power, temperature and ETA while Guardian runs"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Charge limit, heat, trickle-charge and phantom drain alerts"
                enableVibration(true)
            }
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun live(context: Context, snap: BatterySnapshot, eta80: Int?, eta100: Int?): Notification {
        val stop = PendingIntent.getService(
            context, 1,
            Intent(context, GuardianService::class.java).setAction(GuardianService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val charging = snap.isPluggedIn && (snap.currentMa ?: 1f) > 0f
        val temp = String.format(Locale.US, "%.1f°C", snap.tempC)
        val volt = String.format(Locale.US, "%.2f V", snap.voltageMv / 1000f)

        val title: String
        val text: String
        if (snap.isPluggedIn) {
            val w = String.format(Locale.US, "%.1f W", abs(snap.watts))
            title = if (charging) "$w · ${snap.level}%" else "Plugged in · ${snap.level}%"
            val etas = listOfNotNull(
                eta80?.let { "80% in ${com.rahulbaberwal.wattwarden.data.Analytics.formatMinutes(it)}" },
                eta100?.let { "100% in ${com.rahulbaberwal.wattwarden.data.Analytics.formatMinutes(it)}" },
            )
            text = (listOf(temp, volt) + etas).joinToString(" · ")
        } else {
            title = "Guardian watching · ${snap.level}%"
            val ma = snap.currentMa?.let { "${abs(it).roundToInt()} mA drain · " } ?: ""
            text = "$ma$temp"
        }
        return NotificationCompat.Builder(context, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .addAction(0, "Stop Guardian", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun alert(context: Context, id: Int, title: String, text: String) {
        val n = NotificationCompat.Builder(context, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted; nothing to do.
        }
    }
}
