package websocketserver;

import java.io.IOException;
import java.net.UnknownHostException;

import javax.servlet.http.HttpServletRequest;

public class HostByNumberServlet extends JSONServlet {

	@Override
	protected String getJSON(HttpServletRequest request) throws IOException {
		int adr = Integer.parseInt(request.getParameter("v"));
		int err;
		String host;
		try {
			host = addressFromIntValue(adr).getCanonicalHostName();
			err = 0;
		} catch (UnknownHostException ex) {
			err = 3601;
			host = String.format("%d.%d.%d.%d", (adr >> 24) & 0xFF, (adr >> 16) & 0xFF, (adr >> 8) & 0xFF, adr & 0xFF);
		}
		return "{\"ints\": [0, " + err + "],\"so\":3,\"str\":\"" + host.replaceAll("[\\\"\0- ]", " ") + "\"}";
	}
}
