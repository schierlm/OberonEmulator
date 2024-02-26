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
	private boolean inputOccurred = false, irqEnabled = false, fastmode = false;
	private int paravirtPtr;

	private int[] keyBuf = new int[64];
	private int keyCnt;
	private int leds;

	private int[] hwEnumBuf = new int[32];
	private int hwEnumOffs, hwEnumLen;

	private StringBuilder debugBuffer = new StringBuilder();
	private int currentDisplayMode, origWidth, origHeight, keyboardMode;
	private int[] timeInit = null;

	private int spiSelected;
	private Disk sdCard;
	private byte[][] socketsIn = { null, null };
	private int[] socketsInOffs = { 0, 0 };
	private OutputStream[] socketsOut = { null, null };
	private int socketSelected = 0;

	private Network net;

	private final HostFS hostfs;

	private final WizNet wiznet;

	private final HostTransfer hostTransfer;

	private int mouse;

	private Memory mem;

	private JitCPU jitCPU;

	private CharBuffer clipboardData;

	public MemoryMappedIO(String disk_file, final ServerSocket ss, final Socket sock, final InetSocketAddress netAddr, final File hostFsDirectory, final ServerSocket ss2, final Socket sock2) throws IOException {
		if (netAddr != null) Feature.SPI_NETWORK.use();
		if (hostFsDirectory != null) Feature.HOST_FILESYSTEM.use();
		sdCard = disk_file == null ? null : new Disk(disk_file);
		net = netAddr == null ? null : new Network(netAddr);
		hostfs = hostFsDirectory == null ? null : new HostFS(hostFsDirectory);
		wiznet = new WizNet();
		hostTransfer = new HostTransfer();
		if (ss != null || sock != null) {
			Feature.SERIAL.use();
			new RS232Thread(0, sock, ss);
		}
		if (ss2 != null) {
			Feature.SERIAL.use();
			Feature.MULTI_SERIAL.use();
			new RS232Thread(1, sock2, ss2);
		}
	}

	public void setMem(Memory mem) {
		this.mem = mem;
		origWidth = mem.getImageMemory().getWidth();
		origHeight = mem.getImageMemory().getHeight();
	}

	public void setJitCPU(JitCPU jitCPU) {
		this.jitCPU = jitCPU;
	}

	public void setIRQEnabled(boolean enabled) {
		irqEnabled = enabled;
	}

	public void setFastMode(boolean fastmode) {
		this.fastmode = fastmode;
	}

	public void setKeyboardMode(int keyboardMode) {
		this.keyboardMode = keyboardMode;
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
			} else if (spiSelected == 2 && net != null) {
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
			// video mode
			return currentDisplayMode;
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
		if (irqEnabled || fastmode)
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
		case 24: {
			// cache coherence
			if (mem.getCodeRAM() != mem.getRAM()) {
				System.arraycopy(mem.getRAM(), value / 4, mem.getCodeRAM(), value / 4, mem.getRAM().length - value / 4);
			}
			if (jitCPU != null) {
				jitCPU.clearCache(value);
			}
			break;
		}
		case 32: {
			// host filesystem / host transfer / wiznet
			int highBits = mem.getRAM()[value / 4] >> 16;
			if (highBits == 0) {
				Feature.HOST_FILESYSTEM.use();
				if (hostfs != null)
					hostfs.handleCommand(value / 4, mem.getRAM());
			} else if (highBits == 1) {
				Feature.PARAVIRTUAL_WIZNET.use();
				wiznet.handleCommand(value / 4, mem.getRAM());
			} else if (highBits == 2) {
				Feature.PARAVIRTUAL_HOST_TRANSFER.use();
				hostTransfer.handleCommand(value / 4, mem.getRAM());
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
			// video mode
			if (value >= 0 && value < 12) {
				int depth = value < 3 ? 1 : value < 6 ? 4 : value < 9 ? 8 : 32;
				mem.getImageMemory().setDepth(depth);
				switch (value % 3) {
				case 0: mem.getImageMemory().resize(origWidth, origHeight, Math.max(mem.getImageMemory().getMinimumSpan(), origWidth / 8) * depth); break;
				case 1: mem.getImageMemory().resize(1024, 768, Math.max(mem.getImageMemory().getMinimumSpan(), 128) * depth); break;
				case 2:	mem.getImageMemory().resize(800, 600, Math.max(mem.getImageMemory().getMinimumSpan(), 100) * depth); break;
				}
				currentDisplayMode = value;
			} else {
				int mode = value >>> 30;
				int width = (value >>> 15) & ((1 << 15) - 1);
				int height = (value) & ((1 << 15) - 1);
				if (mode < 1 || mode > 3)
					throw new RuntimeException("Invalid video mode " + value);
				if (mode == 2 && width >= 16384 && height < 16384) {
					mode = 4;
					width -= 16384;
				}
				if (width == 0 && height == 0) {
					width = mem.getImageMemory().getPreferredWidth();
					height = mem.getImageMemory().getPreferredHeight();
					width = width / 32 * 32;
					if (width < 64) width = 64;
					if (height < 64) height = 64;
					value = (mode << 30) | (width << 15) | height;
				}
				if (width % 32 != 0)
					throw new RuntimeException("Invalid width: " + width);
				if (mode == 1) {
					mem.getImageMemory().setDepth(1);
				} else if (mode == 2) {
					mem.getImageMemory().setDepth(8);
				} else if (mode == 3) {
					mem.getImageMemory().setDepth(4);
				} else if (mode == 4) {
					mem.getImageMemory().setDepth(32);
				}
				mem.getImageMemory().resize(width, height, -1);
				currentDisplayMode = value;
			}
			String resolutionError = mem.getImageMemory().validateResolution(mem.getPaletteStart());
			if (resolutionError != null) {
				throw new RuntimeException(resolutionError);
			}
			break;
		}
		case 52: {
			if (value == 0 && debugBuffer.length() > 0) {
				System.out.println(debugBuffer);
				debugBuffer.setLength(0);
			} else {
				debugBuffer.append(((char) value));
			}
			break;
		}
		case 60: {
			// hardware enumerator
			hwEnumOffs = hwEnumLen = 0;
			switch (value) {
			case 0:
				hwEnumBuf[hwEnumLen++] = 1; // version
				if (Feature.BW_GRAPHICS.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = (('m' << 24) | ('V' << 16) | ('i' << 8) | 'd');
					if (Feature.DYNAMIC_RESOLUTION.isAllowed()) {
						hwEnumBuf[hwEnumLen++] = (('m' << 24) | ('D' << 16) | ('y' << 8) | 'n');
					}
				}
				hwEnumBuf[hwEnumLen++] = (('T' << 24) | ('i' << 16) | ('m' << 8) | 'r');
				hwEnumBuf[hwEnumLen++] = (('L' << 24) | ('E' << 16) | ('D' << 8) | 's');
				if (socketsOut[0] != null) {
					hwEnumBuf[hwEnumLen++] = (('S' << 24) | ('P' << 16) | ('r' << 8) | 't');
				}
				if (Feature.SPI.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = (('S' << 24) | ('P' << 16) | ('I' << 8) | 'f');
				}
				hwEnumBuf[hwEnumLen++] = (('M' << 24) | ('s' << 16) | ('K' << 8) | 'b');
				if (Feature.PARAVIRTUAL_CLIPBOARD.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = (('v' << 24) | ('C' << 16) | ('l' << 8) | 'p');
				}
				hwEnumBuf[hwEnumLen++] = (('D' << 24) | ('b' << 16) | ('g' << 8) | 'C');
				hwEnumBuf[hwEnumLen++] = (('v' << 24) | ('R' << 16) | ('T' << 8) | 'C');
				if (hostfs != null) {
					hwEnumBuf[hwEnumLen++] = (('H' << 24) | ('s' << 16) | ('F' << 8) | 's');
				}
				if (Feature.PARAVIRTUAL_WIZNET.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = (('v' << 24) | ('N' << 16) | ('e' << 8) | 't');
				}
				if (Feature.PARAVIRTUAL_DISK.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = (('v' << 24) | ('D' << 16) | ('s' << 8) | 'k');
				}
				if (Feature.COLOR16_GRAPHICS.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = (('1' << 24) | ('6' << 16) | ('c' << 8) | 'V');
					if (Feature.DYNAMIC_RESOLUTION.isAllowed()) {
						hwEnumBuf[hwEnumLen++] = (('1' << 24) | ('6' << 16) | ('c' << 8) | 'D');
					}
				}
				if (Feature.COLOR_GRAPHICS.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = (('8' << 24) | ('b' << 16) | ('c' << 8) | 'V');
					hwEnumBuf[hwEnumLen++] = (('t' << 24) | ('r' << 16) | ('c' << 8) | 'V');
					if (Feature.DYNAMIC_RESOLUTION.isAllowed()) {
						hwEnumBuf[hwEnumLen++] = (('8' << 24) | ('b' << 16) | ('c' << 8) | 'D');
						hwEnumBuf[hwEnumLen++] = (('t' << 24) | ('r' << 16) | ('c' << 8) | 'D');
					}
				}
				hwEnumBuf[hwEnumLen++] = (('R' << 24) | ('s' << 16) | ('e' << 8) | 't');
				if (mem.getCodeRAM() != mem.getRAM() || jitCPU != null) {
					hwEnumBuf[hwEnumLen++] = (('I' << 24) | ('C' << 16) | ('I' << 8) | 'v');
				}
				if (Feature.PARAVIRTUAL_HOST_TRANSFER.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = (('v' << 24) | ('H' << 16) | ('T' << 8) | 'x');
				}
				break;
			case ('m' << 24) | ('V' << 16) | ('i' << 8) | 'd':
				if (Feature.BW_GRAPHICS.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = 3; // number of modes
					hwEnumBuf[hwEnumLen++] = -16; // mode switching address
					hwEnumBuf[hwEnumLen++] = origWidth; // screen width
					hwEnumBuf[hwEnumLen++] = origHeight; // screen height
					hwEnumBuf[hwEnumLen++] = Math.max(mem.getImageMemory().getMinimumSpan(), origWidth / 8); // scanline span
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
					hwEnumBuf[hwEnumLen++] = 1024; // screen width
					hwEnumBuf[hwEnumLen++] = 768; // screen height
					hwEnumBuf[hwEnumLen++] = Math.max(mem.getImageMemory().getMinimumSpan(), 128); // scanline span
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
					hwEnumBuf[hwEnumLen++] = 800; // screen width
					hwEnumBuf[hwEnumLen++] = 600; // screen height
					hwEnumBuf[hwEnumLen++] = Math.max(mem.getImageMemory().getMinimumSpan(), 100); // scanline span
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
				}
				break;
			case ('1' << 24) | ('6' << 16) | ('c' << 8) | 'V':
				if (Feature.COLOR16_GRAPHICS.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = 3; // number of modes
					hwEnumBuf[hwEnumLen++] = 3; // first mode
					hwEnumBuf[hwEnumLen++] = -16; // mode switching address
					hwEnumBuf[hwEnumLen++] = mem.getPaletteStart(); // palette start
					hwEnumBuf[hwEnumLen++] = origWidth; // screen width
					hwEnumBuf[hwEnumLen++] = origHeight; // screen height
					hwEnumBuf[hwEnumLen++] = Math.max(mem.getImageMemory().getMinimumSpan(), origWidth / 8) * 4; // scanline span
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
					hwEnumBuf[hwEnumLen++] = 1024; // screen width
					hwEnumBuf[hwEnumLen++] = 768; // screen height
					hwEnumBuf[hwEnumLen++] = Math.max(mem.getImageMemory().getMinimumSpan(), 128) * 4; // scanline span
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
					hwEnumBuf[hwEnumLen++] = 800; // screen width
					hwEnumBuf[hwEnumLen++] = 600; // screen height
					hwEnumBuf[hwEnumLen++] = Math.max(mem.getImageMemory().getMinimumSpan(), 100) * 4; // scanline span
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
				}
				break;
			case ('8' << 24) | ('b' << 16) | ('c' << 8) | 'V':
				if (Feature.COLOR_GRAPHICS.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = 3; // number of modes
					hwEnumBuf[hwEnumLen++] = 6; // first mode
					hwEnumBuf[hwEnumLen++] = -16; // mode switching address
					hwEnumBuf[hwEnumLen++] = mem.getPaletteStart(); // palette start
					hwEnumBuf[hwEnumLen++] = origWidth; // screen width
					hwEnumBuf[hwEnumLen++] = origHeight; // screen height
					hwEnumBuf[hwEnumLen++] = Math.max(mem.getImageMemory().getMinimumSpan(), origWidth / 8) * 8; // scanline span
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
					hwEnumBuf[hwEnumLen++] = 1024; // screen width
					hwEnumBuf[hwEnumLen++] = 768; // screen height
					hwEnumBuf[hwEnumLen++] = Math.max(mem.getImageMemory().getMinimumSpan(), 128) * 8; // scanline span
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
					hwEnumBuf[hwEnumLen++] = 800; // screen width
					hwEnumBuf[hwEnumLen++] = 600; // screen height
					hwEnumBuf[hwEnumLen++] = Math.max(mem.getImageMemory().getMinimumSpan(), 100) * 8; // scanline span
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
				}
				break;
			case ('t' << 24) | ('r' << 16) | ('c' << 8) | 'V':
				if (Feature.COLOR_GRAPHICS.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = 3; // number of modes
					hwEnumBuf[hwEnumLen++] = 9; // first mode
					hwEnumBuf[hwEnumLen++] = -16; // mode switching address
					hwEnumBuf[hwEnumLen++] = origWidth; // screen width
					hwEnumBuf[hwEnumLen++] = origHeight; // screen height
					hwEnumBuf[hwEnumLen++] = Math.max(mem.getImageMemory().getMinimumSpan(), origWidth / 8) * 8 * 4; // scanline span
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
					hwEnumBuf[hwEnumLen++] = 1024; // screen width
					hwEnumBuf[hwEnumLen++] = 768; // screen height
					hwEnumBuf[hwEnumLen++] = Math.max(mem.getImageMemory().getMinimumSpan(), 128) * 8 * 4; // scanline span
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
					hwEnumBuf[hwEnumLen++] = 800; // screen width
					hwEnumBuf[hwEnumLen++] = 600; // screen height
					hwEnumBuf[hwEnumLen++] = Math.max(mem.getImageMemory().getMinimumSpan(), 100) * 8 * 4; // scanline span
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // base address
				}
				break;
			case ('m' << 24) | ('D' << 16) | ('y' << 8) | 'n':
				if (Feature.BW_GRAPHICS.isAllowed() && Feature.DYNAMIC_RESOLUTION.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = -16; // mode switching address
					if (((mem.getImageMemory().getBaseAddress() * 4) & 0xFFFFFFFFL) > Memory.MemSize) {
						hwEnumBuf[hwEnumLen++] = 4096; // maximum width
						hwEnumBuf[hwEnumLen++] = 4096; // maximum height
					} else {
						hwEnumBuf[hwEnumLen++] = 1024; // maximum width
						hwEnumBuf[hwEnumLen++] = 768; // maximum height
					}
					hwEnumBuf[hwEnumLen++] = 32; // Increment in width (valid resolutions need to be a multiple of this value)
					hwEnumBuf[hwEnumLen++] = 1; // Increment in height (likely 1)
					hwEnumBuf[hwEnumLen++] = -1; // Scan line span in bytes, or -1 to determine it dynamically from the vertical resolution
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4;// Base address of framebuffer
					if (Feature.SEAMLESS_RESIZE.isAllowed()) {
						hwEnumBuf[hwEnumLen++] = 1; // 1 for seamless resize
					}
				}
				break;
			case ('1' << 24) | ('6' << 16) | ('c' << 8) | 'D':
				if (Feature.COLOR16_GRAPHICS.isAllowed() && Feature.DYNAMIC_RESOLUTION.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = -16; // mode switching address
					hwEnumBuf[hwEnumLen++] = mem.getPaletteStart(); // palette start
					hwEnumBuf[hwEnumLen++] = 4096; // maximum width
					hwEnumBuf[hwEnumLen++] = 4096; // maximum height
					hwEnumBuf[hwEnumLen++] = 8; // Increment in width (valid resolutions need to be a multiple of this value)
					hwEnumBuf[hwEnumLen++] = 1; // Increment in height (likely 1)
					hwEnumBuf[hwEnumLen++] = -1; // Scan line span in bytes, or -1 to determine it dynamically from the vertical resolution
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4; // Base address of framebuffer
					if (Feature.SEAMLESS_RESIZE.isAllowed()) {
						hwEnumBuf[hwEnumLen++] = 1; // 1 for seamless resize
					}
				}
				break;
			case ('8' << 24) | ('b' << 16) | ('c' << 8) | 'D':
				if (Feature.COLOR_GRAPHICS.isAllowed() && Feature.DYNAMIC_RESOLUTION.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = -16; // mode switching address
					hwEnumBuf[hwEnumLen++] = mem.getPaletteStart(); // palette start
					hwEnumBuf[hwEnumLen++] = 4096; // maximum width
					hwEnumBuf[hwEnumLen++] = 4096; // maximum height
					hwEnumBuf[hwEnumLen++] = 4;// Increment in width (valid resolutions need to be a multiple of this value)
					hwEnumBuf[hwEnumLen++] = 1;// Increment in height (likely 1)
					hwEnumBuf[hwEnumLen++] = -1;// Scan line span in bytes, or -1 to determine it dynamically from the vertical resolution
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4;// Base address of framebuffer
					if (Feature.SEAMLESS_RESIZE.isAllowed()) {
						hwEnumBuf[hwEnumLen++] = 1; // 1 for seamless resize
					}
				}
				break;
			case ('t' << 24) | ('r' << 16) | ('c' << 8) | 'D':
				if (Feature.COLOR_GRAPHICS.isAllowed() && Feature.DYNAMIC_RESOLUTION.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = -16; // mode switching address
					hwEnumBuf[hwEnumLen++] = 4096; // maximum width
					hwEnumBuf[hwEnumLen++] = 4096; // maximum height
					hwEnumBuf[hwEnumLen++] = 1;// Increment in width (valid resolutions need to be a multiple of this value)
					hwEnumBuf[hwEnumLen++] = 1;// Increment in height (likely 1)
					hwEnumBuf[hwEnumLen++] = -1;// Scan line span in bytes, or -1 to determine it dynamically from the vertical resolution
					hwEnumBuf[hwEnumLen++] = mem.getImageMemory().getBaseAddress() * 4;// Base address of framebuffer
					if (Feature.SEAMLESS_RESIZE.isAllowed()) {
						hwEnumBuf[hwEnumLen++] = 1; // 1 for seamless resize
					}
				}
				break;
			case ('T' << 24) | ('i' << 16) | ('m' << 8) | 'r':
				hwEnumBuf[hwEnumLen++] = -64; // MMIO address
				if (Feature.POWER_MANAGEMENT.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = 1; // Power management supported
				}
				break;
			case ('L' << 24) | ('E' << 16) | ('D' << 8) | 's':
				hwEnumBuf[hwEnumLen++] = 8; // number of LEDs
				hwEnumBuf[hwEnumLen++] = -60; // MMIO address
				break;
			case ('S' << 24) | ('P' << 16) | ('r' << 8) | 't':
				if (socketsOut[0] != null) {
					hwEnumBuf[hwEnumLen++] = socketsOut[1] != null ? 2 : 1; // number of serial ports
					hwEnumBuf[hwEnumLen++] = -52; // MMIO status address
					hwEnumBuf[hwEnumLen++] = -56; // MMIO data address
				}
				break;
			case ('S' << 24) | ('P' << 16) | ('I' << 8) | 'f':
				if (Feature.SPI.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = -44; // MMIO control address
					hwEnumBuf[hwEnumLen++] = -48; // MMIO status address
					if (Feature.NATIVE_DISK.isAllowed() && sdCard != null) {
						hwEnumBuf[hwEnumLen++] = (('S' << 24) | ('D' << 16) | ('C' << 8) | 'r'); // SD card
					}
					if (Feature.SPI_NETWORK.isAllowed() && net != null) {
						hwEnumBuf[hwEnumLen++] = (('w' << 24) | ('N' << 16) | ('e' << 8) | 't'); // wireless network
					}
				}
				break;
			case ('M' << 24) | ('s' << 16) | ('K' << 8) | 'b':
				hwEnumBuf[hwEnumLen++] = -40; // MMIO mouse address + keyboard status
				hwEnumBuf[hwEnumLen++] = -36; // MMIO keyboard address
				hwEnumBuf[hwEnumLen++] = keyboardMode; // keyboard mode
				break;
			case ('v' << 24) | ('C' << 16) | ('l' << 8) | 'p':
				if (Feature.PARAVIRTUAL_CLIPBOARD.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = -24; // MMIO clipboard control address
					hwEnumBuf[hwEnumLen++] = -20; // MMIO clipboard data address
				}
				break;
			case ('H' << 24) | ('s' << 16) | ('F' << 8) | 's':
				if (hostfs != null)
					hwEnumBuf[hwEnumLen++] = -32; // MMIO address
				break;
			case ('v' << 24) | ('N' << 16) | ('e' << 8) | 't':
				if (Feature.PARAVIRTUAL_WIZNET.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = -32; // MMIO address
				}
				break;
			case ('v' << 24) | ('H' << 16) | ('T' << 8) | 'x':
				if (Feature.PARAVIRTUAL_HOST_TRANSFER.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = -32; // MMIO address
				}
				break;
			case ('D' << 24) | ('b' << 16) | ('g' << 8) | 'C':
				hwEnumBuf[hwEnumLen++] = -12; // MMIO debug console address
				break;
			case ('v' << 24) | ('R' << 16) | ('T' << 8) | 'C':
				if (timeInit == null) timeInit = getTimeInit();
				hwEnumBuf[hwEnumLen++] = timeInit[0];
				hwEnumBuf[hwEnumLen++] = timeInit[1];
				break;
			case ('v' << 24) | ('D' << 16) | ('s' << 8) | 'k':
				if (Feature.PARAVIRTUAL_DISK.isAllowed()) {
					hwEnumBuf[hwEnumLen++] = -28; // MMIO address
				}
				break;
			case ('B' << 24) | ('o' << 16) | ('o' << 8) | 't':
				hwEnumBuf[hwEnumLen++] = mem.getRAM().length * 4 - 0x10;
				break;
			case ('R' << 24) | ('s' << 16) | ('e' << 8) | 't':
				hwEnumBuf[hwEnumLen++] = mem.getROMStart();
				hwEnumBuf[hwEnumLen++] = 0;
				hwEnumBuf[hwEnumLen++] = mem.getROMStart() - 4;
				break;
			case ('I' << 24) | ('C' << 16) | ('I' << 8) | 'v':
				if (mem.getCodeRAM() != mem.getRAM() || jitCPU != null) {
					hwEnumBuf[hwEnumLen++] = -40;
				}
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
		for (int i = 0; i < scancodeCount; i++) {
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
		hostTransfer.reset();
		timeInit = null;
		mem.getImageMemory().setDepth(1);
		mem.getImageMemory().resize(origWidth, origHeight, Math.max(mem.getImageMemory().getMinimumSpan(), origWidth / 8));
		currentDisplayMode = 0;

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

	private int[] getTimeInit() {
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
