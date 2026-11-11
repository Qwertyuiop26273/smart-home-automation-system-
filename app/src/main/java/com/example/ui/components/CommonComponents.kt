package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ApplianceType
import com.example.model.ConnectionStatus
import com.example.model.LogDirection
import com.example.model.LogMessage
import com.example.model.RelayState
import com.example.model.SensorData
import com.example.ui.theme.AcColor
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentCyanGlow
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavyCardElevated
import com.example.ui.theme.FanColor
import com.example.ui.theme.LampColor
import com.example.ui.theme.LedColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PanhwarCodeBrandChip(modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(AccentCyan)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "PanhwarCode",
                color = AccentCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun ConnectionStatusBadge(
    status: ConnectionStatus,
    deviceName: String? = null,
    onClick: () -> Unit = {}
) {
    val (statusColor, statusText) = when (status) {
        ConnectionStatus.CONNECTED -> AccentEmerald to (deviceName ?: "Connected")
        ConnectionStatus.CONNECTING -> Color(0xFFFBBF24) to "Connecting..."
        ConnectionStatus.ERROR -> AccentRose to "Connection Error"
        ConnectionStatus.DISCONNECTED -> Color(0xFF94A3B8) to "Disconnected"
    }

    Surface(
        color = statusColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("connection_status_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (status == ConnectionStatus.CONNECTING) {
                CircularProgressIndicator(
                    color = statusColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(8.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ApplianceIcon(
    type: ApplianceType,
    isOn: Boolean,
    modifier: Modifier = Modifier
) {
    val iconColor = when (type) {
        ApplianceType.FAN -> FanColor
        ApplianceType.AC -> AcColor
        ApplianceType.LAMP -> LampColor
        ApplianceType.LED -> LedColor
    }

    val infiniteTransition = rememberInfiniteTransition(label = "fan_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fan_rotation_anim"
    )

    val icon: ImageVector = when (type) {
        ApplianceType.FAN -> Icons.Default.Air
        ApplianceType.AC -> Icons.Default.AcUnit
        ApplianceType.LAMP -> Icons.Default.Lightbulb
        ApplianceType.LED -> Icons.Default.WbIncandescent
    }

    val iconModifier = if (type == ApplianceType.FAN && isOn) {
        modifier.rotate(rotation)
    } else {
        modifier
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isOn) iconColor.copy(alpha = 0.18f) else Color(0xFF1E293B)
            )
            .border(
                1.dp,
                if (isOn) iconColor.copy(alpha = 0.6f) else DarkNavyBorder,
                RoundedCornerShape(12.dp)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = type.displayName,
            tint = if (isOn) iconColor else TextMuted,
            modifier = iconModifier.size(26.dp)
        )
    }
}

@Composable
fun RelayControlCard(
    relayState: RelayState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = relayState.type
    val isConfirmedOn = relayState.isConfirmedOn
    val isPending = relayState.isPending

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isConfirmedOn) DarkNavyCardElevated else DarkNavyCard
        ),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isConfirmedOn) 1.5.dp else 1.dp,
            color = if (isConfirmedOn) AccentCyan.copy(alpha = 0.7f) else DarkNavyBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("relay_card_${type.commandKey.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ApplianceIcon(type = type, isOn = isConfirmedOn)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "R${type.channel}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = type.description,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Switch and Pending Spinner
                Box(contentAlignment = Alignment.Center) {
                    Switch(
                        checked = isConfirmedOn,
                        onCheckedChange = { if (!isPending) onToggle() },
                        enabled = !isPending,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentCyan,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.testTag("switch_${type.commandKey.lowercase()}")
                    )
                    if (isPending) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Confirmed Status & Timing/Error Bar
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status label
                Surface(
                    color = if (isConfirmedOn) AccentEmerald.copy(alpha = 0.15f) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (isConfirmedOn) AccentEmerald.copy(alpha = 0.4f) else Color.Transparent
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (isConfirmedOn) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isConfirmedOn) AccentEmerald else TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPending) "AWAITING CONFIRMATION..." else if (isConfirmedOn) "STATE: ON" else "STATE: OFF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPending) Color(0xFFFBBF24) else if (isConfirmedOn) AccentEmerald else TextMuted
                        )
                    }
                }

                // Error feedback if any
                if (relayState.lastError != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = AccentRose,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = relayState.lastError,
                            color = AccentRose,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SensorCard(
    sensorData: SensorData,
    isFahrenheit: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("sensor_overview_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F2B48)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "DHT11 Sensor",
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "DHT11 Climate Telemetry",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Arduino Pin D8 • Digital 1-Wire",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp).testTag("refresh_sensor_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "Query Sensor",
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!sensorData.isAvailable && !sensorData.isError) {
                // Empty / Waiting State
                Surface(
                    color = Color(0xFF131E33),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sensor telemetry unavailable. Connect Bluetooth to Arduino Uno/Nano to stream live readings.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else if (sensorData.isError) {
                // Error State
                Surface(
                    color = AccentRose.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = AccentRose,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Sensor Read Error (ERROR,DHT_READ)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentRose
                            )
                            Text(
                                text = "Verify DHT11 5V/GND power and signal wire on Arduino Pin D8.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                // Valid readings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Temperature Tile
                    val tempVal = if (isFahrenheit) {
                        sensorData.temperatureFahrenheit()?.let { String.format(Locale.US, "%.1f°F", it) } ?: "--"
                    } else {
                        sensorData.temperatureCelsius?.let { String.format(Locale.US, "%.1f°C", it) } ?: "--"
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF152238))
                            .border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "TEMPERATURE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = tempVal,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ambient room thermal",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Humidity Tile
                    val humVal = sensorData.humidityPercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--"

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF152238))
                            .border(1.dp, AccentEmerald.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "HUMIDITY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentEmerald,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = humVal,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Relative humidity (RH)",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Timestamp
                Spacer(modifier = Modifier.height(10.dp))
                val timeStr = sensorData.timestamp?.let {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    sdf.format(Date(it))
                } ?: "Unknown"

                Text(
                    text = "Last reading captured at $timeStr (Safe 2.5–3s polling cycle)",
                    fontSize = 11.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SerialConsoleDialog(
    logs: List<LogMessage>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onSendCustom: (String) -> Unit
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ASCII Serial Terminal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                TextButton(onClick = onClear) {
                    Text("Clear", color = AccentCyan)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                Text(
                    text = "Raw HC-05 / HC-06 Bluetooth UART packets (TX/RX):",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    color = Color(0xFF070B12),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyBorder),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (logs.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "No serial activity logged yet.\nSend commands or connect to view packets.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.padding(8.dp),
                            reverseLayout = false
                        ) {
                            items(logs) { log ->
                                val (tag, tagColor) = when (log.direction) {
                                    LogDirection.TX -> "TX >>" to AccentCyan
                                    LogDirection.RX -> "<< RX" to AccentEmerald
                                    LogDirection.SYS -> "[SYS]" to Color(0xFFFBBF24)
                                }
                                val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(log.timestamp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$time ",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = "$tag ",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = tagColor
                                    )
                                    Text(
                                        text = log.content,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSendCustom("STATUS\n") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Send STATUS", fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            val allLogs = logs.joinToString("\n") { "[${it.direction}] ${it.content}" }
                            clipboardManager.setText(AnnotatedString(allLogs))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkNavyCardElevated)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Text("Close", color = Color(0xFF03101C))
            }
        },
        containerColor = DarkNavyCard
    )
}
