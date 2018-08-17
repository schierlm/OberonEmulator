package oberonemulator;

import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

public class ImageMemory {

	private static int BLACK = 0x657b83, WHITE = 0xfdf6e3;

	private final BufferedImage img;
	private final int span;
	private ImageObserver observer = null;
	private int baseAddress;
	private int slidingWindowBase = -1;
	private int[] palette = null;
	private int[] indexedPixelBuffer = null;

	public ImageMemory(int span, BufferedImage img, int baseAddress) {
		this.span = span;
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

	public int getBaseAddress() {
		return baseAddress;
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
		// create indexed pixel buffer
		indexedPixelBuffer = new int[4096 * img.getWidth() / 4];
	}

	private synchronized void initPalette16() {
		if (palette != null)
			return;
		palette = new int[] {
				0x000000, 0x000080, 0x008000, 0x008080,	0x800000, 0x800080, 0x808000, 0x808080,
				0xc0c0c0, 0x0000ff, 0x00ff00, 0x00ffff,	0xff0000, 0xff00ff, 0xffff00, 0xffffff,
		};
		indexedPixelBuffer = new int[1024 * img.getWidth() / 8];
		if ((baseAddress & 0xFFFFF) == 0xE7F00 / 4) {
			baseAddress = (baseAddress & 0xFFF00000) | 0x9FF00 / 4;
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
			return offs + slidingWindowBase < indexedPixelBuffer.length ? indexedPixelBuffer[offs + slidingWindowBase] : 0;
		} else if (palette != null) {
			Feature.COLOR16_GRAPHICS.use();
			return offs < indexedPixelBuffer.length ? indexedPixelBuffer[offs] : 0;
		}
		Feature.BW_GRAPHICS.use();
		int x = (offs % (Math.abs(span) / 4)) * 32;
		int y = img.getHeight() - 1 - offs / (Math.abs(span) / 4);
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
			indexedPixelBuffer[offs + slidingWindowBase] = value;
			for (int i = 0; i < 4; i++) {
				img.setRGB(x + i, y, palette[(value >>> (i * 8)) & 0xFF]);
			}
			triggerRepaint();
			return;
		} else if (palette != null) {
			Feature.COLOR16_GRAPHICS.use();
			int x = offs % (1024 / 8) * 8;
			int y = img.getHeight() - 1 - offs / (1024 / 8);
			if (y < 0 || x >= img.getWidth()) return;
			indexedPixelBuffer[offs] = value;
			for (int i = 0; i < 8; i++) {
				img.setRGB(x + i, y, palette[(value >>> (i * 4)) & 0xF]);
			}
			triggerRepaint();
			return;
		}
		Feature.BW_GRAPHICS.use();
		int x = (offs % (Math.abs(span) / 4)) * 32;
		int y = img.getHeight() - 1 - offs / (Math.abs(span) / 4);
		if (y < 0 || x >= img.getWidth()) return;
		for (int i = 0; i < 32; i++) {
			img.setRGB(x + i, y, (value & (1 << i)) != 0 ? WHITE : BLACK);
		}
		triggerRepaint();
	}

	public int readPalette(int color) {
		if (palette == null) {
			initPalette16();
		}
		return palette[color];
	};

	public void writePalette(int color, int value) {
		if (palette != null) {
			value &= 0xFFFFFF;
			palette[color] = value;
			if (slidingWindowBase != -1) {
				for (int y = 0; y < img.getHeight(); y++) {
					for (int x = 0; x < img.getWidth(); x++) {
						if (((indexedPixelBuffer[(y * 4096 + x) / 4] >>> (x % 4 * 8)) & 0xFF) == color) {
							img.setRGB(x, img.getHeight() - 1 - y, value);
						}
					}
				}
			} else {
				for (int y = 0; y < img.getHeight(); y++) {
					for (int x = 0; x < img.getWidth(); x++) {
						if (((indexedPixelBuffer[(y * 1024 + x) / 8] >>> (x % 8 * 4)) & 0xF) == color) {
							img.setRGB(x, img.getHeight() - 1 - y, value);
						}
					}
				}
			}
			triggerRepaint();
		}
	}

	public void reset() {
		if (slidingWindowBase == -1) {
			if (palette != null) {
				palette = null;
				if (baseAddress == 0x9FF00 / 4) {
					baseAddress = 0xE7F00 / 4;
				}
			}
			if (span != -128) {
				writeWord(baseAddress, 0x53697A66); // magic value SIZF
				writeWord(baseAddress + 1, img.getWidth());
				writeWord(baseAddress + 2, img.getHeight());				
				return;
			}
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