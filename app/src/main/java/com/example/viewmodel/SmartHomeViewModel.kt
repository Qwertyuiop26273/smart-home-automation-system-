package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothService
import com.example.bluetooth.ProtocolParser
import com.example.model.ApplianceType
import com.example.model.AppSettings
import com.example.model.BluetoothDeviceItem
import com.example.model.ConnectionStatus
import com.example.model.LogMessage
import com.example.model.RelayState
import com.example.model.SensorData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SmartHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothService = BluetoothService(application.applicationContext, viewModelScope)
    private val prefs = application.getSharedPreferences("smart_home_prefs", Context.MODE_PRIVATE)

    // Connection & Devices
    val connectionStatus: StateFlow<ConnectionStatus> = bluetoothService.connectionStatus
    val connectedDevice: StateFlow<BluetoothDeviceItem?> = bluetoothService.connectedDevice
    val discoveredDevices: StateFlow<List<BluetoothDeviceItem>> = bluetoothService.discoveredDevices
    val isScanning: StateFlow<Boolean> = bluetoothService.isScanning
    val errorMessage: StateFlow<String?> = bluetoothService.errorMessage

    private val _bondedDevices = MutableStateFlow<List<BluetoothDeviceItem>>(emptyList())
    val bondedDevices: StateFlow<List<BluetoothDeviceItem>> = _bondedDevices.asStateFlow()

    // Appliance Relays State
    private val _relays = MutableStateFlow<Map<ApplianceType, RelayState>>(
        ApplianceType.entries.associateWith { RelayState(it) }
    )
    val relays: StateFlow<Map<ApplianceType, RelayState>> = _relays.asStateFlow()

    // Sensor State
    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData.asStateFlow()

    // Logs for live serial terminal
    private val _logs = MutableStateFlow<List<LogMessage>>(emptyList())
    val logs: StateFlow<List<LogMessage>> = _logs.asStateFlow()

    // App Settings
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private var pendingTimeoutJobs = mutableMapOf<ApplianceType, Job>()
    private var periodicQueryJob: Job? = null

    init {
        // Collect incoming parsed protocol packets
        viewModelScope.launch {
            bluetoothService.incomingLines.collectLatest { line ->
                handleIncomingProtocolLine(line)
            }
        }

        // Collect logs
        viewModelScope.launch {
            bluetoothService.logs.collect { newLog ->
                val current = _logs.value.toMutableList()
                current.add(0, newLog)
                if (current.size > 80) {
                    current.removeAt(current.size - 1)
                }
                _logs.value = current
            }
        }

        // Watch connection status changes
        viewModelScope.launch {
            bluetoothService.connectionStatus.collectLatest { status ->
                if (status == ConnectionStatus.CONNECTED) {
                    startPeriodicStatusQueries()
                } else {
                    stopPeriodicStatusQueries()
                    // Reset pending flags and sensor availability when disconnected
                    if (status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.ERROR) {
                        clearPendingStates()
                    }
                }
            }
        }

        // Apply initial simulation mode setting
        bluetoothService.setSimulationMode(_settings.value.simulationModeEnabled)
        refreshBondedDevices()
    }

    private fun loadSettings(): AppSettings {
        return AppSettings(
            sensorRefreshIntervalSec = prefs.getInt("pref_refresh_interval", 3),
            isFahrenheit = prefs.getBoolean("pref_is_fahrenheit", false),
            hapticFeedbackEnabled = prefs.getBoolean("pref_haptic", true),
            autoReconnect = prefs.getBoolean("pref_auto_reconnect", true),
            simulationModeEnabled = prefs.getBoolean("pref_simulation_mode", false),
            activeLowRelayMode = prefs.getBoolean("pref_active_low", true)
        )
    }

    private fun saveSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putInt("pref_refresh_interval", newSettings.sensorRefreshIntervalSec)
            putBoolean("pref_is_fahrenheit", newSettings.isFahrenheit)
            putBoolean("pref_haptic", newSettings.hapticFeedbackEnabled)
            putBoolean("pref_auto_reconnect", newSettings.autoReconnect)
            putBoolean("pref_simulation_mode", newSettings.simulationModeEnabled)
            putBoolean("pref_active_low", newSettings.activeLowRelayMode)
            apply()
        }
        _settings.value = newSettings
        bluetoothService.setSimulationMode(newSettings.simulationModeEnabled)
        refreshBondedDevices()
    }

    fun refreshBondedDevices() {
        _bondedDevices.value = bluetoothService.getBondedDevices()
    }

    fun startDiscovery() {
        bluetoothService.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothService.stopDiscovery()
    }

    fun connect(device: BluetoothDeviceItem) {
        bluetoothService.connect(device)
    }

    fun disconnect() {
        bluetoothService.disconnect()
        clearPendingStates()
    }

    /**
     * Toggles an individual relay appliance (Fan, AC, Lamp, LED).
     */
    fun toggleRelay(type: ApplianceType) {
        val current = _relays.value[type] ?: RelayState(type)
        if (current.isPending) return // Prevent duplicate taps

        val targetState = !current.isConfirmedOn
        sendRelayCommand(type, targetState)
    }

    /**
     * Sets a specific relay state.
     */
    fun sendRelayCommand(type: ApplianceType, turnOn: Boolean) {
        // Mark pending
        val updatedMap = _relays.value.toMutableMap()
        val current = updatedMap[type] ?: RelayState(type)
        updatedMap[type] = current.copy(isPending = true, lastError = null)
        _relays.value = updatedMap

        val command = ProtocolParser.buildSetRelayCommand(type, turnOn)
        val sent = bluetoothService.send(command)

        if (!sent) {
            val fallbackMap = _relays.value.toMutableMap()
            fallbackMap[type] = current.copy(isPending = false, lastError = "Not connected")
            _relays.value = fallbackMap
            return
        }

        // Cancel previous timeout and start new timeout
        pendingTimeoutJobs[type]?.cancel()
        pendingTimeoutJobs[type] = viewModelScope.launch {
            delay(3500) // 3.5 seconds timeout
            val timeoutMap = _relays.value.toMutableMap()
            val state = timeoutMap[type]
            if (state?.isPending == true) {
                timeoutMap[type] = state.copy(
                    isPending = false,
                    lastError = "Response timed out"
                )
                _relays.value = timeoutMap
            }
        }
    }

    /**
     * Sets all 4 relays ON or OFF.
     */
    fun setAllRelays(turnOn: Boolean) {
        ApplianceType.entries.forEach { type ->
            sendRelayCommand(type, turnOn)
        }
    }

    /**
     * Requests status from Arduino.
     */
    fun requestStatus() {
        bluetoothService.send(ProtocolParser.buildStatusQueryCommand())
    }

    private fun handleIncomingProtocolLine(line: String) {
        when (val parsed = ProtocolParser.parseLine(line)) {
            is ProtocolParser.ParsedResponse.Ready -> {
                // Arduino initialized or reset
                requestStatus()
            }
            is ProtocolParser.ParsedResponse.State -> {
                pendingTimeoutJobs[parsed.appliance]?.cancel()
                val map = _relays.value.toMutableMap()
                val current = map[parsed.appliance] ?: RelayState(parsed.appliance)
                map[parsed.appliance] = current.copy(
                    isConfirmedOn = parsed.isOn,
                    isPending = false,
                    lastError = null,
                    lastUpdated = System.currentTimeMillis()
                )
                _relays.value = map
            }
            is ProtocolParser.ParsedResponse.Sensor -> {
                _sensorData.value = parsed.data
            }
            is ProtocolParser.ParsedResponse.Error -> {
                if (parsed.code == "DHT_READ") {
                    _sensorData.value = _sensorData.value.copy(
                        isError = true,
                        errorMessage = parsed.message
                    )
                }
            }
            is ProtocolParser.ParsedResponse.Unknown -> {
                // Ignore or logged in serial console
            }
        }
    }

    private fun startPeriodicStatusQueries() {
        periodicQueryJob?.cancel()
        periodicQueryJob = viewModelScope.launch {
            while (isActive) {
                val interval = (_settings.value.sensorRefreshIntervalSec.coerceIn(1, 10)) * 1000L
                delay(interval)
                if (connectionStatus.value == ConnectionStatus.CONNECTED) {
                    bluetoothService.send(ProtocolParser.buildStatusQueryCommand())
                }
            }
        }
    }

    private fun stopPeriodicStatusQueries() {
        periodicQueryJob?.cancel()
        periodicQueryJob = null
    }

    private fun clearPendingStates() {
        pendingTimeoutJobs.values.forEach { it.cancel() }
        pendingTimeoutJobs.clear()

        val map = _relays.value.toMutableMap()
        map.keys.forEach { type ->
            val curr = map[type] ?: RelayState(type)
            map[type] = curr.copy(isPending = false)
        }
        _relays.value = map
    }

    // Setting modifiers
    fun updateRefreshInterval(seconds: Int) {
        saveSettings(_settings.value.copy(sensorRefreshIntervalSec = seconds))
        if (connectionStatus.value == ConnectionStatus.CONNECTED) {
            startPeriodicStatusQueries()
        }
    }

    fun toggleFahrenheit() {
        saveSettings(_settings.value.copy(isFahrenheit = !_settings.value.isFahrenheit))
    }

    fun toggleHapticFeedback() {
        saveSettings(_settings.value.copy(hapticFeedbackEnabled = !_settings.value.hapticFeedbackEnabled))
    }

    fun toggleAutoReconnect() {
        saveSettings(_settings.value.copy(autoReconnect = !_settings.value.autoReconnect))
    }

    fun toggleSimulationMode() {
        val newSim = !_settings.value.simulationModeEnabled
        saveSettings(_settings.value.copy(simulationModeEnabled = newSim))
    }

    fun toggleActiveLowRelayMode() {
        saveSettings(_settings.value.copy(activeLowRelayMode = !_settings.value.activeLowRelayMode))
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothService.unregisterReceiver()
        bluetoothService.disconnect()
    }
}
