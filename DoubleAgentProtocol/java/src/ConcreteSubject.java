import java.util.ArrayList;

/**
 * ConcreteSubject: reusable implementation of Subject (as in the lecture).
 * XBee and PhaseTimer extend this class, just like Clock did in the lecture.
 */
public class ConcreteSubject implements Subject {

	private ArrayList<Observer> observers = new ArrayList<Observer>();

	public void registerObserver(Observer o) {
		observers.add(o);
	}

	public void removeObserver(Observer o) {
		observers.remove(o);
	}

	// Call update() on every registered observer
	public void notifyObservers(String message) {
		for (int i = 0; i < observers.size(); i++) {
			observers.get(i).update(message);
		}
	}
}
