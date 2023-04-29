package oberonemulator;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WizNet {

	private final List<DatagramSocket> udpSockets = new ArrayList<>();
	private final List<TCPSocketThread> tcpSockets = new ArrayList<>();
	private final List<ServerSocket> serverSockets = new ArrayList<>();

	public WizNet() throws IOException {
		reset();
	}

	public void reset() {
		for (DatagramSocket s : udpSockets) {
			if (s == null)
				continue;
			s.close();
		}
		udpSockets.clear();
		for (TCPSocketThread s : tcpSockets) {
			if (s == null)
				continue;
			try {
				synchronized (s) {
					s.availableLen = 0;
				}
				s.sock.close();
				s.join();
			} catch (IOException | InterruptedException ex) {
				// ignore
			}
		}
		udpSockets.clear();
		for (ServerSocket s : serverSockets) {
			if (s == null)
				continue;
			try {
				s.close();
			} catch (IOException ex) {
				// ignore
			}
		}
	}

	private String getNameString(int[] ram, int offset) {
		byte[] bytes = new byte[128];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(ram, offset, 32);
		String result = new String(bytes, StandardCharsets.ISO_8859_1);
		int pos = result.indexOf('\0');
		if (pos != -1) {
			result = result.substring(0, pos);
		}
		return result;
	}

	private void setData(int[] ram, int offset, byte[] bytes, int len, int maxLen) {
		bytes = (len == bytes.length && len % 4 == 0) ? bytes : Arrays.copyOf(bytes, Math.min((len + 3) / 4 * 4, maxLen));
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ram, offset, bytes.length / 4);
	}

	private void setNameString(int[] ram, int offset, String value) {
		byte[] bytes = (value + "\0").getBytes(StandardCharsets.ISO_8859_1);
		setData(ram, offset, bytes, bytes.length, 128);
	}

	private InetAddress addressFromIntValue(int intValue) throws UnknownHostException {
		byte[] adr = new byte[4];
		ByteBuffer.wrap(adr).order(ByteOrder.BIG_ENDIAN).asIntBuffer().put(intValue);
		return InetAddress.getByAddress(adr);
	}

	private int intFromAddress(InetAddress addr) throws UnknownHostException {
		if (addr.getAddress().length != 4)
			throw new UnknownHostException();
		return ByteBuffer.wrap(addr.getAddress()).order(ByteOrder.BIG_ENDIAN).asIntBuffer().get();
	}

	private int allocID(List<?> list) {
		int id = -1;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) == null) {
				id = i;
				break;
			}
		}
		if (id == -1) {
			list.add(null);
			id = list.size() - 1;
		}
		return id;
	}

	public void handleCommand(int offset, int[] ram) {
		try {
			int op = ram[offset];
			switch (op) {
			case 0x10001: { // IP.StrToAdr + DNS.HostByName
				String host = getNameString(ram, offset + 3);
				try {
					InetAddress addr = InetAddress.getByName(host);
					if (!(addr instanceof Inet4Address)) {
						for (InetAddress a : InetAddress.getAllByName(host)) {
							if (a instanceof Inet4Address) {
								addr = a;
								break;
							}
						}
					}
					if (!(addr instanceof Inet4Address)) {
						throw new UnknownHostException();
					}
					ram[offset + 1] = 0;
					ram[offset + 2] = intFromAddress(addr);
				} catch (UnknownHostException ex) {
					ram[offset + 1] = 3601;
					ram[offset + 2] = 0;
				}
				break;
			}
			case 0x10002: { // IP.AdrToStr
				int adr = ram[offset + 2];
				setNameString(ram, offset + 3, String.format("%d.%d.%d.%d", (adr >> 24) & 0xFF, (adr >> 16) & 0xFF, (adr >> 8) & 0xFF, adr & 0xFF));
				ram[offset + 1] = 0;
				break;
			}
			case 0x10003: { // DNS.HostByNumber
				try {
					String host = addressFromIntValue(ram[offset + 2]).getCanonicalHostName();
					ram[offset + 1] = 0;
					setNameString(ram, offset + 3, host);
				} catch (UnknownHostException ex) {
					ex.printStackTrace();
					int adr = ram[offset + 2];
					ram[offset + 1] = 3601;
					setNameString(ram, offset + 3, String.format("%d.%d.%d.%d", (adr >> 24) & 0xFF, (adr >> 16) & 0xFF, (adr >> 8) & 0xFF, adr & 0xFF));
				}
				break;
			}
			case 0x10004: { // UDP.Open
				int lport = ram[offset + 3];
				int socketid = allocID(udpSockets);
				ram[offset + 2] = socketid;
				try {
					DatagramSocket ds = new DatagramSocket(lport);
					InetAddress addr = ds.getLocalAddress();
					udpSockets.set(socketid, ds);
					ram[offset + 3] = intFromAddress(addr);
					ram[offset + 1] = 0;
				} catch (SocketException | UnknownHostException ex) {
					ex.printStackTrace();
					ram[offset + 1] = 9999;
				}
				break;
			}
			case 0x10005: { // UDP.Close
				int socketid = ram[offset + 2];
				if (socketid >= 0 && socketid < udpSockets.size() && udpSockets.get(socketid) != null) {
					udpSockets.get(socketid).close();
					udpSockets.set(socketid, null);
					ram[offset + 1] = 0;
				} else {
					ram[offset + 1] = 3505;
				}
				break;
			}
			case 0x10006: { // UDP.Send
				int socketid = ram[offset + 2], len = ram[offset + 5];
				if (socketid >= 0 && socketid < udpSockets.size() && udpSockets.get(socketid) != null) {
					int wordlen = (len + 3) / 4;
					byte[] buf = new byte[wordlen * 4];
					ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(ram, offset + 6, wordlen);
					try {
						udpSockets.get(socketid).send(new DatagramPacket(buf, len, addressFromIntValue(ram[offset + 3]), ram[offset + 4]));
						ram[offset + 1] = 0;

					} catch (IOException ex) {
						ex.printStackTrace();
						ram[offset + 1] = 9999;
					}

				} else {
					ram[offset + 1] = 3505;
				}
				break;
			}
			case 0x10007: { // UDP.Receive
				int socketid = ram[offset + 2], len = ram[offset + 5];
				if (socketid >= 0 && socketid < udpSockets.size() && udpSockets.get(socketid) != null) {
					DatagramSocket ds = udpSockets.get(socketid);
					byte[] buf = new byte[len];
					DatagramPacket packet = new DatagramPacket(buf, len);
					try {
						ds.setSoTimeout(Math.max(ram[offset + 6], 1));
						ds.receive(packet);
						ram[offset + 1] = 0;
						ram[offset + 3] = intFromAddress(packet.getAddress());
						ram[offset + 4] = packet.getPort();
						ram[offset + 5] = packet.getLength();
						setData(ram, offset + 7, packet.getData(), packet.getLength(), 1500);
					} catch (SocketTimeoutException ex) {
						ram[offset + 1] = 3704;
						ram[offset + 5] = 0;
					} catch (IOException ex) {
						ex.printStackTrace();
						ram[offset + 1] = 9999;
						ram[offset + 5] = 0;
					}
				} else {
					ram[offset + 1] = 3505;
					ram[offset + 5] = 0;
				}
				break;
			}
			case 0x10008: { // TCP.Open
				int lport = ram[offset + 3];
				int fport = ram[offset + 5];
				if (ram[offset + 4] == 0 && fport == 0) { // listen
					int socketid = allocID(serverSockets);
					ram[offset + 2] = -socketid - 1;
					try {
						ServerSocket ss = new ServerSocket(lport);
						ss.setSoTimeout(1);
						serverSockets.set(socketid, ss);

						ram[offset + 1] = 0;
					} catch (IOException ex) {
						ex.printStackTrace();
						ram[offset + 1] = 3705;
					}
				} else { // connect
					int socketid = allocID(tcpSockets);
					ram[offset + 2] = socketid;
					try {
						Socket sock = new Socket(addressFromIntValue(ram[offset + 4]), fport, null, lport);
						tcpSockets.set(socketid, new TCPSocketThread(sock));
						ram[offset + 1] = 0;
					} catch (IOException ex) {
						ex.printStackTrace();
						ram[offset + 1] = 3701;
					}
				}
				break;
			}
			case 0x10009: { // TCP.SendChunk
				int socketid = ram[offset + 2];
				int len = ram[offset + 3];
				if (socketid >= 0 && socketid < tcpSockets.size() && tcpSockets.get(socketid) != null) {
					OutputStream sockout = tcpSockets.get(socketid).out;
					int wordlen = (len + 3) / 4;
					byte[] buf = new byte[wordlen * 4];
					ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(ram, offset + 5, wordlen);
					try {
						sockout.write(buf, 0, len);
						if (ram[offset + 4] != 0) { // flush
							sockout.flush();
						}
						ram[offset + 1] = 0;
					} catch (IOException ex) {
						ex.printStackTrace();
						ram[offset + 1] = 3702;
					}
				} else {
					ram[offset + 1] = 3706;
				}
				break;
			}
			case 0x1000A: { // TCP.ReceiveChunk
				int socketid = ram[offset + 2];
				int len = ram[offset + 3];
				int minlen = ram[offset + 4];
				if (socketid >= 0 && socketid < tcpSockets.size() && tcpSockets.get(socketid) != null) {
					TCPSocketThread tcpsock = tcpSockets.get(socketid);
					synchronized (tcpsock) {
						if (tcpsock.availableLen == 0 && tcpsock.closed) {
							ram[offset + 1] = 3707;
							ram[offset + 3] = 0;
						} else if (tcpsock.availableLen < minlen) {
							ram[offset + 1] = 3704;
							ram[offset + 3] = 0;
						} else {
							int currLen = Math.min(len, tcpsock.availableLen);
							tcpsock.availableLen -= currLen;
							ram[offset + 1] = 0;
							ram[offset + 3] = currLen;
							setData(ram, offset + 5, tcpsock.buf, currLen, 1500);
							System.arraycopy(tcpsock.buf, currLen, tcpsock.buf, 0, tcpsock.availableLen);
							tcpsock.notifyAll();
						}
					}
				} else {
					ram[offset + 1] = 3706;
					ram[offset + 3] = 0;
				}
				break;
			}
			case 0x1000B: { // TCP.Available
				int socketid = ram[offset + 2];
				if (socketid >= 0 && socketid < tcpSockets.size() && tcpSockets.get(socketid) != null) {
					TCPSocketThread tcpsock = tcpSockets.get(socketid);
					ram[offset + 1] = tcpsock.availableLen + (tcpsock.closed ? 1 : 0);
				} else {
					ram[offset + 1] = 0;
				}
				break;
			}
			case 0x1000C: { // TCP.Close
				int socketid = ram[offset + 2];
				if (socketid >= 0 && socketid < tcpSockets.size() && tcpSockets.get(socketid) != null) {
					TCPSocketThread s = tcpSockets.get(socketid);
					try {
						synchronized (s) {
							s.availableLen = 0;
						}
						s.sock.close();
						s.join();
					} catch (IOException | InterruptedException ex) {
						// ignore
					}
					tcpSockets.set(socketid, null);
					ram[offset + 1] = 0;
				} else if (socketid < 0 && -socketid - 1 < serverSockets.size() && serverSockets.get(-socketid - 1) != null) {
					try {
						serverSockets.get(-socketid - 1).close();
					} catch (IOException ex) {
						ex.printStackTrace();
					}
					serverSockets.set(-socketid - 1, null);
					ram[offset + 1] = 0;
				} else {
					ram[offset + 1] = 3706;
				}
				break;
			}
			case 0x1000D: { // TCP.Accept
				int socketid = -ram[offset + 2] - 1;
				if (socketid >= 0 && socketid < serverSockets.size() && serverSockets.get(socketid) != null) {
					int clientid = allocID(tcpSockets);
					ram[offset + 1] = 0;
					try {
						Socket clientsock = serverSockets.get(socketid).accept();
						tcpSockets.set(clientid, new TCPSocketThread(clientsock));
						ram[offset + 3] = clientid;
						ram[offset + 4] = intFromAddress(clientsock.getInetAddress());
						ram[offset + 5] = clientsock.getPort();
					} catch (SocketTimeoutException ex) {
						ram[offset + 3] = -1;
					} catch (IOException ex) {
						ex.printStackTrace();
						ram[offset + 1] = 3702;
					}
				} else {
					ram[offset + 1] = 3706;
					ram[offset + 3] = 0;
				}
				break;
			}
			default:
				throw new RuntimeException("Unsupported WizNet operation");
			}
		} catch (RuntimeException ex) {
			throw new RuntimeException(ex);
		}
	}

	private static class TCPSocketThread extends Thread {
		private final Socket sock;
		private final BufferedOutputStream out;
		private int availableLen = 0;
		private boolean closed = false;
		private final byte[] buf = new byte[1024];

		private TCPSocketThread(Socket sock) throws IOException {
			this.sock = sock;
			this.out = new BufferedOutputStream(sock.getOutputStream());
			start();
		}

		@Override
		public void run() {
			try {
				InputStream in = sock.getInputStream();
				byte[] b = new byte[buf.length];
				while (!closed) {
					int len;
					synchronized (this) {
						while (availableLen == buf.length)
							wait();
						len = buf.length - availableLen;
					}
					len = in.read(b, 0, len);
					synchronized (this) {
						if (len == -1) {
							closed = true;
						} else {
							System.arraycopy(b, 0, buf, availableLen, len);
							availableLen += len;
						}
					}
				}
			} catch (IOException | InterruptedException ex) {
				throw new RuntimeException(ex);
			}
		}
	}
}
