/*
 * =======================================================================================
 * PROJECT: Smart Home Automation by PC
 * BRAND: PanhwarCode
 * DEVELOPER: Developed by PanhwarCode
 * PORTFOLIO: https://ferozalipanhwar.github.io/portfolio/
 * =======================================================================================
 * 
 * HARDWARE SPECIFICATIONS:
 *   - Microcontroller: Arduino Uno or Arduino Nano (ATmega328P)
 *   - Bluetooth Module: HC-05 / HC-06 (6-pin: STATE, RXD, TXD, GND, VCC, EN)
 *   - 4-Channel Relay Module: 5V optocoupled (Active-LOW logic default)
 *   - Climate Sensor: DHT11 Digital Temperature & Humidity Sensor
 * 
 * PIN CONNECTIONS:
 *   Relay 1 (Fan):             Arduino Digital Pin D4
 *   Relay 2 (AC):              Arduino Digital Pin D5
 *   Relay 3 (Lamp):            Arduino Digital Pin D6
 *   Relay 4 (LED):             Arduino Digital Pin D7
 *   DHT11 Sensor Data:         Arduino Digital Pin D8 (with 10k pull-up resistor to 5V)
 *   Bluetooth Module TXD:      Arduino Digital Pin D10 (SoftwareSerial RX)
 *   Bluetooth Module RXD:      Arduino Digital Pin D11 (SoftwareSerial TX, via Voltage Divider)
 * 
 * ELECTRICAL LEVEL SHIFTING (CRITICAL):
 *   Arduino Uno/Nano operates at 5V logic.
 *   HC-05/HC-06 RXD pin expects 3.3V logic!
 *   Wire a simple voltage divider between Arduino D11 (TX) and HC-05 RXD:
 *       Arduino Pin D11 ---> [ 1kΩ Resistor ] ---> HC-05 RXD
 *                                           |
 *                                    [ 2kΩ Resistor ]
 *                                           |
 *                                          GND
 * 
 * SERIAL ASCII PROTOCOL (Newline-delimited '\n'):
 *   Commands from Android:
 *     SET,FAN,1   /   SET,FAN,0
 *     SET,AC,1    /   SET,AC,0
 *     SET,LAMP,1  /   SET,LAMP,0
 *     SET,LED,1   /   SET,LED,0
 *     STATUS
 * 
 *   Responses from Arduino:
 *     READY
 *     STATE,FAN,<1|0>
 *     STATE,AC,<1|0>
 *     STATE,LAMP,<1|0>
 *     STATE,LED,<1|0>
 *     SENSOR,<temp_celsius>,<humidity_percent>
 *     ERROR,BAD_COMMAND
 *     ERROR,UNKNOWN_DEVICE
 *     ERROR,DHT_READ
 * =======================================================================================
 */

#include <SoftwareSerial.h>
#include <DHT.h>

// ---------------- PIN DEFINITIONS ----------------
#define PIN_RELAY_FAN   4
#define PIN_RELAY_AC    5
#define PIN_RELAY_LAMP  6
#define PIN_RELAY_LED   7

#define PIN_DHT         8
#define DHTTYPE         DHT11

#define BT_RX_PIN       10   // Connected to HC-05/06 TXD
#define BT_TX_PIN       11   // Connected to HC-05/06 RXD (through 1k/2k voltage divider)

// ---------------- RELAY POLARITY -----------------
// Standard 5V optocoupled relay modules are Active-LOW.
// Driving pin LOW energizes the coil (relay ON).
// If using an Active-HIGH module, swap LOW and HIGH below.
const int RELAY_ON  = LOW;
const int RELAY_OFF = HIGH;

// ---------------- OBJECTS & GLOBALS -------------
SoftwareSerial bluetooth(BT_RX_PIN, BT_TX_PIN);
DHT dht(PIN_DHT, DHTTYPE);

// Relay state memory
bool stateFan  = false;
bool stateAc   = false;
bool stateLamp = false;
bool stateLed  = false;

// Non-blocking timer for DHT11 reads (safe 2.8-second interval)
unsigned long lastSensorReadTime = 0;
const unsigned long SENSOR_INTERVAL_MS = 2800;

// Command string buffer
String inputBuffer = "";

