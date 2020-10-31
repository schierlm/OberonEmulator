package websocketserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

public class TCPServlet extends WebSocketServlet {

	protected static List<Socket> enqueuedSockets = new ArrayList<>();

	@Override
	public void configure(WebSocketServletFactory factory) {
		factory.register(TCPSocket.class);
	}

	public static class TCPSocket extends WebSocketAdapter {

		private Socket socket;
		private OutputStream out;

		@Override
		public void onWebSocketConnect(Session sess) {
			super.onWebSocketConnect(sess);
			try {
				Map<String, List<String>> params = sess.getUpgradeRequest().getParameterMap();
				if (params.containsKey("id")) {
					int id = Integer.parseInt(params.get("id").get(0));
					synchronized (enqueuedSockets) {
						socket = enqueuedSockets.get(id);
						enqueuedSockets.set(id, null);
						while (!enqueuedSockets.isEmpty() && enqueuedSockets.get(enqueuedSockets.size() - 1) == null) {
							enqueuedSockets.remove(enqueuedSockets.size() - 1);
						}
					}
				} else {
					InetAddress host = JSONServlet.addressFromIntValue(Integer.parseInt(params.get("host").get(0)));
					int fport = Integer.parseInt(params.get("fport").get(0));
					int lport = Integer.parseInt(params.get("lport").get(0));
					socket = new Socket(host, fport, null, lport);
				}
				out = socket.getOutputStream();
				new ForwarderThread(socket.getInputStream(), sess).start();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public void onWebSocketClose(int statusCode, String reason) {
			super.onWebSocketClose(statusCode, reason);
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
				socket = null;
				out = null;
			}
		}

		@Override
		public void onWebSocketError(Throwable cause) {
			cause.printStackTrace();
		}

		@Override
		public void onWebSocketBinary(byte[] payload, int offset, int len) {
			if (out != null) {
				try {
					out.write(payload, offset, len);
					out.flush();
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			}
		}
	}

	public static class ForwarderThread extends Thread {
		private InputStream in;
		private Session sess;

		public ForwarderThread(InputStream in, Session sess) {
			this.in = in;
			this.sess = sess;
		}

		@Override
		public void run() {
			try {
				byte[] buf = new byte[4096];
				int len;
				while ((len = in.read(buf)) != -1) {
					sess.getRemote().sendBytes(ByteBuffer.wrap(buf, 0, len));
				}
			} catch (SocketException ex) {
				// ignore
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			sess.close();
		}
	}
}
