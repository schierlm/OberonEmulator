package oberonemulator;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class MemoryMappedIO {

	private long startMillis = System.currentTimeMillis();
	private boolean inputOccurred = false, irqEnabled = false;
	private int paravirtPtr;

	private int[] keyBuf = new int[64];
	private int keyCnt;
	private int leds;

	private int[] hwEnumBuf = new int[32];
	private int hwEnumOffs, hwEnumLen;

	private int spiSelected;
	private Disk sdCard;
	private byte[][] socketsIn = { null, null };
	private int[] socketsInOffs = { 0, 0 };
	private OutputStream[] socketsOut = { null, null };
	private int socketSelected = 0;

	private Network net;

	private final HostFS hostfs;

	private final WizNet wiznet;

	private int mouse;

	private Memory mem;

	private CharBuffer clipboardData;

	public MemoryMappedIO(String disk_file, final ServerSocket ss, final Socket sock, final InetSocketAddress netAddr, final File hostFsDirectory, final ServerSocket ss2) throws IOException {
		if (netAddr != null) Feature.SPI_NETWORK.use();
		if (hostFsDirectory != null) Feature.HOST_FILESYSTEM.use();
		sdCard = disk_file == null ? null : new Disk(disk_file);
		net = netAddr == null ? null : new Network(netAddr);
		hostfs = hostFsDirectory == null ? null : new HostFS(hostFsDirectory);
		wiznet = new WizNet();
		if (ss != null || sock != null) {
			Feature.SERIAL.use();
			new RS232Thread(0, sock, ss);
		}
		if (ss2 != null) {
			Feature.SERIAL.use();
			Feature.MULTI_SERIAL.use();
			new RS232Thread(1, null, ss2);
		}
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
			int val = 0;
			Feature.SERIAL.use();
			if (socketsIn[socketSelected] != null && socketsInOffs[socketSelected] < socketsIn[socketSelected].length) {
				val = socketsIn[socketSelected][socketsInOffs[socketSelected]] & 0xFF;
				socketsInOffs[socketSelected]++;
				if (socketsInOffs[socketSelected] == socketsIn[socketSelected].length) {
					synchronized (socketsIn[socketSelected]) {
						socketsIn[socketSelected].notifyAll();
					}
				}
			}
			return val;
		}
		case 12: {
			// RS232 status
			int val;
			Feature.SERIAL.use();
			val = (socketsIn[0] != null && socketsInOffs[0] < socketsIn[0].length ? 1 : 0) |
					(socketsOut[0] != null ? 2 : 0) |
					(socketsIn[1] != null && socketsInOffs[1] < socketsIn[1].length ? 4 : 0) |
					(socketsOut[1] != null ? 8 : 0);
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
		case 60: {
			// hardware enumerator
			if (hwEnumOffs < hwEnumLen) {
				return hwEnumBuf[hwEnumOffs++];
			}
			return 0;
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
				if (socketsOut[socketSelected] != null) {
					socketsOut[socketSelected].write(value & 0xff);
					socketsOut[socketSelected].flush();
				}
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
			break;
		}
		case 12: {
			// RS232 socket select
			Feature.MULTI_SERIAL.use();
			socketSelected = value == 1 ? 1 : 0;
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
		case 32: {
			// host filesystem / wiznet
			int highBits = mem.getRAM()[value / 4] >> 16;
			if (highBits == 0) {
				Feature.HOST_FILESYSTEM.use();
				if (hostfs != null)
					hostfs.handleCommand(value / 4, mem.getRAM());
			} else if (highBits == 1) {
				Feature.PARAVIRTUAL_WIZNET.use();
				wiznet.handleCommand(value/4, mem.getRAM());
			}
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
				if (paravirtPtr == 0x3FFFFFFF)
					sdCard.trimBeforeDoubleSector(sector);
				else
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
			break;
		}
		case 60: {
			// hardware enumerator (minimal stub implementation)
			hwEnumOffs = hwEnumLen = 0;
			switch(value) {
			case 0:
				hwEnumBuf[hwEnumLen++] = 1; // version
				hwEnumBuf[hwEnumLen++] = (('m' << 24) | ('V' << 16) | ('i' << 8) | 'd');
				hwEnumBuf[hwEnumLen++] = (('T' << 24) | ('i' << 16) | ('m' << 8) | 'r');
				hwEnumBuf[hwEnumLen++] = (('v' << 24) | ('D' << 16) | ('s' << 8) | 'k');
				break;
			case ('m' << 24) | ('V' << 16) | ('i' << 8) | 'd':
				hwEnumBuf[hwEnumLen++] = 1; // number of modes
				hwEnumBuf[hwEnumLen++] = 0; // mode switching address
				hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getWidth();
				hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getHeight(); // screen height
				hwEnumBuf[hwEnumLen++] = 128; // scanline span
				hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
				break;
			case ('T' << 24) | ('i' << 16) | ('m' << 8) | 'r':
				hwEnumBuf[hwEnumLen++] = -64; // MMIO address
				hwEnumBuf[hwEnumLen++] = 1; // Power management supported
				break;
			case ('v' << 24) | ('D' << 16) | ('s' << 8) | 'k':
				hwEnumBuf[hwEnumLen++] = -28; // MMIO address
				break;
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

	public void reset() {
		wiznet.reset();
		if (hostfs != null)
			hostfs.reset();
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

	public int[] getTimeInit() {
		long millis = System.currentTimeMillis() / 1000 * 1000;
		Calendar cal = new GregorianCalendar();
		cal.setTimeInMillis(millis);
		int clock = ((cal.get(Calendar.YEAR) % 100) * 16 + cal.get(Calendar.MONTH) + 1) * 32 + cal.get(Calendar.DAY_OF_MONTH);
		clock = ((clock * 32 + cal.get(Calendar.HOUR_OF_DAY)) * 64 + cal.get(Calendar.MINUTE)) * 64 + cal.get(Calendar.SECOND);
		return new int[] { (int) (millis - startMillis), clock };
	}

	private class RS232Thread extends Thread {
		private final int number;
		private final Socket sock;
		private final ServerSocket ss;

		private RS232Thread(int number, Socket sock, ServerSocket ss) {
			this.number = number;
			this.sock = sock;
			this.ss = ss;
			setDaemon(true);
			start();
		}

		@Override
		public void run() {
			try {
				Socket s = sock;
				if (ss != null) {
					s = ss.accept();
					ss.close();
				}
				InputStream rawSocketIn = s.getInputStream();
				synchronized (MemoryMappedIO.this) {
					socketsOut[number] = s.getOutputStream();
				}
				int len;
				byte[] b = new byte[4096];
				while ((len = rawSocketIn.read(b)) != -1) {
					synchronized (MemoryMappedIO.this) {
						socketsIn[number] = Arrays.copyOfRange(b, 0, len);
						socketsInOffs[number] = 0;
						inputOccurred = true;
						MemoryMappedIO.this.notifyAll();
					}
					synchronized (socketsIn[number]) {
						while (socketsInOffs[number] < socketsIn[number].length)
							socketsIn[number].wait();
					}
					synchronized (MemoryMappedIO.this) {
						socketsIn[number] = null;
					}
				}
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
	}
}
