package no.infoss.confprofile.task;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import no.infoss.confprofile.R;
import no.infoss.confprofile.crypto.CertificateManager;
import no.infoss.confprofile.crypto.TmpCertificateManager;
import no.infoss.confprofile.format.ConfigurationProfile;
import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.format.Plist;
import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.ScepPayload;
import no.infoss.confprofile.util.CryptoUtils;
import no.infoss.confprofile.util.HttpUtils;
import no.infoss.confprofile.util.ScepUtils;
import no.infoss.confprofile.util.ScepUtils.ScepStruct;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.operator.OperatorCreationException;
import org.jscep.transaction.FailInfo;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

public class RetrieveConfigurationTask extends AsyncTask<Plist, Void, Integer> {
	public static final String TAG = RetrieveConfigurationTask.class.getSimpleName();
	
	private Context mCtx;
	private WeakReference<RetrieveConfigurationTaskListener> mListener;
	private int mHttpStatusCode = HttpStatus.SC_OK;
	private FailInfo mScepFailInfo = null;
	
	private String mUserAgent;
	private ConfigurationProfile mProfile;
	
	public RetrieveConfigurationTask(Context ctx, RetrieveConfigurationTaskListener listener) {
		mCtx = ctx;
		mListener = new WeakReference<RetrieveConfigurationTaskListener>(listener);
		
		mUserAgent = mCtx.getString(R.string.idevice_ua);
	}
	
	public int getHttpStatusCode() {
		return mHttpStatusCode;
	}
	
	public FailInfo getScepFailInfo() {
		return mScepFailInfo;
	}
	
	@Override
	protected Integer doInBackground(Plist... params) {
		Plist plist = params[0];
		
		try {
			Certificate signCert = null;
			PrivateKey privKey = null;
			
			//BEGIN Phase 2
			Plist resp = null;
			
			try {
				CertificateManager tmpMgr = CertificateManager.getManagerSync(mCtx, CertificateManager.MANAGER_TMP);
				Certificate chain[] = tmpMgr.getCertificateChain(TmpCertificateManager.DEFAULT_KEY_ALIAS);
				signCert = chain[0];
				privKey = (PrivateKey) tmpMgr.getKey(TmpCertificateManager.DEFAULT_KEY_ALIAS);
				resp = submitDeviceAttrs(plist, signCert, privKey);
			} catch(HttpResponseException e) {
				Log.e(TAG, "Submitting device attrs failed", e);
				return TaskError.HTTP_FAILED;
			}
			
			ConfigurationProfile profile = ConfigurationProfile.wrap(resp); 
			if(profile.isPayloadContentEncrypted()) {
				profile.decryptPayloadContent(privKey);
			}
			
			boolean certEnrolled = false;
			for(Payload payload : profile.getPayloads()) {
				if(payload instanceof ScepPayload) {
					ScepStruct scep = ScepUtils.doScep((ScepPayload) payload, mCtx, mUserAgent);
					
					if(scep.isFailed) {
						mScepFailInfo = scep.scepFailInfo;
						return TaskError.SCEP_FAILED;
					} else if(scep.isPending) {
						mScepFailInfo = null;
						return TaskError.SCEP_TIMEOUT;
					}
					
					certEnrolled = true;
					
					//set data for Phase 3
					signCert = scep.certs[0];
					privKey = CryptoUtils.getRSAPrivateKey(scep.keyPair);
				} else {
					Log.w(TAG, "Unexpected payload with type=".concat(payload.getPayloadType()));
				}
			}
			
			if(!certEnrolled) {
				return TaskError.MISSING_SCEP_PAYLOAD;
			}
			// END Phase 2
			
			//BEGIN Phase 3
			try {
				resp = submitDeviceAttrs(plist, signCert, privKey);
			} catch(HttpResponseException e) {
				Log.e(TAG, "Submitting device attrs failed", e);
				return TaskError.HTTP_FAILED;
			}
			
			mProfile = ConfigurationProfile.wrap(resp); 
			if(mProfile.isPayloadContentEncrypted()) {
				mProfile.decryptPayloadContent(privKey);
			}
			//SUSPEND Phase 3, wait for user interaction
		} catch(Exception e) {
			Log.e(TAG, "Unexpected exception", e);
			return TaskError.INTERNAL;
		}
		
		return TaskError.SUCCESS;
	}
	
	@Override
	protected void onPostExecute(Integer result) {
		RetrieveConfigurationTaskListener listener = mListener.get();
		if(listener != null) {
			if(result == TaskError.SUCCESS) {
				listener.onRetrieveConfigurationComplete(this, mProfile);
			} else {
				listener.onRetrieveConfigurationFailed(this, result);
			}
		}
		
		mCtx = null;
	}
	
