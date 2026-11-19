package com.rahulbaberwal.wattwarden.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rahulbaberwal.wattwarden.MainActivity
import com.rahulbaberwal.wattwarden.WattWardenApp
import com.rahulbaberwal.wattwarden.data.Analytics
import com.rahulbaberwal.wattwarden.data.BatteryReader
import com.rahulbaberwal.wattwarden.data.DrainAnalyzer
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.abs

private val Navy = Color(0xFF0B1220)
private val Mint = Color(0xFF19E3B1)
private val Amber = Color(0xFFFFB020)
private val Red = Color(0xFFFF5A5F)
private val TextC = Color(0xFFE8EEF7)
private val Muted = Color(0xFF8FA3BF)

class BatteryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as WattWardenApp
        val s = app.settings.flow.first()
        val snap = BatteryReader.read(context, s.calibration)
        val dao = app.db.dao()
        val health = Analytics.healthPercent(dao.recentEstimates(), s.designCapacityMah)
        val drain = DrainAnalyzer.analyze(dao.drainSince(System.currentTimeMillis() - 3 * 24 * 3_600_000L))
        val screenLeft = drain.screenOnHoursLeft(snap.level)

        val status = when {
            snap.isPluggedIn && (snap.currentMa ?: 1f) > 0f ->
                String.format(Locale.US, "Charging %.1f W", abs(snap.watts))
            snap.isPluggedIn -> "Plugged in"
            else -> "On battery"
        }
        val tempText = String.format(Locale.US, "%.1f°C", snap.tempC)
        val tempColor = when {
            snap.tempC >= s.tempLimitC -> Red
            snap.tempC >= s.tempLimitC - 4 -> Amber
            else -> Mint
        }
        val healthText = health?.let { "$it%" } ?: "--"
        val screenText = screenLeft?.let {
            Analytics.formatMinutes((it * 60).toInt())
        } ?: "--"

        provideContent {
            WidgetContent(snap.level, status, tempText, tempColor, healthText, screenText)
        }
    }
}

@Composable
private fun WidgetContent(
    level: Int,
    status: String,
    temp: String,
    tempColor: Color,
    health: String,
    screenLeft: String,
) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Navy))
            .cornerRadius(22.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                "$level%",
                style = TextStyle(color = ColorProvider(Mint), fontSize = 34.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(10.dp))
            Text(status, style = TextStyle(color = ColorProvider(TextC), fontSize = 13.sp))
        }
        Spacer(GlanceModifier.height(10.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Stat("Temp", temp, tempColor)
            Spacer(GlanceModifier.width(18.dp))
            Stat("Health", health, Amber)
            Spacer(GlanceModifier.width(18.dp))
            Stat("Screen-on left", screenLeft, TextC)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Text(label, style = TextStyle(color = ColorProvider(Muted), fontSize = 10.sp))
        Text(value, style = TextStyle(color = ColorProvider(color), fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
}

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()
}
