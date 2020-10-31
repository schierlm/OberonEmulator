package websocketserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class JSONServlet extends HttpServlet {

	public static int intFromAddress(InetAddress addr) throws UnknownHostException {
		if (addr.getAddress().length != 4)
			throw new UnknownHostException();
		return ByteBuffer.wrap(addr.getAddress()).order(ByteOrder.BIG_ENDIAN).asIntBuffer().get();
	}

	public static InetAddress addressFromIntValue(int intValue) throws UnknownHostException {
		byte[] adr = new byte[4];
		ByteBuffer.wrap(adr).order(ByteOrder.BIG_ENDIAN).asIntBuffer().put(intValue);
		return InetAddress.getByAddress(adr);
	}

	@Override
	protected final void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().println(getJSON(request));
	}

	protected abstract String getJSON(HttpServletRequest request) throws IOException;
}
