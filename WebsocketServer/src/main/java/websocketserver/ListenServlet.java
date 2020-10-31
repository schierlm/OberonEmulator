package websocketserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

public class ListenServlet extends WebSocketServlet {

	@Override
	public void configure(WebSocketServletFactory factory) {
		factory.register(ListenSocket.class);
	}

	public static class ListenSocket extends WebSocketAdapter {

		private ServerSocket ss;

		@Override
		public void onWebSocketConnect(Session sess) {
			super.onWebSocketConnect(sess);
			try {
				Map<String, List<String>> params = sess.getUpgradeRequest().getParameterMap();
				int port = Integer.parseInt(params.get("port").get(0));
				ss = new ServerSocket(port);
				new AcceptorThread(ss, sess).start();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public void onWebSocketClose(int statusCode, String reason) {
			super.onWebSocketClose(statusCode, reason);
			if (ss != null) {
				try {
					ss.close();
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
				ss = null;
			}
		}

		@Override
		public void onWebSocketError(Throwable cause) {
			cause.printStackTrace();
		}
	}

	public static class AcceptorThread extends Thread {
		private Session sess;
		private ServerSocket ss;

		public AcceptorThread(ServerSocket ss, Session sess) {
			this.ss = ss;
			this.sess = sess;
		}

		@Override
		public void run() {
			try {
				while (true) {
					Socket s = ss.accept();
					int index;
					synchronized (TCPServlet.enqueuedSockets) {
						index = TCPServlet.enqueuedSockets.size();
						TCPServlet.enqueuedSockets.add(s);
					}
					sess.getRemote().sendString(index + "," + JSONServlet.intFromAddress(s.getInetAddress()) + "," + s.getPort());
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			sess.close();
		}
	}
}
