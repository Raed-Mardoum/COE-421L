/*
 * ===========================================================================
 *  Double Agent Protocol - Puzzle Box firmware (LilyPad Arduino 328)
 *  COE421L Final Project - Section 01, Group 04
 * ===========================================================================
 *
 *  ROLE OF THIS SKETCH
 *  The puzzle box is a "thin client": it reads sensors and drives LEDs/servo,
 *  but every game decision (targets, attempts, timer, sabotage) is made by the
 *  Java program on the computer. The two talk over ZigBee (XBee modules).
 *
 *  1. Java sends  "P,<phase>,<sensor>"  -> 3 LEDs show the sensor number in binary
 *  2. Solver adjusts the sensor and presses the SUBMIT button
 *  3. Box reads the sensor and sends "S,<sensor>,<raw>[,<raw2>]"
 *  4. Java (after the sabotager confirms) replies "R,<1|0>,<C|F|N>"
 *       -> RGB LED shows GREEN (correct) or RED (incorrect),
 *          and the CLOSER or FARTHER LED flashes
 *  5. Java sends "W" (win: servo unlocks the prize) or "L" (lose)
 *     "X" resets the box to idle.
 *
 *  The box sends RAW numbers (ADC counts, or centimetres for the distance sensor); the Java Sensor
 *  classes convert them into %, degrees, tilt angle and degC.
 *
 *  Everything in loop() is non-blocking (millis() based) so the box keeps
 *  listening to the XBee while LEDs blink.
 * ===========================================================================
 */

#include <SoftwareSerial.h>
#include <Servo.h>
#include <NewPing.h>       // install via Library Manager: "NewPing"

// ----------------------------- PIN MAP -------------------------------------
// Analog sensors
const byte PIN_LIGHT     = A0;  // LilyPad light sensor  (S)
const byte PIN_TEMP      = A1;  // LilyPad temperature sensor (S)
const byte PIN_ACCEL_X   = A2;  // Accelerometer (ADXL335) X axis - tilt
const byte PIN_JOY_X     = A3;  // Joystick VRx
const byte PIN_JOY_Y     = A4;  // Joystick VRy
const byte PIN_BUTTON    = A5;  // SUBMIT push button (used as a digital input)

// ALL LEDs are LilyPad RGB LED boards with pads: -, R, G, B.
// Most are used as single-colour LEDs: only the - pad and ONE colour pad
// are connected (the other pads are unused).
//
// These boards are COMMON CATHODE: the shared pad is - (ground), and a colour
// turns ON when its pin is HIGH. If you ever use common-anode RGB LEDs
// (shared pad/leg goes to +, colour ON when the pin is LOW), set this to true.
const bool LEDS_COMMON_ANODE = false;

// Hint LEDs (one RGB LED each, one colour leg used)
const byte PIN_LED_CLOSER    = 2;
const byte PIN_LED_FARTHER   = 3;
// Result RGB LED (one LED, two colour legs used; blue leg not connected)
const byte PIN_RGB_GREEN = 4;   // green = correct
const byte PIN_RGB_RED   = 5;   // red   = incorrect

// Phase indicator: 3 LEDs showing the sensor NUMBER (1-5) in binary.
// Each is an RGB board used as one colour: - pad to ground, colour pad to the pin.
const byte PIN_BIT0 = 6;   // value 1
const byte PIN_BIT1 = 7;   // value 2
const byte PIN_BIT2 = 8;   // value 4

const byte PIN_SERVO   = 9;   // lock servo signal
const byte PIN_XBEE_RX = 10;  // LilyPad receives  <- XBee TX (DOUT)   (same as Lab 8)
const byte PIN_XBEE_TX = 11;  // LilyPad transmits -> XBee RX (DIN)
const byte PIN_SONAR   = 12;  // HC-SR04 TRIG + ECHO joined together (one-pin mode)
// D13 is left free (it has the on-board LED).

// ----------------------------- CONSTANTS -----------------------------------
const char SENSOR_CODES[5] = { 'L', 'J', 'D', 'A', 'T' };   // same codes as Java SensorType

