/**
 * Subject interface (Observer design pattern, same as the lecture).
 * A Subject produces data and notifies all registered Observers.
 */
public interface Subject {
	void registerObserver(Observer o);
	void removeObserver(Observer o);
	void notifyObservers(String message);
}
