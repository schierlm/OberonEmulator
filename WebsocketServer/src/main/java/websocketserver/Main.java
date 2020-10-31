package websocketserver;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TextArea;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

public class Main {
	public static void main(String[] args) throws Exception {
		boolean headless = false;
		if (args.length > 0 && args[0].equals("--headless")) {
			args = Arrays.copyOfRange(args, 1, args.length);
			headless = true;
		}
		Server server = HeadlessMain.init(args);
		if (!headless && !GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
			ByteArrayOutputStream logBuffer = new ByteArrayOutputStream();
			System.setErr(LogRedirectorStream.wrap(System.err, logBuffer));
			System.setOut(LogRedirectorStream.wrap(System.out, logBuffer));
			showTrayIcon(server, logBuffer);
		}
		HeadlessMain.run(server);
	}

	private static void showTrayIcon(Server server, ByteArrayOutputStream logBuffer) throws AWTException {
		int port = ((ServerConnector) server.getConnectors()[0]).getPort();
		Image image = Toolkit.getDefaultToolkit().getImage(Main.class.getResource("/icon.png"));
		SystemTray tray = SystemTray.getSystemTray();
		TextArea text = new TextArea(25, 100);
		ActionListener listener = (e -> {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				try {
					Desktop.getDesktop().browse(new URI("http://localhost:" + port + "/"));
				} catch (IOException | URISyntaxException ex) {
					ex.printStackTrace();
				}
			}
		});
		PopupMenu popup = new PopupMenu();
		MenuItem defaultItem = new MenuItem("Open in browser");
		defaultItem.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
		defaultItem.addActionListener(listener);
		popup.add(defaultItem);
		MenuItem showLogItem = new MenuItem("Show logs");
		showLogItem.addActionListener(e -> {
			synchronized (logBuffer) {
				text.setText(text.getText() + new String(logBuffer.toByteArray()));
				logBuffer.reset();
			}
			text.setFont(new Font("Monospaced", Font.PLAIN, 12));
			Frame statusWindow = new Frame("Logs - OberonEmulator WebsocketServer");
			statusWindow.add(text, BorderLayout.CENTER);
			statusWindow.pack();
			statusWindow.setLocationRelativeTo(null);
			statusWindow.addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					statusWindow.dispose();
				}
			});
			statusWindow.setVisible(true);
		});
		popup.add(showLogItem);
		popup.addSeparator();
		MenuItem exitItem = new MenuItem("Exit");
		popup.add(exitItem);
		TrayIcon trayIcon = new TrayIcon(image, "Running on port " + port, popup);
		exitItem.addActionListener(e -> {
			try {
				server.stop();
				server.join();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			tray.remove(trayIcon);
		});
		trayIcon.addActionListener(listener);
		tray.add(trayIcon);
	}

	public static final class LogRedirectorStream extends OutputStream {

		private final OutputStream out;
		private final OutputStream orig;

		private LogRedirectorStream(ByteArrayOutputStream logBuffer, PrintStream orig) {
			this.out = logBuffer;
			this.orig = orig;
		}

		private static PrintStream wrap(PrintStream orig, ByteArrayOutputStream logBuffer) {
			return new PrintStream(new LogRedirectorStream(logBuffer, orig), true);
		}

		@Override
		public synchronized void write(int b) throws IOException {
			out.write(b);
			orig.write(b);
		}

		@Override
		public synchronized void write(byte[] b) throws IOException {
			out.write(b);
			orig.write(b);
		}

		@Override
		public synchronized void write(byte[] b, int off, int len) throws IOException {
			out.write(b, off, len);
			orig.write(b, off, len);
		}

		@Override
		public synchronized void flush() throws IOException {
			out.flush();
			orig.flush();
		}

		@Override
		public synchronized void close() throws IOException {
			try {
				out.close();
			} finally {
				orig.close();
			}
		}
	}
}
