package oberonemulator;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Disk {

	private static enum DiskState {
		diskCommand,
		diskRead,
		diskWrite,
		diskWriting,
	};

	private DiskState state;
	private RandomAccessFile file;
	private int offset;

	private int[] rx_buf = new int[128];
	private int rx_idx;

	private int[] tx_buf = new int[128 + 2];
	private int tx_cnt;
	private int tx_idx;

	public Disk(String filename) {
		try {
			state = DiskState.diskCommand;
			file = new RandomAccessFile(filename, "rw");

			// Check for filesystem-only image, starting directly at sector 1
			// (DiskAdr 29)
			read_sector(file, tx_buf, 0);
			offset = tx_buf[0] == 0x9B1EA38D ? 0x80002 : 0;
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void free() {
		try {
			file.close();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void write(int value) {
		try {
			tx_idx++;
			switch (state) {
			case diskCommand: {
				if ((byte) value != (byte) 0xFF || rx_idx != 0) {
					rx_buf[rx_idx] = value;
					rx_idx++;
					if (rx_idx == 6) {
						run_command();
						rx_idx = 0;
					}
				}
				break;
			}
			case diskRead: {
				if (tx_idx == tx_cnt) {
					state = DiskState.diskCommand;
					tx_cnt = 0;
					tx_idx = 0;
				}
				break;
			}
			case diskWrite: {
				if (value == 254) {
					state = DiskState.diskWriting;
				}
				break;
			}
			case diskWriting: {
				if (rx_idx < 128) {
					rx_buf[rx_idx] = value;
				}
				rx_idx++;
				if (rx_idx == 128) {
					write_sector(file, rx_buf, 0);
				}
				if (rx_idx == 130) {
					tx_buf[0] = 5;
					tx_cnt = 1;
					tx_idx = -1;
					rx_idx = 0;
					state = DiskState.diskCommand;
				}
				break;
			}
			}
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public int disk_read() {
		int result;
		if (tx_idx >= 0 && tx_idx < tx_cnt) {
			result = tx_buf[tx_idx];
		} else {
			result = 255;
		}
		return result;
	}

	private void run_command() throws IOException {
		int cmd = rx_buf[0];
		int arg = (rx_buf[1] << 24)
				| (rx_buf[2] << 16)
				| (rx_buf[3] << 8)
				| rx_buf[4];

		switch (cmd) {
		case 81: {
			state = DiskState.diskRead;
			tx_buf[0] = 0;
			tx_buf[1] = 254;
			file.seek((arg - offset) * 512);
			read_sector(file, tx_buf, 2);
			tx_cnt = 2 + 128;
			break;
		}
		case 88: {
			state = DiskState.diskWrite;
			file.seek((arg - offset) * 512);
			tx_buf[0] = 0;
			tx_cnt = 1;
			break;
		}
		default: {
			tx_buf[0] = 0;
			tx_cnt = 1;
			break;
		}
		}
		tx_idx = -1;
	}

	public static int[] loadBootloader(String filename) throws IOException {
		int[] bootloader = new int[512];
		RandomAccessFile raf = new RandomAccessFile(filename, "r");
		for (int i = 0; i < 512; i += 128) {
			read_sector(raf, bootloader, i);
		}
		raf.close();
		return bootloader;
	}

	private static void read_sector(RandomAccessFile f, int[] buf, int startOffset) throws IOException {
		byte[] bytes = new byte[512];
		f.readFully(bytes);
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(buf, startOffset, 128);
	}

	private static void write_sector(RandomAccessFile f, int[] buf, int startOffset) throws IOException {
		byte[] bytes = new byte[512];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(buf, startOffset, 128);
		f.write(bytes);
	}

	public void getDoubleSector(int sector, int[] data, int dataOffset) {
		try {
			byte[] bytes = new byte[1024];
			file.seek((sector * 2 - offset) * 512);
			try {
				file.readFully(bytes);
			} catch (EOFException ex) {
				// ignore
			}
			ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(data, dataOffset, 256);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void setDoubleSector(int sector, int[] data, int dataOffset) {
		try {
			byte[] bytes = new byte[1024];
			file.seek((sector * 2 - offset) * 512);
			ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(data, dataOffset, 256);
			file.write(bytes);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
}
