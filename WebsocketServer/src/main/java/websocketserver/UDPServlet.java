package websocketserver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

public class UDPServlet extends WebSocketServlet {

	@Override
	public void configure(WebSocketServletFactory factory) {
		factory.register(UDPSocket.class);
	}

	public static class UDPSocket extends WebSocketAdapter {

		private DatagramSocket socket;

		@Override
		public void onWebSocketConnect(Session sess) {
			super.onWebSocketConnect(sess);
			try {
				Map<String, List<String>> params = sess.getUpgradeRequest().getParameterMap();
				int port = Integer.parseInt(params.get("port").get(0));
				socket = new DatagramSocket(port);
				sess.getRemote().sendString("" + JSONServlet.intFromAddress(socket.getLocalAddress()));
				new ForwarderThread(socket, sess).start();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public void onWebSocketClose(int statusCode, String reason) {
			super.onWebSocketClose(statusCode, reason);
			if (socket != null) {
				socket.close();
				socket = null;
			}
		}

		@Override
		public void onWebSocketError(Throwable cause) {
			cause.printStackTrace();
		}

		@Override
		public void onWebSocketBinary(byte[] payload, int offset, int len) {
			if (socket != null && len > 6) {
				try {
					InetAddress host = InetAddress.getByAddress(Arrays.copyOfRange(payload, offset, offset + 4));
					socket.send(new DatagramPacket(payload, offset + 6, len - 6, host, ((payload[offset + 4] << 8) + payload[offset + 5]) & 0xFFFF));
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			}
		}
	}

	public static class ForwarderThread extends Thread {
		private Session sess;
		private DatagramSocket socket;

		public ForwarderThread(DatagramSocket socket, Session sess) {
			this.socket = socket;
			this.sess = sess;
		}

		@Override
		public void run() {
			try {
				byte[] buf = new byte[1506];
				while (true) {
					DatagramPacket packet = new DatagramPacket(buf, 6, 1500);
					socket.receive(packet);
					System.arraycopy(packet.getAddress().getAddress(), 0, buf, 0, 4);
					buf[4] = (byte) ((packet.getPort() >> 8) & 0xFF);
					buf[5] = (byte) (packet.getPort() & 0xFF);
					sess.getRemote().sendBytes(ByteBuffer.wrap(buf, 0, 6 + packet.getLength()));
				}
			} catch (SocketException ex) {
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			sess.close();
		}
	}
}
