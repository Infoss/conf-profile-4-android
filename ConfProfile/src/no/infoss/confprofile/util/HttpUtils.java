package no.infoss.confprofile.util;

import java.net.URL;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

public class HttpUtils {
	public static HttpClient getHttpClient() {
		return new DefaultHttpClient();
	}
	
	public static HttpClient getHttpsClient() {
		//TODO: implement custom SSL socket factory
		return new DefaultHttpClient();
	}
	
	public static HttpClient getClientForURL(URL url) {
		if("http".equals(url.getProtocol())) {
			return HttpUtils.getHttpClient();
		} else if("https".equals(url.getProtocol())) {
			return HttpUtils.getHttpsClient();
		}
		return null;
	}
}
