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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class Main {

	private static String[] convertLegacyArgs(String[] args) {
		if (args.length == 3 && args[0].equals("PCLink")) {
			return new String[] { "--run-pc-link", args[1] + ":" + args[2] };
		} else if (args.length == 4 && args[0].equals("EncodePNG")) {
			return new String[] { "--encode-png", args[1], "--rom", args[3], args[2] };
		} else if (args.length == 4 && args[0].equals("DecodePNG")) {
			return new String[] { "--decode-png", args[1], "--rom", args[3], args[2] };
		} else if (args.length > 1 && args[0].equals("NativeFloatingPoint")) {
			return convertLegacySubargs(args, 1, "--native-floating-point");
		} else if (args.length > 2 && args[0].equals("LimitFeatures")) {
			return convertLegacySubargs(args, 2, "--limit-features", args[1]);
		} else if (args.length > 2 && args[0].equals("KeyboardEmulation")) {
			return convertLegacySubargs(args, 2, "--keyboard-emulation", args[1]);
		} else if (args.length > 2 && args[0].equals("HostFS")) {
			return convertLegacySubargs(args, 2, "--host-fs", args[1]);
		} else if (args.length > 3 && args[0].equals("MemoryLayout")) {
			return convertLegacySubargs(args, 3, "--memory", args[1], "--display-start", args[2]);
		} else if (args.length >= 4 && args.length <= 6) {
			String[] result;
			if (args[2].equals("-png")) {
				result = new String[] { "--size", args[0] + "x" + args[1], "--png", args[3] };
			} else if (args[0].equals("0") && args[1].equals("0") && args.length == 5 && args[4].equals("CommandLine")) {
				result = new String[] { "--command-line", "--rom", args[3], args[2] };
				args[4] = "-";
			} else {
				result = new String[] { "--size", args[0] + "x" + args[1], "--rom", args[3], args[2] };
			}
			if (args.length >= 5) {
				String[] serialArgs, serialExtraArgs, netArgs;
				if (args[4].endsWith("+PCLink")) {
					args[4] = args[4].substring(0, args[4].length() - 7);
					serialExtraArgs = new String[] { "--next-serial", "--pc-link" };
				} else {
					serialExtraArgs = new String[0];
				}
				if (args[4].equals("PCLink")) {
					serialArgs = new String[] { "--pc-link" };
				} else if (args[4].equals("CommandLine")) {
					serialArgs = new String[] { "--command-line" };
				} else if (args[4].contains(":")) {
					serialArgs = new String[] { "--connect", args[4] };
				} else if (!args[4].equals("-")) {
					serialArgs = new String[] { "--listen", args[4] };
				} else {
					serialArgs = new String[0];
				}
				if (args.length == 6) {
					netArgs = new String[] { "--network", args[6] };
				} else {
					netArgs = new String[0];
				}
				int oldLength = result.length;
				result = Arrays.copyOfRange(result, 0, oldLength + serialArgs.length + serialExtraArgs.length + netArgs.length);
				System.arraycopy(serialArgs, 0, result, oldLength, serialArgs.length);
				System.arraycopy(serialExtraArgs, 0, result, oldLength + serialArgs.length, serialExtraArgs.length);
				System.arraycopy(netArgs, 0, result, oldLength + serialArgs.length + serialExtraArgs.length, netArgs.length);
			}
			return result;
		}
		return null;
	}

	private static String[] convertLegacySubargs(String[] args, int fromIndex, String... converted) {
		String[] newArgs = convertLegacyArgs(Arrays.copyOfRange(args, fromIndex, args.length));
		if (newArgs == null)
			return null;
		String[] result = Arrays.copyOfRange(converted, 0, converted.length + newArgs.length);
		System.arraycopy(newArgs, 0, result, converted.length, newArgs.length);
		return result;
	}

	public static void main(String[] args) throws Exception {
		if (args.length >= 3 && !args[0].startsWith("-") && !args[1].startsWith("-") && (!args[2].startsWith("-") || args[2].equals("-png"))) {
			String[] newArgs = convertLegacyArgs(args);
			if (newArgs != null) {
				StringBuilder sb = new StringBuilder("WARNING: Converted legacy command line to:");
				for (String na : newArgs) {
					sb.append(" ").append(na);
				}
				System.err.println(sb.toString());
				args = newArgs;
			}
		}
		if (args.length == 2 && (args[0].equals("-R") || args[0].equals("--run-pc-link"))) {
			int pos = args[1].lastIndexOf(':');
			if (pos != -1) {
				PCLink.start(args[1].substring(0, pos), Integer.parseInt(args[1].substring(pos + 1)), null);
			} else {
				usage();
			}
			return;
		}

		Keyboard keyboard = new Keyboard.VirtualKeyboard(true);
		File hostFsDirectory = null;
		int[] memoryLayout = null;
		boolean codecache = false;
		int minimumSpan = 128, serialIndex = 0;
		int screenWidth = -1, screenHeight = -1;
		boolean headless = false, decodePNG = false, usePNG = false, noPadding = false, fastMode = false;
		String diskImage = null, romImage = null, pngTarget = null, network = null;
		String[] serialPorts = new String[2];

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.startsWith("-")) {
				String[] options;
				if (arg.startsWith("--") || arg.length() < 3) {
					options = new String[] { arg };
				} else {
					options = new String[arg.length() - 1];
					for (int j = 0; j < options.length; j++) {
						options[j] = "-" + arg.substring(j + 1, j + 2);
					}
				}
				for (String option : options) {
					switch (option) {
					case "-E":
					case "--encode-png":
						decodePNG = false;
						pngTarget = args[++i];
						break;
					case "-D":
					case "--decode-png":
						decodePNG = true;
						pngTarget = args[++i];
						break;
					case "-S":
					case "--display-span":
						minimumSpan = Integer.parseInt(args[++i]);
						break;
					case "-F":
					case "--native-floating-point":
						CPU.nativeFloatingPoint = true;
						break;
					case "-C":
					case "--code-cache":
						codecache = true;
						break;
					case "-0":
					case "--no-padding":
						noPadding = true;
						break;
					case "-L":
					case "--limit-features":
						Feature.allowedFeatures = Feature.parse(args[++i]);
					case "-k":
					case "--keyboard-emulation":
						switch (args[++i]) {
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
							throw new Exception("Unsupported keyboard type: " + args[i]);
						}
						break;
					case "-h":
					case "--host-fs":
						hostFsDirectory = new File(args[++i]);
						break;
					case "-m":
					case "--memory":
						if (memoryLayout == null)
							memoryLayout = new int[] { 1, 0xD0000000 };
						memoryLayout[0] = Integer.parseInt(args[++i]);
						break;
					case "-d":
					case "--display-start":
						if (memoryLayout == null)
							memoryLayout = new int[] { 1, 0xD0000000 };
						memoryLayout[1] = (int) Long.parseLong(args[++i], 16);
						break;
					case "-p":
					case "--png":
						usePNG = true;
						break;
					case "-s":
					case "--size":
						String[] parts = args[++i].split("x", 2);
						screenWidth = Integer.parseInt(parts[0]);
						screenHeight = Integer.parseInt(parts[1]);
						break;
					case "-r":
					case "--rom":
						romImage = args[++i];
						break;
					case "-n":
					case "--next-serial":
						serialIndex++;
						if (serialIndex == 2) {
							usage();
							return;
						}
						break;
					case "-l":
					case "-P":
					case "--pc-link":
						if (serialPorts[serialIndex] != null) {
							usage();
							return;
						}
						serialPorts[serialIndex] = "PCLink";
						break;
					case "--command-line":
						if (serialPorts[serialIndex] != null) {
							usage();
							return;
						}
						serialPorts[serialIndex] = "CommandLine";
						headless = true;
						break;
					case "-c":
					case "--serial":
					case "--connect":
					case "--listen":
						if (serialPorts[serialIndex] != null) {
							usage();
							return;
						}
						serialPorts[serialIndex] = args[++i];
						break;
					case "-N":
					case "--network":
						network = args[++i];
						break;
					case "-f":
					case "--fast-mode":
						fastMode = true;
						break;
					default:
						usage();
						return;
					}
				}
			} else if (diskImage == null) {
				diskImage = arg;
			} else {
				usage();
				return;
			}
		}
		if (diskImage == null) {
			usage();
			return;
		}
		InputStream rom;
		if (decodePNG || usePNG) {
			rom = null;
		} else if (romImage != null) {
			rom = new FileInputStream(romImage);
		} else {
			rom = Main.class.getResourceAsStream("/boot.rom");
		}
		if (pngTarget != null && !decodePNG) {
			PNGEncoder.encode(pngTarget, diskImage, rom, noPadding);
			return;
		} else if (pngTarget != null && decodePNG) {
			OutputStream romStream = romImage == null ? null : new FileOutputStream(romImage);
			PNGEncoder.decode(pngTarget, diskImage, romStream, noPadding);
			if (romStream != null)
				romStream.close();
			return;
		}
		int[] bootloader;
		if (usePNG) {
			File tmpDisk = File.createTempFile("~oberon", null);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			PNGEncoder.decode(diskImage, tmpDisk.getAbsolutePath(), baos, true);
			bootloader = Disk.loadBootloader(new ByteArrayInputStream(baos.toByteArray()));
			tmpDisk.deleteOnExit();
			diskImage = tmpDisk.getAbsolutePath();
		} else {
			bootloader = Disk.loadBootloader(rom);
		}
		boolean largeAddressSpace = false;
		int memSize = Memory.MemSize;
		int displayStart = Memory.DisplayStart;
		int romStart = Memory.ROMStart;
		if (memoryLayout != null) {
			largeAddressSpace = true;
			memSize *= memoryLayout[0];
			displayStart = memoryLayout[1];
			romStart = displayStart;
		}
		if (screenWidth == -1 && screenHeight == -1) {
			screenWidth = 1024;
			screenHeight = 768;
		} else {
			headless = false;
		}
		BufferedImage img = new BufferedImage(screenWidth & ~31, screenHeight, BufferedImage.TYPE_INT_RGB);
		if (img.getWidth() != 1024 || img.getHeight() != 768) {
			Feature.DYNSIZE_GRAPHICS.use();
		}
		ServerSocket[] rs232ss = new ServerSocket[2];
		Socket[] rs232s = new Socket[2];
		InetSocketAddress net = null;
		int pcLinkPort = -1, commandLinePort = -1;
		for (int port = 0; port < 2; port++) {
			if (serialPorts[port] == null)
				continue;
			if (serialPorts[port].equals("PCLink")) {
				rs232ss[port] = new ServerSocket(0);
				if (pcLinkPort != -1) {
					usage();
					return;
				}
				pcLinkPort = rs232ss[port].getLocalPort();
			} else if (serialPorts[port].equals("CommandLine")) {
				rs232ss[port] = new ServerSocket(0);
				if (commandLinePort != -1) {
					usage();
					return;
				}
				commandLinePort = rs232ss[port].getLocalPort();
			} else if (serialPorts[port].contains(":")) {
				String[] parts = serialPorts[port].split(":", 2);
				rs232s[port] = new Socket(parts[0], Integer.parseInt(parts[1]));
			} else {
				rs232ss[port] = new ServerSocket(Integer.parseInt(serialPorts[port]));
			}
		}
		if (network != null) {
			int port = 48654;
			String host = network;
			if (host.contains(":")) {
				int pos = network.lastIndexOf(":");
				port = Integer.parseInt(host.substring(pos + 1));
				host = host.substring(0, pos);
			}
			net = new InetSocketAddress(InetAddress.getByName(host), port);
		}
		MemoryMappedIO mmio = new MemoryMappedIO(diskImage, rs232ss[0], rs232s[0], net, hostFsDirectory, rs232ss[1], rs232s[1]);
		if (fastMode) {
			mmio.setFastMode(true);
		}
		ImageMemory imgmem = new ImageMemory(minimumSpan, img, (int) ((displayStart & 0xFFFFFFFFL) / 4));
		Memory mem = new Memory(imgmem, bootloader, mmio, largeAddressSpace, memSize, displayStart, romStart, codecache);
		String resolutionError = imgmem.validateResolution(mem.getPaletteStart());
		if (resolutionError != null) {
			System.out.println(resolutionError);
			return;
		}
		keyboard.setMMIO(mmio);
		EmulatorFrame emuFrame = null;
		CPU cpu = null;
		cpu = new CPU(mem, largeAddressSpace);
		if (!headless) {
			emuFrame = new EmulatorFrame(cpu, mem, keyboard, mmio, img, imgmem, largeAddressSpace);
		}
		cpu.start();
		if (pcLinkPort != -1) {
			PCLink.start("localhost", pcLinkPort, emuFrame);
		}
		if (commandLinePort != -1) {
			PCLink.startCommandLine("localhost", commandLinePort, cpu, keyboard, mmio);
		}
	}

	private static void usage() {
		System.out.println("Usage: java -jar OberonEmulator.jar -R|--run-pc-link <host>:<port>");
		System.out.println();
		System.out.println("       java -jar OberonEmulator.jar -E|--encode-png <pngfile> [args] <diskimage>");
		System.out.println("       java -jar OberonEmulator.jar -D|--decode-png <pngfile> [args] <diskimage>");
		System.out.println("           Supported args: -r|--rom <romfile>");
		System.out.println("                           -0|--no-padding");
		System.out.println();
		System.out.println("       java -jar OberonEmulator.jar [args] <diskimage>");
		System.out.println("           Supported args: -r|--rom <romfile>");
		System.out.println("                           -k|--keyboard-emulation <kbdtype>");
		System.out.println("                           -h|--host-fs <hostfspath>");
		System.out.println("                           -m|--memory <megabytes>");
		System.out.println("                           -p|--png");
		System.out.println("                           -s|--size <width>x<height>");
		System.out.println("                           -d|--display-start");
		System.out.println("                           -S|--display-span <minSpan>");
		System.out.println("                           -F|--native-floating-point");
		System.out.println("                           -C|--code-cache");
		System.out.println("                           -L|--limit-features <base>[+<feature>|-<feature>]*");
		System.out.println("                           -N|--network <net>");
		System.out.println("                           -f|--fast-mode");
		System.out.println("           Serial options: -P|-l|--pc-link");
		System.out.println("                           --command-line");
		System.out.println("                           -c|--serial|--connect <host>:<port>");
		System.out.println("                           -c|--serial|--listen <port>");
		System.out.println("                           -n|--next-serial");
		System.out.println();
		System.out.println("A maximum of 2 serial ports are supported.");
		System.out.println("<net> is a broadcast IP address, in the form <host>[:<port>]. Default port is 48654 (0BE0Eh, for 0BEr0nnEt).");
		System.out.println("<kbdtype> is one of Virtual, ParaVirtual, NoParaVirtual, Native, Hybrid.");
		System.out.println("<hostfspath> is a path of a directory on the host, which is used as HostFS.");
	}
}
