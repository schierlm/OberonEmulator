package oberonemulator;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class HostTransfer {

	private InputStream currentIn = null;
	private OutputStream currentOut = null;
	private Process currentProc = null;

	public HostTransfer() {
	}

	public void reset() {
		if (currentProc != null) {
			System.err.println("WARNING: Killed host transfer process");
			currentProc.destroy();
		}
		try {
			if (currentIn != null)
				currentIn.close();
			if (currentOut != null)
				currentOut.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		currentIn = null;
		currentOut = null;
		currentProc = null;
	}

	private String getFileName(int[] ram, int offset, int len) {
		byte[] bytes = new byte[len + (4 - (len % 4)) % 4];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(ram, offset, bytes.length / 4);
		String result = new String(bytes, StandardCharsets.ISO_8859_1).substring(0, len);
		int pos = result.indexOf('\0');
		if (pos != -1) {
			result = result.substring(0, pos);
		}
		return result;
	}

	public void handleCommand(int offset, int[] ram) {
		try {
			boolean setError = false;
			int op = ram[offset], len = ram[offset + 1];
			if (len < 0 || len > 4096)
				throw new IOException("Length out of bounds: " + len);
			switch (op) {
			case 0x20001: { // OpWriteToHost = 20001H;
				if (currentOut != null)
					throw new IOException("Write in progress");
				String name = getFileName(ram, offset + 2, len);
				try {
					currentOut = new FileOutputStream(name);
					ram[offset + 1] = 0;
				} catch (IOException ex) {
					ex.printStackTrace();
					setError = true;
				}
				break;
			}
			case 0x20002: { // OpWriteBuffer = 20002H;
				if (currentOut == null)
					throw new IOException("No Write in progress");
				if (len > 0) {
					byte[] data = new byte[len + (4 - (len % 4)) % 4];
					ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(ram, offset + 2, data.length / 4);
					currentOut.write(data, 0, len);
				} else {
					currentOut.close();
					currentOut = null;
				}
				ram[offset + 1] = 0;
				break;
			}
			case 0x20003: { // OpReadFromHost = 20003H;
				if (currentIn != null)
					throw new IOException("Read in progress");
				String name = getFileName(ram, offset + 2, len);
				try {
					currentIn = new FileInputStream(name);
					ram[offset + 1] = 0;
				} catch (IOException ex) {
					ex.printStackTrace();
					setError = true;
				}
				break;
			}
			case 0x20004: { // OpRunOnHost = 20004H;
				if (currentIn != null)
					throw new IOException("Read in progress");
				String cmd = getFileName(ram, offset + 2, len);
				try {
					String[] cmdarray = new String[] { "/bin/sh", "-c", cmd };
					if (System.getProperty("path.separator").equals(";")) {
						cmdarray[0] = "cmd.exe";
						cmdarray[1] = "/c";
					}
					currentProc = new ProcessBuilder().command(cmdarray).redirectErrorStream(true).redirectInput(Redirect.INHERIT).start();
					currentIn = currentProc.getInputStream();
					ram[offset + 1] = 0;
				} catch (IOException ex) {
					ex.printStackTrace();
					setError = true;
				}
				break;
			}
			case 0x20005: { // OpReadBuffer = 20005H;
				if (currentIn == null)
					throw new IOException("No Read in progress");
				byte[] buf = new byte[len - len % 4];
				len = currentIn.read(buf);
				if (len == -1) {
					len = 0;
					if (currentProc != null) {
						String status;
						try {
							int exitCode = currentProc.waitFor();
							status = "\n\nExit Code: " + exitCode + "\n";
						} catch (InterruptedException ex) {
							status = "\n\nWait interrupted\n";
						}
						len = status.length();
						System.arraycopy(status.getBytes(StandardCharsets.ISO_8859_1), 0, buf, 0, len);
						currentProc = null;
					}
					if (len == 0) {
						currentIn.close();
						currentIn = null;
					}
				}
				ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ram, offset + 2, buf.length / 4);
				ram[offset + 1] = len;
				break;
			}
			default:
				throw new RuntimeException("Unsupported HostTransfer operation");
			}
			if (setError) {
				byte[] buf = "\nI/O Error!\n".getBytes();
				ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ram, offset + 2, buf.length / 4);
				ram[offset + 1] = buf.length;
			}
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
}
