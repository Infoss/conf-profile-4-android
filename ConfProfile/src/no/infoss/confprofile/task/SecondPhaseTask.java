package no.infoss.confprofile.task;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.security.Key;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.infoss.confprofile.R;
import no.infoss.confprofile.crypto.AppCertificateManager;
import no.infoss.confprofile.crypto.CertificateManager;
import no.infoss.confprofile.format.Plist;
import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.util.HttpUtils;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.bouncycastle.asn1.util.ASN1Dump;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Xml;

public class SecondPhaseTask extends AsyncTask<Plist, Void, Integer> {
	public static final String TAG = SecondPhaseTask.class.getSimpleName();
	
	private Context mCtx;
	private WeakReference<SecondPhaseTaskListener> mListener;
	private int mHttpStatusCode = HttpStatus.SC_OK;
	
	private Plist mPlist;
	
	public SecondPhaseTask(Context ctx, SecondPhaseTaskListener listener) {
		mCtx = ctx;
		mListener = new WeakReference<SecondPhaseTaskListener>(listener);
		mPlist = null;
	}
	
	@Override
	protected Integer doInBackground(Plist... params) {
		Plist plist = params[0];
		
		//TODO: make smth with spaghetti
		java.util.logging.Logger.getLogger("org.apache.http.wire").setLevel(java.util.logging.Level.FINEST);
		java.util.logging.Logger.getLogger("org.apache.http.headers").setLevel(java.util.logging.Level.FINEST);

		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
		System.setProperty("org.apache.commons.logging.simplelog.log.httpclient.wire", "debug");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "debug");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.headers", "debug");
		
		String userAgent = mCtx.getString(R.string.idevice_ua);
		
		TelephonyManager telMgr = (TelephonyManager) mCtx.getSystemService(Context.TELEPHONY_SERVICE);
		WifiManager wifiManager = (WifiManager) mCtx.getSystemService(Context.WIFI_SERVICE);
		
		Map<String, Object> response = new HashMap<String, Object>();
		
		Dictionary content = plist.getDictionary(Plist.KEY_PAYLOAD_CONTENT);
		
		String url = content.getString("URL");
		//NOTE: Server-side accepts Challenge key (not value!) in uppercase only.
		response.put("Challenge".toUpperCase(), content.get("Challenge"));
		
		Array arr = content.getArray("DeviceAttributes");
		
		
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
		
		CMSSignedData sigData = null;
		
		//serialize to xml
		XmlSerializer serializer = Xml.newSerializer();
		try {
			CertificateManager mgr = CertificateManager.getManager(mCtx, CertificateManager.MANAGER_INTERNAL);
			while(!mgr.isLoaded()) {
				try{
					Thread.sleep(1000);
				} catch(InterruptedException e) {
					//nothing to do here
				}
			}
			Log.d(TAG, "Certificate manager successfully loaded");
			
			ByteArrayOutputStream xmlOs = new ByteArrayOutputStream(8192);
			
			serializer.setOutput(xmlOs, "UTF-8");
			serializer.startDocument("UTF-8", null);
			serializer.docdecl(Plist.DOCDECL);
			serializer.startTag(null, "plist");
			serializer.attribute(null, "version", "1.0");
			
			Dictionary dict = Dictionary.wrap(response);
			dict.writeXml(serializer);
			
			serializer.endTag(null, "plist");
			
			serializer.endDocument();
			serializer.flush();
			
			Log.d(TAG, new String(xmlOs.toByteArray(), "UTF-8"));
			
			List certList = new ArrayList();
		    CMSTypedData msg = new CMSProcessableByteArray(xmlOs.toByteArray());
		    Certificate signCert = mgr.getCertificates().get(AppCertificateManager.DEFAULT_CERT_ALIAS);
		    Key privKey = mgr.getKeys().get(AppCertificateManager.DEFAULT_KEY_ALIAS);
		    certList.add(signCert);

		    Store certs = new JcaCertStore(certList);

		    CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
		    ContentSigner sha1Signer = new JcaContentSignerBuilder("SHA1withRSA").setProvider("BC").build((PrivateKey) privKey);

		    gen.addSignerInfoGenerator(
		    		new JcaSignerInfoGeneratorBuilder(
		    				new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
		    		.build(sha1Signer, (X509Certificate)signCert));

		    gen.addCertificates(certs);
		    sigData = gen.generate(msg, true);
		    Log.d(TAG, ASN1Dump.dumpAsString(sigData.toASN1Structure()));
		    
		} catch(Exception e) {
			Log.e(TAG, "Error while serializing xml", e);
			return TaskError.INTERNAL;
		}
		
		try {
			URI uri = new URI(url);
			HttpClient client = null;
			
			if("http".equals(uri.getScheme())) {
				client = HttpUtils.getHttpClient();
			} else if("https".equals(uri.getScheme())) {
				client = HttpUtils.getHttpsClient();
			}
			
			HttpPost postRequest = new HttpPost(uri.toString());
			postRequest.setHeader("User-Agent", userAgent);
			
			FileOutputStream fos = new FileOutputStream("/mnt/sdcard/" + System.currentTimeMillis() + ".plist.mobileconfig");
			fos.write(sigData.toASN1Structure().getEncoded("DER"));
			fos.flush();
			fos.close();
			
			postRequest.setEntity(new ByteArrayEntity(sigData.toASN1Structure().getEncoded("DER")));
			InputStream stream = null;
		
		
			HttpResponse resp = client.execute(postRequest);
			mHttpStatusCode = resp.getStatusLine().getStatusCode();
			if(mHttpStatusCode != HttpStatus.SC_OK) {
				stream = resp.getEntity().getContent();
				byte buff[] = new byte[1024];
				while(stream.read(buff) != -1) {
					//just empty a stream
				}
				stream.close();
				return TaskError.HTTP_FAILED;
			}
			
			stream = resp.getEntity().getContent();
			sigData = new CMSSignedData(stream);
			mPlist = new Plist(sigData);
			Log.d(TAG, mPlist.toString());
		} catch(Exception e) {
			Log.e(TAG, "", e);
			return TaskError.INTERNAL;
		}
		
		return TaskError.SUCCESS;
	}

	public interface SecondPhaseTaskListener {
		public void onSecondPhaseFailed(SecondPhaseTask task, int taskErrorCode);
		public void onSecondPhaseComplete(SecondPhaseTask task);
	}
}
