package com.rahulbaberwal.wattwarden.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rahulbaberwal.wattwarden.WattWardenApp
import com.rahulbaberwal.wattwarden.data.Analytics
import com.rahulbaberwal.wattwarden.data.BatteryReader
import com.rahulbaberwal.wattwarden.data.BatterySnapshot
import com.rahulbaberwal.wattwarden.data.Calibration
import com.rahulbaberwal.wattwarden.data.DrainAnalyzer
import com.rahulbaberwal.wattwarden.data.DrainStats
import com.rahulbaberwal.wattwarden.data.SampleEntity
import com.rahulbaberwal.wattwarden.data.SessionEntity
import com.rahulbaberwal.wattwarden.data.Settings
import com.rahulbaberwal.wattwarden.service.GuardianService
import com.rahulbaberwal.wattwarden.service.Webhook
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WattWardenApp
    private val dao = app.db.dao()

    /** Null until DataStore has loaded, so text fields never initialise from defaults. */
    val settings: StateFlow<Settings?> = app.settings.flow
        .map<Settings, Settings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Live battery reading, refreshed every second while the UI is visible. */
    val live: StateFlow<BatterySnapshot?> = app.settings.flow
        .map { it.calibration }
        .distinctUntilChanged()
        .flatMapLatest { cal: Calibration ->
            flow {
                while (true) {
                    emit(BatteryReader.read(app, cal))
                    delay(1_000)
                }
            }
        }
        .map<BatterySnapshot, BatterySnapshot?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sessions: StateFlow<List<SessionEntity>> = dao.sessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastSessionSamples: StateFlow<List<SampleEntity>> = dao.latestSessionSamples()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val estimates: StateFlow<List<Int>> = dao.recentEstimatesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val drainStats: StateFlow<DrainStats?> = flow {
        while (true) {
            emit(DrainAnalyzer.analyze(dao.drainSince(System.currentTimeMillis() - 3 * 24 * 3_600_000L)))
            delay(30_000)
        }
    }
        .map<DrainStats, DrainStats?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun samples(sessionId: Long): Flow<List<SampleEntity>> = dao.samples(sessionId)

    fun capacityMah(estimates: List<Int>, settings: Settings?): Int =
        Analytics.effectiveCapacity(estimates, settings?.designCapacityMah ?: 4500)

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { app.settings.update(transform) }
    }

    fun setGuardian(on: Boolean) {
        update { it.copy(guardianEnabled = on) }
        if (on) GuardianService.start(app) else GuardianService.stop(app)
    }

    fun setLabel(id: Long, label: String) {
        viewModelScope.launch { dao.setLabel(id, label.trim()) }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            dao.deleteSamples(id)
            dao.deleteSession(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearSamples()
            dao.clearSessions()
            dao.clearDrain()
        }
    }

    fun testWebhook(url: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = Webhook.fire(url, mapOf("event" to "test", "source" to "WattWarden"))
            onResult(ok)
        }
    }

    /** Ensures the service is running if the user left Guardian on. */
    fun ensureServiceIfEnabled() {
        viewModelScope.launch {
            if (app.settings.flow.first().guardianEnabled) GuardianService.start(app)
        }
    }
}
