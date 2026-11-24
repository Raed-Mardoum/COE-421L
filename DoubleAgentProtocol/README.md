# Double Agent Protocol — COE421L Final Project (Sec 01, Group 04)

A two-player puzzle-box game. The **solver** handles a LilyPad-powered box with five sensors; the **sabotager** sits at the computer, picks each phase's target, and can send fake "closer / farther" hints. The two connect over ZigBee (XBee).

```
java/src/               Java game controller (copy into an Eclipse project)
arduino/DoubleAgentBox/ LilyPad sketch
```

---

## 1. Java program (terminal version)

### Setup in Eclipse
1. Create a Java project (JavaSE-20 or lower, as in Lab 9) and copy all the `.java` files from `java/src/` into its `src` folder.
2. Build Path → Add External JARs → `jssc-2.9.6.jar`.
3. In `Main.java`, set `PORT` to your XBee Explorer port (for example `"COM8"`).
4. Run `Main.java`. The sabotager plays in the Eclipse console:
   * type a number to set the target for each phase
   * when the solver submits, type `t` to send the truthful hint or `f` to send a fake one
   * type `new` after a game to play again

### Classes

| Class | Type | Job |
|---|---|---|
| `Main` | — | Creates the objects and reads the sabotager's typing |
| `GameController` | Control, **Observer** | Game rules: phases, attempts, sabotages, win/lose |
| `XBee` | Boundary, **Subject**, own thread | Reads messages from the box and sends commands to it |
| `PhaseTimer` | Control, **Subject**, own thread | Counts down each phase; can pause and resume |
| `SerialPortHandle` | Boundary | The Lab 9 class |
| `Phase` | Entity | Target, attempts, sabotages and hint for one phase (`checkValue`) |
| `Sensor` (abstract) | Entity | Range, error interval, and `calcValue()` for each sensor |
| `Light`, `Joystick`, `Distance`, `Accelerometer`, `Temperature` | Entity | Convert raw readings into real values |
| `Subject`, `Observer`, `ConcreteSubject` | — | Observer pattern, exactly as in the lecture |

**Observer pattern:** `XBee` and `PhaseTimer` are Subjects. `GameController` registers with both. When the box sends a line, or the time runs out, they call `update()` on it.

**Threads:** main (keyboard input), XBee (reading the serial port) and PhaseTimer (counting down). `GameController` methods are `synchronized`, so only one thread changes the game at a time.

### Game rules implemented
* 5 phases, and each sensor is used once in random order.
* 5 attempts and 150 s per phase. The timer pauses while the sabotager decides.
* At most 2 fake hints per phase and 8 per game. A correct answer cannot be faked.
* A wrong answer gets **CLOSER** if it is nearer the target than the previous attempt, otherwise **FARTHER**. On the first attempt, CLOSER means the answer is within 25% of the largest possible error.
* Ranges and error intervals are set in each sensor's constructor. Tune them after testing.

---

## 2. Protocol (ASCII lines, 9600 baud)

| Direction | Message | Meaning |
|---|---|---|
| PC → box | `P,<n>,<code>` | Start phase *n* with sensor `L` `J` `D` `A` `T` |
| PC → box | `R,<1/0>,<C/F/N>` | Result: correct?, hint Closer/Farther/None |
| PC → box | `W` / `L` / `X` | Won (unlock) / Lost / Reset |
| box → PC | `READY` | Box powered up |
| box → PC | `S,<code>,<raw>[,<raw2>]` | Submission: ADC value, joystick X,Y, or distance in cm |

---

## 3. Wiring (LilyPad Arduino 328 main board)

The LilyPad has 12 digital pins (D2–D13) and 6 analog pins (A0–A5). The game needs 14 digital signals but only 13 pins are free (D2–D13 plus A5), so the **five sensor-indicator LEDs are charlieplexed on 3 pins**. Only one indicator is ever lit, which charlieplexing handles. That leaves D13 spare.

### Sensors

| Component | Component pin | LilyPad |
|---|---|---|
| Light sensor | `+` / `−` / `S` | `+` / `−` / **A0** |
| Temperature sensor (MCP9700) | `+` / `−` / `S` | `+` / `−` / **A1** |
| Accelerometer (LilyPad ADXL335) | `+` / `−` / X (Y, Z unused) | `+` / `−` / **A2** |
| Joystick | VCC / GND / VRx / VRy | `+` / `−` / **A3** / **A4** (SW unused) |
| Ultrasonic HC-SR04 (one-pin mode) | VCC / GND / TRIG+ECHO joined | `+` / `−` / **D12** through 2.2 kΩ |
| Submit push button | leg 1 / leg 2 | **A5** / `−` (internal pull-up, no resistor) |

