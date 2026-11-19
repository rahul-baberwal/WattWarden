package com.rahulbaberwal.wattwarden.data

import kotlin.math.max
import kotlin.math.roundToInt

object Analytics {

    fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        return values.sorted()[values.size / 2]
    }

    /** Estimated battery health as a percentage of design capacity. */
    fun healthPercent(estimates: List<Int>, designMah: Int): Int? =
        median(estimates)?.let { (it * 100f / designMah).roundToInt().coerceIn(0, 110) }

    /** Capacity used for ETAs: measured when known, otherwise design capacity. */
    fun effectiveCapacity(estimates: List<Int>, designMah: Int): Int =
        median(estimates) ?: designMah

    fun totalCycles(sessions: List<SessionEntity>): Float =
        sessions.sumOf { it.cycleFraction.toDouble() }.toFloat()

    /**
     * Minutes until [target]% while charging. Charge current tapers above 80%,
     * so the part of the climb above 80% is weighted 2x.
     */
    fun etaMinutes(snap: BatterySnapshot, capacityMah: Int, target: Int): Int? {
        val current = snap.currentMa ?: return null
        if (current < 100f || snap.level >= target) return null
        val below = (minOf(target, 80) - snap.level).coerceAtLeast(0)
        val above = (target - max(snap.level, 80)).coerceAtLeast(0)
        val weightedPct = below + above * 2f
        val mah = capacityMah * weightedPct / 100f
        return (mah / current * 60f).roundToInt()
    }

    fun formatMinutes(minutes: Int): String =
        if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

    fun formatDuration(ms: Long): String = formatMinutes((ms / 60_000).toInt().coerceAtLeast(0))
}

data class DrainStats(
    val idlePctPerHr: Float?,
    val idleHours: Float,
    val screenOnPctPerHr: Float?,
    val screenOnHours: Float,
) {
    fun screenOnHoursLeft(level: Int): Float? =
        screenOnPctPerHr?.takeIf { it > 0.1f }?.let { level / it }
}

object DrainAnalyzer {
    private const val MAX_GAP_MS = 60 * 60_000L
    const val MIN_HOURS = 1.0f

    /** Pairs consecutive unplugged samples and attributes the drop to idle or screen-on time. */
    fun analyze(samples: List<DrainEntity>): DrainStats {
        var idleDrop = 0f
        var idleMs = 0L
        var onDrop = 0f
        var onMs = 0L
        for (k in 0 until samples.size - 1) {
            val a = samples[k]
            val b = samples[k + 1]
            val gap = b.time - a.time
            if (a.charging || b.charging || gap <= 0 || gap > MAX_GAP_MS) continue
            val drop = max(0, a.level - b.level).toFloat()
            when {
                !a.screenOn && !b.screenOn -> { idleDrop += drop; idleMs += gap }
                a.screenOn && b.screenOn -> { onDrop += drop; onMs += gap }
            }
        }
        val idleH = idleMs / 3_600_000f
        val onH = onMs / 3_600_000f
        return DrainStats(
            idlePctPerHr = if (idleH >= MIN_HOURS) idleDrop / idleH else null,
            idleHours = idleH,
            screenOnPctPerHr = if (onH >= MIN_HOURS) onDrop / onH else null,
            screenOnHours = onH,
        )
    }
}
