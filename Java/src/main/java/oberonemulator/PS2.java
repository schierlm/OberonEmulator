package oberonemulator;

import java.awt.event.KeyEvent;

public class PS2 {

	byte[] scancode_map = new byte[128];
	int[][] vkMap = new int[5][1024];

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
		addKey(0x6B, (char) 0x11, '\0');
		addKey(0x74, (char) 0x12, '\0');
		addKey(0x75, (char) 0x13, '\0');
		addKey(0x72, (char) 0x14, '\0');

		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_A, 0x1C, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_B, 0x32, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_C, 0x21, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_D, 0x23, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_E, 0x24, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F, 0x2B, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_G, 0x34, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_H, 0x33, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_I, 0x43, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_J, 0x3B, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_K, 0x42, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_L, 0x4B, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_M, 0x3A, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_N, 0x31, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_O, 0x44, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_P, 0x4D, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_Q, 0x15, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_R, 0x2D, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_S, 0x1B, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_T, 0x2C, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_U, 0x3C, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_V, 0x2A, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_W, 0x1D, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_X, 0x22, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_Y, 0x35, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_Z, 0x1A, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_1, 0x16, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_2, 0x1E, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_3, 0x26, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_4, 0x25, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_5, 0x2E, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_6, 0x36, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_7, 0x3D, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_8, 0x3E, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_9, 0x46, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_0, 0x45, VK_TYPE.CHAR_BASED);

		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_ENTER, 0x5A, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_ESCAPE, 0x76, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_BACK_SPACE, 0x66, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_TAB, 0x0D, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_SPACE, 0x29, VK_TYPE.CHAR_BASED); // CHAR_BASED because of dead keys

		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_MINUS, 0x4E, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_EQUALS, 0x55, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_LEFT_PARENTHESIS, 0x54, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_RIGHT_PARENTHESIS, 0x5B, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_BACK_SLASH, 0x5D, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_LESS, 0x61, VK_TYPE.CHAR_BASED);

		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_SEMICOLON, 0x4C, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_QUOTE, 0x52, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_BACK_QUOTE, 0x0E, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_COMMA, 0x41, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_PERIOD, 0x49, VK_TYPE.CHAR_BASED);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_SLASH, 0x4A, VK_TYPE.CHAR_BASED);

		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F1, 0x05, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F2, 0x06, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F3, 0x04, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F4, 0x0C, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F5, 0x03, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F6, 0x0B, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F7, 0x83, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F8, 0x0A, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F9, 0x01, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F10, 0x09, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F11, 0x78, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_F12, 0x07, VK_TYPE.NORMAL);

		// Most of the keys below are not used by Oberon

		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_INSERT, 0x70, VK_TYPE.NUMLOCK_HACK);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_HOME, 0x6C, VK_TYPE.NUMLOCK_HACK);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_PAGE_UP, 0x7D, VK_TYPE.NUMLOCK_HACK);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_DELETE, 0x71, VK_TYPE.NUMLOCK_HACK);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_END, 0x69, VK_TYPE.NUMLOCK_HACK);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_PAGE_DOWN, 0x7A, VK_TYPE.NUMLOCK_HACK);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_RIGHT, 0x74, VK_TYPE.NUMLOCK_HACK);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_LEFT, 0x6B, VK_TYPE.NUMLOCK_HACK);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_DOWN, 0x72, VK_TYPE.NUMLOCK_HACK);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_UP, 0x75, VK_TYPE.NUMLOCK_HACK);

		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_DIVIDE, 0x4A, VK_TYPE.SHIFT_HACK);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_MULTIPLY, 0x7C, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_SUBTRACT, 0x7B, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_ADD, 0x79, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_ENTER, 0x5A, VK_TYPE.EXTENDED);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_NUMPAD1, 0x69, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_NUMPAD2, 0x72, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_NUMPAD3, 0x7A, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_NUMPAD4, 0x6B, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_NUMPAD5, 0x73, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_NUMPAD6, 0x74, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_NUMPAD7, 0x6C, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_NUMPAD8, 0x75, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_NUMPAD9, 0x7D, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_NUMPAD0, 0x70, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_NUMPAD, KeyEvent.VK_DECIMAL, 0x71, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_STANDARD, KeyEvent.VK_CONTEXT_MENU, 0x2F, VK_TYPE.EXTENDED);
		addVK(KeyEvent.KEY_LOCATION_LEFT, KeyEvent.VK_CONTROL, 0x14, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_LEFT, KeyEvent.VK_SHIFT, 0x12, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_LEFT, KeyEvent.VK_ALT, 0x11, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_LEFT, KeyEvent.VK_WINDOWS, 0x1F, VK_TYPE.EXTENDED);
		addVK(KeyEvent.KEY_LOCATION_RIGHT, KeyEvent.VK_CONTROL, 0x14, VK_TYPE.EXTENDED);
		addVK(KeyEvent.KEY_LOCATION_RIGHT, KeyEvent.VK_SHIFT, 0x59, VK_TYPE.NORMAL);
		addVK(KeyEvent.KEY_LOCATION_RIGHT, KeyEvent.VK_ALT, 0x11, VK_TYPE.EXTENDED);
		addVK(KeyEvent.KEY_LOCATION_RIGHT, KeyEvent.VK_WINDOWS, 0x27, VK_TYPE.EXTENDED);
	}

	private void addKey(int scancode, char lower, char upper) {
		if (scancode > 0x7F)
			throw new RuntimeException();
		scancode_map[lower] = (byte) scancode;
		if (upper != '\0')
			scancode_map[upper] = (byte) (0x80 | scancode);
	}

	private void addVK(int location, int keycode, int scancode, VK_TYPE type) {
		vkMap[location][keycode] = scancode + (type.ordinal() << 8);
	}

	private static enum VK_TYPE {
		CHAR_BASED, NORMAL, EXTENDED, NUMLOCK_HACK, SHIFT_HACK
	}

	public byte decode(char ch) {
		return ch < 128 ? scancode_map[ch] : 0;
	}

	public int[] ps2_encode(char ch, boolean hint) {
		int[] scancodes;
		if (ch == '\n')
			ch = '\r';
		int hintValue = hint ? ((ch << 24) | (ch >>> 8 << 16)) : 0;
		byte scancode = decode(ch);
		if (scancode == 0) {
			// type "x" with character hint
			scancodes = hint ? new int[] { 0x22 | hintValue, 0xF0, 0x22 } : new int[0];
		} else if ((scancode & 0x80) != 0) {
			int keycode = scancode & 0x7F;
			scancodes = new int[] { 0x12, keycode | hintValue, 0xF0, keycode, 0xF0, 0x12 };
		} else {
			scancodes = new int[] { scancode | hintValue, 0xF0, scancode };
		}
		return scancodes;
	}

	public int[] encodeNative(int location, int keyCode, boolean press, boolean includeCharBased) {
		int value = vkMap[location][keyCode];
		if (value == 0 && vkMap[KeyEvent.KEY_LOCATION_STANDARD][keyCode] != 0)
			System.out.println("Key " + keyCode + " exists as standard but not as " + location);
		VK_TYPE type = VK_TYPE.values()[value >> 8];
		if (value == 0 || (type == VK_TYPE.CHAR_BASED && !includeCharBased))
			return new int[0];
		value = value & 0xFF;
		switch (type) {
		case CHAR_BASED:
		case NORMAL:
			if (press)
				return new int[] { value };
			else
				return new int[] { 0xF0, value };
		case EXTENDED:
			if (press)
				return new int[] { 0xE0, value };
			else
				return new int[] { 0xE0, 0xF0, value };
		case NUMLOCK_HACK:
			if (press)
				return new int[] { 0xE0, 0x12, 0xE0, value };
			else
				return new int[] { 0xE0, 0xF0, value, 0xE0, 0xF0, 0x12 };
		case SHIFT_HACK:
			if (press)
				return new int[] { 0xE0, 0xF0, 0x12, 0xE0, 0xF0, 0x59, 0xE0, value };
			else
				return new int[] { 0xE0, 0xF0, value };
		}
		return new int[0];
	}
}