// ---------------- SETUP -------------------------
void setup() {
  // 1. Initialize USB Serial for local engineering debug monitoring
  Serial.begin(115200);
  Serial.println(F("================================================"));
  Serial.println(F(" Smart Home Automation by PC - PanhwarCode"));
  Serial.println(F(" Developer: Developed by PanhwarCode"));
  Serial.println(F(" Portfolio: https://ferozalipanhwar.github.io/portfolio/"));
  Serial.println(F("================================================"));

  // 2. Initialize SoftwareSerial for Bluetooth (Default HC-05/HC-06 Baud is 9600)
  bluetooth.begin(9600);

  // 3. Pre-set all relay pins to SAFE OFF state BEFORE switching to OUTPUT mode
  digitalWrite(PIN_RELAY_FAN, RELAY_OFF);
  digitalWrite(PIN_RELAY_AC, RELAY_OFF);
  digitalWrite(PIN_RELAY_LAMP, RELAY_OFF);
  digitalWrite(PIN_RELAY_LED, RELAY_OFF);

  pinMode(PIN_RELAY_FAN, OUTPUT);
  pinMode(PIN_RELAY_AC, OUTPUT);
  pinMode(PIN_RELAY_LAMP, OUTPUT);
  pinMode(PIN_RELAY_LED, OUTPUT);

  // 4. Initialize DHT11 sensor
  dht.begin();

  // 5. Broadcast initial handshake and state across Bluetooth
  bluetooth.println(F("READY"));
  sendStatusReport();
  Serial.println(F("[STATUS] Arduino setup complete. Bluetooth listening at 9600 baud."));
}

// ---------------- MAIN LOOP ---------------------
void loop() {
  // A. Process incoming Bluetooth serial bytes
  while (bluetooth.available() > 0) {
    char c = (char)bluetooth.read();
    if (c == '\n') {
      inputBuffer.trim();
      if (inputBuffer.length() > 0) {
        processCommand(inputBuffer);
      }
      inputBuffer = "";
    } else if (c != '\r') {
      if (inputBuffer.length() < 64) {
        inputBuffer += c;
      }
    }
  }

  // B. Non-blocking periodic DHT11 telemetry broadcast
  unsigned long currentMillis = millis();
  if (currentMillis - lastSensorReadTime >= SENSOR_INTERVAL_MS) {
    lastSensorReadTime = currentMillis;
    readAndBroadcastSensor();
  }
}

// ---------------- PROTOCOL PARSER ---------------
void processCommand(String cmd) {
  cmd.toUpperCase();
  Serial.print(F("[BT RECV] "));
  Serial.println(cmd);

  // STATUS QUERY
  if (cmd == "STATUS") {
    sendStatusReport();
    readAndBroadcastSensor();
    return;
  }

  // RELAY SET COMMAND
  if (cmd.startsWith("SET,")) {
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
      Serial.println(turnOn ? F("[RELAY 1] FAN -> ON") : F("[RELAY 1] FAN -> OFF"));
    } else if (device == "AC") {
      stateAc = turnOn;
      digitalWrite(PIN_RELAY_AC, turnOn ? RELAY_ON : RELAY_OFF);
      bluetooth.print(F("STATE,AC,"));
      bluetooth.println(turnOn ? "1" : "0");
      Serial.println(turnOn ? F("[RELAY 2] AC -> ON") : F("[RELAY 2] AC -> OFF"));
    } else if (device == "LAMP") {
      stateLamp = turnOn;
      digitalWrite(PIN_RELAY_LAMP, turnOn ? RELAY_ON : RELAY_OFF);
      bluetooth.print(F("STATE,LAMP,"));
      bluetooth.println(turnOn ? "1" : "0");
      Serial.println(turnOn ? F("[RELAY 3] LAMP -> ON") : F("[RELAY 3] LAMP -> OFF"));
    } else if (device == "LED") {
      stateLed = turnOn;
      digitalWrite(PIN_RELAY_LED, turnOn ? RELAY_ON : RELAY_OFF);
      bluetooth.print(F("STATE,LED,"));
      bluetooth.println(turnOn ? "1" : "0");
      Serial.println(turnOn ? F("[RELAY 4] LED -> ON") : F("[RELAY 4] LED -> OFF"));
    } else {
      bluetooth.println(F("ERROR,UNKNOWN_DEVICE"));
    }
  } else {
    bluetooth.println(F("ERROR,BAD_COMMAND"));
  }
}

// ---------------- TELEMETRY REPORTERS -----------
void sendStatusReport() {
  bluetooth.print(F("STATE,FAN,"));  bluetooth.println(stateFan ? "1" : "0");
  bluetooth.print(F("STATE,AC,"));   bluetooth.println(stateAc ? "1" : "0");
  bluetooth.print(F("STATE,LAMP,")); bluetooth.println(stateLamp ? "1" : "0");
  bluetooth.print(F("STATE,LED,"));  bluetooth.println(stateLed ? "1" : "0");
}

void readAndBroadcastSensor() {
  float h = dht.readHumidity();
  float t = dht.readTemperature(); // Celsius

  // Validate DHT11 readings
  if (isnan(h) || isnan(t)) {
    Serial.println(F("[DHT11 ERROR] Sensor read failure on pin D8"));
    bluetooth.println(F("ERROR,DHT_READ"));
    return;
  }

  // Format: SENSOR,<temp>,<humidity>
  bluetooth.print(F("SENSOR,"));
  bluetooth.print(t, 1);
  bluetooth.print(F(","));
  bluetooth.println(h, 1);

  Serial.print(F("[DHT11] Temp: "));
  Serial.print(t, 1);
  Serial.print(F(" °C, Humidity: "));
  Serial.print(h, 1);
  Serial.println(F(" %"));
}
