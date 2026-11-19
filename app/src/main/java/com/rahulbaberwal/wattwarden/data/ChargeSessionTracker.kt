package com.rahulbaberwal.wattwarden.data

import kotlin.math.max

/** Builds a [SessionEntity] + time-series [SampleEntity] rows while the phone is plugged in. */
class ChargeSessionTracker(private val dao: GuardianDao) {

    private var id = 0L
    private var first: BatterySnapshot? = null
    private var peak = 0f
    private var sum = 0.0
    private var count = 0
    private var maxTemp = 0f
    private var lastSample = 0L
    private var lastPersist = 0L
    private var designMah = 4500

    val active: Boolean get() = first != null
    val peakWatts: Float get() = peak

    suspend fun onCharging(s: BatterySnapshot, designMah: Int) {
        this.designMah = designMah
        if (first == null) {
            first = s
            peak = 0f; sum = 0.0; count = 0; maxTemp = s.tempC
            lastSample = 0L; lastPersist = s.time
            id = dao.insertSession(
                SessionEntity(
                    startTime = s.time, endTime = s.time,
                    startLevel = s.level, endLevel = s.level,
                    startCounterUah = s.chargeCounterUah, endCounterUah = s.chargeCounterUah,
                    charger = s.chargerLabel,
                )
            )
        }
        val w = max(0f, s.watts)
        peak = max(peak, w)
        sum += w
        count++
        maxTemp = max(maxTemp, s.tempC)

        if (s.time - lastSample >= 10_000) {
            dao.insertSample(
                SampleEntity(
                    sessionId = id, time = s.time, level = s.level,
                    currentMa = s.currentMa ?: 0f, voltageMv = s.voltageMv,
                    tempC = s.tempC, watts = s.watts,
                )
            )
            lastSample = s.time
        }
        if (s.time - lastPersist >= 30_000) {
            persist(s)
            lastPersist = s.time
        }
    }

    /** Called when the charger is removed. Drops accidental blips (<1 min, no gain). */
    suspend fun finish(s: BatterySnapshot) {
        val f = first ?: return
        if (s.time - f.time < 60_000 && s.level <= f.level) {
            dao.deleteSamples(id)
            dao.deleteSession(id)
        } else {
            persist(s)
        }
        first = null
    }

    private suspend fun persist(s: BatterySnapshot) {
        val f = first ?: return
        val delta = s.level - f.level
        var est: Int? = null
        // Need a meaningful span (>=20%) for the coulomb-counter estimate to be trustworthy.
        if (delta >= 20 && f.chargeCounterUah > 0 && s.chargeCounterUah > f.chargeCounterUah) {
            val mah = (s.chargeCounterUah - f.chargeCounterUah) / 1000f / (delta / 100f)
            if (mah in designMah * 0.3f..designMah * 1.5f) est = mah.toInt()
        }
        dao.updateSessionStats(
            id = id,
            endTime = s.time,
            endLevel = s.level,
            peak = peak,
            avg = if (count > 0) (sum / count).toFloat() else 0f,
            maxTemp = maxTemp,
            endCounter = s.chargeCounterUah,
            est = est,
            cycle = max(0, delta) / 100f,
        )
    }
}
