package no.infoss.confprofile.util;

import java.io.IOException;
import java.net.URL;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;

public class HttpUtils {
	public static HttpClient getHttpClient() {
		return new DefaultHttpClient();
	}
	
	public static HttpClient getHttpsClient() {
		HostnameVerifier verifier = new X509HostnameVerifier() {
			private X509HostnameVerifier mWrapped = SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
			
			@Override
			public void verify(String host, String[] cns, String[] subjectAlts)
					throws SSLException {
				try {
					mWrapped.verify(host, cns, subjectAlts);
				} catch(SSLException e) {
					//verifying FDQN with trailing dot
					if(host.endsWith(".")) {
						mWrapped.verify(host.substring(0, host.length() - 1), cns, subjectAlts);
					} else {
						throw new SSLException(e);
					}
				}	
			}
			
			@Override
			public void verify(String host, X509Certificate cert) throws SSLException {
				try {
					mWrapped.verify(host, cert);
				} catch(SSLException e) {
					//verifying FDQN with trailing dot
					if(host.endsWith(".")) {
						mWrapped.verify(host.substring(0, host.length() - 1), cert);
					} else {
						throw new SSLException(e);
					}
				}
			}
			
			@Override
			public void verify(String host, SSLSocket ssl) throws IOException {
				try {
					mWrapped.verify(host, ssl);
				} catch(SSLException e) {
					//verifying FDQN with trailing dot
					if(host.endsWith(".")) {
						mWrapped.verify(host.substring(0, host.length() - 1), ssl);
					} else {
						throw new SSLException(e);
					}
				}
			}
			
			@Override
			public boolean verify(String host, SSLSession session) {
				if(mWrapped.verify(host, session)) {
					return true;
				}
				
				//verifying FDQN with trailing dot
				if(host.endsWith(".")) {
					return mWrapped.verify(host.substring(0, host.length() - 1), session);
				}
				
				return false;
			}
		};
		SchemeRegistry registry = new SchemeRegistry();
		SSLSocketFactory factory =  SSLSocketFactory.getSocketFactory();
		factory.setHostnameVerifier((X509HostnameVerifier) verifier);
		registry.register(new Scheme("https", factory, 443));
		
		HttpClient client = new DefaultHttpClient();
		client.getConnectionManager().getSchemeRegistry().unregister("https");
		client.getConnectionManager().getSchemeRegistry().register(new Scheme("https", factory, 443));
		
		return client;
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
