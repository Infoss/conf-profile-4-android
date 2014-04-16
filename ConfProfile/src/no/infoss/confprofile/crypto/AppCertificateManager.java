package no.infoss.confprofile.crypto;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import no.infoss.confprofile.util.CryptoUtils;

import org.bouncycastle.asn1.x509.TBSCertificate;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

//TODO: switch to BouncyCastle as far as we using it
public class AppCertificateManager extends CertificateManager {
	public static final String TAG = AppCertificateManager.class.getSimpleName();
	public static final String DEFAULT_KEY_ALIAS = "defkey:00000000-0000-0000-0000-000000000000";
	public static final String DEFAULT_CERT_ALIAS = "defcert:00000000-0000-0000-0000-000000000000";
	private static final String PREF_PASSWORD = "AppCertificateManager_password";
	private static final String STORAGE = "storage.jks";
	private Context mContext;
	private HashMap<String, Certificate> mCerts;
	private HashMap<String, Key> mKeys;

	protected AppCertificateManager(Context context) {
		mContext = context.getApplicationContext();
		mCerts = new HashMap<String, Certificate>();
		mKeys  = new HashMap<String, Key>();
		
		if(Security.getProvider("BC") == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}
	
	@Override
	protected void doLoad() 
			throws KeyStoreException, 
				   NoSuchProviderException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   OperatorCreationException, 
				   InvalidKeySpecException, 
				   UnrecoverableKeyException,
				   IOException {		
		char[] password = getPassword().toCharArray();
		KeyStore keyStore = KeyStore.getInstance("BKS", "BC");
		InputStream is = null;
		try {
			is = mContext.openFileInput(STORAGE);
		} catch(FileNotFoundException e) {
			Log.d(TAG, "Cert storage doesn't exist", e);
			createEmpty(keyStore, password);
		}
		keyStore.load(is, password);
		genDummyCertIfNotExist(keyStore, password);
		
		//TODO: thread safety
		fetch(keyStore, password);
	}
	
	@Override
	protected void doReload() 
			throws KeyStoreException, 
				   NoSuchProviderException, 
				   UnrecoverableKeyException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   IOException {
		char[] password = getPassword().toCharArray();
		KeyStore keyStore = KeyStore.getInstance("BKS", "BC");
		InputStream is = mContext.openFileInput(STORAGE);
		keyStore.load(is, password);
		
		//TODO: thread safety
		fetch(keyStore, password);
	}
	
	private void createEmpty(KeyStore keyStore, char[] password) 
			throws NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException, OperatorCreationException, InvalidKeySpecException {
		keyStore.load(null, password);
		
		OutputStream stream = mContext.openFileOutput(STORAGE, Context.MODE_PRIVATE);
		try {
			keyStore.store(stream, password);
		} finally {
			stream.flush();
			stream.close();
		}
		
	}
	
	private void genDummyCertIfNotExist(KeyStore keyStore, char[] password) 
			throws CertificateEncodingException, 
				   KeyStoreException, 
				   NoSuchAlgorithmException, 
				   OperatorCreationException, 
				   CertificateException, 
				   InvalidKeySpecException, 
				   FileNotFoundException, 
				   IOException {
		if(!keyStore.containsAlias(DEFAULT_KEY_ALIAS)) {
			AsymmetricCipherKeyPair keypair = CryptoUtils.genBCRSAKeypair(2048);
			TBSCertificate tbs = CryptoUtils.createBCTBSCert(null, "DN=dummy Android cert", keypair.getPublic(), "SHA1WithRSAEncryption");
			Certificate cert = CryptoUtils.signCert(tbs, keypair.getPrivate());
			
			keyStore.setCertificateEntry(DEFAULT_CERT_ALIAS, cert);
			keyStore.setKeyEntry(DEFAULT_KEY_ALIAS, CryptoUtils.getRSAPrivateKey(keypair), password, new Certificate[] {cert});
			
			OutputStream stream = mContext.openFileOutput(STORAGE, Context.MODE_PRIVATE);
			try {
				keyStore.store(stream, password);
			} finally {
				stream.flush();
				stream.close();
			}
		}
	}
	
	private String getPassword() {
		SharedPreferences mgr = mContext.getSharedPreferences("confprofile.pref", Context.MODE_PRIVATE);
		String password = null;
		if(mgr.contains(PREF_PASSWORD)) {
			password = mgr.getString(PREF_PASSWORD, null);
		}
		
		if(password == null) {
			password = CryptoUtils.getRandomAlphanumericString(32);
			
			Editor editor = mgr.edit();
			editor.putString(PREF_PASSWORD, password);
			editor.commit();
		}
		return password;
	}
	
	private void fetch(KeyStore store, char[] password) 
			throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
		HashMap<String, Certificate> certs = new HashMap<String, Certificate>();
		HashMap<String, Key> keys = new HashMap<String, Key>();

		Enumeration<String> aliases = store.aliases();
		while(aliases.hasMoreElements()) {
			String alias = aliases.nextElement();
			if(store.isCertificateEntry(alias)) {
				certs.put(alias, store.getCertificate(alias));
			}
			
			if(store.isKeyEntry(alias)) {
				keys.put(alias, store.getKey(alias, password));
			}
		}
		
		HashMap<String, Certificate> oldCerts = mCerts;
		mCerts = certs;
		oldCerts.clear();
		
		HashMap<String, Key> oldKeys = mKeys;
		mKeys = keys;
		oldKeys.clear();
	}

	@Override
	public Map<String, Certificate> getCertificates() {
		return new HashMap<String,Certificate>(mCerts);
	}
	
	@Override
	public Map<String, Key> getKeys() {
		return new HashMap<String,Key>(mKeys);
	}

}
