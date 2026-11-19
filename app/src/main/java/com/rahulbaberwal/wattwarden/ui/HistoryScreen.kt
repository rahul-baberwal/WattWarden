package com.rahulbaberwal.wattwarden.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahulbaberwal.wattwarden.data.Analytics
import com.rahulbaberwal.wattwarden.data.SessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text("Charge history", fontSize = 22.sp, color = WW.Text, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Tap a session for its curve. Name it after the charger + cable you used to compare them.",
                    color = WW.Muted, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (sessions.isEmpty()) {
            item {
                Panel {
                    Text("No sessions yet.", color = WW.Text)
                    Text(
                        "Turn Guardian on from the Live tab, then plug in your charger.",
                        color = WW.Muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(sessions, key = { it.id }) { SessionCard(vm, it) }
        item { Column(Modifier.height(24.dp)) {} }
    }
}

private val dateFmt = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault())

@Composable
private fun SessionCard(vm: MainViewModel, s: SessionEntity) {
    var open by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    Panel(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.label.ifBlank { "${s.charger} charge" },
                    color = WW.Text, fontSize = 16.sp,
                )
                Text(dateFmt.format(Date(s.startTime)), color = WW.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Mono(String.format(Locale.US, "%.1f W", s.peakWatts), 18.sp, WW.Mint)
        }
        Text(
            buildString {
                append("${s.startLevel}% → ${s.endLevel}%")
                append(" · ${Analytics.formatDuration(s.endTime - s.startTime)}")
                append(String.format(Locale.US, " · avg %.1f W", s.avgWatts))
                append(String.format(Locale.US, " · max %.0f°C", s.maxTempC))
            },
            color = WW.Muted, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        s.estimatedCapacityMah?.let {
            Text("Capacity estimate: ${"%,d".format(it)} mAh", color = WW.Amber, style = MaterialTheme.typography.bodySmall)
        }

        if (open) {
            val samples by remember(s.id) { vm.samples(s.id) }.collectAsState(initial = emptyList())
            if (samples.size >= 2) {
                Label("Power (W)", Modifier.padding(top = 12.dp))
                LineChart(samples.map { it.watts }, WW.Mint, Modifier.fillMaxWidth().height(110.dp))
                Label("Temperature (°C)", Modifier.padding(top = 8.dp))
                LineChart(samples.map { it.tempC }, WW.Amber, Modifier.fillMaxWidth().height(80.dp))
            } else {
                Text("Not enough samples recorded for a curve.", color = WW.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { renaming = true }) { Text("Name charger", color = WW.Mint) }
                TextButton(onClick = { vm.deleteSession(s.id) }) { Text("Delete", color = WW.Red) }
            }
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(s.label) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Name this charger") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("e.g. Anker 65W + blue cable") },
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.setLabel(s.id, text); renaming = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
            containerColor = WW.Surface,
        )
    }
}
