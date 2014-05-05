package no.infoss.confprofile.task;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertSelector;
import java.security.cert.CertStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.security.auth.x500.X500Principal;

import no.infoss.confprofile.BuildConfig;
import no.infoss.confprofile.R;
import no.infoss.confprofile.crypto.AppCertificateManager;
import no.infoss.confprofile.crypto.CertificateManager;
import no.infoss.confprofile.crypto.TmpCertificateManager;
import no.infoss.confprofile.format.ConfigurationProfile;
import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.format.Plist;
import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.ScepPayload;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.util.CryptoUtils;
import no.infoss.confprofile.util.HttpUtils;
import no.infoss.jscep.transport.HttpClientTransportFactory;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.Store;
import org.jscep.client.Client;
import org.jscep.client.ClientException;
import org.jscep.client.EnrollmentResponse;
import org.jscep.client.verification.OptimisticCertificateVerifier;
import org.jscep.transaction.TransactionException;
import org.jscep.transport.response.Capabilities;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

//TODO: rename to InstallConfigurationTask
public class SecondPhaseTask extends AsyncTask<Plist, Void, Integer> {
	public static final String TAG = SecondPhaseTask.class.getSimpleName();
	
	private Context mCtx;
	private WeakReference<SecondPhaseTaskListener> mListener;
	private int mHttpStatusCode = HttpStatus.SC_OK;
	
	private String mUserAgent;
	
	public SecondPhaseTask(Context ctx, SecondPhaseTaskListener listener) {
		mCtx = ctx;
		mListener = new WeakReference<SecondPhaseTaskListener>(listener);
		
		mUserAgent = mCtx.getString(R.string.idevice_ua);
	}
	
	@Override
	protected Integer doInBackground(Plist... params) {
		Plist plist = params[0];
		//TODO: make smth with spaghetti
		if(BuildConfig.DEBUG) {
			//TODO: remove this
			System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
			
			java.util.logging.Logger.getLogger("org.apache.http.wire").setLevel(java.util.logging.Level.FINEST);
			java.util.logging.Logger.getLogger("org.apache.http.headers").setLevel(java.util.logging.Level.FINEST);
	
			System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
			System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
			System.setProperty("org.apache.commons.logging.simplelog.log.httpclient.wire", "debug");
			System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "debug");
			System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.headers", "debug");
			
		}
		
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
			
			Plist resp = null;
			String uuid = plist.getString(ConfigurationProfile.KEY_PAYLOAD_UUID, null);
			
			try {
				resp = submitDeviceAttrs(plist);
			} catch(HttpResponseException e) {
				Log.e(TAG, "Submitting device attrs failed", e);
				return TaskError.HTTP_FAILED;
			}
			
			List<ConfigurationProfile> confProfiles = new ArrayList<ConfigurationProfile>(2);
			ConfigurationProfile profile = ConfigurationProfile.wrap(resp); 
			if(profile.isPayloadContentEncrypted()) {
				profile.decryptPayloadContent(getKeyForUUID(mgr, uuid));
			}
			confProfiles.add(profile);
			
