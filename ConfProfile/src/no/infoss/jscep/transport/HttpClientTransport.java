package no.infoss.jscep.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;

import no.infoss.confprofile.util.HttpUtils;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.bouncycastle.asn1.util.ASN1Dump;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.util.encoders.Base64;
import org.jscep.transport.AbstractTransport;
import org.jscep.transport.TransportException;
import org.jscep.transport.TransportFactory.Method;
import org.jscep.transport.request.Operation;
import org.jscep.transport.request.Request;
import org.jscep.transport.response.ScepResponseHandler;

import android.util.Log;

public class HttpClientTransport extends AbstractTransport {
	public static final String TAG = HttpClientTransport.class.getSimpleName();
	
	private final Method mMethod;
	private final String mUserAgent;
	
	protected HttpClientTransport(URL url, Method method, String userAgent) {
		super(url);
		mMethod = method;
		mUserAgent = userAgent;
	}

	@Override
	public <T> T sendRequest(Request request, ScepResponseHandler<T> handler)
			throws TransportException {
		T result = null;
		
		switch (mMethod) {
		case GET: {
			result = getRequest(request, handler);
			break;
		}
		case POST: {
			result = postRequest(request, handler);
			break;
		}
		default: {
			Log.e(TAG, "Unknown method ".concat(String.valueOf(mMethod)));
			result = null;
			break;
		}
		}
		
		return result;
	}
	
	private <T> T postRequest(Request msg, ScepResponseHandler<T> handler)
			throws TransportException {
		URL url = getUrl(msg.getOperation());
		HttpPost request;
	    try {
	    	request = new HttpPost(url.toURI());
	    	request.setHeader("Content-Type", "application/octet-stream");
	    	request.setHeader("User-Agent", mUserAgent);
	    } catch (URISyntaxException e) {
	        throw new TransportException(e);
	    }

	    byte[] message;
	    try {
	        message = Base64.decode(msg.getMessage().getBytes(Charsets.US_ASCII.name()));
	        try {
				CMSSignedData testData = new CMSSignedData(message);
				Log.d(TAG, ASN1Dump.dumpAsString(testData.toASN1Structure()));
			} catch (CMSException e) {
				Log.e(TAG, "CMSSignedData error", e);
			}
	        
	    } catch (UnsupportedEncodingException e) {
	        throw new RuntimeException(e);
	    }

	    request.setEntity(new ByteArrayEntity(message));

	    HttpResponse response = null;
	    try {
	    	response = HttpUtils.getClientForURL(url).execute(request);
	        int responseCode = response.getStatusLine().getStatusCode();
	        String responseMessage = response.getStatusLine().getReasonPhrase();

	        String logMsg = String.format("Received '%d %s' when sending %s to %s", 
	        		responseCode, 
	        		responseMessage, 
	        		msg.toString(), 
	        		url.toString());
	        Log.d(TAG, logMsg);
	        
	        if (responseCode != HttpStatus.SC_OK) {
	            throw new TransportException(responseCode + " "
	                    + responseMessage);
	        }
	    } catch (IOException e) {
	        throw new TransportException("Error connecting to server.", e);
	    }

	    String contentType = response.getEntity().getContentType().getValue();
        InputStream inStream = null;
	    byte[] data;
	    try {
	    	inStream = response.getEntity().getContent();
	        data = IOUtils.toByteArray(inStream);
	    } catch (IOException e) {
	        throw new TransportException("Error reading response stream", e);
	    } finally {
        	if(inStream != null) {
        		try {
					inStream.close();
				} catch (IOException e) {
					Log.d(TAG, "Error while closing HTTP response stream", e);
				}
        	}
	    }

	    return handler.getResponse(data, contentType);
	}

	private <T> T getRequest(Request msg, ScepResponseHandler<T> handler)
			throws TransportException {
		URL url = getUrl(msg.getOperation(), msg.getMessage());

		String logMsg = String.format("Sending %s to %s", msg.toString(), url.toString());
        Log.d(TAG, logMsg);

        HttpGet request;
        try {
            request = new HttpGet(url.toURI());
            request.setHeader("User-Agent", mUserAgent);
        } catch (URISyntaxException e) {
            throw new TransportException(e);
        }

        HttpResponse response = null;
        try {
        	response = HttpUtils.getClientForURL(url).execute(request);
            int responseCode = response.getStatusLine().getStatusCode();
            String responseMessage = response.getStatusLine().getReasonPhrase();

            String logMsg2 = String.format("Received '%s %s' when sending %s to %s",
                    responseCode, responseMessage, msg, url);
            Log.d(TAG, logMsg2);
            if (responseCode != HttpStatus.SC_OK) {
                throw new TransportException(responseCode + " "
                        + responseMessage);
            }
        } catch (IOException e) {
            throw new TransportException("Error connecting to server", e);
        }

        String contentType = response.getEntity().getContentType().getValue();
        InputStream inStream = null;
        byte[] data;
        try {
        	inStream = response.getEntity().getContent();
            data = IOUtils.toByteArray(inStream);
        } catch (IOException e) {
            throw new TransportException("Error reading response stream", e);
        } finally {
        	if(inStream != null) {
        		try {
					inStream.close();
				} catch (IOException e) {
					Log.d(TAG, "Error while closing HTTP response stream", e);
				}
        	}
        }

        return handler.getResponse(data, contentType);
	}
	
	private URL getUrl(final Operation op, final String message)
            throws TransportException {
        try {
            return new URL(getUrl(op).toExternalForm() + "&message="
                    + URLEncoder.encode(message, "UTF-8"));
        } catch (MalformedURLException e) {
            throw new TransportException(e);
        } catch (UnsupportedEncodingException e) {
            throw new TransportException(e);
        }
    }
}
