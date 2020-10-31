package websocketserver;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.servlet.http.HttpServletRequest;

public class HostByNameServlet extends JSONServlet {

	@Override
	protected String getJSON(HttpServletRequest request) throws IOException {
		String host = request.getParameter("v");
		int err, val;
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
			err = 0;
			val = intFromAddress(addr);
		} catch (UnknownHostException ex) {
			err = 3601;
			val = 0;
		}
		return "{\"ints\": [0, " + err + "," + val + "]}";
	}
}
