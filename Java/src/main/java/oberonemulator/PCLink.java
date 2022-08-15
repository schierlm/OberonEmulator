package oberonemulator;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class PCLink extends JFrame {

	public static final byte ACK = 0x10, NAK = 0x11, REC = 0x21, SND = 0x22, LST = 0x23;

	private JTextField nameField;
	private JButton hide;
	private final List<String[]> receiveJobs = new ArrayList<String[]>();
	private final List<String[]> sendJobs = new ArrayList<String[]>();
	private final Object lock = new Object();

	public static void start(String host, int port, EmulatorFrame emuFrame) throws Exception {
		Socket s = new Socket(host, port);
		PCLink link = new PCLink(emuFrame);
		InputStream in = s.getInputStream();
		OutputStream out = new BufferedOutputStream(s.getOutputStream());
		while (true) {
			String[] job = null, sjob = null;
			synchronized (link.lock) {
				while (link.receiveJobs.size() == 0 && link.sendJobs.size() == 0) {
					link.lock.wait();
				}
				if (link.receiveJobs.size() > 0) {
					job = link.receiveJobs.remove(0);
				}
				if (link.sendJobs.size() > 0) {
					sjob = link.sendJobs.remove(0);
					if (sjob == null) {
						s.close();
						return;
					}
				}
			}
			if (job != null) {
				FileInputStream fIn = new FileInputStream(job[0]);
				String filename = job[1] + "\0";
				int flen = (int) new File(job[0]).length();
				System.out.printf("PCLink REC Filename: %s size %d\n", job[1], flen);
				out.write(REC);
				out.write(filename.getBytes("ISO-8859-1"));
				out.flush();
				waitAck(in);
				int partLen;
				do {
					partLen = Math.min(flen, 255);
					out.write(partLen);
					for (int i = 0; i < partLen; i++) {
						out.write(fIn.read());
					}
					out.flush();
					waitAck(in);
					flen -= partLen;
				} while (partLen == 255);
				waitAck(in);
				fIn.close();
			}
			if (sjob != null) {
				String filename = sjob[1] + "\0";
				if (filename.contains("*")) {
					Feature.WILDCARD_PCLINK.use();
					File f = new File(sjob[0]).getParentFile();
					System.out.printf("PCLink LST Pattern: %s\n", sjob[1]);
					out.write(LST);
					out.write(filename.getBytes("ISO-8859-1"));
					out.flush();
					waitAck(in);
					int b;
					while ((b = in.read()) > 0) {
						StringBuilder sb = new StringBuilder();
						do {
							sb.append((char) b);
						} while ((b = in.read()) > 0);
						link.sendJobs.add(new String[] { new File(f, sb.toString()).getAbsolutePath(), sb.toString() });
					}
					waitAck(in);
					continue;
				}
				System.out.printf("PCLink SND Filename: %s\n", sjob[1]);
				out.write(SND);
				out.write(filename.getBytes("ISO-8859-1"));
				out.flush();
				int b = in.read();
				if (b == ACK) {
					FileOutputStream fOut = new FileOutputStream(sjob[0]);
					int len = 255;
					while (len == 255) {
						len = in.read();
						for (int i = 0; i < len; i++) {
							fOut.write(in.read());
						}
						out.write(ACK);
						out.flush();
					}
					fOut.close();
				} else if (b == NAK) {
					System.out.println("File not found.");
				} else {
					s.close();
					throw new IOException("Unexpected byte received: " + b);
				}
			}
		}
	}

	public static void startCommandLine(String host, int port, CPU cpu, Keyboard keyboard, MemoryMappedIO mmio) throws Exception {
		Socket s = new Socket(host, port);
		DataInputStream in = new DataInputStream(s.getInputStream());
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line;
		File baseDirectory = new File(".").getCanonicalFile();
		int autosleep = 0;
		while ((line = br.readLine()) != null) {
			if (autosleep > 0) {
				Thread.sleep(autosleep);
			}
			if (line.startsWith("!")) {
				if (line.equalsIgnoreCase("!reset")) {
					cpu.reset(true);
				} else if (line.equalsIgnoreCase("!exit")) {
					System.exit(0);
				} else if (line.toLowerCase().startsWith("!mouse ")) {
					String args = line.substring(7);
					if (args.matches("[0-9]+,[0-9]+")) {
						String[] coords = args.split(",");
						mmio.mouseMoved(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]));
					} else if (args.matches("[LlMmRr]+")) {
						for (int i = 0; i < args.length(); i++) {
							int idx = "lmrLMR".indexOf(args.charAt(i));
							if (idx != -1)
								mmio.mouseButton(idx % 3 + 1, idx >= 3);
						}
					} else {
						System.out.println("Unsupported extended command: " + line);
					}
				} else if (line.toLowerCase().startsWith("!type ")) {
					for (int i = 6; i < line.length(); i++) {
						keyboard.type(line.charAt(i));
					}
				} else if (line.toLowerCase().startsWith("!typechar ")) {
					keyboard.type((char) Integer.parseInt(line.substring(10)));
				} else if (line.toLowerCase().startsWith("!key +")) {
					keyboard.press(KeyEvent.KEY_LOCATION_STANDARD, Integer.parseInt(line.substring(6)));
				} else if (line.toLowerCase().startsWith("!key -")) {
					keyboard.release(KeyEvent.KEY_LOCATION_STANDARD, Integer.parseInt(line.substring(6)));
				} else if (line.toLowerCase().startsWith("!sleep ")) {
					Thread.sleep(Integer.parseInt(line.substring(7)));
				} else if (line.toLowerCase().startsWith("!autosleep ")) {
					autosleep = Integer.parseInt(line.substring(11));
				} else if (line.toLowerCase().startsWith("!cd ")) {
					baseDirectory = new File(baseDirectory, line.substring(4)).getCanonicalFile();
					System.out.println("Now at " + baseDirectory);
				} else if (line.startsWith("!+")) {
					File f = new File(baseDirectory, line.substring(2));
					try (FileInputStream fIn = new FileInputStream(f)) {
						String filename = line.substring(2) + "\0";
						int flen = (int) f.length();
						out.write(REC);
						out.write(filename.getBytes("ISO-8859-1"));
						out.flush();
						waitAck(in);
						int partLen;
						do {
							partLen = Math.min(flen, 255);
							out.write(partLen);
							for (int i = 0; i < partLen; i++) {
								out.write(fIn.read());
							}
							out.flush();
							waitAck(in);
							flen -= partLen;
						} while (partLen == 255);
						waitAck(in);
					}
				} else {
					System.out.println("Unsupported extended command: " + line);
				}
				continue;
			}
			out.write(line.getBytes("ISO-8859-1"));
			out.writeByte(0);
			out.flush();
			if (line.startsWith("+")) {
				File f = new File(baseDirectory, line.substring(1));
				out.writeInt((int) f.length());
				try (FileInputStream fis = new FileInputStream(f)) {
					byte[] buf = new byte[4096];
					int len;
					while ((len = fis.read(buf)) != -1) {
						out.write(buf, 0, len);
					}
				}
				out.write(0);
				out.flush();
			} else if (line.startsWith("-")) {
				try (FileOutputStream fos = new FileOutputStream(new File(baseDirectory, line.substring(1)))) {
					byte[] data = new byte[in.readInt()];
					in.readFully(data);
					fos.write(data);
				}
			}
			int b;
			while ((b = in.read()) > 0) {
				if (b == 1) {
					System.exit(1);
				} else if (b == '\r') {
					System.out.print('\n');
				} else {
					System.out.print((char) b);
				}
			}
		}
		s.close();
	}

	private static void waitAck(InputStream in) throws IOException {
		int b = in.read();
		if (b != ACK)
			throw new IOException("Unexpected byte received: " + b);
	}

	public PCLink(final EmulatorFrame emuFrame) {
		super("PCLink");
		// ugly layout, I know.
		setLayout(new BorderLayout());
		JPanel jp = new JPanel(new GridLayout(1, 2));
		add(jp, BorderLayout.NORTH);
		jp.add(new JLabel("File name to copy out:"));
		jp.add(nameField = new JTextField("DiskImage.Bin"));
		String helpText = "<html>Drop files here to copy in.<br>Drop a folder here to copy out.";
		if (emuFrame != null)
			helpText = helpText.replace("here", "here (or on the emulator window)");
		add(new JLabel(helpText, JLabel.CENTER), BorderLayout.CENTER);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				synchronized (PCLink.this.lock) {
					sendJobs.add(null);
					PCLink.this.lock.notifyAll();
				}
				if (emuFrame != null) {
					emuFrame.setDropTarget(null);
					hide.setEnabled(false);
				}
				dispose();
			}
		});
		DropTargetListener dtl = new DropTargetListener() {

			protected final DataFlavor df = DataFlavor.javaFileListFlavor;
			protected final int action = DnDConstants.ACTION_COPY;

			public void dragEnter(DropTargetDragEvent evt) {
				myDrag(evt);
			}

			public void dragOver(DropTargetDragEvent evt) {
				myDrag(evt);
			}

			public void dropActionChanged(DropTargetDragEvent evt) {
				myDrag(evt);
			}

			protected void myDrag(DropTargetDragEvent evt) {
				if (!evt.isDataFlavorSupported(df)) {
					evt.rejectDrag();
				} else if (evt.getDropAction() != action) {
					evt.acceptDrag(action);
				}
			}

			public void drop(DropTargetDropEvent evt) {
				if (!evt.isDataFlavorSupported(df)) {
					evt.rejectDrop();
				} else {
					evt.acceptDrop(action);
					try {
						@SuppressWarnings("unchecked")
						java.util.List<File> l = (java.util.List<File>) evt.getTransferable()
								.getTransferData(DataFlavor.javaFileListFlavor);
						File[] fls = (File[]) l.toArray(new File[l.size()]);
						evt.dropComplete(drop(fls));
					} catch (UnsupportedFlavorException e) {
						e.printStackTrace();
						evt.dropComplete(false);
					} catch (IOException e) {
						e.printStackTrace();
						evt.dropComplete(false);
					}
				}
			}

			public void dragExit(DropTargetEvent dte) {
			}

			public boolean drop(File[] fs) {
				PCLink link = PCLink.this;
				synchronized (link.lock) {
					for (File f : fs) {
						if (f.isDirectory()) {
							if (nameField.getText().isEmpty()) {
								setVisible(true);
							} else {
								link.sendJobs.add(new String[] { new File(f, nameField.getText()).getAbsolutePath(), nameField.getText() });
							}
						} else {
							String fileName = f.getName();

							if (fileName.endsWith(".txt")) {
								link.receiveJobs.add(new String[] { createReformattedFile(f).getAbsolutePath(), fileName.substring(0, fileName.length() - 4) });
							} else {
								link.receiveJobs.add(new String[] { f.getAbsolutePath(), fileName });
							}
						}
					}
					link.lock.notifyAll();
				}
				return true;
			}

		};
		DropTarget dt = new DropTarget(this, DnDConstants.ACTION_COPY,
				dtl, true, null);
		setDropTarget(dt);
		if (emuFrame != null) {
			emuFrame.setDropTarget(new DropTarget(emuFrame, DnDConstants.ACTION_COPY, dtl, true, null));
			hide = new JButton("Hide");
			hide.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					nameField.setText("");
					setVisible(false);
				}
			});
			add(hide, BorderLayout.SOUTH);
			emuFrame.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					if (hide.isEnabled()) {
						setVisible(true);
						hide.setEnabled(false);
					}
				}
			});
		}
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
	}

	// normalize line endings and encoding
	private File createReformattedFile(File f) {
		try {
			File tempFile = File.createTempFile("~pclink", null);
			tempFile.deleteOnExit();
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "ISO-8859-1"));
			Writer w = new OutputStreamWriter(new FileOutputStream(tempFile), "ISO-8859-1");
			String line;
			while ((line = br.readLine()) != null) {
				w.write(line + "\r");
			}
			w.close();
			br.close();
			return tempFile;
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
}