// Sensor numbers shown on the binary indicator = index + 1:
//   1 Light (001)  2 Joystick (010)  3 Distance (011)
//   4 Accelerometer (100)  5 Temperature (101)

const int  SERVO_LOCKED_ANGLE   = 0;     // tune to your latch
const int  SERVO_UNLOCKED_ANGLE = 90;
const unsigned long SERVO_HOLD_MS     = 800;   // time to reach the angle before detaching
const unsigned long HINT_FLASH_MS     = 3000;  // how long CLOSER/FARTHER flashes
const unsigned long RESULT_SHOW_MS    = 3000;  // how long the result colour stays lit
const unsigned long DEBOUNCE_MS       = 30;
const unsigned int  SONAR_MAX_CM      = 200;   // sensor tested reliable up to ~2 m

// ----------------------------- STATE ---------------------------------------
enum BoxState { BOX_IDLE, BOX_PHASE_ACTIVE, BOX_AWAITING, BOX_WON, BOX_LOST };
BoxState boxState = BOX_IDLE;

int  activeIndex = -1;              // index into SENSOR_CODES, -1 = none
char activeSensor = 0;

byte hintPin = 0;                   // CLOSER or FARTHER LED currently flashing
unsigned long hintUntil = 0;
char resultColour = 0;              // 'G' green, 'R' red, 0 = off
unsigned long resultUntil = 0;
unsigned long servoDetachAt = 0;
int lastIndicator = -2;             // indicator colour currently shown (cache)

char rxBuf[24];                     // incoming XBee line buffer
byte rxLen = 0;

bool lastButtonReading = HIGH;      // debounce state (INPUT_PULLUP: HIGH = released)
bool buttonState = HIGH;
unsigned long lastButtonChange = 0;

SoftwareSerial xbee(PIN_XBEE_RX, PIN_XBEE_TX);
Servo lockServo;
// Passing the same pin twice puts NewPing into one-pin mode
NewPing sonar(PIN_SONAR, PIN_SONAR, SONAR_MAX_CM);


// ===========================================================================
//  SETUP
// ===========================================================================
void setup() {
  Serial.begin(9600);               // USB/FTDI debug output (serial monitor)
  xbee.begin(9600);                 // XBees are configured for 9600 baud

  pinMode(PIN_LED_CLOSER, OUTPUT);
  pinMode(PIN_LED_FARTHER, OUTPUT);
  pinMode(PIN_BIT0, OUTPUT);
  pinMode(PIN_BIT1, OUTPUT);
  pinMode(PIN_BIT2, OUTPUT);
  ledWrite(PIN_LED_CLOSER, false);  // start with every LED off
  ledWrite(PIN_LED_FARTHER, false);
  pinMode(PIN_RGB_GREEN, OUTPUT);
  pinMode(PIN_RGB_RED, OUTPUT);
  setRGB(false, false);             // start with the RGB LED off
  pinMode(PIN_BUTTON, INPUT_PULLUP); // button to GND, internal pull-up -> no resistor needed
  showIndicator(-1);

  moveServo(SERVO_LOCKED_ANGLE);

  xbee.println(F("READY"));         // tell the Java program the box is online
  Serial.println(F("Puzzle box ready"));
}

// ===========================================================================
//  MAIN LOOP (non-blocking)
// ===========================================================================
void loop() {
  unsigned long now = millis();

  readXBee();                       // 1. handle commands from the computer
  if (buttonPressed(now)) {         // 2. solver pressed SUBMIT
    submitReading();
  }
  updateOutputs(now);               // 3. refresh LEDs (blinking etc.)

  if (servoDetachAt != 0 && now >= servoDetachAt) {
    // Detach once the servo has moved: stops jitter caused by SoftwareSerial
    // interrupts and saves battery. The servo gears hold the latch in place.
    lockServo.detach();
    servoDetachAt = 0;
  }
}

