package oberonemulator;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.CharBuffer;

public class MemoryMappedIO {

	private long startMillis = System.currentTimeMillis();
	private boolean inputOccurred = false, irqEnabled = false;
	private int paravirtPtr;

	private int[] keyBuf = new int[64];
	private int keyCnt;
	private int leds;

	private int spiSelected;
	private Disk sdCard;
	private InputStream socketIn = null;
	private OutputStream socketOut = null;

	private Network net;

	private int mouse;

	private Memory mem;

	private CharBuffer clipboardData;

	public MemoryMappedIO(String disk_file, final ServerSocket ss, InetSocketAddress netAddr) throws IOException {
		if (netAddr != null) Feature.SPI_NETWORK.use();
		sdCard = disk_file == null ? null : new Disk(disk_file);
		net = netAddr == null ? null : new Network(netAddr);
		if (ss == null)
			return;
		Feature.SERIAL.use();
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

	public void setIRQEnabled(boolean enabled) {
		irqEnabled = enabled;
	}

	public synchronized int readWord(int wordAddress) {
		switch ((wordAddress % Memory.MemWords) * 4 - Memory.IOStart) {
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
			Feature.SERIAL.use();
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
			Feature.SERIAL.use();
			try {
				val = socketIn != null && socketIn.available() > 0 ? 3 : 2;
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
			return val;
		}
		case 16: {
			// SPI data
			Feature.SPI.use();
			if (spiSelected == 1 && sdCard != null) {
				Feature.NATIVE_DISK.use();
				return sdCard.disk_read();
			} else if (spiSelected == 2 && net != null){
				Feature.SPI_NETWORK.use();
				return net.read();
			}
			return 255;
		}
		case 20: {
			// SPI status
			// Bit 0: rx ready
			// Other bits unused
			Feature.SPI.use();
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
		case 40: {
			// Clipboard control
			Feature.PARAVIRTUAL_CLIPBOARD.use();
			try {
				String text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
				if (text != null && text.length() > 0) {
					clipboardData = CharBuffer.wrap(text.replace('\n', '\r'));
					return clipboardData.remaining();
				} else {
					clipboardData = null;
					return 0;
				}
			} catch (Exception ex) {
				System.out.println(ex.toString());
			}
			return 0;
		}
		case 44: {
			// Clipboard data
			Feature.PARAVIRTUAL_CLIPBOARD.use();
			char ch = clipboardData.get();
			if (!clipboardData.hasRemaining())
				clipboardData = null;
			return ch;
		}
		case 48: {
			Feature.COLOR_GRAPHICS.use();
			return (mem.getImageMemory().getWidth() << 16) | mem.getImageMemory().getHeight();
		}
		default: {
			return 0;
		}
		}
	}

	private synchronized void checkProgress() {
		if (irqEnabled)
			return;
		try {
			wait(10);
		} catch (InterruptedException ex) {
		}
	}

	public synchronized void writeWord(int wordAddress, int value) {
		switch ((wordAddress % Memory.MemWords) * 4 - Memory.IOStart) {
		case 0: {
			// Power management
			Feature.POWER_MANAGEMENT.use();
			long waitMillis = startMillis + value;
			long now = System.currentTimeMillis();
			while (!inputOccurred && !irqEnabled && waitMillis > now) {
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
			Feature.SERIAL.use();
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
			Feature.SPI.use();
			if (spiSelected == 1 && sdCard != null) {
				Feature.NATIVE_DISK.use();
				sdCard.write(value);
			} else if (spiSelected == 2 && net != null) {
				Feature.SPI_NETWORK.use();
				net.write(value);
			}
			break;
		}
		case 20: {
			// SPI control
			// Bit 0-1: slave select
			// Bit 2: fast mode
			// Bit 3: netwerk enable
			// Other bits unused
			Feature.SPI.use();
			spiSelected = value & 3;
			break;
		}
		case 36: {
			// paravirtualized storage
			Feature.PARAVIRTUAL_DISK.use();
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
		case 40: {
			// Clipboard control
			Feature.PARAVIRTUAL_CLIPBOARD.use();
			clipboardData = CharBuffer.allocate(value);
			break;
		}
		case 44: {
			// Clipboard data
			Feature.PARAVIRTUAL_CLIPBOARD.use();
			clipboardData.put((char) value);
			if (!clipboardData.hasRemaining()) {
				clipboardData.flip();
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(clipboardData.toString().replace('\r', '\n')), null);
				clipboardData = null;
			}
			break;
		}
		case 48: {
			Feature.COLOR_GRAPHICS.use();
			mem.getImageMemory().setSlidingWindowBase(value);
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

	public synchronized void keyboardInput(int[] scancodes) {
		int scancodeCount = scancodes.length;
		if (scancodeCount == 0)
			return;
		for(int i=0; i < scancodeCount; i++) {
			if ((scancodes[i] & 0xff) != 0) {
				Feature.NATIVE_KEYBOARD.use();
			}
			if ((scancodes[i] & ~0xff) != 0) {
				Feature.PARAVIRTUAL_KEYBOARD.use();
			}
		}
		if (keyBuf.length - keyCnt >= scancodeCount) {
			System.arraycopy(scancodes, 0, keyBuf, keyCnt, scancodeCount);
			keyCnt += scancodeCount;
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
