package oberonemulator;

public class Memory {

	public static final int ROMWords = 512;
	public static final int ROMStart = 0x0FE000;
	public static final int IOStart = 0x0FFFC0;
	public static final int MemSize = 0x100000;
	public static final int MemWords = (MemSize / 4);
	public static final int DisplayStart = 0x0E7F00;

	private int[] ram = new int[DisplayStart / 4];
	private int[] rom = new int[ROMWords];
	private MemoryMappedIO mmio;
	private ImageMemory imgio;

	public Memory(ImageMemory imgio, int[] bootloader, MemoryMappedIO mmio) {
		this.imgio = imgio;
		this.mmio = mmio;
		mmio.setMem(this);
		System.arraycopy(bootloader, 0, rom, 0, bootloader.length);
	}

	public int readWord(int wordAddress, boolean mapROM) {
		if (mapROM && wordAddress >= ROMStart / 4) {
			return rom[wordAddress - ROMStart / 4];
		} else if (wordAddress >= IOStart / 4) {
			return mmio.readWord(wordAddress);
		} else if (wordAddress >= DisplayStart / 4) {
			return imgio.readWord(wordAddress);
		} else {
			return ram[wordAddress];
		}
	}

	public void writeWord(int wordAddress, int value) {
		if (wordAddress >= IOStart / 4) {
			mmio.writeWord(wordAddress, value);
		} else if (wordAddress >= DisplayStart / 4) {
			imgio.writeWord(wordAddress, value);
		} else {
			ram[wordAddress] = value;
		}
	}

	public void dispose() {
		mmio.dispose();
	}

	public void reset() {
		imgio.reset();
	}
	
	public int[] getRAM() {
		return ram;
	}

	public void triggerRepaint() {
		imgio.triggerRepaint();
	}
}