	private Plist submitDeviceAttrs(Plist request, Certificate signCert, PrivateKey privKey) 
			throws IOException, 
				   CertificateEncodingException, 
				   OperatorCreationException, 
				   CMSException, 
				   XmlPullParserException, 
				   URISyntaxException, 
				   KeyStoreException, 
				   UnrecoverableKeyException, 
				   NoSuchAlgorithmException, 
				   NoSuchPaddingException, 
				   InvalidKeyException, 
				   IllegalBlockSizeException, 
				   BadPaddingException {
		Plist result = null;

		//obtain device data, serialize it to xml and sign
		byte deviceInfoXml[] = obtainDeviceAttrsXml(request);
		
		CMSSignedData sigData = CryptoUtils.signData(deviceInfoXml, signCert, privKey);    
		Log.d(TAG, CryptoUtils.debugCMSSignedData(sigData));
		
		Dictionary content = request.getDictionary(ConfigurationProfile.KEY_PAYLOAD_CONTENT);
		URI uri = new URI(content.getString("URL"));
		HttpClient client = null;
		
		if("http".equals(uri.getScheme())) {
			client = HttpUtils.getHttpClient();
		} else if("https".equals(uri.getScheme())) {
			client = HttpUtils.getHttpsClient();
		}
		
		HttpPost postRequest = new HttpPost(uri.toString());
		postRequest.setHeader("User-Agent", mUserAgent);
		postRequest.setEntity(new ByteArrayEntity(sigData.toASN1Structure().getEncoded("DER")));
		InputStream stream = null;
	
	
		HttpResponse resp = client.execute(postRequest);
		mHttpStatusCode = resp.getStatusLine().getStatusCode();
		try {
			if(mHttpStatusCode != HttpStatus.SC_OK) {
				String exMsg = String.format("Response status code should be 200 (received %d)", mHttpStatusCode);
				throw new HttpResponseException(mHttpStatusCode, exMsg);
			}
			
			stream = resp.getEntity().getContent();
			sigData = new CMSSignedData(stream);			
			result = new Plist(sigData);
		} finally {
			if(stream != null) {
				stream.close();
			}
		}
		
		return result;
	}
	
	private byte[] obtainDeviceAttrsXml(Plist plist) 
			throws IllegalArgumentException, 
				   IllegalStateException, 
				   IOException {
		TelephonyManager telMgr = (TelephonyManager) mCtx.getSystemService(Context.TELEPHONY_SERVICE);
		WifiManager wifiManager = (WifiManager) mCtx.getSystemService(Context.WIFI_SERVICE);
		
		Map<String, Object> response = new HashMap<String, Object>();
		
		Dictionary content = plist.getDictionary(ConfigurationProfile.KEY_PAYLOAD_CONTENT);
		
		//NOTE: Server-side accepts Challenge key (not value!) in uppercase only.
		response.put("Challenge".toUpperCase(), content.get("Challenge"));
		
		//...and put Challenge key as it was requested
		response.put("Challenge", content.get("Challenge"));
		
		Array arr = content.getArray("DeviceAttributes");
		
		//filling requested attributes
		int size = arr.size();
		for(int i = 0; i < size; i++) {
			String val = arr.getString(i);
			if(val != null) {
				if("UDID".equals(val)) {
					response.put("UDID", Build.SERIAL);
				} else if("IMEI".equals(val)) {
					response.put("IMEI", telMgr.getDeviceId());
				} else if("ICCID".equals(val)) {
					response.put("ICCID", telMgr.getSimSerialNumber());
				} else if("VERSION".equals(val)) {
					response.put("VERSION", Build.VERSION.RELEASE);
				} else if("PRODUCT".equals(val)) {
					response.put("PRODUCT", Build.MODEL);
				} else if("DEVICE_NAME".equals(val)) {
					response.put("DEVICE_NAME", Build.DEVICE);
				} else if("MAC_ADDRESS_EN0".equals(val)) {
					WifiInfo wInfo = wifiManager.getConnectionInfo();
					response.put("MAC_ADDRESS_EN0", wInfo.getMacAddress());
				}
			}
		}
		
		Plist resultPlist = new Plist(Dictionary.wrap(response));
		ByteArrayOutputStream xmlOs = new ByteArrayOutputStream(8192);
		resultPlist.writeXml(xmlOs);
		return xmlOs.toByteArray();
	}
	
	public interface RetrieveConfigurationTaskListener {
		public void onRetrieveConfigurationFailed(RetrieveConfigurationTask task, int taskErrorCode);
		public void onRetrieveConfigurationComplete(RetrieveConfigurationTask task, ConfigurationProfile profile);
	}

}
