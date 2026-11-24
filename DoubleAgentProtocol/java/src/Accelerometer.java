/**
 * Accelerometer (ADXL335, X axis only): used as a tilt sensor.
 * The solver tilts the box forward/back to reach the target angle.
 *
 *   g = (raw - ZERO_G) / COUNTS_PER_G      gravity along X, between -1 and 1
 *   tilt = asin(g)                          0 = flat, +-90 = standing on its edge
 *
 * The ADXL335 output scales with its supply (ratiometric), so these raw
 * numbers are the same at 3.3 V and 5 V.
 * Calibration from our experiments (raw X: min ~408, max ~616):
 *   ZERO_G       = (616 + 408) / 2 = 512
 *   COUNTS_PER_G = (616 - 408) / 2 = 104
 */
public class Accelerometer extends Sensor {

	private static final double ZERO_G = 512;
	private static final double COUNTS_PER_G = 104;

	public Accelerometer() {
		//    name             code unit   min  max errorInterval
		super("Accelerometer", 'A', "deg", -60, 60, 7);
	}

	public double calcValue(int[] raw) {
		double g = (raw[0] - ZERO_G) / COUNTS_PER_G;
		g = Math.max(-1, Math.min(1, g));   // keep g inside -1..1 so asin works
		return Math.toDegrees(Math.asin(g));
	}
}
