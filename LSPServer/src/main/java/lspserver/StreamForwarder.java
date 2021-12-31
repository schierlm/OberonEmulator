package lspserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamForwarder extends Thread {

	private InputStream in;
	private OutputStream out;

	public StreamForwarder(InputStream in, OutputStream out) {
		this.in = in;
		this.out = out;
	}

	@Override
	public void run() {
		try {
			byte[] buf = new byte[4096];
			int len;
			while ((len = in.read(buf)) != -1) {
				out.write(buf, 0, len);
				out.flush();
			}
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
}
