package no.infoss.confprofile.format;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import android.util.Base64;
import android.util.Log;
import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.format.Plist.Dictionary;

public class RootCertPayload extends Payload { 
	public static final String TAG = RootCertPayload.class.getSimpleName();
	public static final String VALUE_PAYLOAD_TYPE = "com.apple.security.root";

	public RootCertPayload(Dictionary dict) throws ConfigurationProfileException {
		super(dict);
	}
	
	public X509Certificate getPayloadContent() {
		X509Certificate cert = null;
		InputStream is = null;
		try {
			byte[] decoded = Base64.decode((byte[]) super.getPayloadContent(), Base64.DEFAULT);
			is = new ByteArrayInputStream(decoded);
			CertificateFactory factory = CertificateFactory.getInstance("X509");
			cert = (X509Certificate) factory.generateCertificate(is);
		} catch (CertificateException e) {
			Log.e(TAG, "Can't construct certificate from this data", e);
		} catch(IllegalArgumentException e) {
			Log.e(TAG, "Wrong base64 padding", e);
		} finally {
			if(is != null) {
				try {
					is.close();
				} catch(Exception e) {
					//nothing to do here
				}
			}
		}
		return cert;
	}
}
