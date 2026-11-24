/**
 * XBee (runs on its own thread)
 * ----
 * Handles communication with the puzzle box through the XBee Explorer.
 *
 * - The thread waits for lines from the puzzle box and passes each one to
 *   its observers (the GameController) -> Observer pattern, XBee is a Subject.
 * - send() transmits a command line to the puzzle box.
 *
 * Messages FROM the box:  READY            box switched on
 *                         S,<sensor>,<raw> solver pressed submit (joystick: S,J,<x>,<y>)
 * Messages TO the box:    P,<phase>,<sensor>   start a phase with this sensor
 *                         R,<1|0>,<C|F|N>      result: correct?, hint Closer/Farther/None
 *                         W / L / X            won / lost / reset
 */
public class XBee extends ConcreteSubject implements Runnable {

	private SerialPortHandle sph;

	public XBee(String port) {
		sph = new SerialPortHandle(port);
		Thread t = new Thread(this);   // create the reading thread
		t.setDaemon(true);             // thread stops when the program closes
		t.start();
	}

	// Reading thread: forever read a line and notify the observers
	public void run() {
		while (true) {
			String line = sph.readLine();
			if (line.length() > 0) {
				notifyObservers(line);
			}
		}
	}

	// Send one command to the puzzle box
	public void send(String command) {
		sph.printLine(command);
	}
}
