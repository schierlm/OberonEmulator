package lspserver;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import oberonemulator.CPU;
import oberonemulator.Disk;
import oberonemulator.ImageMemory;
import oberonemulator.Memory;
import oberonemulator.MemoryMappedIO;

public class Main {

	public static void main(String[] args) throws Exception {
		boolean debug = false, embeddedMode = false;
		InputStream stdin = System.in;
		OutputStream stdout = System.out;
		File cacheDir = null;
		if (args.length >= 1 && args[0].equals("-debug")) {
			debug = true;
			args = Arrays.copyOfRange(args, 1, args.length);
		}
		if (args.length > 2 && args[0].equals("-wrapper")) {
			ServerSocket ss = new ServerSocket(Integer.parseInt(args[1]));
			args = Arrays.copyOfRange(args, 2, args.length);
			Socket s = ss.accept();
			ss.close();
			stdout = s.getOutputStream();
			stdin = s.getInputStream();
		}
		if (args.length > 2 && args[0].equals("-cache")) {
			cacheDir = new File(args[1]);
			cacheDir.mkdirs();
			args = Arrays.copyOfRange(args, 2, args.length);
		}
		InputStream in;
		OutputStream out;
		Callable<Void> shutdown;
		if (args.length == 3 && args[0].equals("-connect")) {
			Socket bs = new Socket(args[1], Integer.parseInt(args[2]));
			in = bs.getInputStream();
			out = bs.getOutputStream();
			shutdown = () -> {
				bs.close();
				return null;
			};
		} else if (args.length > 1 && args[0].equals("-exec")) {
			File tempDir = null, workDir = cacheDir;
			if (workDir == null) {
				tempDir = File.createTempFile("~oberondir", null);
				tempDir.delete();
				tempDir.mkdirs();
				workDir = tempDir;
			}
			String[] childArgs = Arrays.copyOfRange(args, 1, args.length);
			for (int i = 0; i < childArgs.length; i++) {
				if (childArgs[i].equals("%WORKPATH%"))
					childArgs[i] = "\"" + workDir.getAbsolutePath() + "\"";
			}
			Process proc = Runtime.getRuntime().exec(childArgs, null, workDir);
			new StreamForwarder(proc.getErrorStream(), System.err).start();
			in = proc.getInputStream();
			out = proc.getOutputStream();
			final File tempDir_ = tempDir;
			shutdown = () -> {
				proc.waitFor(120, TimeUnit.SECONDS);
				proc.destroy();
				if (tempDir_ != null) {
					Files.walk(tempDir_.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(f -> {
						if (!f.delete())
							f.deleteOnExit();
					});
					tempDir_.delete();
				}
				return null;
			};
		} else if (args.length >= 2 && args.length <= 3 && args[0].equals("-emulator") && (args.length == 2 || args[2].equals("-writable") || args[2].equals("-autocopy"))) {
			File fileToDelete = null;
			if (args.length == 2 || args[2].equals("-autocopy")) {
				if (args.length == 3 && cacheDir != null) {
					MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
					try (FileInputStream fis = new FileInputStream(args[1])) {
						byte[] buffer = new byte[4096];
						int len;
						while ((len = fis.read(buffer)) != -1)
							sha256.update(buffer, 0, len);
					}
					String hash = OberonFile.toHex(sha256.digest());
					File tmpDisk = new File(cacheDir, hash + ".dsk");
					if (!tmpDisk.exists())
						Files.copy(new File(args[1]).toPath(), tmpDisk.toPath());
					args[1] = tmpDisk.getAbsolutePath();
				} else {
					File tmpDisk = File.createTempFile("~oberon", null);
					tmpDisk.delete();
					Files.copy(new File(args[1]).toPath(), tmpDisk.toPath());
					fileToDelete = tmpDisk;
					args[1] = tmpDisk.getAbsolutePath();
				}
			}
			int memSize = Memory.MemSize * 16; // 16 MB
			int displayStart = 0xFCE00000;
			ServerSocket dynamicSocket = new ServerSocket(0);
			MemoryMappedIO mmio = new MemoryMappedIO(args[1], dynamicSocket, null, null, null, null, null);
			ImageMemory imgmem = new ImageMemory(128, new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB), displayStart >>> 2);
			Memory mem = new Memory(imgmem, Disk.loadBootloader(Main.class.getResourceAsStream("/boot.rom")), mmio, true, memSize, displayStart, 0xfff00000, false);
			CPU cpu = new CPU(mem, true);
			cpu.start();
			Socket s = new Socket("localhost", dynamicSocket.getLocalPort());
			in = s.getInputStream();
			out = s.getOutputStream();
			embeddedMode = true;
			final File fileToDelete_ = fileToDelete;
			shutdown = () -> {
				cpu.dispose();
				s.close();
				if (fileToDelete_ != null && !fileToDelete_.delete()) {
					fileToDelete_.deleteOnExit();
				}
				return null;
			};
		} else {
			System.err.println("Usage: java -jar LSPServer.jar [-cache <dir>] -connect <host> <port>");
			System.err.println("       java -jar LSPServer.jar [-cache <dir>] -exec <command>");
			System.err.println("       java -jar LSPServer.jar [-cache <dir>] -emulator <imagefile> [-writable|-autocopy]");
			return;
		}
		Bridge bridge = new Bridge(new DataOutputStream(out), new DataInputStream(in), shutdown);
		if (embeddedMode)
			bridge.switchEmbeddedMode();
		Server server = cacheDir == null ? new Server(bridge, debug) : new CachingServer(cacheDir, bridge, debug);
		Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, stdin, stdout);
		server.connect(launcher.getRemoteProxy());
		launcher.startListening();
	}
}
