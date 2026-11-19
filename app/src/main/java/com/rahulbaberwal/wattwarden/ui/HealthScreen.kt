package com.rahulbaberwal.wattwarden.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahulbaberwal.wattwarden.data.Analytics
import java.util.Locale

@Composable
fun HealthScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val estimates by vm.estimates.collectAsStateWithLifecycle()
    val drain by vm.drainStats.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val cfg = settings ?: return

    val health = Analytics.healthPercent(estimates, cfg.designCapacityMah)
    val measured = Analytics.median(estimates)
    val cycles = Analytics.totalCycles(sessions)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Battery health", fontSize = 22.sp, color = WW.Text, style = MaterialTheme.typography.titleLarge)

        // ---- Health ring ----
        Panel {
            Label("Estimated health")
            val color = when {
                health == null -> WW.Muted
                health >= 85 -> WW.Mint
                health >= 70 -> WW.Amber
                else -> WW.Red
            }
            ArcGauge(
                fraction = (health ?: 0) / 100f,
                color = color,
                modifier = Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Mono(health?.let { "$it%" } ?: "--", 40.sp, color)
                    Text("of design", color = WW.Muted, fontSize = 12.sp)
                }
            }
            Text(
                if (measured != null)
                    "Measured ${"%,d".format(measured)} mAh vs ${"%,d".format(cfg.designCapacityMah)} mAh design. " +
                        "Estimate = median of your last ${estimates.size} qualifying charges (±5%)."
                else
                    "Not measured yet. With Guardian on, charge across at least a 20% span " +
                        "(e.g. 30% → 55%) and WattWarden will integrate the coulomb counter to estimate real capacity.",
                style = MaterialTheme.typography.bodySmall, color = WW.Muted,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Charge cycles", String.format(Locale.US, "%.1f", cycles),
                "equivalent full cycles", WW.Mint, Modifier.weight(1f),
            )
            StatTile(
                "Sessions", "${sessions.size}",
                "logged", WW.Text, Modifier.weight(1f),
            )
        }

        // ---- Drain analysis ----
        Panel {
            Label("Drain analysis · last 3 days")
            val d = drain
            if (d == null || (d.idlePctPerHr == null && d.screenOnPctPerHr == null)) {
                Text(
                    "Collecting data… needs about an hour of unplugged idle time and an hour of screen-on time. " +
                        "Samples are taken every 15 minutes, and on every screen on/off while Guardian runs.",
                    color = WW.Muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Label("Screen off")
                        val idle = d.idlePctPerHr
                        Mono(
                            idle?.let { String.format(Locale.US, "%.1f%%/h", it) } ?: "--", 22.sp,
                            if (idle != null && idle > cfg.phantomThreshold) WW.Red else WW.Mint,
                        )
                        Text(
                            "limit ${"%.1f".format(Locale.US, cfg.phantomThreshold)}%/h",
                            color = WW.Muted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Label("Screen on")
                        Mono(
                            d.screenOnPctPerHr?.let { String.format(Locale.US, "%.1f%%/h", it) } ?: "--",
                            22.sp, WW.Amber,
                        )
                        val left = live?.let { d.screenOnHoursLeft(it.level) }
                        Text(
                            left?.let { "≈ ${Analytics.formatMinutes((it * 60).toInt())} left" } ?: "",
                            color = WW.Muted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    String.format(
                        Locale.US, "Based on %.1f h idle and %.1f h screen-on.",
                        d.idleHours, d.screenOnHours,
                    ),
                    color = WW.Muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // ---- Charger comparison ----
        Panel {
            Label("Charger & cable comparison")
            val groups = sessions
                .filter { it.label.isNotBlank() && it.peakWatts > 0f }
                .groupBy { it.label }
                .map { (name, list) ->
                    Triple(name, list.maxOf { it.peakWatts }, list.map { it.avgWatts }.average().toFloat() to list.size)
                }
                .sortedByDescending { it.second }
            if (groups.isEmpty()) {
                Text(
                    "Name a session in History (e.g. “Anker 65W + blue cable”) and your chargers will be ranked here by peak power.",
                    color = WW.Muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                val best = groups.first().second
                groups.forEach { (name, peak, avgAndCount) ->
                    Column(Modifier.padding(top = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, color = WW.Text, fontSize = 14.sp)
                            Mono(String.format(Locale.US, "%.1f W", peak), 14.sp, WW.Mint)
                        }
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { if (best > 0) peak / best else 0f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            color = WW.Mint, trackColor = WW.SurfaceHi,
                        )
                        Text(
                            String.format(Locale.US, "avg %.1f W · %d session(s)", avgAndCount.first, avgAndCount.second),
                            color = WW.Muted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
