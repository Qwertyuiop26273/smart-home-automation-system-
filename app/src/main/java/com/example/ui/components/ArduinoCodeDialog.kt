package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

const val ARDUINO_FIRMWARE_CODE = """/*
 * Smart Home Automation by PC
 * Brand: PanhwarCode
 * Developer: Developed by PanhwarCode
 * Portfolio: https://ferozalipanhwar.github.io/portfolio/
 * 
 * Hardware Target: Arduino Uno or Arduino Nano (ATmega328P)
 * Bluetooth: HC-05 / HC-06 Serial Module (Default Baud: 9600)
 * Sensor: DHT11 Digital Temperature & Humidity Sensor
 * Actuator: 4-Channel 5V Relay Module (Configurable Active-LOW / Active-HIGH)
 * 
 * Pinout Map:
 *   Relay 1 (Fan)  -> Arduino Pin D4
 *   Relay 2 (AC)   -> Arduino Pin D5
 *   Relay 3 (Lamp) -> Arduino Pin D6
 *   Relay 4 (LED)  -> Arduino Pin D7
 *   DHT11 Data     -> Arduino Pin D8 (with 10k pull-up resistor to 5V)
 *   Bluetooth RXD  -> Arduino Pin D11 (SoftwareSerial TX, via 1k/2k voltage divider)
 *   Bluetooth TXD  -> Arduino Pin D10 (SoftwareSerial RX)
 */

#include <SoftwareSerial.h>
#include <DHT.h>

// ---------------------- PIN DEFINITIONS ----------------------
#define PIN_RELAY_FAN   4
#define PIN_RELAY_AC    5
#define PIN_RELAY_LAMP  6
#define PIN_RELAY_LED   7

#define PIN_DHT         8
#define DHTTYPE         DHT11

#define BT_RX_PIN       10   // Connects to Bluetooth TXD
#define BT_TX_PIN       11   // Connects to Bluetooth RXD (via 5V->3.3V divider)

// ------------------ RELAY LOGIC POLARITY ---------------------
// Most common Arduino relay breakout modules are Active-LOW (LOW turns relay ON).
// If using an Active-HIGH relay module, invert these two values.
const int RELAY_ON  = LOW;
const int RELAY_OFF = HIGH;

// ----------------------- OBJECTS -----------------------------
SoftwareSerial bluetooth(BT_RX_PIN, BT_TX_PIN);
DHT dht(PIN_DHT, DHTTYPE);

// Relay state memory
bool stateFan  = false;
bool stateAc   = false;
bool stateLamp = false;
bool stateLed  = false;

// Non-blocking timer for DHT11 reads
unsigned long lastSensorReadTime = 0;
const unsigned long SENSOR_INTERVAL_MS = 2800; // 2.8 seconds safe reading cycle

// Serial command input buffer
String inputBuffer = "";

void setup() {
  // Initialize hardware USB Serial for debugging
  Serial.begin(115200);
  Serial.println(F("[PANHWARCODE] Smart Home Automation by PC - Initializing"));

  // Initialize SoftwareSerial for Bluetooth communication (HC-05/06 default: 9600)
  bluetooth.begin(9600);

  // Initialize relay pins to SAFE OFF state before declaring as OUTPUT
  digitalWrite(PIN_RELAY_FAN, RELAY_OFF);
  digitalWrite(PIN_RELAY_AC, RELAY_OFF);
  digitalWrite(PIN_RELAY_LAMP, RELAY_OFF);
  digitalWrite(PIN_RELAY_LED, RELAY_OFF);

  pinMode(PIN_RELAY_FAN, OUTPUT);
  pinMode(PIN_RELAY_AC, OUTPUT);
  pinMode(PIN_RELAY_LAMP, OUTPUT);
  pinMode(PIN_RELAY_LED, OUTPUT);

  // Initialize DHT11 sensor
  dht.begin();

  // Send ready handshake across Bluetooth
  bluetooth.println(F("READY"));
  sendStatusReport();
  Serial.println(F("[PANHWARCODE] System Ready"));
}

void loop() {
  // 1. Process incoming Bluetooth serial bytes
  while (bluetooth.available() > 0) {
    char c = (char)bluetooth.read();
    if (c == '\n') {
      inputBuffer.trim();
      if (inputBuffer.length() > 0) {
        processCommand(inputBuffer);
      }
      inputBuffer = "";
    } else if (c != '\r') {
      // Guard against buffer overflow
      if (inputBuffer.length() < 64) {
        inputBuffer += c;
      }
    }
  }

  // 2. Periodic DHT11 temperature and humidity telemetry (non-blocking)
  unsigned long currentMillis = millis();
  if (currentMillis - lastSensorReadTime >= SENSOR_INTERVAL_MS) {
    lastSensorReadTime = currentMillis;
    readAndBroadcastSensor();
  }
}

void processCommand(String cmd) {
  cmd.toUpperCase();
  Serial.print(F("[BT CMD] "));
  Serial.println(cmd);

  if (cmd == "STATUS") {
    sendStatusReport();
    readAndBroadcastSensor();
    return;
  }

  if (cmd.startsWith("SET,")) {
    // Expected format: SET,<DEVICE>,<0|1>
    int firstComma = cmd.indexOf(',');
    int secondComma = cmd.indexOf(',', firstComma + 1);

    if (secondComma == -1) {
      bluetooth.println(F("ERROR,BAD_COMMAND"));
      return;
    }

    String device = cmd.substring(firstComma + 1, secondComma);
    String valStr = cmd.substring(secondComma + 1);
    valStr.trim();

    bool turnOn = (valStr == "1");

    if (device == "FAN") {
      stateFan = turnOn;
      digitalWrite(PIN_RELAY_FAN, turnOn ? RELAY_ON : RELAY_OFF);
      bluetooth.print(F("STATE,FAN,"));
      bluetooth.println(turnOn ? "1" : "0");
    } else if (device == "AC") {
      stateAc = turnOn;
      digitalWrite(PIN_RELAY_AC, turnOn ? RELAY_ON : RELAY_OFF);
      bluetooth.print(F("STATE,AC,"));
      bluetooth.println(turnOn ? "1" : "0");
    } else if (device == "LAMP") {
      stateLamp = turnOn;
      digitalWrite(PIN_RELAY_LAMP, turnOn ? RELAY_ON : RELAY_OFF);
      bluetooth.print(F("STATE,LAMP,"));
      bluetooth.println(turnOn ? "1" : "0");
    } else if (device == "LED") {
      stateLed = turnOn;
      digitalWrite(PIN_RELAY_LED, turnOn ? RELAY_ON : RELAY_OFF);
      bluetooth.print(F("STATE,LED,"));
      bluetooth.println(turnOn ? "1" : "0");
    } else {
      bluetooth.println(F("ERROR,UNKNOWN_DEVICE"));
    }
  } else {
    bluetooth.println(F("ERROR,BAD_COMMAND"));
  }
}

void sendStatusReport() {
  bluetooth.print(F("STATE,FAN,"));  bluetooth.println(stateFan ? "1" : "0");
  bluetooth.print(F("STATE,AC,"));   bluetooth.println(stateAc ? "1" : "0");
  bluetooth.print(F("STATE,LAMP,")); bluetooth.println(stateLamp ? "1" : "0");
  bluetooth.print(F("STATE,LED,"));  bluetooth.println(stateLed ? "1" : "0");
}

void readAndBroadcastSensor() {
  float h = dht.readHumidity();
  float t = dht.readTemperature(); // Read Celsius

  if (isnan(h) || isnan(t)) {
    Serial.println(F("[ERROR] Failed to read from DHT11 sensor!"));
    bluetooth.println(F("ERROR,DHT_READ"));
    return;
  }

  // Format: SENSOR,<temp>,<humidity>
  bluetooth.print(F("SENSOR,"));
  bluetooth.print(t, 1);
  bluetooth.print(F(","));
  bluetooth.println(h, 1);

  Serial.print(F("[SENSOR] Temp: "));
  Serial.print(t);
  Serial.print(F(" °C, Hum: "));
  Serial.print(h);
  Serial.println(F(" %"));
}
"""

@Composable
fun ArduinoCodeDialog(
    onDismiss: () -> Unit
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Arduino Uno / Nano Firmware",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "SmartHomeAutomation.ino • PanhwarCode",
                        fontSize = 11.sp,
                        color = AccentCyan
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                Surface(
                    color = Color(0xFF070B12),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyBorder),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = ARDUINO_FIRMWARE_CODE,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFFCBE6FF),
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pins: D4..D7 Relays, D8 DHT11, D10/D11 BT",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(ARDUINO_FIRMWARE_CODE))
                            copied = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (copied) AccentEmerald else AccentCyan
                        )
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color(0xFF03101C),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (copied) "Copied!" else "Copy Sketch",
                            color = Color(0xFF03101C),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = DarkNavyCard
    )
}
