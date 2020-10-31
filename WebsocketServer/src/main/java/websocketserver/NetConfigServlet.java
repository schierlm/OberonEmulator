package websocketserver;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

public class NetConfigServlet extends JSONServlet {

	@Override
	protected String getJSON(HttpServletRequest request) throws IOException {
		String urlSuffix = "://" + request.getServerName() + ":" + request.getServerPort() + "/";
		String url = request.getScheme() + urlSuffix;
		String wsurl = url.replaceFirst("^http", "ws");
		return "{" +
				"\"HostByName\": \"" + url + "net/hostbyname\"," +
				"\"HostByNumber\": \"" + url + "net/hostbynumber\"," +
				"\"UDP\": \"" + wsurl + "net/udp\"," +
				"\"TCP\": \"" + wsurl + "net/tcp\"," +
				"\"Listen\": \"" + wsurl + "net/listen\"" +
				"}";
	}
}
