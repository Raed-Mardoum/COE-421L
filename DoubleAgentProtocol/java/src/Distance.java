/**
 * Ultrasonic distance sensor (HC-SR04, one-pin mode with NewPing).
 * The Arduino already converts the echo time to centimetres, so the value
 * received is the distance itself. 0 means nothing was detected in range.
 * Tested range: about 2 cm (blind spot below) to 2 m.
 */
public class Distance extends Sensor {

	public Distance() {
		//    name                   code unit  min max errorInterval
		super("Ultrasonic distance", 'D', "cm", 5, 100, 3);
	}

	public double calcValue(int[] raw) {
		return raw[0];
	}
}
