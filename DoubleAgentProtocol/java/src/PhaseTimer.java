/**
 * PhaseTimer (runs on its own thread)
 * ----------
 * Counts down the time the solver has for the current phase. It works like
 * the Clock example from the Observer lecture: every 100 ms it wakes up, and
 * when the time runs out it notifies its observers with "TIMEOUT".
 *
 * The timer can be paused: while the sabotager is deciding which hint to send,
 * the solver's time must not run down.
 */
public class PhaseTimer extends ConcreteSubject implements Runnable {

	private long timeLeftMs = 0;      // milliseconds remaining
	private boolean running = false;  // true only while counting down

	public PhaseTimer() {
		Thread t = new Thread(this);
		t.setDaemon(true);
		t.start();
	}

	public synchronized void startTimer(int seconds) {
		timeLeftMs = seconds * 1000L;
		running = true;
	}

	public synchronized void pauseTimer() {
		running = false;
	}

	public synchronized void resumeTimer() {
		running = true;
	}

	public synchronized int getSecondsLeft() {
		return (int) (timeLeftMs / 1000);
	}

	public void run() {
		while (true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				return;
			}

			boolean expired = false;
			synchronized (this) {                // safely update the time
				if (running) {
					timeLeftMs -= 100;
					if (timeLeftMs <= 0) {
						running = false;
						expired = true;
					}
				}
			}
			// Notify outside the synchronized block so the timer never holds
			// its lock while the GameController is running (avoids deadlock).
			if (expired) {
				notifyObservers("TIMEOUT");
			}
		}
	}
}
