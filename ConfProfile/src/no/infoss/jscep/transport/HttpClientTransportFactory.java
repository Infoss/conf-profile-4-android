package no.infoss.jscep.transport;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.jscep.transport.Transport;
import org.jscep.transport.TransportFactory;

import android.content.Context;

public class HttpClientTransportFactory implements TransportFactory {
	public static final String TAG = HttpClientTransportFactory.class.getSimpleName();

	private Context mCtx;
	private Map<String, String> mAdditionalHeaders;

	@Deprecated
	public HttpClientTransportFactory(Context ctx, String userAgent) {
		this(ctx, (Map<String, String>) null);
		mAdditionalHeaders.put("User-Agent", userAgent);
	}
	
	public HttpClientTransportFactory(Context ctx, Map<String, String> additionalHeaders) {
		mCtx = ctx.getApplicationContext();
		mAdditionalHeaders = new HashMap<String, String>();
		if(additionalHeaders != null) {
			mAdditionalHeaders.putAll(additionalHeaders);
		}
	}
	
	@Override
	public Transport forMethod(Method method, URL url) {		
		return new HttpClientTransport(mCtx, url, method, mAdditionalHeaders);
	}

}
