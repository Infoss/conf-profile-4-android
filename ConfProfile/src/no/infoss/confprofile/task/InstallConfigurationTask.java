package no.infoss.confprofile.task;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.security.auth.x500.X500Principal;

import no.infoss.confprofile.BuildConfig;
import no.infoss.confprofile.R;
import no.infoss.confprofile.crypto.CertificateManager;
import no.infoss.confprofile.crypto.TmpCertificateManager;
import no.infoss.confprofile.format.ConfigurationProfile;
import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.format.Plist;
import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.RootCertPayload;
import no.infoss.confprofile.format.ScepPayload;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.format.json.PlistTypeAdapterFactory;
import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadsCursorLoader;
import no.infoss.confprofile.profile.ProfilesCursorLoader;
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
import org.jscep.transaction.FailInfo;
import org.jscep.transaction.TransactionException;
import org.jscep.transport.response.Capabilities;
import org.xmlpull.v1.XmlPullParserException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

public class InstallConfigurationTask extends AsyncTask<Plist, Void, Integer> {
	public static final String TAG = InstallConfigurationTask.class.getSimpleName();
	
	private static CertSelector ALL_SELECTOR = new CertSelector() {
		
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
	};
	
	private Context mCtx;
	private DbOpenHelper mDbHelper;
	private WeakReference<InstallConfigurationTaskListener> mListener;
	private int mHttpStatusCode = HttpStatus.SC_OK;
	private FailInfo mScepFailInfo = null;
	
	private String mUserAgent;
	private List<Action> mActions = new LinkedList<Action>();
	
	public InstallConfigurationTask(Context ctx, DbOpenHelper dbHelper, InstallConfigurationTaskListener listener) {
		mCtx = ctx;
		mDbHelper = dbHelper;
		mListener = new WeakReference<InstallConfigurationTaskListener>(listener);
		
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
			
			Certificate signCert = null;
			PrivateKey privKey = null;
			
			//BEGIN Phase 2
			Plist resp = null;
			
			try {
				CertificateManager tmpMgr = CertificateManager.getManager(mCtx, CertificateManager.MANAGER_TMP);
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
					ScepStruct scep = doScep((ScepPayload) payload);
					
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
			
			profile = ConfigurationProfile.wrap(resp); 
			if(profile.isPayloadContentEncrypted()) {
				profile.decryptPayloadContent(privKey);
			}
			
			List<ConfigurationProfile> confProfiles = new ArrayList<ConfigurationProfile>(2);
			confProfiles.add(profile);
			
			InstallProfile act = new InstallProfile();
			act.profile = profile;
			mActions.add(act);
			
			while(confProfiles.size() > 0) {
				Log.d(TAG, "confProfiles.size = " + confProfiles.size());
				ConfigurationProfile confProfile = confProfiles.remove(0);
				
				List<Payload> payloads = confProfile.getPayloads();
				for(Payload payload : payloads) {
					if(payload instanceof ScepPayload) {
						ScepStruct scep = doScep((ScepPayload) payload);
						if(!scep.isFailed && !scep.isPending) {
							InstallPrivateKey action = new InstallPrivateKey();
							action.chain = scep.certs;
							action.privateKey = CryptoUtils.getRSAPrivateKey(scep.keyPair);
							action.alias = payload.getPayloadUUID();
							action.certificateMgrId = CertificateManager.MANAGER_INTERNAL;
							mActions.add(action);
						}
					} else if(payload instanceof VpnPayload) {
						Log.d(TAG, payload.toString());
						
						InstallVpn action = new InstallVpn();
						action.payload = (VpnPayload) payload;
						mActions.add(action);
					} else if(payload instanceof RootCertPayload) {
						Log.d(TAG, payload.toString());
						
						InstallCertificate action = new InstallCertificate();
						action.certificate = ((RootCertPayload) payload).getPayloadContent();
						action.alias = payload.getPayloadUUID();
						action.certificateMgrId = CertificateManager.MANAGER_ANDROID_RAW;
						mActions.add(action);
					} else {
						Log.d(TAG, payload.toString());
					}
				}			
			}
			//END Phase 3
			
			//perform some actions in background
			List<Action> actions = new LinkedList<Action>();
			actions.addAll(mActions);
			mActions.clear();
			for(Action action : actions) {
				
				//filter out actions with user interaction
				if(action instanceof InstallCertificate) {
					InstallCertificate instAction = (InstallCertificate) action;
					if(CertificateManager.MANAGER_ANDROID_RAW.equals(instAction.certificateMgrId)) {
						mActions.add(instAction);
						continue;
					}
				}
				
				//TODO: fight with spaghetti again 
				if(action instanceof InstallProfile) {
					InstallProfile instAction = (InstallProfile) action;
					BaseQueryCursorLoader.perform(
							ProfilesCursorLoader.create(mCtx, 0, instAction.asBundle(), mDbHelper));
				} else if(action instanceof InstallCertificate) {
					InstallCertificate instAction = (InstallCertificate) action;
					mgr = CertificateManager.getManager(mCtx, instAction.certificateMgrId);
					if(mgr == null) {
						Log.e(TAG, "Unknown certificate manager ".concat(instAction.certificateMgrId));
						continue;
					}
					
					while(!mgr.isLoaded()) {
						try{
							Thread.sleep(1000);
						} catch(InterruptedException e) {
							//nothing to do here
						}
					}
					
					mgr.putCertificate(instAction.alias, instAction.certificate);
					mgr.store();
				} else if(action instanceof InstallPrivateKey) {
					InstallPrivateKey instAction = (InstallPrivateKey) action;
					mgr = CertificateManager.getManager(mCtx, instAction.certificateMgrId);
					if(mgr == null) {
						Log.e(TAG, "Unknown certificate manager ".concat(instAction.certificateMgrId));
						continue;
					}
					
					while(!mgr.isLoaded()) {
						try{
							Thread.sleep(1000);
						} catch(InterruptedException e) {
							//nothing to do here
						}
					}
					
					mgr.putKey(instAction.alias, instAction.privateKey, null, instAction.chain);
					mgr.store();
				} else if(action instanceof InstallVpn) {
					InstallVpn instAction = (InstallVpn) action;
					BaseQueryCursorLoader.perform(
							PayloadsCursorLoader.create(mCtx, 0, instAction.asBundle(), mDbHelper));
				}
			}
			actions.clear();
			
		} catch(Exception e) {
			Log.e(TAG, "", e);
			return TaskError.INTERNAL;
		}
		
		return TaskError.SUCCESS;
	}
	
