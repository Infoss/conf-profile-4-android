package no.infoss.jscep.transport;

import java.net.URL;

import org.jscep.transport.Transport;
import org.jscep.transport.TransportFactory;

import android.content.Context;

public class HttpClientTransportFactory implements TransportFactory {
	public static final String TAG = HttpClientTransportFactory.class.getSimpleName();

	private Context mCtx;
	private String mUserAgent;

	public HttpClientTransportFactory(Context ctx, String userAgent) {
		mCtx = ctx.getApplicationContext();
		mUserAgent = userAgent;
	}
	
	@Override
	public Transport forMethod(Method method, URL url) {		
		return new HttpClientTransport(mCtx, url, method, mUserAgent);
	}

}