			while(confProfiles.size() > 0) {
				ConfigurationProfile confProfile = confProfiles.remove(0);
				
				List<Payload> payloads = confProfile.getPayloads();
				for(Payload payload : payloads) {
					if(payload instanceof ScepPayload) {
						doScep((ScepPayload) payload, uuid);
						
						profile = ConfigurationProfile.wrap(submitDeviceAttrs(plist));
						if(profile.isPayloadContentEncrypted()) {
							profile.decryptPayloadContent(getKeyForUUID(mgr, uuid));
						}
						
						confProfiles.add(profile);
					} else if(payload instanceof VpnPayload) {
						Log.d(TAG, payload.toString());
					} else {
						Log.d(TAG, payload.toString());
					}
				}			
			}
		} catch(Exception e) {
			Log.e(TAG, "", e);
			return TaskError.INTERNAL;
		}
		
		return TaskError.SUCCESS;
	}
	
	private PrivateKey getKeyForUUID(CertificateManager mgr, String uuid) 
			throws UnrecoverableKeyException, 
				   KeyStoreException, 
				   NoSuchAlgorithmException {
		if(uuid == null || mgr == null) {
	    	mgr = CertificateManager.getManager(mCtx, CertificateManager.MANAGER_TMP);
	    	return getKeyForUUID(mgr, TmpCertificateManager.DEFAULT_ALIAS);
	    }
	    
	    String keyAlias = CryptoUtils.makeKeyAlias(uuid);
	    Key privKey = mgr.getKey(keyAlias);
	    
	    return (PrivateKey) privKey;
	}
	
	private Certificate[] getCertChainForUUID(CertificateManager mgr, String uuid) throws KeyStoreException {
		if(uuid == null || mgr == null) {
	    	mgr = CertificateManager.getManager(mCtx, CertificateManager.MANAGER_TMP);
	    	return getCertChainForUUID(mgr, TmpCertificateManager.DEFAULT_ALIAS);
	    }
	    
	    String keyAlias = CryptoUtils.makeKeyAlias(uuid);
	    Certificate[] signChain = mgr.getCertificateChain(keyAlias);
	    
	    return signChain;
	}

	private Plist submitDeviceAttrs(Plist request) 
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
		
		//obtain certificates
		String uuid = request.getString(ConfigurationProfile.KEY_PAYLOAD_UUID, null);
	    
		CertificateManager mgr = CertificateManager.getManager(mCtx, CertificateManager.MANAGER_INTERNAL);
	    Key privKey = getKeyForUUID(mgr, uuid);
	    Certificate[] signChain = getCertChainForUUID(mgr, uuid);
	    
	    if(privKey == null || signChain == null || signChain.length == 0) {
	    	mgr = CertificateManager.getManager(mCtx, CertificateManager.MANAGER_TMP);
	    	privKey = getKeyForUUID(mgr, TmpCertificateManager.DEFAULT_ALIAS);
		    signChain = getCertChainForUUID(mgr, TmpCertificateManager.DEFAULT_ALIAS);
	    }
	    
	    Certificate signCert = (signChain == null || signChain.length == 0) ? null : signChain[0];
	    
	    if(signCert == null || privKey == null) {
	    	throw new IOException("Can't load certificate or key for uuid=".concat(uuid));
	    }
	    
	    Log.d(TAG, "submit device attrs with cert issuer=" + 
	    		((X509Certificate)signCert).getIssuerDN().toString() + 
	    		" for subject=" + 
	    		((X509Certificate)signCert).getSubjectDN().toString());
	    
		//obtain device data, serialize it to xml and sign
		byte deviceInfoXml[] = obtainDeviceAttrsXml(request);
		
		CMSSignedData sigData = signData(deviceInfoXml, signCert, privKey);    
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
	
	private CMSSignedData signData(byte[] data, Certificate signCert, Key privKey) 
			throws CertificateEncodingException, 
				   OperatorCreationException, 
				   CMSException {
		List<Certificate> certList = new ArrayList<Certificate>();
	    CMSTypedData msg = new CMSProcessableByteArray(data);
	    
	    certList.add(signCert);

	    Store certs = new JcaCertStore(certList);

	    CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
	    ContentSigner sha1Signer = new JcaContentSignerBuilder("SHA1withRSA").setProvider("BC").build((PrivateKey) privKey);

	    gen.addSignerInfoGenerator(
	    		new JcaSignerInfoGeneratorBuilder(
	    				new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
	    		.build(sha1Signer, (X509Certificate)signCert));

	    gen.addCertificates(certs);
	    return gen.generate(msg, true);
	}
	
	private void doScep(ScepPayload scepPayload, String uuid) 
			throws NoSuchAlgorithmException, 
				   IOException, 
				   OperatorCreationException, 
				   CertificateException, 
				   InvalidKeySpecException, 
				   ClientException, 
				   TransactionException, 
				   CertStoreException, 
				   KeyStoreException {
		//prepare SCEP
		Client scepClient = new Client(new URL(scepPayload.getURL()), new OptimisticCertificateVerifier());
		scepClient.setTransportFactory(new HttpClientTransportFactory(mCtx, mUserAgent));
		Capabilities caps = scepClient.getCaCapabilities();
		String sigAlg = caps.getStrongestSignatureAlgorithm();
		
		//create base certificate for SCEP
		AsymmetricCipherKeyPair requesterKeypair = CryptoUtils.genBCRSAKeypair(scepPayload.getKeysize());
		X509Certificate requesterCert = CryptoUtils.createCert(
				null, 
				scepPayload.getSubject(), 
				requesterKeypair, 
				sigAlg);
		
		//create CSR
		AsymmetricCipherKeyPair entityKeypair = CryptoUtils.genBCRSAKeypair(scepPayload.getKeysize());
		PublicKey entityPubKey = CryptoUtils.getRSAPublicKey(entityKeypair);
		PrivateKey entityPrivKey = CryptoUtils.getRSAPrivateKey(entityKeypair);
		
		X500Principal entitySubject = requesterCert.getSubjectX500Principal(); // use the same subject as the self-signed certificate
		PKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder(entitySubject, entityPubKey); 
		
		DERPrintableString password = new DERPrintableString(scepPayload.getChallenge());
		csrBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_challengePassword, password);
		
		ExtensionsGenerator extGen = new ExtensionsGenerator();
		int keyUsage = CryptoUtils.appleSCEPKeyUsageToBC(scepPayload.getKeyUsage());
		extGen.addExtension(Extension.keyUsage, false, new KeyUsage(keyUsage));
		csrBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());
		
		JcaContentSignerBuilder csrSignerBuilder = new JcaContentSignerBuilder("SHA1withRSA");
		ContentSigner csrSigner = csrSignerBuilder.build(entityPrivKey);
		PKCS10CertificationRequest csr = csrBuilder.build(csrSigner);

		//Enroll certificate via SCEP 
		EnrollmentResponse enrollment = scepClient.enrol(
				(X509Certificate)requesterCert, 
				CryptoUtils.getRSAPrivateKey(requesterKeypair), 
				csr, 
				scepPayload.getName());
		
		while(enrollment.isPending()) {
			//TODO: avoid infinite loop if enrollment is permanently pending
			try {
				Thread.sleep(5000);
			} catch(InterruptedException e) {
				//nothing to do here
			}
			
			enrollment = scepClient.poll(
					(X509Certificate)requesterCert, 
					CryptoUtils.getRSAPrivateKey(requesterKeypair), 
					entitySubject, 
					enrollment.getTransactionId(), 
					scepPayload.getName());
		}
		
		if(enrollment.isFailure()) {
			//TODO: implement correct way of error reporting
			throw new IOException("SCEP failed");
		}
		
		Collection<? extends Certificate> certs = enrollment.getCertStore().getCertificates(new CertSelector() {
			
			@Override
			public boolean match(Certificate cert) {
				return true;
			}
			
			@Override
			public CertSelector clone() {
				try {
					return (CertSelector) super.clone();
				} catch(Exception e) {
					return null;
				}
			}
		});
		
		CertificateManager mgr = CertificateManager.getManager(mCtx, CertificateManager.MANAGER_INTERNAL);
		((AppCertificateManager)mgr).putKey(
				CryptoUtils.makeKeyAlias(uuid), 
				entityPrivKey, 
				null, 
				certs.toArray(new Certificate[certs.size()]));
		mgr.store();
	}
	
	public interface SecondPhaseTaskListener {
		public void onSecondPhaseFailed(SecondPhaseTask task, int taskErrorCode);
		public void onSecondPhaseComplete(SecondPhaseTask task);
	}
}
