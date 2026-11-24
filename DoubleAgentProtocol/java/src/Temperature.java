/**
 * Temperature sensor (LilyPad MCP9700): 0.5 V at 0 degC, +10 mV per degree.
 *   temperature = (volts - 0.5) * 100
 * Uses toVolts(), so VOLTS in Sensor.java must match the supply voltage.
 * Measured raw values at 3.3 V: cold water 181-215 (8-19 degC), room 215-245
 * (19-29 degC), heated hands 263-272 (35-38 degC).
 */
public class Temperature extends Sensor {

	public Temperature() {
		//    name                  code unit   min max errorInterval
		super("Temperature sensor", 'T', "degC", 10, 37, 1.5);
	}

	public double calcValue(int[] raw) {
		return (toVolts(raw[0]) - 0.5) * 100;
	}
}
