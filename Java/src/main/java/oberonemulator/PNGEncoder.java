package oberonemulator;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;

public class PNGEncoder {

	public static void encode(String pngFile, String diskImage, InputStream rom, boolean noPadding) throws IOException {
		int size = (int) new File(diskImage).length();
		if (size % 1024 != 0)
			throw new IOException("Disk image size must be a multiple of the sector size (1KB)");
		byte[] range = new byte[256];
		for (int i = 0; i < range.length; i++) {
			range[i] = (byte) i;
		}
		IndexColorModel cm = new IndexColorModel(8, 256, new byte[256], new byte[256], range);
		BufferedImage bufimg = new BufferedImage(1024, size / 1024, BufferedImage.TYPE_BYTE_INDEXED, cm);
		DataInputStream in = new DataInputStream(rom);
		readSector(0, in, bufimg);
		verifyZeroSector(in);
		if (in.read() != -1)
			throw new IOException("ROM image too large (must be 2KB)");
		in.close();
		in = new DataInputStream(new FileInputStream(diskImage));
		if (!noPadding)
			verifyZeroSector(in);
		for (int i = 1; i < size / 1024; i++) {
			readSector(i, in, bufimg);
		}
		if (in.read() != -1)
			throw new IOException("Internal error - disk image size mismatch");
		in.close();
		ImageIO.write(bufimg, "png", new File(pngFile));
	}

	private static void readSector(int secnum, DataInputStream in, BufferedImage bufimg) throws IOException {
		byte[] sector = new byte[1024];
		in.readFully(sector);
		for (int i = 0; i < 1024; i++) {
			bufimg.setRGB(i, secnum, 0xFF000000 + (sector[i] & 0xFF) * 0x010101);
		}
	}

	private static void verifyZeroSector(DataInputStream in) throws IOException {
		byte[] sector = new byte[1024];
		in.readFully(sector);
		if (!Arrays.equals(sector, new byte[1024]))
			throw new IOException("Empty sector expected");
	}

	public static void decode(String pngFile, String diskImage, OutputStream romStream, boolean noPadding) throws IOException {
		BufferedImage bufimg = ImageIO.read(new File(pngFile));
		if (bufimg.getWidth() != 1024 || bufimg.getHeight() < 2)
			throw new IOException("Invalid image size: " + bufimg.getWidth() + "x" + bufimg.getHeight());
		if (romStream != null) {
			writeSector(0, bufimg, romStream);
			romStream.write(new byte[1024]);
			romStream.close();
		}
		OutputStream out = new FileOutputStream(diskImage);
		if (!noPadding)
			out.write(new byte[1024]);
		for (int i = 1; i < bufimg.getHeight(); i++) {
			writeSector(i, bufimg, out);
		}
		out.close();
	}

	private static void writeSector(int secnum, BufferedImage bufimg, OutputStream out) throws IOException {
		byte[] sector = new byte[1024];
		for (int i = 0; i < 1024; i++) {
			int rgb = bufimg.getRGB(i, secnum);
			sector[i] = (byte) rgb;
			if (rgb != (sector[i] & 0xFF) + 0xFF000000)
				throw new IllegalStateException(Integer.toHexString(rgb));
		}
		out.write(sector);
	}
}
