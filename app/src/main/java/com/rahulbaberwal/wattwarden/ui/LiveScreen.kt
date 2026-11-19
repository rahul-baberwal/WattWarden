package com.rahulbaberwal.wattwarden.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahulbaberwal.wattwarden.R
import com.rahulbaberwal.wattwarden.data.Analytics
import com.rahulbaberwal.wattwarden.data.BatterySnapshot
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun LiveScreen(vm: MainViewModel) {
    val snap by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val samples by vm.lastSessionSamples.collectAsStateWithLifecycle()
    val estimates by vm.estimates.collectAsStateWithLifecycle()
    val s = snap
    val cfg = settings

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header()

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Guardian", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        if (cfg?.guardianEnabled == true) "Logging sessions and guarding limits"
                        else "Turn on to log sessions and enable alerts",
                        style = MaterialTheme.typography.bodySmall, color = WW.Muted,
                    )
                }
                Switch(
                    checked = cfg?.guardianEnabled == true,
                    onCheckedChange = { vm.setGuardian(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = WW.Mint, checkedThumbColor = WW.Navy),
                )
            }
        }

        if (s == null) {
            Text("Reading sensors…", color = WW.Muted)
            return@Column
        }

        val capacity = vm.capacityMah(estimates, cfg)
        val chargingNow = s.isPluggedIn && (s.currentMa ?: 1f) > 0f
        val sessionPeak = samples.maxOfOrNull { it.watts } ?: 0f

        // ---- Gauge ----
        val gaugeMax = max(20f, ceil(max(sessionPeak, abs(s.watts)) / 10f) * 10f)
        val gaugeColor = when {
            s.tempC >= (cfg?.tempLimitC ?: 42) -> WW.Red
            chargingNow -> WW.Mint
            else -> WW.Amber
        }
        val mode = when {
            s.level >= 100 && s.isPluggedIn -> "Full · ${s.chargerLabel}"
            chargingNow -> "Charging · ${s.chargerLabel}"
            s.isPluggedIn -> "Plugged in · not charging"
            else -> "On battery"
        }
        Panel {
            Label(mode)
            ArcGauge(
                fraction = abs(s.watts) / gaugeMax,
                color = gaugeColor,
                modifier = Modifier.fillMaxWidth(0.85f).align(Alignment.CenterHorizontally),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Mono(String.format(Locale.US, "%.1f", abs(s.watts)), 52.sp, gaugeColor)
                    Text("WATTS", color = WW.Muted, fontSize = 12.sp, letterSpacing = 2.sp)
                    Spacer8()
                    Text(
                        s.currentMa?.let {
                            String.format(Locale.US, "%+d mA · %.2f V", it.roundToInt(), s.voltageMv / 1000f)
                        } ?: "Current sensor unavailable",
                        color = WW.Text, fontSize = 13.sp,
                    )
                }
            }
            Text(
                "Scale 0–${gaugeMax.roundToInt()} W · wrong numbers or sign? Fix it in Settings › Calibration",
                style = MaterialTheme.typography.bodySmall, color = WW.Muted,
            )
        }

        // ---- Tiles ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BatteryTile(s, capacity, chargingNow, Modifier.weight(1f))
            val limit = cfg?.tempLimitC ?: 42
            val tColor = when {
                s.tempC >= limit -> WW.Red
                s.tempC >= limit - 4 -> WW.Amber
                else -> WW.Mint
            }
            StatTile(
                "Temperature", String.format(Locale.US, "%.1f°C", s.tempC),
                "Health: ${s.healthLabel}", tColor, Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Session peak", String.format(Locale.US, "%.1f W", sessionPeak),
                if (s.isPluggedIn) "this session" else "last session", WW.Amber, Modifier.weight(1f),
            )
            StatTile(
                "Capacity", "$capacity mAh",
                if (estimates.isEmpty()) "design value" else "measured", WW.Text, Modifier.weight(1f),
            )
        }

        // ---- Chart ----
        var metric by remember { mutableIntStateOf(0) }
        Panel {
            Label(if (s.isPluggedIn) "This session" else "Last session")
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Power", "Temp", "Voltage").forEachIndexed { i, name ->
                    FilterChip(selected = metric == i, onClick = { metric = i }, label = { Text(name) })
                }
            }
            val (values, color, unit) = when (metric) {
                0 -> Triple(samples.map { it.watts }, WW.Mint, "W")
                1 -> Triple(samples.map { it.tempC }, WW.Amber, "°C")
                else -> Triple(samples.map { it.voltageMv / 1000f }, WW.Text, "V")
            }
            if (values.size < 2) {
                Text(
                    "Plug in with Guardian on to record a charging curve.",
                    color = WW.Muted, style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    String.format(Locale.US, "min %.1f · max %.1f %s", values.min(), values.max(), unit),
                    color = WW.Muted, style = MaterialTheme.typography.bodySmall,
                )
                LineChart(values, color, Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun BatteryTile(s: BatterySnapshot, capacity: Int, charging: Boolean, modifier: Modifier) {
    val e80 = if (charging) Analytics.etaMinutes(s, capacity, 80) else null
    val e100 = if (charging) Analytics.etaMinutes(s, capacity, 100) else null
    val sub = when {
        !charging -> s.technology.ifBlank { "Battery" }
        else -> listOfNotNull(
            e80?.let { "80% in ${Analytics.formatMinutes(it)}" },
            e100?.let { "100% in ${Analytics.formatMinutes(it)}" },
        ).joinToString(" · ").ifBlank { "Charged" }
    }
    StatTile("Battery", "${s.level}%", sub, WW.Mint, modifier)
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(40.dp),
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text("WattWarden", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WW.Text)
            Text("Battery & charging guardian", fontSize = 12.sp, color = WW.Muted)
        }
    }
}
