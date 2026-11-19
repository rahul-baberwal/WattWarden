package com.rahulbaberwal.wattwarden.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

object BatteryReader {

    fun read(context: Context, cal: Calibration = Calibration()): BatterySnapshot {
        val app = context.applicationContext
        val intent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = if (rawLevel >= 0 && scale > 0) {
            rawLevel * 100 / scale
        } else {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }

        var voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        if (voltage in 1..20) voltage *= 1000 // a few devices report volts

        val tempC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        val raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa: Float? =
            if (raw == Long.MIN_VALUE || raw == Int.MIN_VALUE.toLong()) {
                null
            } else {
                val ma = when (cal.unit) {
                    CurrentUnit.MICROAMPS -> raw / 1000f
                    CurrentUnit.MILLIAMPS -> raw.toFloat()
                    // Heuristic: no phone draws/charges >20 A, so large values are microamps.
                    CurrentUnit.AUTO -> if (abs(raw) >= 20_000) raw / 1000f else raw.toFloat()
                }
                if (cal.invert) -ma else ma
            }

        var counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (counter == Long.MIN_VALUE || counter <= 0) counter = 0
        else if (counter < 20_000) counter *= 1000 // some devices report mAh instead of µAh

        return BatterySnapshot(
            time = System.currentTimeMillis(),
            level = level,
            voltageMv = voltage,
            tempC = tempC,
            plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
            status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0,
            health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0,
            currentMa = currentMa,
            chargeCounterUah = counter,
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "",
        )
    }
}
