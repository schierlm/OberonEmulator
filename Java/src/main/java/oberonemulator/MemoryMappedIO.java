package oberonemulator;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class MemoryMappedIO {

	private long startMillis = System.currentTimeMillis();
	private boolean inputOccurred = false;
	private int paravirtPtr;

	private int[] keyBuf = new int[64];
	private int keyCnt;
	private int leds;

	private int spiSelected;
	private Disk sdCard;
	private InputStream socketIn = null;
	private OutputStream socketOut = null;

	private int mouse;

	private Memory mem;

	public MemoryMappedIO(String disk_file, final ServerSocket ss) throws IOException {
		sdCard = disk_file == null ? null : new Disk(disk_file);
		if (ss == null)
			return;
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					Socket s = ss.accept();
					synchronized (MemoryMappedIO.this) {
						socketIn = s.getInputStream();
						socketOut = s.getOutputStream();
					}
					ss.close();
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
			}
		});
		t.setDaemon(true);
		t.start();
	}

	public void setMem(Memory mem) {
		this.mem = mem;
	}

	public synchronized int readWord(int wordAddress) {
		switch (wordAddress * 4 - Memory.IOStart) {
		case 0: {
			// Millisecond counter
			checkProgress();
			return (int) (System.currentTimeMillis() - startMillis);
		}
		case 4: {
			// Switches
			return 0;
		}
		case 8: {
			// RS232 data
			int val;
			try {
				val = socketIn == null ? 0 : socketIn.read();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
			return val;
		}
		case 12: {
			// RS232 status
			int val;
			try {
				val = socketIn != null && socketIn.available() > 0 ? 3 : 2;
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
			return val;
		}
		case 16: {
			// SPI data
			if (spiSelected == 1 && sdCard != null) {
				return sdCard.disk_read();
			}
			return 255;
		}
		case 20: {
			// SPI status
			// Bit 0: rx ready
			// Other bits unused
			return 1;
		}
		case 24: {
			// Mouse input / keyboard status
			int _mouse = mouse;
			if (keyCnt > 0) {
				_mouse |= 0x10000000;
			} else {
				checkProgress();
			}
			return _mouse;
		}
		case 28: {
			// Keyboard input
			if (keyCnt > 0) {
				int scancode = keyBuf[0];
				keyCnt--;
				System.arraycopy(keyBuf, 1, keyBuf, 0, keyCnt);
				return scancode;
			}
			return 0;
		}

		case 32: {
			// clipboard
			try {
				String text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
				return text == null ? 0 : text.length();
			} catch (Exception ex) {
				System.out.println(ex.toString());
				return 0;
			}
		}
		default: {
			return 0;
		}
		}
	}

	private synchronized void checkProgress() {
		try {
			wait(10);
		} catch (InterruptedException ex) {
		}
	}

	public synchronized void writeWord(int wordAddress, int value) {
		switch (wordAddress * 4 - Memory.IOStart) {
		case 0: {
			// Power management
			long waitMillis = startMillis + value;
			long now = System.currentTimeMillis();
			while (!inputOccurred && waitMillis > now) {
				try {
					wait(waitMillis - now);
				} catch (InterruptedException ex) {
				}
				now = System.currentTimeMillis();
			}
			inputOccurred = false;
			break;
		}
		case 4: {
			// LED control
			leds = value;
			mem.triggerRepaint();
			break;
		}
		case 8: {
			// RS232 data
			try {
				if (socketOut != null) {
					socketOut.write(value & 0xff);
					socketOut.flush();
				}
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
			break;
		}
		case 16: {
			// SPI write
			if (spiSelected == 1 && sdCard != null) {
				sdCard.write(value);
			}
			break;
		}
		case 20: {
			// SPI control
			// Bit 0-1: slave select
			// Bit 2: fast mode
			// Bit 3: netwerk enable
			// Other bits unused
			spiSelected = value & 3;
			break;
		}
		case 32: {
			// clipboard
			// Bit 31: Store (=1) or load (=0)
			// Rest: Pointer
			int ptr = value & 0x7FFFFFFF;
			if ((value & 0x80000000) != 0) {
				// store
				StringBuilder sb = new StringBuilder();
				int wrd = mem.readWord(ptr / 4, false);
				outer: while (true) {
					for (int i = 0; i < 4; i++) {
						char b = (char) ((wrd >> (8 * i)) & 0xFF);
						if (b == 0)
							break outer;
						sb.append(b);
					}
					ptr += 4;
					wrd = mem.readWord(ptr / 4, false);
				}
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString().replace('\r', '\n')), null);
			} else {
				try {
					String text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
					text = text.replace('\n', '\r');
					for (int i = 0; i < text.length(); i += 4) {
						int vl = 0;
						for (int j = 0; j < Math.min(text.length() - i, 4); j++) {
							vl |= (((byte) text.charAt(i + j)) & 0xFF) << (j * 8);
						}
						mem.writeWord((ptr + i) / 4, vl);
					}
				} catch (Exception ex) {
					System.out.println(ex.toString());
				}
			}
			break;
		}
		case 36: {
			// paravirtualized storage
			if ((value & 0xC0000000) == 0) { // setPtr
				paravirtPtr = value;
			}
			if ((value & 0xC0000000) == 0x80000000) { // read
				int sector = value - 0x80000000;
				sdCard.getDoubleSector(sector, mem.getRAM(), paravirtPtr / 4);
			}
			if ((value & 0xC0000000) == 0xC0000000) { // write
				int sector = value - 0xC0000000;
				sdCard.setDoubleSector(sector, mem.getRAM(), paravirtPtr / 4);
			}
			break;
		}
		}
	}

	public synchronized void mouseMoved(int mouse_x, int mouse_y) {
		int oldMouse = mouse;
		if (mouse_x >= 0 && mouse_x < 4096) {
			mouse = (mouse & ~0x00000FFF) | mouse_x;
		}
		if (mouse_y >= 0 && mouse_y < 4096) {
			mouse = (mouse & ~0x00FFF000) | (mouse_y << 12);
		}
		if (mouse != oldMouse) {
			inputOccurred = true;
			notifyAll();
		}
	}

	public synchronized void mouseButton(int button, boolean down) {
		if (button >= 1 && button < 4) {
			int bit = 1 << (27 - button);
			if (down) {
				mouse |= bit;
			} else {
				mouse &= ~bit;
			}
		}
		inputOccurred = true;
		notifyAll();
	}

	private PS2 ps2 = new PS2();

	public synchronized void keyboardInput(char keyChar) {
		keyboardInput(ps2.ps2_encode(keyChar));
	}

	public synchronized void keyboardInput(int[] scancodes) {
		if (keyBuf.length - keyCnt >= scancodes.length) {
			System.arraycopy(scancodes, 0, keyBuf, keyCnt, scancodes.length);
			keyCnt += scancodes.length;
		}
		inputOccurred = true;
		notifyAll();
	}

	public synchronized void dispose() {
		sdCard.free();
	}

	public String getLEDs() {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 7; i >= 0; i--) {
			if ((leds & (1 << i)) != 0) {
				sb.append(i);
			} else {
				sb.append('-');
			}
		}
		return sb.append("]").toString();
	}
}
