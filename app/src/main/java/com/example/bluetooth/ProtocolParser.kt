package com.example.bluetooth

import com.example.model.ApplianceType
import com.example.model.SensorData

/**
 * Protocol parser for Arduino Uno / Nano newline-delimited ASCII communication.
 */
object ProtocolParser {

    sealed class ParsedResponse {
        data object Ready : ParsedResponse()
        data class State(val appliance: ApplianceType, val isOn: Boolean) : ParsedResponse()
        data class Sensor(val data: SensorData) : ParsedResponse()
        data class Error(val code: String, val message: String) : ParsedResponse()
        data class Unknown(val raw: String) : ParsedResponse()
    }

    /**
     * Formats a SET relay command, ending with \n.
     */
    fun buildSetRelayCommand(appliance: ApplianceType, turnOn: Boolean): String {
        val value = if (turnOn) "1" else "0"
        return "SET,${appliance.commandKey},$value\n"
    }

    /**
     * Formats a STATUS query command, ending with \n.
     */
    fun buildStatusQueryCommand(): String {
        return "STATUS\n"
    }

    /**
     * Parses a single complete ASCII line received from Arduino.
     */
    fun parseLine(line: String): ParsedResponse {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return ParsedResponse.Unknown(line)

        if (trimmed.equals("READY", ignoreCase = true)) {
            return ParsedResponse.Ready
        }

        val parts = trimmed.split(",")
        if (parts.isEmpty()) return ParsedResponse.Unknown(trimmed)

        val header = parts[0].uppercase()
        return when (header) {
            "STATE" -> {
                if (parts.size >= 3) {
                    val deviceKey = parts[1].uppercase()
                    val value = parts[2].trim()
                    val appliance = ApplianceType.entries.find { it.commandKey == deviceKey }
                    val isOn = value == "1"
                    if (appliance != null) {
                        ParsedResponse.State(appliance, isOn)
                    } else {
                        ParsedResponse.Error("UNKNOWN_DEVICE", "Unknown device in state response: $deviceKey")
                    }
                } else {
                    ParsedResponse.Error("MALFORMED", "Malformed STATE line: $trimmed")
                }
            }
            "SENSOR" -> {
                if (parts.size >= 3) {
                    val tempStr = parts[1].trim()
                    val humStr = parts[2].trim()
                    val temp = tempStr.toFloatOrNull()
                    val hum = humStr.toFloatOrNull()

                    // Validate reasonable DHT11 physical limits: temp -10°C to 80°C, humidity 0% to 100%
                    if (temp != null && hum != null && !temp.isNaN() && !hum.isNaN() &&
                        temp in -10.0f..85.0f && hum in 0.0f..100.0f
                    ) {
                        ParsedResponse.Sensor(
                            SensorData(
                                temperatureCelsius = temp,
                                humidityPercent = hum,
                                timestamp = System.currentTimeMillis(),
                                isError = false
                            )
                        )
                    } else {
                        ParsedResponse.Sensor(
                            SensorData(
                                isError = true,
                                errorMessage = "Invalid sensor values ($tempStr, $humStr)",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                } else {
                    ParsedResponse.Error("MALFORMED", "Malformed SENSOR line: $trimmed")
                }
            }
            "ERROR" -> {
                val errorCode = if (parts.size > 1) parts[1].trim().uppercase() else "GENERIC"
                val descriptiveMsg = when (errorCode) {
                    "BAD_COMMAND" -> "Arduino reported invalid command syntax"
                    "UNKNOWN_DEVICE" -> "Arduino could not identify targeted appliance"
                    "DHT_READ" -> "DHT11 sensor reading failure on pin D8"
                    else -> "Arduino error: $errorCode"
                }
                ParsedResponse.Error(errorCode, descriptiveMsg)
            }
            else -> {
                ParsedResponse.Unknown(trimmed)
            }
        }
    }
}
