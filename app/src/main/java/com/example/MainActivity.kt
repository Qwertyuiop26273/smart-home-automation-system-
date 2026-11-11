package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.BluetoothDevicesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SmartHomeViewModel

enum class AppScreen {
    SPLASH,
    DASHBOARD,
    BLUETOOTH_DEVICES,
    SETTINGS,
    ABOUT
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SmartHomeApp()
            }
        }
    }
}

@Composable
fun SmartHomeApp(
    viewModel: SmartHomeViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }

    // Custom back navigation handler
    if (currentScreen != AppScreen.DASHBOARD && currentScreen != AppScreen.SPLASH) {
        BackHandler {
            currentScreen = AppScreen.DASHBOARD
        }
    }

    Crossfade(
        targetState = currentScreen,
        label = "screen_transition",
        modifier = Modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            AppScreen.SPLASH -> {
                SplashScreen(
                    onSplashFinished = {
                        currentScreen = AppScreen.DASHBOARD
                    }
                )
            }
            AppScreen.DASHBOARD -> {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToDevices = { currentScreen = AppScreen.BLUETOOTH_DEVICES },
                    onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                    onNavigateToAbout = { currentScreen = AppScreen.ABOUT }
                )
            }
            AppScreen.BLUETOOTH_DEVICES -> {
                BluetoothDevicesScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.DASHBOARD }
                )
            }
            AppScreen.SETTINGS -> {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.DASHBOARD },
                    onNavigateToAbout = { currentScreen = AppScreen.ABOUT }
                )
            }
            AppScreen.ABOUT -> {
                AboutScreen(
                    onBack = { currentScreen = AppScreen.DASHBOARD }
                )
            }
        }
    }
}
