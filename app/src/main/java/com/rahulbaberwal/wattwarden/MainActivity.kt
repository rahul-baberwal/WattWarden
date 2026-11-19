package com.rahulbaberwal.wattwarden

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rahulbaberwal.wattwarden.ui.HealthScreen
import com.rahulbaberwal.wattwarden.ui.HistoryScreen
import com.rahulbaberwal.wattwarden.ui.LiveScreen
import com.rahulbaberwal.wattwarden.ui.MainViewModel
import com.rahulbaberwal.wattwarden.ui.SettingsScreen
import com.rahulbaberwal.wattwarden.ui.WW
import com.rahulbaberwal.wattwarden.ui.WattWardenTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            WattWardenTheme {
                val vm: MainViewModel = viewModel()
                var tab by rememberSaveable { mutableIntStateOf(0) }
                androidx.compose.runtime.LaunchedEffect(Unit) { vm.ensureServiceIfEnabled() }

                Scaffold(
                    containerColor = WW.Navy,
                    bottomBar = {
                        NavigationBar(containerColor = WW.Surface) {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = WW.Navy,
                                        selectedTextColor = WW.Mint,
                                        indicatorColor = WW.Mint,
                                        unselectedIconColor = WW.Muted,
                                        unselectedTextColor = WW.Muted,
                                    ),
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (tab) {
                            0 -> LiveScreen(vm)
                            1 -> HealthScreen(vm)
                            2 -> HistoryScreen(vm)
                            else -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Live", Icons.Filled.Home),
    Tab("Health", Icons.Filled.Favorite),
    Tab("History", Icons.Filled.DateRange),
    Tab("Settings", Icons.Filled.Settings),
)
