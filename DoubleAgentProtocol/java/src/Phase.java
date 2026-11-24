/**
 * Phase
 * -----
 * One of the five phases of the game. Keeps track of the sensor used, the
 * target value, the attempts used, the sabotages used and the hint signal.
 *
 * Hint rule for a wrong answer:
 *   'C' (closer)  if this attempt is nearer the target than the previous one
 *   'F' (farther) otherwise
 * On the first attempt there is nothing to compare with, so 'C' is given when
 * the answer is within 25% of the largest possible error.
 */
public class Phase {

	private Sensor sensor;
	private double target;
	private int attempts = 0;          // attempts used by the solver
	private int sabotageAttempts = 0;  // fake hints used by the sabotager
	private char hintSignal = 'N';     // 'C' closer, 'F' farther, 'N' none
	private double lastError = -1;     // error of the previous attempt (-1 = none yet)

	public Phase(Sensor sensor) {
		this.sensor = sensor;
	}

	// Check a submitted value. Returns true if it is correct.
	public boolean checkValue(double value) {
		attempts++;
		double error = sensor.getError(value, target);

		if (sensor.isMatch(value, target)) {
			hintSignal = 'N';
			return true;
		}

		if (lastError < 0) {
			// First attempt: close if within 25% of the maximum error
			hintSignal = (error <= 0.25 * sensor.getMaxError()) ? 'C' : 'F';
		} else {
			hintSignal = (error < lastError) ? 'C' : 'F';
		}
		lastError = error;
		return false;
	}

	public Sensor getSensor()        { return sensor; }
	public double getTarget()        { return target; }
	public void setTarget(double t)  { target = t; }
	public int getAttempts()         { return attempts; }
	public int getSabotageAttempts() { return sabotageAttempts; }
	public void addSabotage()        { sabotageAttempts++; }
	public char getHintSignal()      { return hintSignal; }
}
