package com.rahulbaberwal.wattwarden.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("wattwarden_settings")

data class Settings(
    val guardianEnabled: Boolean = false,
    // Charge alarm
    val alarmEnabled: Boolean = true,
    val alarmLevel: Int = 80,
    // Thermal
    val tempLimitC: Int = 42,
    // Overnight trickle-charge guard
    val overnightEnabled: Boolean = true,
    val overnightMinutes: Int = 90,
    // Phantom drain
    val phantomThreshold: Float = 2f,
    // Smart-plug webhooks
    val webhookOffUrl: String = "",
    val webhookOnUrl: String = "",
    val lowLevel: Int = 30,
    // Calibration
    val currentUnit: CurrentUnit = CurrentUnit.AUTO,
    val invertCurrent: Boolean = false,
    val designCapacityMah: Int = 4500,
    // Internal
    val lastDrainAlert: Long = 0L,
) {
    val calibration: Calibration get() = Calibration(currentUnit, invertCurrent)
}

class SettingsStore(private val context: Context) {

    private object K {
        val guardian = booleanPreferencesKey("guardian")
        val alarmEnabled = booleanPreferencesKey("alarm_enabled")
        val alarmLevel = intPreferencesKey("alarm_level")
        val tempLimit = intPreferencesKey("temp_limit")
        val overnightEnabled = booleanPreferencesKey("overnight_enabled")
        val overnightMinutes = intPreferencesKey("overnight_minutes")
        val phantom = floatPreferencesKey("phantom_threshold")
        val webhookOff = stringPreferencesKey("webhook_off")
        val webhookOn = stringPreferencesKey("webhook_on")
        val lowLevel = intPreferencesKey("low_level")
        val unit = stringPreferencesKey("current_unit")
        val invert = booleanPreferencesKey("invert_current")
        val design = intPreferencesKey("design_capacity")
        val lastDrain = longPreferencesKey("last_drain_alert")
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            guardianEnabled = this[K.guardian] ?: d.guardianEnabled,
            alarmEnabled = this[K.alarmEnabled] ?: d.alarmEnabled,
            alarmLevel = this[K.alarmLevel] ?: d.alarmLevel,
            tempLimitC = this[K.tempLimit] ?: d.tempLimitC,
            overnightEnabled = this[K.overnightEnabled] ?: d.overnightEnabled,
            overnightMinutes = this[K.overnightMinutes] ?: d.overnightMinutes,
            phantomThreshold = this[K.phantom] ?: d.phantomThreshold,
            webhookOffUrl = this[K.webhookOff] ?: d.webhookOffUrl,
            webhookOnUrl = this[K.webhookOn] ?: d.webhookOnUrl,
            lowLevel = this[K.lowLevel] ?: d.lowLevel,
            currentUnit = this[K.unit]?.let { runCatching { CurrentUnit.valueOf(it) }.getOrNull() }
                ?: d.currentUnit,
            invertCurrent = this[K.invert] ?: d.invertCurrent,
            designCapacityMah = this[K.design] ?: d.designCapacityMah,
            lastDrainAlert = this[K.lastDrain] ?: d.lastDrainAlert,
        )
    }

    val flow: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    /** Atomic read-modify-write so the UI, service and worker never clobber each other. */
    suspend fun update(block: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = block(p.toSettings())
            p[K.guardian] = s.guardianEnabled
            p[K.alarmEnabled] = s.alarmEnabled
            p[K.alarmLevel] = s.alarmLevel
            p[K.tempLimit] = s.tempLimitC
            p[K.overnightEnabled] = s.overnightEnabled
            p[K.overnightMinutes] = s.overnightMinutes
            p[K.phantom] = s.phantomThreshold
            p[K.webhookOff] = s.webhookOffUrl
            p[K.webhookOn] = s.webhookOnUrl
            p[K.lowLevel] = s.lowLevel
            p[K.unit] = s.currentUnit.name
            p[K.invert] = s.invertCurrent
            p[K.design] = s.designCapacityMah
            p[K.lastDrain] = s.lastDrainAlert
        }
    }
}