### Outputs

| Component | LilyPad |
|---|---|
| CLOSER LED (LilyPad RGB) | `−` pad → `−`, one colour pad → 360/510 Ω → **D2** |
| FARTHER LED (LilyPad RGB) | `−` pad → `−`, one colour pad → 360/510 Ω → **D3** |
| Result LED (LilyPad RGB) | `−` pad → `−`, **G** → 360/510 Ω → **D4**, **R** → 360/510 Ω → **D5**, B not connected |
| Servo signal (orange/yellow) | **D9**. Red → `+`, brown/black → `−` |

Use sewable LilyPad LEDs (they have built-in resistors), or put about 100 Ω in series with each standard LED.

### Sensor-indicator LEDs (charlieplexed on D6 = A, D7 = B, D8 = C)

| Indicator | LED **+** (anode) to | LED **−** (cathode) to |
|---|---|---|
| Light | D6 (A) | D7 (B) |
| Joystick | D7 (B) | D6 (A) |
| Distance | D7 (B) | D8 (C) |
| Accelerometer | D8 (C) | D7 (B) |
| Temperature | D6 (A) | D8 (C) |

Each pair is two LEDs wired back-to-back between the same two pins. Plain LEDs need about 47–100 Ω in series with **each pin** (D6, D7, D8). LilyPad LEDs need no extra resistor.

### XBee (same as Lab 8)

| LilyPad XBee | LilyPad |
|---|---|
| `+` / VCC | `+` |
| `−` / GND | `−` |
| RX (DIN) | **D11** (SoftwareSerial TX) |
| TX (DOUT) | **D10** (SoftwareSerial RX) |

Coordinator XBee goes in the **XBee Explorer**, which plugs into the computer by USB. The end-device XBee goes on the **LilyPad XBee** board.

### Power: 3.3 V for testing, 5 V for the final box
Everything runs from the LilyPad's own `+` and `−`, so all parts always get the same voltage.

* **To switch to 5 V, change one line:** `VOLTS` in `Sensor.java` (3.3 → 5.0). It is used by the temperature and light conversions. The joystick and accelerometer scale with the supply on their own, and the distance sensor sends centimetres, so they need no change.
* **RGB LEDs:** all LEDs are LilyPad RGB boards with pads `−`, R, G and B. They are **common cathode**, so a colour lights when its pin is HIGH (`LEDS_COMMON_ANODE = false` in the sketch). Each colour needs its own pin: joining R, G and B together would make every colour switch together.
* **Ultrasonic sensor:** join TRIG and ECHO and connect them to D12 through the **2.2 kΩ resistor**. It's safe at both voltages, so leave it in. The sketch needs the **NewPing** library (Arduino IDE → Library Manager → "NewPing").
* **Phase indicators:** each one uses only its **R pad** and its **`−` pad**, wired between lines A, B and C (not to `+` or `−`). In the charlieplex table, the "+" column is the R pad and the "−" column is the `−` pad. Red is the brightest colour at 3.3 V.
* **Charlieplex resistors:** D6 → 360 Ω, D7 → 100 Ω, D8 → 360 Ω. Different values only change brightness. Anything from about 47 to 360 Ω is fine.
* **Servo:** works best at 5 V. At 3.3 V it may be weak, which is fine for bench testing.
* **Light sensor:** the experiment values were measured at 3.3 V. Re-check them at 5 V.
* **All grounds must be common.**
* The accelerometer uses only the X axis, because no analog pin is free for Y/Z. Mount it so X points along the direction the solver tilts the box (forward/back). The value is the tilt angle from flat, from −60° to +60°.

---

## 4. Calibration checklist
1. Open the Arduino serial monitor (USB) and watch the `RX:` lines while the Java program runs.
2. For each sensor, note the realistic raw range in the demo room. Then adjust min, max and errorInterval in its class constructor. Repeat the light and temperature checks after switching to 5 V.
3. Accelerometer: stand the box on each edge and note the raw X reading at +1 g and −1 g. Set `ZERO_G` = (max+min)/2 and `COUNTS_PER_G` = (max−min)/2 in `Accelerometer.java`. This is the min/max method from Lab 8.
4. Adjust `SERVO_LOCKED_ANGLE` / `SERVO_UNLOCKED_ANGLE` in the sketch to suit your latch.
