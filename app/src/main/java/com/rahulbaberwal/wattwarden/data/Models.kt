package com.rahulbaberwal.wattwarden.data

import android.os.BatteryManager

enum class CurrentUnit { AUTO, MICROAMPS, MILLIAMPS }

/** Per-device calibration for BATTERY_PROPERTY_CURRENT_NOW quirks. */
data class Calibration(
    val unit: CurrentUnit = CurrentUnit.AUTO,
    val invert: Boolean = false,
)

/**
 * One reading of the battery. [currentMa] is signed: positive = charging
 * (current flowing into the battery), negative = discharging. Null when the
 * device does not expose the current sensor.
 */
data class BatterySnapshot(
    val time: Long,
    val level: Int,
    val voltageMv: Int,
    val tempC: Float,
    val plugged: Int,
    val status: Int,
    val health: Int,
    val currentMa: Float?,
    val chargeCounterUah: Long,
    val technology: String,
) {
    val isPluggedIn: Boolean get() = plugged != 0

    /** Signed power in watts (positive while charging). */
    val watts: Float
        get() {
            val c = currentMa ?: return 0f
            return c * voltageMv / 1_000_000f
        }

    val chargerLabel: String
        get() = when {
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless"
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged != 0 -> "Dock"
            else -> "Battery"
        }

    val healthLabel: String
        get() = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }
}
