package no.infoss.jscep.transport;

import java.net.URL;

import org.jscep.transport.Transport;
import org.jscep.transport.TransportFactory;

public class HttpClientTransportFactory implements TransportFactory {
	public static final String TAG = HttpClientTransportFactory.class.getSimpleName();
	
	private String mUserAgent;

	public HttpClientTransportFactory(String userAgent) {
		mUserAgent = userAgent;
	}
	
	@Override
	public Transport forMethod(Method method, URL url) {		
		return new HttpClientTransport(url, method, mUserAgent);
	}

}
