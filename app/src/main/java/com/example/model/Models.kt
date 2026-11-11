package com.example.model

/**
 * The four supported appliance types corresponding to Relay 1-4.
 */
enum class ApplianceType(
    val channel: Int,
    val commandKey: String,
    val displayName: String,
    val description: String
) {
    FAN(1, "FAN", "Fan", "Relay Channel 1 • Air Circulator"),
    AC(2, "AC", "AC", "Relay Channel 2 • Air Conditioner"),
    LAMP(3, "LAMP", "Lamp", "Relay Channel 3 • Desk / Room Lamp"),
    LED(4, "LED", "LED", "Relay Channel 4 • Ambient Light")
}

/**
 * Confirmed and pending state of a relay.
 */
data class RelayState(
    val type: ApplianceType,
    val isConfirmedOn: Boolean = false,
    val isPending: Boolean = false,
    val lastError: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Sensor data from the DHT11 sensor.
 */
data class SensorData(
    val temperatureCelsius: Float? = null,
    val humidityPercent: Float? = null,
    val timestamp: Long? = null,
    val isError: Boolean = false,
    val errorMessage: String? = null
) {
    val isAvailable: Boolean
        get() = temperatureCelsius != null && humidityPercent != null && !isError

    fun temperatureFahrenheit(): Float? {
        return temperatureCelsius?.let { (it * 9f / 5f) + 32f }
    }
}

/**
 * Bluetooth connection lifecycle states.
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Bluetooth device representation (e.g. HC-05 / HC-06).
 */
data class BluetoothDeviceItem(
    val name: String,
    val address: String,
    val isBonded: Boolean = true,
    val rssi: Short? = null
)

/**
 * Direction for Bluetooth ASCII Serial logging.
 */
enum class LogDirection {
    TX, // Transmitted to Arduino
    RX, // Received from Arduino
    SYS // System / Status Event
}

/**
 * Serial Packet Log for debugging and hardware inspection.
 */
data class LogMessage(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: LogDirection,
    val content: String
)

/**
 * User configurable app settings.
 */
data class AppSettings(
    val sensorRefreshIntervalSec: Int = 3,
    val isFahrenheit: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val autoReconnect: Boolean = true,
    val simulationModeEnabled: Boolean = false,
    val activeLowRelayMode: Boolean = true
)