// ===========================================================================
//  XBEE RECEIVE: collect characters into a line, then process it
// ===========================================================================
void readXBee() {
  while (xbee.available()) {
    char c = xbee.read();
    if (c == '\n' || c == '\r') {
      if (rxLen > 0) {
        rxBuf[rxLen] = '\0';
        handleCommand(rxBuf);
        rxLen = 0;
      }
    } else if (rxLen < sizeof(rxBuf) - 1) {
      rxBuf[rxLen++] = c;
    } else {
      rxLen = 0;                    // line too long = garbage, discard it
    }
  }
}

// Execute one command line received from the Java program.
void handleCommand(const char *cmd) {
  Serial.print(F("RX: "));
  Serial.println(cmd);

  switch (cmd[0]) {
    case 'P': {                     // P,<phase>,<sensor>
      const char *comma = strrchr(cmd, ',');
      if (comma == NULL) return;
      int idx = sensorIndex(comma[1]);
      if (idx < 0) return;
      activeIndex = idx;
      activeSensor = comma[1];
      boxState = BOX_PHASE_ACTIVE;
      clearFeedback();
      break;
    }

    case 'R': {                     // R,<1|0>,<C|F|N>
      if (strlen(cmd) < 5) return;
      bool correct = (cmd[2] == '1');
      char hint = cmd[4];
      clearFeedback();
      resultColour = correct ? 'G' : 'R';
      resultUntil = millis() + RESULT_SHOW_MS;
      if (!correct) {
        if (hint == 'C') hintPin = PIN_LED_CLOSER;
        if (hint == 'F') hintPin = PIN_LED_FARTHER;
        hintUntil = millis() + HINT_FLASH_MS;
        boxState = BOX_PHASE_ACTIVE;    // solver may try again
      } else {
        boxState = BOX_IDLE;            // phase solved, wait for the next "P"
        activeIndex = -1;
      }
      break;
    }

    case 'W':                       // game won -> open the box
      boxState = BOX_WON;
      clearFeedback();
      moveServo(SERVO_UNLOCKED_ANGLE);
      break;

    case 'L':                       // game lost -> stay locked
      boxState = BOX_LOST;
      clearFeedback();
      moveServo(SERVO_LOCKED_ANGLE);
      break;

    case 'X':                       // reset to idle
      boxState = BOX_IDLE;
      activeIndex = -1;
      clearFeedback();
      moveServo(SERVO_LOCKED_ANGLE);
      break;
  }
}

// ===========================================================================
//  SUBMIT: read the active sensor and send the raw value(s)
// ===========================================================================
void submitReading() {
  if (boxState != BOX_PHASE_ACTIVE) {
    return;                         // ignore presses between phases / while waiting
  }

  xbee.print(F("S,"));
  xbee.print(activeSensor);
  xbee.print(',');

  switch (activeSensor) {
    case 'L': xbee.println(readAnalogAvg(PIN_LIGHT)); break;
    case 'T': xbee.println(readAnalogAvg(PIN_TEMP));  break;
    case 'A': xbee.println(readAnalogAvg(PIN_ACCEL_X)); break;  // tilt
    case 'J':                                          // joystick sends X and Y
      xbee.print(readAnalogAvg(PIN_JOY_X));
      xbee.print(',');
      xbee.println(readAnalogAvg(PIN_JOY_Y));
      break;
    case 'D':                                          // distance in cm (0 = nothing in range)
      // ping_median takes 5 pings and returns the middle echo time,
      // convert_cm turns it into centimetres
      xbee.println(sonar.convert_cm(sonar.ping_median(5)));
      break;
  }

  boxState = BOX_AWAITING;          // block further presses until the reply arrives
  clearFeedback();
  Serial.println(F("Submitted, waiting for computer"));
}

// Average 16 ADC samples to smooth out noise. The first reading after
// switching analog channels is discarded because the ADC needs time to settle.
int readAnalogAvg(byte pin) {
  analogRead(pin);
  long sum = 0;
  for (byte i = 0; i < 16; i++) {
    sum += analogRead(pin);
    delayMicroseconds(200);
  }
  return (int)(sum / 16);
}


