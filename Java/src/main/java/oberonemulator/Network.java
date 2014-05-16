package oberonemulator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Network {

	private static enum NetState {
		command, pauseRead, writeReg, txPayload, rxPayload
	}

	private InetSocketAddress addr;
	private DatagramSocket ds;
	private BlockingQueue<DatagramPacket> netQueue = new ArrayBlockingQueue<>(16);
	private NetState state = NetState.command;
	private IntBuffer currentPacket = IntBuffer.allocate(576 / 4);
	private boolean continuePacket = false;

	public Network(InetSocketAddress addr) throws IOException {
		this.addr = addr;
		ds = new DatagramSocket(null);
		ds.setReuseAddress(true);
		ds.bind(new InetSocketAddress((InetAddress) null, addr.getPort()));
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (true) {
						byte[] buf = new byte[576];
						DatagramPacket dp = new DatagramPacket(buf, buf.length);
						ds.receive(dp);
						if (dp.getLength() % 32 == 0)
							netQueue.put(dp);
					}
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
			}
		});
		t.setDaemon(true);
		t.start();
	}

	public int read() {
		switch (state) {
		case command:
			return netQueue.size() > 0 || continuePacket ? 0x60 : 0x21;
		case pauseRead:
			try {
				Thread.sleep(150);
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
			state = NetState.command;
			return 0x21;
		case rxPayload:
			int result = currentPacket.get();
			if (currentPacket.remaining() == 0) {
				state = NetState.pauseRead;
				currentPacket.clear();
			} else if (currentPacket.position() % 8 == 0) {
				continuePacket = true;
				state = NetState.command;
			}
			return result;
		default:
			throw new IllegalStateException("Illegal state for reading: " + state);
		}
	}

	public void write(int value) {
		switch (state) {
		case pauseRead:
		case command:
			if (value == 0xFF || value == 0xE1 || value == 0xE2 || value == -1 || value == 0x17) {
				// can be safely ignored
			} else if (value >= 0x20 && value <= 0x31) {
				state = NetState.writeReg;
				if (value == 0x20 && currentPacket.position() != 0) {
					ByteBuffer bb = ByteBuffer.allocate(currentPacket.position() * 4);
					currentPacket.flip();
					bb.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(currentPacket);
					currentPacket.clear();
					try {
						ds.send(new DatagramPacket(bb.array(), bb.array().length, addr));
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
			} else if (value == 0xA0) {
				state = NetState.txPayload;
			} else if (value == 0x61) {
				if (continuePacket) {
					continuePacket = false;
				} else {
					DatagramPacket dp = netQueue.remove();
					ByteBuffer bb = ByteBuffer.allocate(dp.getLength());
					bb.put(dp.getData(), dp.getOffset(), dp.getLength());
					bb.flip();
					currentPacket.put(bb.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer());
					currentPacket.flip();
				}
				state = NetState.rxPayload;
			} else {
				throw new IllegalStateException("Unknown command: " + value);
			}
			break;
		case writeReg:
			state = NetState.command;
			break;
		case txPayload:
			currentPacket.put(value);
			if (currentPacket.position() % 8 == 0)
				state = NetState.command;
			break;
		case rxPayload:
			if (value != -1)
				throw new IllegalStateException("Invalid value in state rxPayload: " + value);
			break;
		default:
			throw new IllegalStateException("Illegal state for writing: " + state);
		}
	}
}
