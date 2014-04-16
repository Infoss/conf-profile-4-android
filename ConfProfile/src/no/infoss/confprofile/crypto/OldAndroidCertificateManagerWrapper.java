package no.infoss.confprofile.crypto;

import java.security.Key;
import java.security.cert.Certificate;
import java.util.Hashtable;

public class OldAndroidCertificateManagerWrapper extends CertificateManager {
	public static final OldAndroidCertificateManagerWrapper SYSTEM = 
			new OldAndroidCertificateManagerWrapper(MANAGER_ANDROID_SYSTEM);
	
	public static final OldAndroidCertificateManagerWrapper USER = 
			new OldAndroidCertificateManagerWrapper(MANAGER_ANDROID_USER);
	
	private String mType;
	private OldAndroidCertificateManager mWrapped;
	
	private OldAndroidCertificateManagerWrapper(String type) {
		mType = type;
		mWrapped = OldAndroidCertificateManager.getInstance();
	}
	
	@Override
	protected void doLoad() {
		mWrapped.load();
		mIsLoaded = true;
	}
	
	@Override
	protected void doReload() {
		mWrapped.reload();
		mIsLoaded = true;
	}

	@Override
	public Hashtable<String, Certificate> getCertificates() {
		if(MANAGER_ANDROID_SYSTEM.equals(mType)) {
			return mWrapped.getSystemCACertificates();
		} else if(MANAGER_ANDROID_USER.equals(mType)) {
			return mWrapped.getUserCACertificates();
		}
		return null;
	}

	@Override
	public Hashtable<String, Key> getKeys() {
		throw new UnsupportedOperationException();
	}
	
}
