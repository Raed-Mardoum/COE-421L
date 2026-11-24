import java.util.Scanner;

/**
 * Main: creates the objects and reads the sabotager's commands from the terminal.
 *
 * Threads while the game runs:
 *   1. main thread  - reads what the sabotager types
 *   2. XBee thread  - reads messages from the puzzle box
 *   3. timer thread - counts down the phase time
 */
public class Main {

	// Change this to your XBee Explorer port (Arduino IDE > Tools > Port)
	static final String PORT = "COM8";

	public static void main(String[] args) {
		XBee xbee = new XBee(PORT);
		PhaseTimer timer = new PhaseTimer();
		GameController game = new GameController(xbee, timer);

		Scanner scanner = new Scanner(System.in);
		game.newGame();
		while (true) {
			String input = scanner.nextLine();
			game.handleInput(input);
		}
	}
}
