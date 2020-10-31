package websocketserver;

import java.io.File;
import java.net.ServerSocket;
import java.net.SocketException;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.resource.PathResource;

public class HeadlessMain {
	public static void main(String[] args) throws Exception {
		run(init(args));
	}

	public static Server init(String[] args) throws Exception {
		int port = 8088;
		if (args.length > 0 && args[0].matches("[0-9]+")) {
			port = Integer.parseInt(args[0]);
		} else {
			ServerSocket ss;
			try {
				ss = new ServerSocket(port);
			} catch (SocketException ex) {
				ss = new ServerSocket(0);
				port = ss.getLocalPort();
			}
			ss.close();
		}
		Server server = new Server(port);
		ResourceHandler resourceHandler = new ResourceHandler();
		resourceHandler.setDirectoriesListed(false);
		resourceHandler.setBaseResource(new PathResource(new File("offline-emu.html")));
		ServletHandler servletHandler = new ServletHandler();
		servletHandler.addServletWithMapping(NetConfigServlet.class, "/net/config.json");
		servletHandler.addServletWithMapping(HostByNameServlet.class, "/net/hostbyname");
		servletHandler.addServletWithMapping(HostByNumberServlet.class, "/net/hostbynumber");
		servletHandler.addServletWithMapping(TCPServlet.class, "/net/tcp");
		servletHandler.addServletWithMapping(UDPServlet.class, "/net/udp");
		servletHandler.addServletWithMapping(ListenServlet.class, "/net/listen");
		HandlerList handlers = new HandlerList();
		handlers.setHandlers(new Handler[] { resourceHandler, servletHandler, new DefaultHandler() });
		server.setHandler(handlers);
		return server;
	}

	public static void run(Server server) throws Exception {
		int port = ((ServerConnector) server.getConnectors()[0]).getPort();
		System.err.println("Starting server on port " + port);
		System.err.println("URL: http://localhost:" + port + "/");
		System.err.println();
		server.start();
		server.join();
	}
}
