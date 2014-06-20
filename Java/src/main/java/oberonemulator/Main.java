/*
 * Copyright © 2014 Peter De Wachter
 * Copyright © 2014 Michael Schierl
 * 
 * Permission to use, copy, modify, and/or distribute this software for
 * any purpose with or without fee is hereby granted, provided that the
 * above copyright notice and this permission notice appear in all
 * copies.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL
 * WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE
 * AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THIS SOFTWARE.
 * 
 */

package oberonemulator;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;

public class Main {

	public static void main(String[] args) throws Exception {
		if (args.length == 3 && args[0].equals("PCLink")) {
			PCLink.start(args[1], Integer.parseInt(args[2]));
		} else if (args.length == 4 && args[0].equals("EncodePNG")) {
			PNGEncoder.encode(args[1], args[2], args[3]);
		} else if (args.length == 4 && args[0].equals("DecodePNG")) {
			PNGEncoder.decode(args[1], args[2], args[3]);
		} else if (args.length >= 4 && args.length <= 6) {
			int[] bootloader;
			if (args[2].equals("-png")) {
				File tmpBoot = File.createTempFile("~oberon", null);
				File tmpDisk = File.createTempFile("~oberon", null);
				PNGEncoder.decode(args[3], tmpDisk.getAbsolutePath(), tmpBoot.getAbsolutePath());
				bootloader = Disk.loadBootloader(tmpBoot.getAbsolutePath());
				if (!tmpBoot.delete())
					throw new IOException("Deleting temp file failed");
				tmpDisk.deleteOnExit();
				args[2] = tmpDisk.getAbsolutePath();
			} else {
				bootloader = Disk.loadBootloader(args[3]);
			}
			boolean largeAddressSpace = false;
			int memSize = Memory.MemSize;
			int displayStart = Memory.DisplayStart;
			int romStart = Memory.ROMStart;
			if (bootloader[511] != 0) {
				largeAddressSpace = true;
				memSize *= bootloader[511];
				displayStart = bootloader[510];
				romStart = bootloader[509];
			}
			BufferedImage img = new BufferedImage(Integer.parseInt(args[0]) & ~31, Integer.parseInt(args[1]), BufferedImage.TYPE_INT_RGB);
			ServerSocket rs232 = null;
			InetSocketAddress net = null;
			int pcLinkPort = -1;
			if (args.length >= 5) {
				if (args[4].equals("PCLink")) {
					rs232 = new ServerSocket(0);
					pcLinkPort = rs232.getLocalPort();
				} else if (!args[4].equals("-")){
					rs232 = new ServerSocket(Integer.parseInt(args[4]));
				}
				if (args.length == 6) {
					int port = 48654;
					String host = args[5];
					if (host.contains(":")) {
						int pos = args[5].lastIndexOf(":");
						port = Integer.parseInt(host.substring(pos+1));
						host = host.substring(0, pos);
					}
					net = new InetSocketAddress(InetAddress.getByName(host), port);
				}
			}
			MemoryMappedIO mmio = new MemoryMappedIO(args[2], rs232, net);
			ImageMemory imgmem = new ImageMemory(img, (int)((displayStart & 0xFFFFFFFFL) / 4));
			Memory mem = new Memory(imgmem, bootloader, mmio, largeAddressSpace, memSize, displayStart, romStart);
			new EmulatorFrame(mem, mmio, img, imgmem, largeAddressSpace);
			if (pcLinkPort != -1) {
				PCLink.start("localhost", pcLinkPort);
			}
		} else {
			System.out.println("Usage: java -jar OberonEmulator.jar PCLink <host> <port>");
			System.out.println("       java -jar OberonEmulator.jar EncodePNG <pngfile> <diskimage> <romimage>");
			System.out.println("       java -jar OberonEmulator.jar DecodePNG <pngfile> <diskimage> <romimage>");
			System.out.println("       java -jar OberonEmulator.jar <width> <height> <diskimage> <romimage> [<rs232> [<net>]]");
			System.out.println("       java -jar OberonEmulator.jar <width> <height> -png <pngfile> [<rs232> [<net>]]");
			System.out.println();
			System.out.println("<rs232> can be a TCP port number, the word 'PCLink' to run PCLink over virtual RS232, or '-' to ignore.");
			System.out.println("<net> is a broadcast IP address, in the form <host>[:<port>]. Default port is 48654 (0BE0Eh, for 0BEr0nnEt).");
		}
	}
}
