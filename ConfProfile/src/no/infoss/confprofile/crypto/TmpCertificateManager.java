package no.infoss.confprofile.crypto;

import java.io.IOException;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import no.infoss.confprofile.util.CryptoUtils;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.operator.OperatorCreationException;

import android.content.Context;

public class TmpCertificateManager extends CertificateManager {
	public static final String DEFAULT_ALIAS = "00000000-0000-0000-0000-000000000000";
	public static final String DEFAULT_KEY_ALIAS = CryptoUtils.makeKeyAlias(DEFAULT_ALIAS);
	private HashMap<String, Key> mKeys = new HashMap<String, Key>();
	private HashMap<String, Certificate> mCerts = new HashMap<String, Certificate>();
	private HashMap<String, Certificate[]> mCertChains = new HashMap<String, Certificate[]>();

	protected TmpCertificateManager(Context ctx) {
		super(ctx);
	}

	@Override
	public Map<String, Certificate> getCertificates() {
		return Collections.unmodifiableMap(mCerts);
	}

	@Override
	public Certificate[] getCertificateChain(String alias)
			throws KeyStoreException {
		if(DEFAULT_KEY_ALIAS.equals(alias)) {
			Certificate[] chain = mCertChains.get(alias);
			if(chain == null || chain.length == 0 || !mKeys.containsKey(DEFAULT_KEY_ALIAS)) {
				try {
					genDummyCert();
				} catch (Exception e) {
					throw new KeyStoreException(e);
				}
			} else {
				boolean regen = false;
				for(Certificate tmpCert : chain) {
					X509Certificate cert = (X509Certificate) tmpCert;
					
					try {
						cert.checkValidity();
					} catch (CertificateExpiredException e) {
						regen = true;
					} catch (CertificateNotYetValidException e) {
						regen = true;
					}
					
					if(regen) {
						break;
					}
				}
				
				if(regen) {
					try {
						genDummyCert();
					} catch(Exception e) {
						throw new KeyStoreException(e);
					}
				}
			}
		}
		
		return mCertChains.get(alias);
	}

	@Override
	public Key getKey(String alias) throws UnrecoverableKeyException,
			KeyStoreException, NoSuchAlgorithmException {
		if(DEFAULT_KEY_ALIAS.equals(alias)) {
			Certificate[] chain = getCertificateChain(alias);
			if(chain == null) {
				return null;
			}
		}
		
		return mKeys.get(alias);
	}
	
	@Override
	public void putCertificate(String alias, Certificate cert) 
			throws KeyStoreException {
		mCerts.put(alias, cert);
	}

	@Override
	protected void doLoad() 
			throws KeyStoreException, NoSuchProviderException,
			NoSuchAlgorithmException, CertificateException,
			OperatorCreationException, InvalidKeySpecException,
			UnrecoverableKeyException, IOException {
		//nothing to do here
	}

	@Override
	protected void doReload() 
			throws KeyStoreException,
				   NoSuchProviderException, 
				   NoSuchAlgorithmException,
				   CertificateException, 
				   OperatorCreationException, 
				   InvalidKeySpecException, 
				   UnrecoverableKeyException, 
				   IOException {
		//nothing to do here
	}

	@Override
	protected void doStore() 
			throws KeyStoreException, 
				   NoSuchProviderException,
				   NoSuchAlgorithmException, 
				   CertificateException,
				   OperatorCreationException, 
				   InvalidKeySpecException, 
				   IOException {
		//nothing to do here

	}
	
	private void genDummyCert() 
			throws NoSuchAlgorithmException, 
				   OperatorCreationException, 
				   CertificateException, 
				   InvalidKeySpecException, 
				   IOException {
		AsymmetricCipherKeyPair keypair = CryptoUtils.genBCRSAKeypair(2048);
		Certificate cert = CryptoUtils.createCert(
				null, 
				"DN=self-signed OCMS Android cert", 
				keypair, 
				"SHA1WithRSAEncryption");
		mKeys.put(DEFAULT_KEY_ALIAS, CryptoUtils.getRSAPrivateKey(keypair));
		mCertChains.put(DEFAULT_KEY_ALIAS, new Certificate[] { cert });
	}

}
