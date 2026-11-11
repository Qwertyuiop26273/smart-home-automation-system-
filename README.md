# Smart Home Automation by PC

**Brand:** PanhwarCode  
**Developer Credit:** Developed by PanhwarCode  
**Official Portfolio:** [https://ferozalipanhwar.github.io/portfolio/](https://ferozalipanhwar.github.io/portfolio/)

---

## 1. Project Overview

**Smart Home Automation by PC** is a professional, lightweight, standalone IoT smart home control system engineered by **PanhwarCode**. The solution bridges an Android smartphone with an **Arduino Uno or Arduino Nano (ATmega328P)** using **Bluetooth Classic Serial (HC-05 / HC-06)** to control a **4-channel relay module** and monitor ambient climate data via a **DHT11 digital temperature & humidity sensor**.

Key architectural features:
- **Zero Cloud Dependence:** Works 100% locally through direct Bluetooth RFCOMM serial communication.
- **Strict Acknowledged State Model:** Switches only transition into a confirmed state upon receiving `STATE,<DEVICE>,<0|1>` acknowledgements from the Arduino firmware.
- **Real-Time ASCII Serial Console:** Built-in packet monitor allowing hardware developers to inspect every transmitted (`TX >>`) and received (`<< RX`) byte stream.
- **Hardware Simulation Mode:** Integrated toggle for developing and evaluating dashboard UI/telemetry without requiring physical hardware attached.
- **High-Voltage Safety Architecture:** Relay modules are optocoupled and initialized to the safe `OFF` state during boot.

---

## 2. Hardware Bill of Materials (BOM)

| Item | Component | Specification | Quantity |
| :--- | :--- | :--- | :--- |
| **MCU** | Arduino Uno or Arduino Nano | ATmega328P, 16MHz, 5V | 1 |
| **Bluetooth** | HC-05 or HC-06 Module | 6-Pin Serial SPP (STATE, RXD, TXD, GND, VCC, EN) | 1 |
| **Actuator** | 4-Channel Relay Module | 5V Optocoupled (10A 250VAC / 10A 30VDC rated contacts) | 1 |
| **Sensor** | DHT11 Sensor | Single-wire digital capacitive temperature & humidity | 1 |
| **Resistors** | Voltage Divider Resistors | 1kΩ and 2kΩ (for 5V to 3.3V logic level conversion) | 1 each |
| **Power** | 5V DC Power Supply | 2A recommended to supply coils without MCU brownout | 1 |

---

## 3. Pin Mapping & Wiring Schematics

### Pin Connections Table

| Module | Pin | Arduino Uno / Nano Connection | Notes |
| :--- | :--- | :--- | :--- |
| **Relay 1 (Fan)** | IN1 | **Pin D4** | Active-LOW (LOW = ON, HIGH = OFF) |
| **Relay 2 (AC)** | IN2 | **Pin D5** | Active-LOW |
| **Relay 3 (Lamp)** | IN3 | **Pin D6** | Active-LOW |
| **Relay 4 (LED)** | IN4 | **Pin D7** | Active-LOW |
| **DHT11 Data** | OUT / DATA | **Pin D8** | 10kΩ pull-up to 5V (if not built into module) |
| **Bluetooth TXD** | TXD | **Pin D10** | SoftwareSerial RX (Direct 3.3V to 5V input) |
| **Bluetooth RXD** | RXD | **Pin D11 (via Divider)** | **Requires 5V -> 3.3V divider!** |
| **Bluetooth VCC** | VCC | **5V Pin** | Breakout board has 3.3V LDO regulator |
| **Bluetooth GND** | GND | **GND Pin** | Common Ground |

### Voltage Divider Circuit (CRITICAL for Bluetooth Module Longevity)
Arduino Uno/Nano digital outputs are 5V logic. The HC-05/HC-06 RXD pin is rated for 3.3V logic:
```
Arduino Pin D11 (TX) ---> [ 1kΩ Resistor ] ---> HC-05 / HC-06 RXD
                                           |
                                    [ 2kΩ Resistor ]
                                           |
                                      Arduino GND
```
*Formula: $V_{out} = 5V \times \frac{2k\Omega}{1k\Omega + 2k\Omega} = 3.33V$ (Safe for HC-05/06).*

---

## 4. Bluetooth Communication Protocol

The protocol operates over standard Bluetooth Classic Serial Port Profile (SPP - UUID `00001101-0000-1000-8000-00805F9B34FB`) at **9600 baud, 8-N-1**. All commands are newline-delimited (`\n`).

### A. Android to Arduino Commands
- `SET,FAN,1` / `SET,FAN,0` : Turn Relay 1 (Fan) ON or OFF.
- `SET,AC,1` / `SET,AC,0` : Turn Relay 2 (AC) ON or OFF.
- `SET,LAMP,1` / `SET,LAMP,0` : Turn Relay 3 (Lamp) ON or OFF.
- `SET,LED,1` / `SET,LED,0` : Turn Relay 4 (LED) ON or OFF.
- `STATUS` : Query states of all 4 relays and request fresh DHT11 sensor readings.

### B. Arduino to Android Responses
- `READY` : Sent once on MCU boot / initialization.
- `STATE,FAN,1` or `STATE,FAN,0` : Confirmed state acknowledgement for Fan.
- `STATE,AC,1` or `STATE,AC,0` : Confirmed state acknowledgement for AC.
- `STATE,LAMP,1` or `STATE,LAMP,0` : Confirmed state acknowledgement for Lamp.
- `STATE,LED,1` or `STATE,LED,0` : Confirmed state acknowledgement for LED.
- `SENSOR,<temperature_celsius>,<humidity_percent>` : e.g., `SENSOR,27.5,62.0`.
- `ERROR,BAD_COMMAND` : Sent if syntax is unrecognized.
- `ERROR,UNKNOWN_DEVICE` : Sent if device name is invalid.
- `ERROR,DHT_READ` : Sent if DHT11 fails to sample (e.g. wire loose).

---

## 5. Building the Android APK

The project is built natively using Android Gradle Plugin with Kotlin and Jetpack Compose.

### Step 1: Compile Debug APK
In the project root or terminal:
```bash
gradle assembleDebug
```
On Windows:
```cmd
gradlew.bat assembleDebug
```

### Step 2: Locate Output APK
Upon build completion, the debug APK is generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Install onto Physical Android Phone
Enable **Developer Options** and **USB Debugging** on your Android device, then run:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```
Or transfer `app-debug.apk` directly to your phone via USB, Google Drive, or email, and install via your phone's file manager.

---

## 6. Pairing & Connecting HC-05 / HC-06

1. Power on your Arduino board with the HC-05 or HC-06 module attached. The module LED will blink rapidly (indicating pairing mode).
2. On your Android phone, open **System Settings > Bluetooth**.
3. Scan for devices and select **HC-05** or **HC-06**.
4. When prompted for PIN, enter **`1234`** or **`0000`**.
5. Once paired, launch **Smart Home Automation by PC**.
6. Tap **Connect** on the dashboard, grant Bluetooth permissions if prompted, and select the paired HC-05 module from the list.
7. The connection badge will turn green (**Connected**), the module LED will blink in slow double-bursts, and live climate telemetry will begin streaming!

---

## 7. 10-Stage Hardware Testing Checklist

- [ ] **Stage 1 (App Disconnected State):** Verify dashboard opens cleanly with all switches disabled or indicating pending status, and sensor displays "telemetry unavailable".
- [ ] **Stage 2 (Permissions):** Grant Android 12+ `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` permissions.
- [ ] **Stage 3 (Pairing & Connect):** Connect to HC-05 / HC-06 and observe the connection pill change to emerald green.
- [ ] **Stage 4 (Handshake):** Verify receipt of `READY` and initial 4 `STATE` lines in the built-in serial terminal.
- [ ] **Stage 5 (Relay 1 Fan):** Toggle Fan switch. Verify click on relay channel 1 and check that UI updates to ON only after `STATE,FAN,1` is received.
- [ ] **Stage 6 (Relay 2 AC):** Toggle AC switch. Verify click on relay channel 2 and check that UI updates only after `STATE,AC,1` is received.
- [ ] **Stage 7 (Relay 3 Lamp & Relay 4 LED):** Test independent toggling of channels 3 and 4.
- [ ] **Stage 8 (DHT11 Telemetry):** Observe temperature and humidity auto-updating every 2.8 seconds.
- [ ] **Stage 9 (Disconnect Recovery):** Power off the Arduino; confirm the app smoothly transitions to Disconnected without crashing, and marks readings as stale.
- [ ] **Stage 10 (High Voltage Verification):** Always test with low-voltage DC loads (e.g. 5V LEDs or 12V DC fans) before wiring any AC mains appliances. Enclose all high-voltage relays in an insulated junction box.

---

## 8. React Native Companion Codebase Notes

For users wishing to deploy using **React Native CLI**, the exact same state machine and ASCII serial protocol can be deployed by wrapping `react-native-bluetooth-classic`:
```bash
npm install react-native-bluetooth-classic
cd android && ./gradlew assembleDebug
```
The protocol strings (`SET,<DEVICE>,<0|1>\n`, `STATUS\n`) and regex parser defined in `app/src/main/java/com/example/bluetooth/ProtocolParser.kt` map 1:1 into TypeScript regex logic.

---

## 9. Credits & License

**Developed by PanhwarCode**  
Personal Portfolio: [https://ferozalipanhwar.github.io/portfolio/](https://ferozalipanhwar.github.io/portfolio/)  
Licensed for academic, personal, and IoT demonstration purposes.
