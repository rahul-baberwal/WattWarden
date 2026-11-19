package com.rahulbaberwal.wattwarden.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahulbaberwal.wattwarden.data.CurrentUnit
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val ctx = LocalContext.current

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", fontSize = 22.sp, color = WW.Text, style = MaterialTheme.typography.titleLarge)

        // ---- Charge alarm ----
        Panel {
            Label("Charge limit alarm")
            SwitchRow("Alert at limit", "Notify when charging reaches the level below", s.alarmEnabled) {
                vm.update { st -> st.copy(alarmEnabled = it) }
            }
            SliderRow("Limit", s.alarmLevel.toFloat(), 50f..100f, 49, { "${it.roundToInt()}%" }) {
                vm.update { st -> st.copy(alarmLevel = it.roundToInt()) }
            }
        }

        // ---- Thermal ----
        Panel {
            Label("Thermal monitor")
            SliderRow("Heat alert at", s.tempLimitC.toFloat(), 35f..50f, 14, { "${it.roundToInt()}°C" }) {
                vm.update { st -> st.copy(tempLimitC = it.roundToInt()) }
            }
        }

        // ---- Overnight ----
        Panel {
            Label("Trickle-charge guard")
            SwitchRow("Pinned-at-100% alert", "Warn if the phone sits full on the charger", s.overnightEnabled) {
                vm.update { st -> st.copy(overnightEnabled = it) }
            }
            SliderRow("After", s.overnightMinutes.toFloat(), 15f..240f, 14, { "${it.roundToInt()} min" }) {
                vm.update { st -> st.copy(overnightMinutes = it.roundToInt()) }
            }
        }

        // ---- Phantom drain ----
        Panel {
            Label("Phantom drain")
            SliderRow(
                "Alert above", s.phantomThreshold, 0.5f..6f, 10,
                { String.format(Locale.US, "%.1f%%/h", it) },
            ) { vm.update { st -> st.copy(phantomThreshold = (it * 2).roundToInt() / 2f) } }
            Text(
                "Measured only while the screen is off and the phone is unplugged.",
                color = WW.Muted, style = MaterialTheme.typography.bodySmall,
            )
        }

        // ---- Webhooks ----
        Panel {
            Label("Smart-plug webhooks")
            Text(
                "POST JSON to Home Assistant, IFTTT or any HTTP endpoint.",
                color = WW.Muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            UrlField("Power-OFF URL (fires at charge limit)", s.webhookOffUrl) {
                vm.update { st -> st.copy(webhookOffUrl = it.trim()) }
            }
            UrlField("Power-ON URL (fires at low level)", s.webhookOnUrl) {
                vm.update { st -> st.copy(webhookOnUrl = it.trim()) }
            }
            SliderRow("Low level", s.lowLevel.toFloat(), 5f..60f, 10, { "${it.roundToInt()}%" }) {
                vm.update { st -> st.copy(lowLevel = it.roundToInt()) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = s.webhookOffUrl.isNotBlank(),
                    onClick = {
                        vm.testWebhook(s.webhookOffUrl) {
                            Toast.makeText(ctx, if (it) "OFF webhook OK" else "OFF webhook failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text("Test OFF") }
                OutlinedButton(
                    enabled = s.webhookOnUrl.isNotBlank(),
                    onClick = {
                        vm.testWebhook(s.webhookOnUrl) {
                            Toast.makeText(ctx, if (it) "ON webhook OK" else "ON webhook failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text("Test ON") }
            }
        }

        // ---- Calibration ----
        Panel {
            Label("Calibration")
            Text(
                "Some OEMs (Xiaomi, Samsung, OnePlus…) report current in µA, or flip the sign.",
                color = WW.Muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    CurrentUnit.AUTO to "Auto",
                    CurrentUnit.MICROAMPS to "µA",
                    CurrentUnit.MILLIAMPS to "mA",
                ).forEach { (unit, name) ->
                    FilterChip(
                        selected = s.currentUnit == unit,
                        onClick = { vm.update { st -> st.copy(currentUnit = unit) } },
                        label = { Text(name) },
                    )
                }
            }
            SwitchRow("Invert current sign", "Enable if charging shows negative", s.invertCurrent) {
                vm.update { st -> st.copy(invertCurrent = it) }
            }
            NumberField("Design capacity (mAh)", s.designCapacityMah) {
                if (it in 1000..20000) vm.update { st -> st.copy(designCapacityMah = it) }
            }
        }

        // ---- System ----
        Panel {
            Label("System")
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Exempt WattWarden from battery optimisation") }
            OutlinedButton(
                onClick = {
                    vm.clearHistory()
                    Toast.makeText(ctx, "History cleared", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Clear all history", color = WW.Red) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = WW.Text, fontSize = 15.sp)
            Text(subtitle, color = WW.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = WW.Mint, checkedThumbColor = WW.Navy),
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = WW.Text, fontSize = 14.sp)
            Mono(format(local), 14.sp, WW.Mint)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            valueRange = range,
            steps = steps,
            onValueChangeFinished = { onCommit(local) },
            colors = SliderDefaults.colors(
                thumbColor = WW.Mint, activeTrackColor = WW.Mint, inactiveTrackColor = WW.SurfaceHi,
            ),
        )
    }
}

@Composable
private fun UrlField(label: String, value: String, onChange: (String) -> Unit) {
    // Initialised once from the stored value; the settings screen only renders after DataStore loads.
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() }.take(5)
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
