package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ApplianceType
import com.example.model.ConnectionStatus
import com.example.ui.components.ConnectionStatusBadge
import com.example.ui.components.PanhwarCodeBrandChip
import com.example.ui.components.RelayControlCard
import com.example.ui.components.SensorCard
import com.example.ui.components.SerialConsoleDialog
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavyCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.SmartHomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: SmartHomeViewModel,
    onNavigateToDevices: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val relays by viewModel.relays.collectAsState()
    val sensorData by viewModel.sensorData.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val logs by viewModel.logs.collectAsState()

    var showSerialConsole by remember { mutableStateOf(false) }

    if (showSerialConsole) {
        SerialConsoleDialog(
            logs = logs,
            onDismiss = { showSerialConsole = false },
            onClear = { viewModel.clearLogs() },
            onSendCustom = { cmd -> viewModel.requestStatus() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Smart Home",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            PanhwarCodeBrandChip()
                        }
                        Text(
                            text = "Arduino Uno/Nano Bluetooth Controller",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    // Terminal Icon for ASCII Serial Inspect
                    IconButton(
                        onClick = { showSerialConsole = true },
                        modifier = Modifier.testTag("serial_console_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Serial Console",
                            tint = AccentCyan
                        )
                    }

                    // Settings Icon
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("dashboard_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkNavyBackground
                )
            )
        },
        containerColor = DarkNavyBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> Color(0xFF0E2235)
                            ConnectionStatus.CONNECTING -> Color(0xFF262211)
                            ConnectionStatus.ERROR -> Color(0xFF281116)
                            ConnectionStatus.DISCONNECTED -> DarkNavyCard
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> AccentCyan.copy(alpha = 0.5f)
                            ConnectionStatus.CONNECTING -> Color(0xFFFBBF24).copy(alpha = 0.5f)
                            ConnectionStatus.ERROR -> AccentRose.copy(alpha = 0.5f)
                            ConnectionStatus.DISCONNECTED -> DarkNavyBorder
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToDevices)
                        .testTag("dashboard_connection_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            ConnectionStatusBadge(
                                status = connectionStatus,
                                deviceName = connectedDevice?.name,
                                onClick = onNavigateToDevices
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (connectionStatus == ConnectionStatus.CONNECTED) {
                                        connectedDevice?.name ?: "HC-05 / HC-06"
                                    } else {
                                        "Bluetooth Controller"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (connectionStatus == ConnectionStatus.CONNECTED) {
                                        "Connected via RFCOMM SPP"
                                    } else {
                                        "Tap to connect hardware"
                                    },
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Button(
                            onClick = onNavigateToDevices,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (connectionStatus == ConnectionStatus.CONNECTED) DarkNavyCardElevated else AccentCyan
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = if (connectionStatus == ConnectionStatus.CONNECTED) TextPrimary else Color(0xFF03101C),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (connectionStatus == ConnectionStatus.CONNECTED) "Manage" else "Connect",
                                color = if (connectionStatus == ConnectionStatus.CONNECTED) TextPrimary else Color(0xFF03101C),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Simulation Mode Warning Indicator (if active)
            if (settings.simulationModeEnabled) {
                item {
                    Surface(
                        color = Color(0xFF332508),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF78560B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⚡ Hardware Simulation Mode Active",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFDE68A)
                                )
                            }
                            Text(
                                text = "Settings >",
                                fontSize = 11.sp,
                                color = AccentCyan,
                                modifier = Modifier.clickable(onClick = onNavigateToSettings)
                            )
                        }
                    }
                }
            }

            // Sensor Telemetry Card
            item {
                SensorCard(
                    sensorData = sensorData,
                    isFahrenheit = settings.isFahrenheit,
                    onRefresh = { viewModel.requestStatus() }
                )
            }

            // Master Control Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Appliance Controls (4-Channel)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.setAllRelays(turnOn = false) },
                            modifier = Modifier.testTag("all_off_button")
                        ) {
                            Text("All OFF", fontSize = 11.sp, color = TextPrimary)
                        }
                        Button(
                            onClick = { viewModel.setAllRelays(turnOn = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            modifier = Modifier.testTag("all_on_button")
                        ) {
                            Text("All ON", fontSize = 11.sp, color = Color(0xFF03101C), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // The Four Appliance Relay Control Cards
            items(ApplianceType.entries) { type ->
                val state = relays[type] ?: com.example.model.RelayState(type)
                RelayControlCard(
                    relayState = state,
                    onToggle = { viewModel.toggleRelay(type) }
                )
            }

            // Footer Brand and Portfolio Credit
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Developed by PanhwarCode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        modifier = Modifier
                            .clickable {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://ferozalipanhwar.github.io/portfolio/")
                                )
                                context.startActivity(intent)
                            }
                            .testTag("footer_portfolio_link")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Smart Home Automation by PC • Arduino ATmega328P IoT Project",
                        fontSize = 11.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
