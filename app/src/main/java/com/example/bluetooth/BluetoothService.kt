package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.BluetoothDeviceItem
import com.example.model.ConnectionStatus
import com.example.model.LogDirection
import com.example.model.LogMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Native Android Bluetooth Classic Serial (RFCOMM / SPP) Service for HC-05 / HC-06 modules.
 */
class BluetoothService(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        // Standard SPP UUID for HC-05 / HC-06 Serial Bluetooth
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        // Fallback well-known RFCOMM UUID
        val WELL_KNOWN_RFCOMM_UUID: UUID = UUID.fromString("00000003-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? =
        ContextCompat.getSystemService(context, BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDeviceItem?>(null)
    val connectedDevice: StateFlow<BluetoothDeviceItem?> = _connectedDevice.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceItem>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceItem>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _incomingLines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingLines: SharedFlow<String> = _incomingLines.asSharedFlow()

    private val _logs = MutableSharedFlow<LogMessage>(extraBufferCapacity = 100)
    val logs: SharedFlow<LogMessage> = _logs.asSharedFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var activeSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null
    private var simulationJob: Job? = null

    // Simulation states
    private var isSimulating = false
    private val simulatedRelays = mutableMapOf(
        "FAN" to false,
        "AC" to false,
        "LAMP" to false,
        "LED" to false
    )
    private var simulatedTemp = 26.5f
    private var simulatedHum = 58.0f

    val isBluetoothSupported: Boolean
        get() = bluetoothAdapter != null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    /**
     * Get bonded (paired) devices.
     */
    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDeviceItem> {
        if (isSimulating) {
            return listOf(
                BluetoothDeviceItem("HC-05 [PanhwarCode Mock]", "98:D3:31:FC:21:40", isBonded = true),
                BluetoothDeviceItem("HC-06 Arduino Nano", "98:D3:51:FD:82:11", isBonded = true)
            )
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return emptyList()
        }

        return try {
            bluetoothAdapter.bondedDevices?.map {
                BluetoothDeviceItem(
                    name = it.name ?: "Unknown Device",
                    address = it.address,
                    isBonded = true
                )
            } ?: emptyList()
        } catch (e: SecurityException) {
            log(LogDirection.SYS, "Permission denied while getting bonded devices: ${e.message}")
            emptyList()
        }
    }

    /**
     * Starts device discovery/scan.
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (isSimulating) {
            _isScanning.value = true
            scope.launch {
                delay(1200)
                _discoveredDevices.value = listOf(
                    BluetoothDeviceItem("HC-05 [Smart Home]", "98:D3:31:FC:21:40", isBonded = true),
                    BluetoothDeviceItem("HC-06 Uno Lab", "98:D3:71:FD:55:09", isBonded = false)
                )
                _isScanning.value = false
            }
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _errorMessage.value = "Bluetooth is not enabled on this device"
            return
        }

        try {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            _discoveredDevices.value = emptyList()
            registerScanReceiver()
            _isScanning.value = bluetoothAdapter.startDiscovery()
            log(LogDirection.SYS, "Bluetooth discovery started")
        } catch (e: SecurityException) {
            _errorMessage.value = "Missing Bluetooth scan permission"
            log(LogDirection.SYS, "Scan error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            // ignore
        } finally {
            _isScanning.value = false
        }
    }

    private var scanReceiverRegistered = false
    private val scanReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)

                    if (device != null) {
                        val deviceName = try { device.name ?: "Unnamed Device" } catch (e: SecurityException) { "Unnamed Device" }
                        val item = BluetoothDeviceItem(
                            name = deviceName,
                            address = device.address,
                            isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
                            rssi = if (rssi != Short.MIN_VALUE) rssi else null
                        )
                        val currentList = _discoveredDevices.value.toMutableList()
                        if (currentList.none { it.address == item.address }) {
                            currentList.add(item)
                            _discoveredDevices.value = currentList
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                    log(LogDirection.SYS, "Bluetooth discovery finished")
                }
            }
        }
    }

    private fun registerScanReceiver() {
        if (!scanReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(scanReceiver, filter)
            scanReceiverRegistered = true
        }
    }

    fun unregisterReceiver() {
        if (scanReceiverRegistered) {
            try {
                context.unregisterReceiver(scanReceiver)
            } catch (e: Exception) {
                // ignore
            }
            scanReceiverRegistered = false
        }
    }

    /**
     * Connect to target Bluetooth Device by MAC address.
     */
    @SuppressLint("MissingPermission")
    fun connect(deviceItem: BluetoothDeviceItem) {
        if (_connectionStatus.value == ConnectionStatus.CONNECTING) return

        disconnect()
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _errorMessage.value = null
        log(LogDirection.SYS, "Initiating connection to ${deviceItem.name} (${deviceItem.address})")

        if (isSimulating) {
            simulateConnection(deviceItem)
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionStatus.value = ConnectionStatus.ERROR
            _errorMessage.value = "Bluetooth is disabled"
            log(LogDirection.SYS, "Cannot connect: Bluetooth adapter is disabled")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                // Cancel ongoing discovery before connecting to conserve bandwidth and CPU
                try {
                    bluetoothAdapter.cancelDiscovery()
                } catch (e: SecurityException) {
                    // ignore
                }

                val device = bluetoothAdapter.getRemoteDevice(deviceItem.address)
                log(LogDirection.SYS, "Opening RFCOMM socket with SPP UUID...")

                var socket: BluetoothSocket? = null
                try {
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    socket.connect()
                } catch (e: IOException) {
                    log(LogDirection.SYS, "Primary SPP failed (${e.message}), attempting fallback reflection socket...")
                    // Fallback for some non-standard HC-05 clones
                    socket?.close()
                    val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    socket = m.invoke(device, 1) as BluetoothSocket
                    socket.connect()
                }

                activeSocket = socket
                outputStream = socket.outputStream
                _connectedDevice.value = deviceItem
                _connectionStatus.value = ConnectionStatus.CONNECTED

                log(LogDirection.SYS, "Connected successfully to ${deviceItem.name} via RFCOMM")

                // Start reading incoming stream
                startReader(socket.inputStream)

                // Query initial status from Arduino
                send(ProtocolParser.buildStatusQueryCommand())

            } catch (e: Exception) {
                log(LogDirection.SYS, "Connection failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    _connectionStatus.value = ConnectionStatus.ERROR
                    _errorMessage.value = "Connection failed: ${e.localizedMessage ?: "Device unreachable. Ensure module is powered and paired."}"
                    disconnect()
                }
            }
        }
    }

    private fun startReader(inputStream: InputStream) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            val lineBuffer = StringBuilder()

            try {
                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val chunk = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        for (char in chunk) {
                            if (char == '\n') {
                                val completeLine = lineBuffer.toString().trim()
                                if (completeLine.isNotEmpty()) {
                                    log(LogDirection.RX, completeLine)
                                    _incomingLines.emit(completeLine)
                                }
                                lineBuffer.clear()
                            } else if (char != '\r') {
                                lineBuffer.append(char)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                if (isActive) {
                    log(LogDirection.SYS, "Socket read closed: ${e.message}")
                    withContext(Dispatchers.Main) {
                        disconnect()
                        _connectionStatus.value = ConnectionStatus.DISCONNECTED
                        _errorMessage.value = "Bluetooth connection lost"
                    }
                }
            }
        }
    }

    /**
     * Sends raw ASCII string to Arduino ending in \n.
     */
    fun send(message: String): Boolean {
        log(LogDirection.TX, message.trim())

        if (isSimulating) {
            handleSimulatedCommand(message.trim())
            return true
        }

        val out = outputStream
        if (out == null || _connectionStatus.value != ConnectionStatus.CONNECTED) {
            log(LogDirection.SYS, "Cannot send: Not connected to Arduino")
            return false
        }

        return try {
            out.write(message.toByteArray(Charsets.UTF_8))
            out.flush()
            true
        } catch (e: IOException) {
            log(LogDirection.SYS, "Write error: ${e.message}")
            disconnect()
            _connectionStatus.value = ConnectionStatus.ERROR
            _errorMessage.value = "Failed to send command to Bluetooth module"
            false
        }
    }

    /**
     * Disconnects current socket and cleans up resources.
     */
    fun disconnect() {
        readJob?.cancel()
        readJob = null

        simulationJob?.cancel()
        simulationJob = null

        try {
            outputStream?.close()
        } catch (e: Exception) {
            // ignore
        }
        outputStream = null

        try {
            activeSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        activeSocket = null

        _connectedDevice.value = null
        if (_connectionStatus.value != ConnectionStatus.ERROR) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
        log(LogDirection.SYS, "Disconnected from Bluetooth device")
    }

    private fun log(direction: LogDirection, text: String) {
        val msg = LogMessage(direction = direction, content = text)
        _logs.tryEmit(msg)
    }

    // ==========================================
    // Hardware Simulation Mode (UI Test Mode)
    // ==========================================

    fun setSimulationMode(enabled: Boolean) {
        isSimulating = enabled
        if (!enabled && simulationJob != null) {
            disconnect()
        }
    }

    private fun simulateConnection(deviceItem: BluetoothDeviceItem) {
        simulationJob?.cancel()
        simulationJob = scope.launch(Dispatchers.Default) {
            delay(600) // Realistic connection latency
            _connectedDevice.value = deviceItem
            _connectionStatus.value = ConnectionStatus.CONNECTED
            log(LogDirection.SYS, "[MOCK] Connected to simulated HC-05 module")

            // Send READY and initial states
            delay(150)
            emitSimulatedLine("READY")
            emitSimulatedLine("STATE,FAN,${if (simulatedRelays["FAN"] == true) "1" else "0"}")
            emitSimulatedLine("STATE,AC,${if (simulatedRelays["AC"] == true) "1" else "0"}")
            emitSimulatedLine("STATE,LAMP,${if (simulatedRelays["LAMP"] == true) "1" else "0"}")
            emitSimulatedLine("STATE,LED,${if (simulatedRelays["LED"] == true) "1" else "0"}")
            emitSimulatedLine("SENSOR,26.4,55.0")

            // Simulate DHT11 3-second heartbeat
            while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                delay(3000)
                simulatedTemp += ((-2..2).random() * 0.1f)
                simulatedHum += ((-3..3).random() * 0.2f)
                val tempFormatted = String.format(java.util.Locale.US, "%.1f", simulatedTemp)
                val humFormatted = String.format(java.util.Locale.US, "%.1f", simulatedHum)
                emitSimulatedLine("SENSOR,$tempFormatted,$humFormatted")
            }
        }
    }

    private fun handleSimulatedCommand(command: String) {
        scope.launch(Dispatchers.Default) {
            delay(90) // Arduino serial processing latency
            val parts = command.split(",")
            if (parts[0] == "STATUS") {
                emitSimulatedLine("READY")
                simulatedRelays.forEach { (key, isOn) ->
                    emitSimulatedLine("STATE,$key,${if (isOn) "1" else "0"}")
                }
            } else if (parts[0] == "SET" && parts.size >= 3) {
                val dev = parts[1].uppercase()
                val turnOn = parts[2] == "1"
                if (simulatedRelays.containsKey(dev)) {
                    simulatedRelays[dev] = turnOn
                    emitSimulatedLine("STATE,$dev,${if (turnOn) "1" else "0"}")
                } else {
                    emitSimulatedLine("ERROR,UNKNOWN_DEVICE")
                }
            } else {
                emitSimulatedLine("ERROR,BAD_COMMAND")
            }
        }
    }

    private suspend fun emitSimulatedLine(line: String) {
        log(LogDirection.RX, line)
        _incomingLines.emit(line)
    }
}
