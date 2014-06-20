package oberonemulator;

import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.Arrays;

public class ImageMemory {

	private static final int RISC_ORIG_SCREEN_WIDTH = 1024;

	private static int BLACK = 0x657b83, WHITE = 0xfdf6e3;

	private final BufferedImage img;
	private ImageObserver observer = null;
	private int baseAddress;
	private int slidingWindowBase = -1;
	private int[] palette = null;
	private byte[][][] reversePalette = null;

	public ImageMemory(BufferedImage img, int baseAddress) {
		this.img = img;
		this.baseAddress = baseAddress;
	}

	public void setObserver(ImageObserver observer) {
		this.observer = observer;
	}

	public int getWidth() {
		return img.getWidth();
	}

	public int getHeight() {
		return img.getHeight();
	}

	public void setSlidingWindowBase(int value) {
		initPalette();
		slidingWindowBase = value;
	}

	private synchronized void initPalette() {
		if (palette != null)
			return;
		palette = new int[256];
		fill8Colors(0, 0x657b83, 0xfdf6e3, 0x657b83, 0xfdf6e3);
		fill8Colors(8, 0, 0xFFFFFF, 0, 0xFFFFFF);
		fill8Colors(16, 0x010101, 0x7F7F7F, 0x808080, 0xFEFEFE);
		fill8Colors(24, 0x808080, 0xFEFEFE, 0x010101, 0x7F7F7F);
		int idx = 32;
		// add some grays
		for (int i = 0; i < 8; i++) {
			int g = i * 15 + 15;
			palette[idx++] = g * 0x01010;
			palette[idx++] = (0xFF - g) * 0x010101;
		}
		// don't forget the remaining "web-safe" colors
		int[] vals = new int[] { 0x00, 0x33, 0x66, 0x99, 0xcc, 0xff };
		for (int r = 0; r < 3; r++) {
			for (int g = 0; g < 6; g++) {
				for (int b = 0; b < 6; b++) {
					if (r == 0 && (g % 5 == 0) && (b % 5 == 0))
						continue;
					int col = vals[r] * 0x10000 + vals[g] * 0x100 + vals[b];
					palette[idx++] = col;
					palette[idx++] = 0xFFFFFF - col;
				}
			}
		}
		// create reverse palette lookup array (only store the parts that are
		// not empty)
		reversePalette = new byte[256][][];
		for (int i = 0; i < 256; i++) {
			int r = (palette[i] >> 16) & 0xFF, g = (palette[i] >> 8) & 0xff, b = palette[i] & 0xFF;
			if (reversePalette[r] == null)
				reversePalette[r] = new byte[256][];
			if (reversePalette[r][g] == null) {
				reversePalette[r][g] = new byte[256];
				Arrays.fill(reversePalette[r][g], (byte) -1);
			}
			if (reversePalette[r][g][b] != -1)
				throw new IllegalStateException("Ambiguous reverse palette mapping");
			reversePalette[r][g][b] = (byte) i;
		}
	}

	private void fill8Colors(int start, int black, int white, int iwhite, int iblack) {
		int mask = 0xFF000000;
		for (int i = 0; i < 4; i++) {
			palette[start + i * 2] = white & mask | black & ~mask;
			palette[start + i * 2 + 1] = iwhite & mask | iblack & ~mask;
			mask >>>= 8;
		}
	}

	public int readWord(int wordAddress) {
		int offs = wordAddress - baseAddress;
		if (slidingWindowBase != -1) {
			Feature.COLOR_GRAPHICS.use();
			int x = (offs + slidingWindowBase) % (4096 / 4) * 4;
			int y = img.getHeight() - 1 - (offs + slidingWindowBase) / (4096 / 4);
			if (y < 0 || x >= img.getWidth()) return 0;
			int val = 0;
			for (int i = 0; i < 4; i++) {
				int col = img.getRGB(x + i, y) & 0xFFFFFF;
				int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xff;
				int idx = reversePalette[r] != null && reversePalette[r][g] != null
						? reversePalette[r][g][col & 0xFF] & 0xFF : 255;
				val |= (idx << (i * 8));
			}
			return val;
		}
		Feature.BW_GRAPHICS.use();
		int x = (offs % (RISC_ORIG_SCREEN_WIDTH / 32)) * 32;
		int y = img.getHeight() - 1 - offs / (RISC_ORIG_SCREEN_WIDTH / 32);
		if (y < 0 || x >= img.getWidth()) return 0;
		int val = 0;
		for (int i = 0; i < 32; i++) {
			if ((img.getRGB(x + i, y) & 0xFFFFFF) == WHITE) {
				val |= (1 << i);
			}
		}
		return val;
	};

	public void writeWord(int wordAddress, int value) {
		int offs = wordAddress - baseAddress;
		if (slidingWindowBase != -1) {
			Feature.COLOR_GRAPHICS.use();
			int x = (offs + slidingWindowBase) % (4096 / 4) * 4;
			int y = img.getHeight() - 1 - (offs + slidingWindowBase) / (4096 / 4);
			if (y < 0 || x >= img.getWidth()) return;
			for (int i = 0; i < 4; i++) {
				img.setRGB(x + i, y, palette[(value >>> (i * 8)) & 0xFF]);
			}
			triggerRepaint();
			return;
		}
		Feature.BW_GRAPHICS.use();
		int x = (offs % (RISC_ORIG_SCREEN_WIDTH / 32)) * 32;
		int y = img.getHeight() - 1 - offs / (RISC_ORIG_SCREEN_WIDTH / 32);
		if (y < 0 || x >= img.getWidth()) return;
		for (int i = 0; i < 32; i++) {
			img.setRGB(x + i, y, (value & (1 << i)) != 0 ? WHITE : BLACK);
		}
		triggerRepaint();
	}

	public void reset() {
		if (slidingWindowBase == -1) {
			writeWord(baseAddress, 0x53697A65); // magic value SIZE
			writeWord(baseAddress + 1, Math.min(img.getWidth(), 1024));
			writeWord(baseAddress + 2, Math.min(img.getHeight(), 768));
		}
	}

	public void triggerRepaint() {
		if (observer != null)
			observer.imageUpdate(img, ImageObserver.FRAMEBITS, 0, 0, 0, 0);
	}
}