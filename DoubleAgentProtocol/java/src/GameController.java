import java.util.ArrayList;
import java.util.Collections;

/**
 * GameController
 * --------------
 * Runs the game. It is an OBSERVER of two subjects:
 *   - XBee       : tells it when the puzzle box sends a message (solver submitted)
 *   - PhaseTimer : tells it when the time for the phase runs out
 * The sabotager types commands in the terminal; Main passes them to handleInput().
 *
 * Game states:
 *   WAITING_TARGET    sabotager must type the target value for this phase
 *   PLAYING           timer running, waiting for the solver to submit
 *   WAITING_SABOTEUR  solver submitted, timer paused, sabotager types t or f
 *   GAME_OVER         game finished, type "new" to play again
 *
 * Three threads call this class (main/terminal, XBee, timer), so the methods
 * are synchronized: only one thread can change the game at a time.
 */
public class GameController implements Observer {

	// Game rules from the Phase 2 report
	private static final int NUM_PHASES = 5;
	private static final int MAX_ATTEMPTS = 5;
	private static final int PHASE_SECONDS = 150;          // 2.5 minutes
	private static final int MAX_SABOTAGES_TOTAL = 8;
	private static final int MAX_SABOTAGES_PER_PHASE = 2;

	private static final int WAITING_TARGET = 0;
	private static final int PLAYING = 1;
	private static final int WAITING_SABOTEUR = 2;
	private static final int GAME_OVER = 3;

	private XBee xbee;
	private PhaseTimer timer;
	private ArrayList<Phase> phases = new ArrayList<Phase>();
	private int current;               // index of the current phase (0-4)
	private int totalSabotages;        // fake hints used in the whole game
	private int state = GAME_OVER;
	private boolean lastAnswerCorrect; // result of the submission being decided

	public GameController(XBee xbee, PhaseTimer timer) {
		this.xbee = xbee;
		this.timer = timer;
		xbee.registerObserver(this);    // observe the puzzle box
		timer.registerObserver(this);   // observe the timer
	}

	// ---------------------------------------------------------------
	// Start a new game: every sensor is used once, in random order
	// ---------------------------------------------------------------
	public synchronized void newGame() {
		ArrayList<Sensor> sensors = new ArrayList<Sensor>();
		sensors.add(new Light());
		sensors.add(new Joystick());
		sensors.add(new Distance());
		sensors.add(new Accelerometer());
		sensors.add(new Temperature());
		Collections.shuffle(sensors);

		phases.clear();
		for (int i = 0; i < NUM_PHASES; i++) {
			phases.add(new Phase(sensors.get(i)));
		}
		totalSabotages = 0;
		xbee.send("X");                 // reset the puzzle box
		System.out.println("\n========== NEW GAME ==========");
		startPhase(0);
	}

	private void startPhase(int index) {
		current = index;
		state = WAITING_TARGET;
		Sensor s = phases.get(current).getSensor();
		System.out.println("\n--- Phase " + (current + 1) + " of " + NUM_PHASES + ": " + s.getName() + " ---");
		System.out.println("Enter target value (" + s.getMin() + " to " + s.getMax() + " " + s.getUnit()
				+ ", accepted within +-" + s.getErrorInterval() + "):");
	}

	// ---------------------------------------------------------------
	// Input typed by the sabotager (called from the main thread)
	// ---------------------------------------------------------------
	public synchronized void handleInput(String input) {
		input = input.trim().toLowerCase();

		if (state == WAITING_TARGET) {
			setTarget(input);
		} else if (state == WAITING_SABOTEUR) {
			if (input.equals("t")) {
				sendResponse(false);
			} else if (input.equals("f")) {
				sendResponse(true);
			} else {
				System.out.println("Type t (truthful) or f (fake).");
			}
		} else if (state == GAME_OVER) {
			if (input.equals("new")) {
				newGame();
			} else {
				System.out.println("Game over. Type new to play again.");
			}
		} else { // PLAYING
			System.out.println("Waiting for the solver... " + timer.getSecondsLeft() + " s left.");
		}
	}

	private void setTarget(String input) {
		Phase p = phases.get(current);
		Sensor s = p.getSensor();
		double target;
		try {
			target = Double.parseDouble(input);
		} catch (NumberFormatException e) {
			System.out.println("Please enter a number.");
			return;
		}
		// The sabotager can only pick realistic values for this sensor
		if (target < s.getMin() || target > s.getMax()) {
			System.out.println("Out of range! Choose between " + s.getMin() + " and " + s.getMax() + ".");
			return;
		}
		p.setTarget(target);
		xbee.send("P," + (current + 1) + "," + s.getCode());   // light the sensor's LED on the box
		timer.startTimer(PHASE_SECONDS);
		state = PLAYING;
		System.out.println("Target set. Solver has " + PHASE_SECONDS + " s and " + MAX_ATTEMPTS + " attempts.");
	}

