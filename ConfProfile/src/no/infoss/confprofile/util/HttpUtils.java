package no.infoss.confprofile.util;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

public class HttpUtils {
	public static HttpClient getHttpClient() {
		return new DefaultHttpClient();
	}
	
	public static HttpClient getHttpsClient() {
		//TODO: implement this
		return null;
	}
}
