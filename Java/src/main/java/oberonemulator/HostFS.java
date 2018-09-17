package oberonemulator;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class HostFS {

	private static final int HOSTFS_SECTOR_MAGIC = 290000000;

	private final File directory;
	private final List<String> allocatedNames = new ArrayList<>();
	private List<String> remainingSearchResults = null;

	public HostFS(File directory) throws IOException {
		this.directory = directory;
		if (!directory.exists() || !directory.isDirectory())
			throw new IOException("HostFS directory does not exist: " + directory.getPath());
		for (File file : directory.listFiles()) {
			if (file.getName().startsWith("~"))
				file.delete();
		}
	}

	private String getFileName(int[] ram, int offset) {
		byte[] bytes = new byte[32];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(ram, offset, 8);
		String result = new String(bytes, StandardCharsets.ISO_8859_1);
		int pos = result.indexOf('\0');
		if (pos != -1) {
			result = result.substring(0, pos);
		}
		return result;
	}

	private int searchFile(String name) {
		int result = HOSTFS_SECTOR_MAGIC + allocatedNames.indexOf(name);
		if (result == HOSTFS_SECTOR_MAGIC - 1) {
			result = 0;
			if (new File(directory, name).exists()) {
				if (allocatedNames.size() % 29 == 0)
					allocatedNames.add(null);
				result = HOSTFS_SECTOR_MAGIC + allocatedNames.size();
				allocatedNames.add(name);
			}
		}
		return result;
	}

	public void handleCommand(int offset, int[] ram) {
		try {
			int op = ram[offset];
			switch (op) {
			case 0: { // FileDir.Search
				String name = getFileName(ram, offset + 2);
				ram[offset + 1] = searchFile(name);
				break;
			}
			case 1: { // FileDir.Enumerate Start
				String prefix = getFileName(ram, offset + 2);
				remainingSearchResults = new ArrayList<>();
				for (String entry : directory.list()) {
					if (entry.startsWith(prefix) && !entry.startsWith("~"))
						remainingSearchResults.add(entry);
				}
				// FALL THROUGH
			}
			case 2: { // FileDir.Enumerate Next
				if (remainingSearchResults == null)
					throw new IOException("No Enumerate operation pending");
				if (remainingSearchResults.isEmpty()) {
					remainingSearchResults = null;
					ram[offset + 1] = 0;
				} else {
					String file = remainingSearchResults.remove(0);
					byte[] bytes = Arrays.copyOf(file.getBytes(StandardCharsets.ISO_8859_1), 32);
					ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ram, offset + 2, 8);
					int sector = searchFile(file);
					if (sector == 0)
						throw new IOException("Enumerated file not found: " + file);
					ram[offset + 1] = sector;
				}
				break;
			}
			case 3: { // FileDir.GetAttributes
				int sector = ram[offset + 1] - HOSTFS_SECTOR_MAGIC;
				if (sector < 0 || sector >= allocatedNames.size())
					throw new IOException("Invalid sector number: " + sector);
				File f = new File(directory, allocatedNames.get(sector));
				GregorianCalendar cal = new GregorianCalendar();
				cal.setTime(new Date(f.lastModified()));
				ram[offset + 2] = cal.get(Calendar.SECOND) + cal.get(Calendar.MINUTE) * 0x40 + cal.get(Calendar.HOUR_OF_DAY) * 0x1000
						+ cal.get(Calendar.DAY_OF_MONTH) * 0x20000 + (cal.get(Calendar.MONTH) + 1) * 0x400000 + (cal.get(Calendar.YEAR) % 100) * 0x4000000;
				ram[offset + 3] = (int) f.length();
				break;
			}
			case 4: { // FileDir.Insert
				String fileName = getFileName(ram, offset + 2);
				int sector = ram[offset + 1] - HOSTFS_SECTOR_MAGIC;
				if (sector < 0 || sector >= allocatedNames.size())
					throw new IOException("Invalid sector number: " + sector);
				if (!allocatedNames.get(sector).startsWith("~"))
					throw new IOException("Trying to link file " + allocatedNames.get(sector) + " to " + fileName);
				if (new File(directory, fileName).exists()) {
					int pos = allocatedNames.indexOf(fileName);
					if (pos == -1) {
						new File(directory, fileName).delete();
					} else {
						File tmpFile = File.createTempFile("~OvW~" + fileName + "_", ".tmp", directory);
						if (!tmpFile.delete())
							throw new IOException("Deleting temp file failed");
						if (!new File(directory, fileName).renameTo(tmpFile))
							throw new IOException("Renaming file to overwrite failed");
						allocatedNames.set(sector, tmpFile.getName());
					}
				}
				if (!new File(directory, allocatedNames.get(sector)).renameTo(new File(directory, fileName)))
					throw new IOException("Renaming file failed");
				allocatedNames.set(sector, fileName);
				break;
			}
			case 5: { // FileDir.Delete
				String fileName = getFileName(ram, offset + 2);
				int sector = searchFile(fileName);
				ram[offset + 1] = sector;
				if (sector == 0)
					break;
				File tmpFile = File.createTempFile("~Del~" + fileName + "_", ".tmp", directory);
				if (!tmpFile.delete())
					throw new IOException("Deleting temp file failed");
				if (!new File(directory, fileName).renameTo(tmpFile))
					throw new IOException("Renaming file failed");
				allocatedNames.set(sector - HOSTFS_SECTOR_MAGIC, tmpFile.getName());
				break;
			}
			case 6: { // Files.New
				String fileName = getFileName(ram, offset + 2);
				File tmpFile = File.createTempFile("~New~" + fileName + "_", ".tmp", directory);
				ram[offset + 1] = searchFile(tmpFile.getName());
				break;
			}
			case 7: { // Files.ReadBuf
				int sector = ram[offset + 1] - HOSTFS_SECTOR_MAGIC;
				int offs = ram[offset + 2], len = ram[offset + 3], ptr = ram[offset + 4];
				if (sector < 0 || sector >= allocatedNames.size())
					throw new IOException("Invalid sector number: " + sector);
				File f = new File(directory, allocatedNames.get(sector));
				byte[] data = new byte[len + (4 - (len % 4)) % 4];
				try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
					raf.seek(offs);
					raf.readFully(data, 0, len);
				}
				ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ram, ptr / 4, data.length / 4);
				break;
			}
			case 8: { // Files.WriteBuf
				int sector = ram[offset + 1] - HOSTFS_SECTOR_MAGIC;
				int offs = ram[offset + 2], len = ram[offset + 3], ptr = ram[offset + 4];
				if (sector < 0 || sector >= allocatedNames.size())
					throw new IOException("Invalid sector number: " + sector);
				File f = new File(directory, allocatedNames.get(sector));
				byte[] data = new byte[len + (4 - (len % 4)) % 4];
				ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(ram, ptr / 4, data.length / 4);
				try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
					raf.seek(offs);
					raf.write(data, 0, len);
				}
				break;
			}
			default:
				throw new RuntimeException("Unsupported HostFS operation");
			}
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

}
