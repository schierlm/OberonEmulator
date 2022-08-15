package oberonemulator;

import java.awt.event.KeyEvent;

public abstract class Keyboard {

	public abstract void press(int location, int keyCode);

	public abstract void release(int location, int keyCode);

	public abstract void type(char keyChar);

	protected PS2 ps2 = new PS2();
	protected MemoryMappedIO mmio;

	public void setMMIO(MemoryMappedIO mmio) {
		this.mmio = mmio;
	}

	public static class VirtualKeyboard extends ParavirtualKeyboard {

		private boolean hint;

		public VirtualKeyboard(boolean hint) {
			this.hint = hint;
		}

		public void type(char keyChar) {
			mmio.keyboardInput(ps2.ps2_encode(keyChar, hint));
		}
	}

	public static class ParavirtualKeyboard extends Keyboard {
		public void press(int location, int keyCode) {
			if (keyCode == KeyEvent.VK_F1)
				type((char) 26);
			if (keyCode == KeyEvent.VK_LEFT)
				type((char) 0x11);
			if (keyCode == KeyEvent.VK_RIGHT)
				type((char) 0x12);
			if (keyCode == KeyEvent.VK_UP)
				type((char) 0x13);
			if (keyCode == KeyEvent.VK_DOWN)
				type((char) 0x14);
		}

		public void release(int location, int keyCode) {
		}

		public void type(char keyChar) {
			mmio.keyboardInput(new int[] { (keyChar << 24) | (keyChar >>> 8 << 16) });
		}
	}

	public static class NativeKeyboard extends Keyboard {
		public void press(int location, int keyCode) {
			mmio.keyboardInput(ps2.encodeNative(location, keyCode, true, true));
		}

		public void release(int location, int keyCode) {
			mmio.keyboardInput(ps2.encodeNative(location, keyCode, false, true));
		}

		public void type(char keyChar) {
		}
	}

	public static class HybridKeyboard extends Keyboard {

		boolean shiftState, shiftToggled;
		int keyQueueOffset = 0;
		int[] keyQueue = new int[16];

		private void toggleShift(boolean always) {
			if (!always && !shiftToggled)
				return;
			shiftState = !shiftState;
			shiftToggled = !shiftToggled;
			mmio.keyboardInput(ps2.encodeNative(KeyEvent.KEY_LOCATION_LEFT, KeyEvent.VK_SHIFT, shiftState, false));
		}

		public void press(int location, int keyCode) {
			if (keyCode == KeyEvent.VK_SHIFT) {
				shiftState = true;
				shiftToggled = false;
			}
			if (keyCode == KeyEvent.VK_ALT && location == KeyEvent.KEY_LOCATION_RIGHT) {
				// AltGr hack
				mmio.keyboardInput(ps2.encodeNative(KeyEvent.KEY_LOCATION_LEFT, KeyEvent.VK_CONTROL, false, false));
			}
			int[] scancodes = ps2.encodeNative(location, keyCode, true, false);
			if (scancodes.length > 0) {
				toggleShift(false);
				mmio.keyboardInput(scancodes);
			} else if (keyQueueOffset < keyQueue.length - 1) {
				keyQueueOffset++;
				keyQueue[keyQueueOffset] = (keyCode << 16) | 1;
			}
		}

		public void type(char keyChar) {
			byte decoded = ps2.decode(keyChar);
			if (decoded == 0)
				return;
			if ((keyQueue[keyQueueOffset] & 0x0F) != 1) {
				if (keyChar >= ' ')
					System.out.println("Decoded key " + keyChar + " but no event available to assign it to.");
				return;
			}
			keyQueue[keyQueueOffset] |= ((decoded & 0xFF) << 8) | 2;
			if (shiftState ^ ((decoded & 0x80) != 0))
				toggleShift(true);
			mmio.keyboardInput(new int[] { decoded & 0x7F });
		}

		public void release(int location, int keyCode) {
			if (keyCode == KeyEvent.VK_SHIFT) {
				shiftState = shiftToggled = false;
			}
			int[] scancodes = ps2.encodeNative(location, keyCode, false, false);
			if (scancodes.length > 0) {
				mmio.keyboardInput(scancodes);
				toggleShift(false);
			} else {
				int offs = keyQueueOffset;
				while (offs > 0 && ((keyQueue[offs] & 1) == 0 || (keyQueue[offs] >> 16) != keyCode)) {
					offs--;
				}
				if (offs == 0) {
					System.out.println("Key released but not found in queue");
					return;
				}
				if ((keyQueue[offs] & 2) == 2) {
					mmio.keyboardInput(new int[] { 0xF0, (keyQueue[offs] >> 8) & 0x7F });
					toggleShift(false);
				}
				keyQueue[offs] = 0;
				while (keyQueueOffset > 0 && keyQueue[keyQueueOffset] == 0)
					keyQueueOffset--;
			}
		}
	}
}
