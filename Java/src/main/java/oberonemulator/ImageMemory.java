package oberonemulator;

import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

public class ImageMemory {

	private static int BLACK = 0x657b83, WHITE = 0xfdf6e3;

	private static final int[] PALETTE16 = new int[] {
			0xffffff, 0xff0000, 0x00ff00, 0x0000ff, 0xff00ff, 0xffff00, 0x00ffff, 0xaa0000,
			0x009a00, 0x00009a, 0x0acbf3, 0x008282, 0x8a8a8a, 0xbebebe, 0xdfdfdf, 0x000000
	};

	private BufferedImage img;
	private int span, minimumSpan;
	private ImageObserver observer = null;
	private Thread updateThread = null;
	private int baseAddress;
	private int[] palette = null;
	private int[] indexedPixelBuffer = null;
	private ResizeCallback resizeCallback = null;

	public ImageMemory(int minimumSpan, BufferedImage img, int baseAddress) {
		this.minimumSpan = minimumSpan;
		this.span = Math.max(minimumSpan, img.getWidth() / 8);
		this.img = img;
		this.baseAddress = baseAddress;
	}

	public void setObserver(ImageObserver observer) {
		this.observer = observer;
		this.updateThread = new Thread() {
			public synchronized void run() {
				long lastUpdate = 0;
				while (true) {
					try {
						wait();
						while (System.currentTimeMillis() < lastUpdate + 40)
							wait(40);
						ImageMemory.this.observer.imageUpdate(img, ImageObserver.FRAMEBITS, 0, 0, 0, 0);
						lastUpdate = System.currentTimeMillis();
					} catch (InterruptedException ex) {
					}
				}
			};
		};
		updateThread.setDaemon(true);
		updateThread.start();
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

	public void setDepth(int depth) {
		if (depth == 8) {
			if (palette != null && palette.length != 256) palette = null;
			initPalette();
		} else if (depth == 4) {
			if (palette != null && palette.length != 16) palette = null;
			initPalette16();
		} else if (depth == 1) {
			palette = null;
		} else if (depth == 32) {
			palette = new int[0];
		} else {
			throw new IllegalArgumentException();
		}
	}

	private synchronized void initPalette() {
		if (palette != null)
			return;
		palette = new int[256];
		System.arraycopy(PALETTE16, 0, palette, 0, 16);
		// add some grays
		for (int i = 16; i < 40; i++) {
			palette[i] = (i - 15) * 10 * 0x010101;
		}
		int idx = 40;
		// don't forget the remaining "web-safe" colors
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 6; j++) {
				for (int k = 0; k < 6; k++) {
					palette[idx++] = i * 0x330000 + j * 0x3300 + k * 0x33;
				}
			}
		}
		// create indexed pixel buffer
		indexedPixelBuffer = new int[span * getHeight()];
	}

	private synchronized void initPalette16() {
		if (palette != null)
			return;
		palette = PALETTE16;
		indexedPixelBuffer = new int[span * getHeight()];
		if ((baseAddress & 0x3FFFF) == 0xE7F00 / 4) {
			baseAddress = (baseAddress & 0xFFFC0000) | 0x9FF00 / 4;
		}
	}

	public int readWord(int wordAddress) {
		int offs = wordAddress - baseAddress;
		if (palette != null) {
			(palette.length == 16 ? Feature.COLOR16_GRAPHICS : Feature.COLOR_GRAPHICS).use();

			return offs < indexedPixelBuffer.length ? indexedPixelBuffer[offs] : 0;
		}
		Feature.BW_GRAPHICS.use();
		int x = (offs % (span / 4)) * 32;
		int y = img.getHeight() - 1 - offs / (span / 4);
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
		if (palette != null && palette.length == 256) {
			Feature.COLOR_GRAPHICS.use();
			int x = offs % (span / 4) * 4;
			int y = img.getHeight() - 1 - (offs) / (span / 4);
			if (y < 0 || x >= img.getWidth()) return;
			indexedPixelBuffer[offs] = value;
			for (int i = 0; i < 4; i++) {
				img.setRGB(x + i, y, palette[(value >>> (i * 8)) & 0xFF]);
			}
			triggerRepaint();
			return;
		} else if (palette != null && palette.length == 16) {
			Feature.COLOR16_GRAPHICS.use();
			int x = offs % (span / 4) * 8;
			int y = img.getHeight() - 1 - offs / (span / 4);
			if (y < 0 || x >= img.getWidth()) return;
			indexedPixelBuffer[offs] = value;
			for (int i = 0; i < 8; i++) {
				img.setRGB(x + i, y, palette[(value >>> (i * 4)) & 0xF]);
			}
			triggerRepaint();
			return;
		} else if (palette != null && palette.length == 0) {
			Feature.COLOR_GRAPHICS.use();
			int x = offs % (span / 4);
			int y = img.getHeight() - 1 - (offs) / (span / 4);
			if (y < 0 || x >= img.getWidth()) return;
			indexedPixelBuffer[offs] = value;
			img.setRGB(x, y, value & 0xFFFFFF);
			triggerRepaint();
			return;
		}
		Feature.BW_GRAPHICS.use();
		int x = (offs % (span / 4)) * 32;
		int y = img.getHeight() - 1 - offs / (span / 4);
		if (y < 0 || x >= img.getWidth()) return;
		for (int i = 0; i < 32; i++) {
			img.setRGB(x + i, y, (value & (1 << i)) != 0 ? WHITE : BLACK);
		}
		triggerRepaint();
	}

	public int readPalette(int color) {
		if (palette == null || palette.length == 0) {
			return 0;
		}
		return palette[color];
	};

	public void writePalette(int color, int value) {
		if (palette != null && palette.length > 0) {
			value &= 0xFFFFFF;
			palette[color] = value;
			if (palette.length == 256) {
				for (int y = 0; y < img.getHeight(); y++) {
					for (int x = 0; x < img.getWidth(); x++) {
						if (((indexedPixelBuffer[(y * span + x) / 4] >>> (x % 4 * 8)) & 0xFF) == color) {
							img.setRGB(x, img.getHeight() - 1 - y, value);
						}
					}
				}
			} else {
				for (int y = 0; y < img.getHeight(); y++) {
					for (int x = 0; x < img.getWidth(); x++) {
						if (((indexedPixelBuffer[(y * span * 2 + x) / 8] >>> (x % 8 * 4)) & 0xF) == color) {
							img.setRGB(x, img.getHeight() - 1 - y, value);
						}
					}
				}
			}
			triggerRepaint();
		}
	}

	public void reset() {
		if (palette == null || palette.length != 256) {
			if (palette != null) {
				palette = null;
				if ((baseAddress & 0x3FFFF) == 0x9FF00 / 4) {
					baseAddress = (baseAddress & 0xFFFC0000) | 0xE7F00 / 4;
				}
			}
			if (span != 128) {
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

	public int getMinimumSpan() {
		return minimumSpan;
	}

	public void resize(int newWidth, int newHeight, int newSpan) {
		int depth = 1;
		if (palette != null && palette.length == 256)
			depth = 8;
		if (palette != null && palette.length == 16)
			depth = 4;
		if (palette != null && palette.length == 0)
			depth = 32;
		newSpan = newSpan == -1 ? newWidth * depth / 8 : newSpan;
		if (span != newSpan || img.getWidth() != newWidth || img.getHeight() != newHeight) {
			span = newSpan;
			img = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
			if (palette != null) {
				indexedPixelBuffer = new int[span * getHeight() / 4];
			}
			resizeCallback.resized();
		}
	}

	public void setResizeCallback(ResizeCallback resizeCallback) {
		this.resizeCallback = resizeCallback;
	}

	public void triggerRepaint() {
		if (updateThread != null) {
			synchronized (updateThread) {
				updateThread.notifyAll();
			}
		}
	}

	public String validateResolution(int paletteStart) {
		int availableBytes = paletteStart - baseAddress * 4;
		int requiredBytes = span * getHeight();
		if (requiredBytes > availableBytes)
			return "Insufficient image memory: Mode requires " + requiredBytes + " bytes, but only " + availableBytes + " bytes are available.";
		else
			return null;
	}

	protected BufferedImage getImage() {
		return img;
	}

	public int getPreferredWidth() { return resizeCallback.getPreferredWidth(); }
	public int getPreferredHeight() { return resizeCallback.getPreferredHeight(); }

	public static interface ResizeCallback {
		public void resized();

		public int getPreferredWidth();

		public int getPreferredHeight();
	}
}