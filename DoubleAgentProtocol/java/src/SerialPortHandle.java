import jssc.SerialPort;
import jssc.SerialPortException;

/**
 * SerialPortHandle: the class given in Lab 9, unchanged except that
 * printLine() is now synchronized (two threads may send at the same time,
 * and without the lock their characters could get mixed together).
 */
class SerialPortHandle {

	SerialPort sp;
	String path;

	public SerialPortHandle(String path) {
		super();
		this.sp = new SerialPort(path);
		this.path = path;
		try {
			sp.openPort();                 // open the serial port
			sp.setParams(9600, 8, 1, 0);   // 9600 baud, 8 data bits, 1 stop bit, no parity

			// Flush garbage data on initial open
			while (sp.getInputBufferBytesCount() > 0) {
				sp.readBytes();
			}
		} catch (SerialPortException e) {
			e.printStackTrace();
		}
	}

	// Read characters one at a time until a newline; return the line
	public String readLine() {
		StringBuilder string = new StringBuilder();
		while (true) {
			try {
				byte[] buffer = sp.readBytes(1);   // blocking read of 1 byte
				if (buffer == null || buffer.length == 0)
					continue;

				char c = (char) buffer[0];

				if (c == '\n' || c == '\r') {
					if (string.length() == 0) {
						continue; // skip empty lines
					} else {
						break;    // return non-empty line
					}
				}
				string.append(c);

			} catch (SerialPortException e) {
				e.printStackTrace();
				break;
			}
		}
		return string.toString().trim();
	}

	// Send a string followed by a newline
	public synchronized void printLine(String s) {
		byte byteArray[] = s.getBytes();
		try {
			sp.writeBytes(byteArray);
			sp.writeByte((byte) '\n');
		} catch (SerialPortException e1) {
			e1.printStackTrace();
		}
	}
}