	@Override
	protected void onPostExecute(Integer result) {
		InstallConfigurationTaskListener listener = mListener.get();
		if(listener != null) {
			if(result == TaskError.SUCCESS) {
				listener.onInstallConfigurationComplete(this, mActions);
			} else {
				listener.onInstallConfigurationFailed(this, result);
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
	
	private CMSSignedData signData(byte[] data, Certificate signCert, PrivateKey privKey) 
			throws CertificateEncodingException, 
				   OperatorCreationException, 
				   CMSException {
		List<Certificate> certList = new ArrayList<Certificate>();
	    CMSTypedData msg = new CMSProcessableByteArray(data);
	    
	    certList.add(signCert);

	    Store certs = new JcaCertStore(certList);

	    CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
	    ContentSigner sha1Signer = new JcaContentSignerBuilder("SHA1withRSA").setProvider("BC").build(privKey);

	    gen.addSignerInfoGenerator(
	    		new JcaSignerInfoGeneratorBuilder(
	    				new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
	    		.build(sha1Signer, (X509Certificate)signCert));

	    gen.addCertificates(certs);
	    return gen.generate(msg, true);
	}
	
	private ScepStruct doScep(ScepPayload scepPayload) 
			throws NoSuchAlgorithmException, 
				   IOException, 
				   OperatorCreationException, 
				   CertificateException, 
				   InvalidKeySpecException, 
				   ClientException, 
				   TransactionException, 
				   CertStoreException, 
				   KeyStoreException {
		ScepStruct result = new ScepStruct();
		//prepare SCEP
		//TODO: be less optimistic when verify a certificate
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
		
		result.keyPair = entityKeypair;
		
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
		
		int retryCycles = 6;
		while(enrollment.isPending() && retryCycles > 0) {
			retryCycles--;
			
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
		
		result.isFailed = enrollment.isFailure();
		result.isPending = enrollment.isPending();
		
		if(result.isFailed) {
			result.scepFailInfo = enrollment.getFailInfo();
		}
		
		if(result.isFailed || result.isPending) {
			return result;
		}
		
		Collection<? extends Certificate> certs = enrollment.getCertStore().getCertificates(ALL_SELECTOR);
		result.certs = certs.toArray(new Certificate[certs.size()]);
		
		return result;
	}
	
	public interface InstallConfigurationTaskListener {
		public void onInstallConfigurationFailed(InstallConfigurationTask task, int taskErrorCode);
		public void onInstallConfigurationComplete(InstallConfigurationTask task, List<Action> actions);
	}
	
	private static class ScepStruct {
		public boolean isPending;
		public boolean isFailed;
		public FailInfo scepFailInfo;
		
		public AsymmetricCipherKeyPair keyPair;
		public Certificate[] certs;
	}
	
	public abstract static class Action {
		public abstract Bundle asBundle();
	}
	
	public static class InstallCertificate extends Action {
		public Certificate certificate;
		public String alias;
		public String certificateMgrId;
		
		@Override
		public Bundle asBundle() {
			return null;
		}
	}
	
	public static class InstallPrivateKey extends InstallCertificate {
		public PrivateKey privateKey;
		public Certificate[] chain;
		
		@Override
		public Bundle asBundle() {
			return null;
		}
	}
	
	public static class InstallProfile extends Action {
		public ConfigurationProfile profile;
		
		@Override
		public Bundle asBundle() {
			Bundle bundle = new Bundle();
			bundle.putInt(BaseQueryCursorLoader.STMT_TYPE, BaseQueryCursorLoader.STMT_INSERT);
			bundle.putString(ProfilesCursorLoader.P_ID, profile.getPayloadIdentifier());
			bundle.putString(ProfilesCursorLoader.P_NAME, profile.getPayloadDisplayName());
			
			GsonBuilder gsonBuilder = new GsonBuilder();
			gsonBuilder.registerTypeAdapterFactory(new PlistTypeAdapterFactory());
			Gson gson = gsonBuilder.create();
			
			bundle.putString(ProfilesCursorLoader.P_DATA, gson.toJson(profile));
			return bundle;
		}
	}
	
	public static class InstallVpn extends Action {
		public String profileId;
		public VpnPayload payload;
		
		@Override
		public Bundle asBundle() {
			Bundle bundle = new Bundle();
			bundle.putInt(BaseQueryCursorLoader.STMT_TYPE, BaseQueryCursorLoader.STMT_INSERT);
			bundle.putString(PayloadsCursorLoader.P_PROFILE_ID, profileId);
			bundle.putString(PayloadsCursorLoader.P_PAYLOAD_UUID, payload.getPayloadUUID());
			
			GsonBuilder gsonBuilder = new GsonBuilder();
			gsonBuilder.registerTypeAdapterFactory(new PlistTypeAdapterFactory());
			Gson gson = gsonBuilder.create();
			
			bundle.putString(PayloadsCursorLoader.P_DATA, gson.toJson(payload));
			return bundle;
		}
	}
}
