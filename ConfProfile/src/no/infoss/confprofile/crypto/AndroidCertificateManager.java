package no.infoss.confprofile.crypto;

import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.operator.OperatorCreationException;

import android.content.Context;

public class AndroidCertificateManager extends CertificateManager {
	private Context mContext;
	private HashMap<String, Certificate> mCerts;
	private HashMap<String, Key> mKeys;
	
	protected AndroidCertificateManager(Context context) {
		mContext = context.getApplicationContext();
		mCerts = new HashMap<String, Certificate>();
		mKeys  = new HashMap<String, Key>();
	}

	@Override
	public Map<String, Certificate> getCertificates() {
		return new HashMap<String, Certificate>(mCerts);
	}

	@Override
	public Map<String, Key> getKeys() {
		return new HashMap<String, Key>(mKeys);
	}

	@Override
	protected void doLoad() 
			throws KeyStoreException, 
				   NoSuchProviderException,
				   NoSuchAlgorithmException, 
				   CertificateException,
				   OperatorCreationException, 
				   InvalidKeySpecException, 
				   IOException {
		KeyStore store = KeyStore.getInstance("AndroidCAStore");
		store.load(null, null);
		
		//TODO: thread safety
		fetch(store);
	}

	@Override
	protected void doReload() 
			throws KeyStoreException, 
				   NoSuchProviderException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   OperatorCreationException, 
				   InvalidKeySpecException, 
				   IOException {
		doLoad();
	}
	
	private void fetch(KeyStore store) throws KeyStoreException {
		HashMap<String, Certificate> certs = new HashMap<String, Certificate>();
		HashMap<String, Key> keys = new HashMap<String, Key>();

		Enumeration<String> aliases = store.aliases();
		while(aliases.hasMoreElements()) {
			String alias = aliases.nextElement();
			if(store.isCertificateEntry(alias)) {
				certs.put(alias, store.getCertificate(alias));
			}
			
			if(store.isKeyEntry(alias)) {
				//TODO: make something with per-key passwords
				//keys.put(alias, store.getKey(alias, password));
			}
		}
		
		HashMap<String, Certificate> oldCerts = mCerts;
		mCerts = certs;
		oldCerts.clear();
		
		HashMap<String, Key> oldKeys = mKeys;
		mKeys = keys;
		oldKeys.clear();
	}

}
