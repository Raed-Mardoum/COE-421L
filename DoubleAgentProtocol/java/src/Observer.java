/**
 * Observer interface (Observer design pattern, same as the lecture).
 * Any class that wants to be told when a Subject has new data implements this.
 * The message tells the observer what happened:
 *   - from XBee       : the line received from the puzzle box, e.g. "S,L,512"
 *   - from PhaseTimer : "TIMEOUT"
 */
public interface Observer {
	void update(String message);
}