// ===========================================================================
//  OUTPUTS: LEDs and servo
// ===========================================================================

// Called every loop: decides what every LED should show right now.
void updateOutputs(unsigned long now) {
  if (boxState == BOX_WON) {
    // Celebration: RGB blinks green, binary LEDs count 1 to 5
    setRGB(false, (now / 300) % 2);   // green blinking
    ledWrite(PIN_LED_CLOSER, false);
    ledWrite(PIN_LED_FARTHER, false);
    showIndicator((now / 150) % 5);
    return;
  }
  if (boxState == BOX_LOST) {
    // Defeat: RGB blinks red, everything else off
    setRGB((now / 300) % 2, false);   // red blinking
    ledWrite(PIN_LED_CLOSER, false);
    ledWrite(PIN_LED_FARTHER, false);
    showIndicator(-1);
    return;
  }

  // CLOSER / FARTHER: flash for HINT_FLASH_MS after a wrong answer
  bool hintOn = (hintPin != 0 && now < hintUntil && (now / 150) % 2);
  ledWrite(PIN_LED_CLOSER,  hintOn && hintPin == PIN_LED_CLOSER);
  ledWrite(PIN_LED_FARTHER, hintOn && hintPin == PIN_LED_FARTHER);

  // RGB result LED: solid green or red for RESULT_SHOW_MS
  bool resultOn = (resultColour != 0 && now < resultUntil);
  setRGB(resultOn && resultColour == 'R', resultOn && resultColour == 'G');

  // Sensor indicator: steady while playing, blinking while the computer
  // (sabotager) is deciding, off when idle.
  if (boxState == BOX_PHASE_ACTIVE) {
    showIndicator(activeIndex);
  } else if (boxState == BOX_AWAITING) {
    showIndicator((now / 250) % 2 ? activeIndex : -1);
  } else {
    showIndicator(-1);
  }
}

// Switch one LED colour on or off.
// Common cathode (our boards): HIGH = on. Common anode would be inverted.
void ledWrite(byte pin, bool on) {
  if (LEDS_COMMON_ANODE) {
    on = !on;
  }
  digitalWrite(pin, on ? HIGH : LOW);
}

// Show the result colour on the RGB result LED.
void setRGB(bool red, bool green) {
  ledWrite(PIN_RGB_RED, red);
  ledWrite(PIN_RGB_GREEN, green);
}

// Show sensor number (index + 1) in binary on D6-D8 (index < 0 = all off).
void showIndicator(int index) {
  if (index == lastIndicator) return;   // nothing changed, avoid needless writes
  lastIndicator = index;

  int number = (index >= 0 && index <= 4) ? index + 1 : 0;
  ledWrite(PIN_BIT0, number & 1);   // 1s bit
  ledWrite(PIN_BIT1, number & 2);   // 2s bit
  ledWrite(PIN_BIT2, number & 4);   // 4s bit
}

void clearFeedback() {
  hintPin = 0;
  resultColour = 0;
}

// Attach, move, and schedule a detach (see loop()).
void moveServo(int angle) {
  lockServo.attach(PIN_SERVO);
  lockServo.write(angle);
  servoDetachAt = millis() + SERVO_HOLD_MS;
}

// ===========================================================================
//  HELPERS
// ===========================================================================

// Debounced button: returns true once per press (on the HIGH -> LOW edge).
bool buttonPressed(unsigned long now) {
  bool reading = digitalRead(PIN_BUTTON);
  if (reading != lastButtonReading) {
    lastButtonChange = now;
    lastButtonReading = reading;
  }
  if ((now - lastButtonChange) > DEBOUNCE_MS && reading != buttonState) {
    buttonState = reading;
    if (buttonState == LOW) {
      return true;
    }
  }
  return false;
}

int sensorIndex(char code) {
  for (byte i = 0; i < 5; i++) {
    if (SENSOR_CODES[i] == code) return i;
  }
  return -1;
}