	// ---------------------------------------------------------------
	// Messages from XBee (box) or PhaseTimer (called from their threads)
	// ---------------------------------------------------------------
	public synchronized void update(String message) {
		if (message.equals("TIMEOUT")) {
			if (state == PLAYING) {
				endGame(false, "Time ran out!");
			}
		} else if (message.equals("READY")) {
			System.out.println("[Puzzle box connected]");
		} else if (message.startsWith("S,")) {
			handleSubmission(message);
		}
	}

	// The solver pressed submit. Message format: S,<code>,<raw>[,<raw2>]
	private void handleSubmission(String message) {
		if (state != PLAYING) {
			return;                                   // not expecting an answer now
		}
		Phase p = phases.get(current);
		Sensor s = p.getSensor();
		String[] parts = message.split(",");
		if (parts.length < 3 || parts[1].charAt(0) != s.getCode()) {
			return;                                   // wrong/corrupted message, ignore
		}

		int[] raw = new int[parts.length - 2];        // the joystick sends two numbers
		try {
			for (int i = 0; i < raw.length; i++) {
				raw[i] = Integer.parseInt(parts[i + 2]);
			}
		} catch (NumberFormatException e) {
			return;
		}

		timer.pauseTimer();                           // don't charge the solver for our thinking time
		double value = s.calcValue(raw);
		lastAnswerCorrect = p.checkValue(value);
		state = WAITING_SABOTEUR;

		System.out.printf("%nSolver submitted %.1f %s (target %.1f) -> %s%n",
				value, s.getUnit(), p.getTarget(), lastAnswerCorrect ? "CORRECT" : "WRONG");
		System.out.println("Attempt " + p.getAttempts() + "/" + MAX_ATTEMPTS
				+ ", time left " + timer.getSecondsLeft() + " s");

		if (lastAnswerCorrect) {
			System.out.println("Correct answers can't be faked. Type t to confirm.");
		} else {
			String hint = (p.getHintSignal() == 'C') ? "CLOSER" : "FARTHER";
			System.out.println("True hint: " + hint + ". Sabotages left: "
					+ (MAX_SABOTAGES_PER_PHASE - p.getSabotageAttempts()) + " this phase, "
					+ (MAX_SABOTAGES_TOTAL - totalSabotages) + " total.");
			System.out.println("Type t = send truthful hint, f = send FAKE hint");
		}
	}

	// ---------------------------------------------------------------
	// Send the answer back to the puzzle box
	// ---------------------------------------------------------------
	private void sendResponse(boolean fake) {
		Phase p = phases.get(current);
		char hint = p.getHintSignal();

		if (fake) {
			boolean allowed = !lastAnswerCorrect
					&& p.getSabotageAttempts() < MAX_SABOTAGES_PER_PHASE
					&& totalSabotages < MAX_SABOTAGES_TOTAL;
			if (allowed) {
				hint = (hint == 'C') ? 'F' : 'C';     // flip the hint
				p.addSabotage();
				totalSabotages++;
				System.out.println("FAKE hint sent!");
			} else {
				System.out.println("No sabotage allowed - sending the truth.");
			}
		}

		xbee.send("R," + (lastAnswerCorrect ? 1 : 0) + "," + hint);

		if (lastAnswerCorrect) {
			System.out.println("Solver passed phase " + (current + 1) + ".");
			if (current + 1 == NUM_PHASES) {
				endGame(true, "Solver passed all phases!");
			} else {
				startPhase(current + 1);
			}
		} else if (p.getAttempts() >= MAX_ATTEMPTS) {
			endGame(false, "Solver used all " + MAX_ATTEMPTS + " attempts!");
		} else {
			state = PLAYING;
			timer.resumeTimer();
			System.out.println("Waiting for the next attempt...");
		}
	}

	private void endGame(boolean solverWon, String reason) {
		timer.pauseTimer();
		state = GAME_OVER;
		xbee.send(solverWon ? "W" : "L");             // W opens the box, L keeps it locked
		System.out.println("\n" + reason);
		System.out.println(solverWon ? "*** SOLVER WINS - box unlocked ***" : "*** SABOTAGER WINS ***");
		System.out.println("Type new to play again.");
	}
}
