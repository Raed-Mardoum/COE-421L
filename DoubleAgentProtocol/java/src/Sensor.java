/**
 * Sensor (abstract)
 * ------
 * General behaviour of every puzzle sensor. The Arduino sends RAW readings
 * (ADC values 0-1023, or echo time in microseconds). Each subclass converts
 * them into a real value in calcValue().
 *
 * Each sensor also stores:
 *  - code          : letter used in the XBee messages (L, J, D, A, T)
 *  - min / max     : the range the sabotager may choose a target from
 *  - errorInterval : how far from the target an answer may be and still count
 */
public abstract class Sensor {

	// LilyPad supply voltage. THE ONLY LINE TO CHANGE when switching power:
	// 3.3 for 3.3 V testing, 5.0 when the box runs on 5 V.
	// (The ADC measures relative to the supply, so volts = reading * VOLTS / 1023.)
	protected static final double VOLTS = 3.3;

	private String name;
	private char code;
	private String unit;
	private double min, max;
	private double errorInterval;

	public Sensor(String name, char code, String unit, double min, double max, double errorInterval) {
		this.name = name;
		this.code = code;
		this.unit = unit;
		this.min = min;
		this.max = max;
		this.errorInterval = errorInterval;
	}

	// Convert the raw reading(s) from the Arduino to the sensor's value
	public abstract double calcValue(int[] raw);

	// How far a value is from the target (the joystick overrides this)
	public double getError(double value, double target) {
		return Math.abs(value - target);
	}

	// Largest possible error, used for the first hint of a phase
	public double getMaxError() {
		return max - min;
	}

	// Is the value inside the accepted interval around the target?
	public boolean isMatch(double value, double target) {
		return getError(value, target) <= errorInterval;
	}

	// Convert an ADC reading (0-1023) to volts
	protected double toVolts(int adc) {
		return adc * VOLTS / 1023.0;
	}

	public String getName()          { return name; }
	public char getCode()            { return code; }
	public String getUnit()          { return unit; }
	public double getMin()           { return min; }
	public double getMax()           { return max; }
	public double getErrorInterval() { return errorInterval; }
}
