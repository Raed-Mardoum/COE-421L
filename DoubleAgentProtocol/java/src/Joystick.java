/**
 * Joystick: the value is the DIRECTION the stick points, as an angle
 * (0 = right, 90 = up, 180 = left, 270 = down). The Arduino sends X and Y.
 */
public class Joystick extends Sensor {

	public Joystick() {
		//    name        code unit   min max  errorInterval
		super("Joystick", 'J', "deg", 0, 359, 15);
	}

	public double calcValue(int[] raw) {
		double x = raw[0] - 512;   // measured at rest: X = 511-512
		double y = raw[1] - 509;   // measured at rest: Y = 508-510
		double angle = Math.toDegrees(Math.atan2(y, x));   // -180..180
		if (angle < 0) {
			angle += 360;                                   // 0..360
		}
		return angle;
	}

	// Angles wrap around: 350 and 10 degrees are only 20 degrees apart
	public double getError(double value, double target) {
		double diff = Math.abs(value - target) % 360;
		if (diff > 180) {
			diff = 360 - diff;
		}
		return diff;
	}

	public double getMaxError() {
		return 180;
	}
}
