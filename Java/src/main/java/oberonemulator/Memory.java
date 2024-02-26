package oberonemulator;

public class Memory {

	public static final int ROMWords = 512;
	public static final int ROMStart = 0x0FE000;
	public static final int IOStart = 0x0FFFC0;
	public static final long LargeIOStart = 0xFFFFFFC0L;
	public static final int MemSize = 0x100000;
	public static final long LargeMemSize = 0x100000000L;
	public static final int MemWords = (MemSize / 4);
	public static final int LargeMemWords = (int) (LargeMemSize / 4);
	public static final int DisplayStart = 0x0E7F00;

	private int[] ram, coderam;
	private int[] rom = new int[ROMWords];
	private MemoryMappedIO mmio;
	private ImageMemory imgio;
	private int romStart;
	private boolean largeAddressSpace;

	public Memory(ImageMemory imgio, int[] bootloader, MemoryMappedIO mmio, boolean largeAddressSpace, int memSize, int displayStart, int romStart, boolean codecache) {
		this.largeAddressSpace = largeAddressSpace;
		this.romStart = romStart;
		ram = new int[Math.min(memSize >>> 2, displayStart >>> 2)];
		coderam = codecache ? new int[ram.length] : ram;
		this.imgio = imgio;
		this.mmio = mmio;
		mmio.setMem(this);
		System.arraycopy(bootloader, 0, rom, 0, bootloader.length);
	}

	public int getROMStart() {
		return romStart;
	}

	public int getPaletteStart() {
		return largeAddressSpace ? (int) LargeIOStart - 0x400 : IOStart - 0x40;
	}

	public int readWord(int wordAddress, boolean mapROM) {
		if (mapROM && wordAddress >= romStart >>> 2 && wordAddress < (romStart >>> 2) + rom.length) {
			return rom[wordAddress - (romStart >>> 2)];
		} else if (mapROM && wordAddress == (romStart >>> 2) - 1) {
			System.exit(0); // System.Quit
			return 0;
		} else if (wordAddress >= (largeAddressSpace ? (int) (LargeIOStart / 4) : (IOStart / 4))) {
			return mmio.readWord(wordAddress);
		} else if (wordAddress >= getPaletteStart() >>> 2) {
			return imgio.readPalette((wordAddress - (getPaletteStart() >>> 2)) % 0x100);
		} else if (wordAddress >= imgio.getBaseAddress()) {
			return imgio.readWord(wordAddress);
		} else if (wordAddress < ram.length) {
			return mapROM ? coderam[wordAddress] : ram[wordAddress];
		} else {
			return 0;
			// throw new IllegalStateException("Access outside of mapped memory: " + wordAddress);
		}
	}

	public void writeWord(int wordAddress, int value) {
		if (wordAddress >= (largeAddressSpace ? (int) (LargeIOStart / 4) : (IOStart / 4))) {
			mmio.writeWord(wordAddress, value);
		} else if (wordAddress >= getPaletteStart() >>> 2) {
			imgio.writePalette((wordAddress - (getPaletteStart() >>> 2)) % 0x100, value);
		} else if (wordAddress >= imgio.getBaseAddress()) {
			imgio.writeWord(wordAddress, value);
		} else if (wordAddress < ram.length) {
			ram[wordAddress] = value;
		} else {
			throw new IllegalStateException("Access outside of mapped memory: " + wordAddress);
		}
	}

	public void dispose() {
		mmio.dispose();
	}

	public void reset() {
		imgio.reset();
		mmio.reset();
		setIRQEnabled(false);
	}

	public int[] getRAM() {
		return ram;
	}

	public int[] getCodeRAM() {
		return coderam;
	}

	public ImageMemory getImageMemory() {
		return imgio;
	}

	public void triggerRepaint() {
		imgio.triggerRepaint();
	}

	public void setIRQEnabled(boolean enabled) {
		mmio.setIRQEnabled(enabled);
	}

	public void setJITCpu(JitCPU cpu) {
		mmio.setJitCPU(cpu);
	}

	public void unblock() {
		mmio.mouseButton(1, false);
	}
}
