package oberonemulator;

public class PS2 {

	int[][] scancode_map = new int[128][];

	public PS2() {
		addKey(0x1C, 'a', 'A');
		addKey(0x32, 'b', 'B');
		addKey(0x21, 'c', 'C');
		addKey(0x23, 'd', 'D');
		addKey(0x24, 'e', 'E');
		addKey(0x2B, 'f', 'F');
		addKey(0x34, 'g', 'G');
		addKey(0x33, 'h', 'H');
		addKey(0x43, 'i', 'I');
		addKey(0x3B, 'j', 'J');
		addKey(0x42, 'k', 'K');
		addKey(0x4B, 'l', 'L');
		addKey(0x3A, 'm', 'M');
		addKey(0x31, 'n', 'N');
		addKey(0x44, 'o', 'O');
		addKey(0x4D, 'p', 'P');
		addKey(0x15, 'q', 'Q');
		addKey(0x2D, 'r', 'R');
		addKey(0x1B, 's', 'S');
		addKey(0x2C, 't', 'T');
		addKey(0x3C, 'u', 'U');
		addKey(0x2A, 'v', 'V');
		addKey(0x1D, 'w', 'W');
		addKey(0x22, 'x', 'X');
		addKey(0x35, 'y', 'Y');
		addKey(0x1A, 'z', 'Z');
		addKey(0x16, '1', '!');
		addKey(0x1E, '2', '@');
		addKey(0x26, '3', '#');
		addKey(0x25, '4', '$');
		addKey(0x2E, '5', '%');
		addKey(0x36, '6', '^');
		addKey(0x3D, '7', '&');
		addKey(0x3E, '8', '*');
		addKey(0x46, '9', '(');
		addKey(0x45, '0', ')');
		addKey(0x5A, '\r', '\0');
		addKey(0x76, (char) 27, '\0');
		addKey(0x66, '\b', '\0');
		addKey(0x0D, '\t', '\0');
		addKey(0x29, ' ', '\0');
		addKey(0x4E, '-', '_');
		addKey(0x55, '=', '+');
		addKey(0x54, '[', '{');
		addKey(0x5B, ']', '}');
		addKey(0x5D, '\\', '|');
		addKey(0x4C, ';', ':');
		addKey(0x52, '\'', '"');
		addKey(0x0E, '`', '~');
		addKey(0x41, ',', '<');
		addKey(0x49, '.', '>');
		addKey(0x4A, '/', '?');
		addKey(0x05, (char) 26, '\0');
	}

	private void addKey(int keycode, char lower, char upper) {
		scancode_map[lower] = new int[] { keycode | (lower << 24), 0xF0, keycode };
		if (upper != '\0')
			scancode_map[upper] = new int[] { 0x12, keycode | (upper << 24), 0xF0, keycode, 0xF0, 0x12 };
	}

	public int[] ps2_encode(char ch) {
		int[] scancodes = null;
		if (ch == '\n')
			ch = '\r';
		if (ch < 128)
			scancodes = scancode_map[ch];
		if (scancodes == null) {
			// type "x" with character hint
			scancodes = new int[] { 0x22 | (ch << 24), 0xF0, 0x22 };
		}
		return scancodes;
	}
}
