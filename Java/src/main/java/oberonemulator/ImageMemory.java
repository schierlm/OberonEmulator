package oberonemulator;

import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

public class ImageMemory {

	private static final int RISC_ORIG_SCREEN_WIDTH = 1024;

	private static int BLACK = 0x657b83, WHITE = 0xfdf6e3;

	private final BufferedImage img;
	private ImageObserver observer = null;
	private int baseAddress;

	public ImageMemory(BufferedImage img, int baseAddress) {
		this.img = img;
		this.baseAddress = baseAddress;
	}

	public void setObserver(ImageObserver observer) {
		this.observer = observer;
	}

	public int readWord(int wordAddress) {
		int offs = wordAddress - baseAddress;
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
		int x = (offs % (RISC_ORIG_SCREEN_WIDTH / 32)) * 32;
		int y = img.getHeight() - 1 - offs / (RISC_ORIG_SCREEN_WIDTH / 32);
		if (y < 0 || x >= img.getWidth()) return;
		for (int i = 0; i < 32; i++) {
			img.setRGB(x + i, y, (value & (1 << i)) != 0 ? WHITE : BLACK);
		}
		triggerRepaint();
	}

	public void reset() {
		writeWord(Memory.DisplayStart/4, 0x53697A65); // magic value SIZE
		writeWord(Memory.DisplayStart/4+1, img.getWidth());
		writeWord(Memory.DisplayStart/4+2, img.getHeight());
	}

	public void triggerRepaint() {
		if (observer != null)
			observer.imageUpdate(img, ImageObserver.FRAMEBITS, 0, 0, 0, 0);
	}
}