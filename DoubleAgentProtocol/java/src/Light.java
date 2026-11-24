/**
 * Light sensor: light level as a percentage of 3.3 V.
 * The sensor's output VOLTAGE depends on the light, not on the supply, so the
 * reading is converted to volts first. That way the same light gives the same
 * percentage whether the box runs on 3.3 V or 5 V.
 *
 * Measured at 3.3 V: covered = 0-2, room light = ~37 (about 4%), a light
 * source at 10 cm = ~200 (20%), at 20 cm = ~80 (8%). So the solver moves a
 * light source closer/farther (or covers the sensor) to reach the target.
 */
public class Light extends Sensor {

	public Light() {
		//    name            code unit  min  max  errorInterval
		super("Light sensor", 'L', "%", 2, 40, 3);
	}

	public double calcValue(int[] raw) {
		return toVolts(raw[0]) / 3.3 * 100.0;
	}
}
