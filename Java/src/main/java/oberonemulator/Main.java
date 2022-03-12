/*
 * Copyright © 2014 Peter De Wachter
 * Copyright © 2014, 2022 Michael Schierl
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class Main {

	private static Keyboard keyboard = new Keyboard.VirtualKeyboard(true);
	private static File hostFsDirectory = null;

	public static void main(String[] args) throws Exception {
		if (args.length == 3 && args[0].equals("PCLink")) {
			PCLink.start(args[1], Integer.parseInt(args[2]), null);
		} else if (args.length == 4 && args[0].equals("--command-line") && args[1].equals("--rom")) {
			// dirty workaround to support "new" command line syntax in "old" emulator
			main(new String[] { "0", "0", args[3],args[2], "CommandLine"});
		} else if (args.length == 5 && args[0].equals("--encode-png") && args[2].equals("--rom")) {
			// dirty workaround to support "new" command line syntax in "old" emulator
			main(new String[] { "EncodePNG", args[1], args[4], args[3]});
		} else if (args.length == 4 && args[0].equals("EncodePNG")) {
			PNGEncoder.encode(args[1], args[2], args[3]);
		} else if (args.length == 4 && args[0].equals("DecodePNG")) {
			PNGEncoder.decode(args[1], args[2], args[3]);
		} else if (args.length > 1 && args[0].equals("NativeFloatingPoint")) {
			CPU.nativeFloatingPoint = true;
			main(Arrays.copyOfRange(args, 1, args.length));
		} else if (args.length > 2 && args[0].equals("LimitFeatures")) {
			Feature.allowedFeatures = Feature.parse(args[1]);
			main(Arrays.copyOfRange(args, 2, args.length));
		} else if (args.length > 2 && args[0].equals("KeyboardEmulation")) {
			switch (args[1]) {
			case "Virtual":
				keyboard = new Keyboard.VirtualKeyboard(true);
				break;
			case "ParaVirtual":
				keyboard = new Keyboard.ParavirtualKeyboard();
				break;
			case "NoParaVirtual":
				keyboard = new Keyboard.VirtualKeyboard(false);
				break;
			case "Native":
				keyboard = new Keyboard.NativeKeyboard();
				break;
			case "Hybrid":
				keyboard = new Keyboard.HybridKeyboard();
				break;
			default:
				throw new Exception("Unsupported keyboard type: " + args[1]);
			}
			main(Arrays.copyOfRange(args, 2, args.length));
		} else if (args.length > 2 && args[0].equals("HostFS")) {
			hostFsDirectory = new File(args[1]);
			main(Arrays.copyOfRange(args, 2, args.length));
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
				Feature.LARGE_ADDRESS_SPACE.use();
				largeAddressSpace = true;
				memSize *= bootloader[511];
				displayStart = bootloader[510];
				romStart = bootloader[509];
			} else if ((bootloader[255] & 0xFFFFFF) == 0x3D424D && Arrays.equals(Arrays.copyOfRange(bootloader, 256, 512), new int[256])) {
				int mb = bootloader[255] >>> 24;
				if (mb == '1' || mb == '2' || mb == '4' || mb == '8')
					mb &= 0xF;
				Feature.LARGE_ADDRESS_SPACE.use();
				largeAddressSpace = true;
				memSize *= mb;
				displayStart += (mb-1) * 0x100000;
				romStart += (mb-1) * 0x100000;
			}
			boolean headless = false;
			if (args[0].equals("0") && args[1].equals("0")) {
				headless = true;
				args[0] = "1024";
				args[1] = "768";
			}
			BufferedImage img = new BufferedImage(Math.abs(Integer.parseInt(args[0])) & ~31, Integer.parseInt(args[1]), BufferedImage.TYPE_INT_RGB);
			int span = -128;
			if (Integer.parseInt(args[0]) < 0) {
				Feature.NEW_DYNSIZE_GRAPHICS.use();
				span = img.getWidth() / 8;
			} else if (img.getWidth() != 1024 || img.getHeight() != 768)
				Feature.DYNSIZE_GRAPHICS.use();
			ServerSocket rs232ss = null, rs232ss2 = null;
			Socket rs232s = null;
			InetSocketAddress net = null;
			int pcLinkPort = -1, commandLinePort = -1;
			if (args.length >= 5) {
				if (args[4].endsWith("+PCLink")) {
					args[4] = args[4].substring(0, args[4].length() - 7);
					rs232ss2 = new ServerSocket(0);
					pcLinkPort = rs232ss2.getLocalPort();
				}
				if (args[4].equals("PCLink")) {
					rs232ss = new ServerSocket(0);
					pcLinkPort = rs232ss.getLocalPort();
				} else if (args[4].equals("CommandLine")) {
					rs232ss = new ServerSocket(0);
					commandLinePort = rs232ss.getLocalPort();
				} else if (args[4].contains(":")) {
					String[] parts = args[4].split(":", 2);
					rs232s = new Socket(parts[0], Integer.parseInt(parts[1]));
				} else if (!args[4].equals("-")){
					rs232ss = new ServerSocket(Integer.parseInt(args[4]));
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
			MemoryMappedIO mmio = new MemoryMappedIO(args[2], rs232ss, rs232s, net, hostFsDirectory, rs232ss2);
			ImageMemory imgmem = new ImageMemory(span, img, (int)((displayStart & 0xFFFFFFFFL) / 4));
			Memory mem = new Memory(imgmem, bootloader, mmio, largeAddressSpace, memSize, displayStart, romStart);
			keyboard.setMMIO(mmio);
			EmulatorFrame emuFrame = null;
			CPU cpu = null;
			if (headless) {
				cpu = new CPU(mem, largeAddressSpace);
				cpu.start();
			} else {
				emuFrame = new EmulatorFrame(mem, keyboard, mmio, img, imgmem, largeAddressSpace);
				cpu = emuFrame.getCPU();
			}
			if (pcLinkPort != -1) {
				PCLink.start("localhost", pcLinkPort, emuFrame);
			}
			if (commandLinePort != -1) {
				PCLink.startCommandLine("localhost",  rs232ss.getLocalPort(), cpu, keyboard, mmio);
			}
		} else {
			System.out.println("Usage: java -jar OberonEmulator.jar PCLink <host> <port>");
			System.out.println("       java -jar OberonEmulator.jar EncodePNG <pngfile> <diskimage> <romimage>");
			System.out.println("       java -jar OberonEmulator.jar DecodePNG <pngfile> <diskimage> <romimage>");
			System.out.println("       java -jar OberonEmulator.jar <width> <height> <diskimage> <romimage> [<rs232> [<net>]]");
			System.out.println("       java -jar OberonEmulator.jar <width> <height> -png <pngfile> [<rs232> [<net>]]");
			System.out.println("       java -jar OberonEmulator.jar NativeFloatingPoint ...");
			System.out.println("       java -jar OberonEmulator.jar LimitFeatures <base>[+<feature>|-<feature>]* ...");
			System.out.println("       java -jar OberonEmulator.jar KeyboardEmulation <kbdtype> ...");
			System.out.println("       java -jar OberonEmulator.jar HostFS <hostfspath> ...");
			System.out.println();
			System.out.println("<rs232> can be a TCP port number, <host>:<port>, the word 'PCLink' to run PCLink over virtual RS232, or '-' to ignore.");
			System.out.println("        It may also be 'CommandLine' to launch a CommandLineCompiler compatible command line on stdin/stdout.");
			System.out.println("        You can also add +PCLink to one of the other options, to run PCLink on the second RS232 port.");
			System.out.println("<net> is a broadcast IP address, in the form <host>[:<port>]. Default port is 48654 (0BE0Eh, for 0BEr0nnEt).");
			System.out.println("<kbdtype> is one of Virtual, ParaVirtual, NoParaVirtual, Native, Hybrid.");
			System.out.println("<hostfspath> is a path of a directory on the host, which is used as HostFS.");
		}
	}
}
