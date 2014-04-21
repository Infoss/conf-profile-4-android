package no.infoss.confprofile.crypto;

import java.io.IOException;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bouncycastle.operator.OperatorCreationException;

import android.content.Context;

public class AndroidCertificateManagerWrapper extends CertificateManager {	
	private String mType;
	private CertificateManager mWrapped;
	
	protected AndroidCertificateManagerWrapper(Context ctx, String type) {
		mType = type;
		mWrapped = getManager(ctx, MANAGER_ANDROID_RAW);
	}
	
	@Override
	public boolean isLoaded() {
		return mWrapped.isLoaded();
	}
	
	@Override
	public long getUpdateRequestedTime() {
		return mWrapped.getUpdateRequestedTime();
	}
	
	@Override
	public long getUpdateProcessedTime() {
		return mWrapped.getUpdateProcessedTime();
	}
	
	@Override
	protected void doLoad() 
			throws UnrecoverableKeyException, 
				   KeyStoreException, 
				   NoSuchProviderException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   OperatorCreationException, 
				   InvalidKeySpecException, 
				   IOException {
		mWrapped.doLoad();
	}
	
	@Override
	protected void doReload() 
			throws UnrecoverableKeyException, 
				   KeyStoreException, 
				   NoSuchProviderException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   OperatorCreationException, 
				   InvalidKeySpecException, 
				   IOException {
		mWrapped.doReload();
	}

	@Override
	public Map<String, Certificate> getCertificates() {
		Map<String, Certificate> result = mWrapped.getCertificates();
		List<String> keylistToRemove = new ArrayList<String>(result.keySet());
		if(MANAGER_ANDROID_SYSTEM.equals(mType)) {
			for(String key : result.keySet()) {
				if(key.startsWith("system:")) {
					keylistToRemove.remove(key);
				}
			}
		} else if(MANAGER_ANDROID_USER.equals(mType)) {
			for(String key : result.keySet()) {
				if(key.startsWith("user:")) {
					keylistToRemove.remove(key);
				}
			}
		}
		
		for(String key : keylistToRemove) {
			result.remove(key);
		}
		
		return result;
	}

	@Override
	public Map<String, Key> getKeys() {
		Map<String, Key> result = mWrapped.getKeys();
		List<String> keylistToRemove = new ArrayList<String>(result.keySet());
		if(MANAGER_ANDROID_SYSTEM.equals(mType)) {
			for(String key : result.keySet()) {
				if(key.startsWith("system:")) {
					keylistToRemove.remove(key);
				}
			}
		} else if(MANAGER_ANDROID_USER.equals(mType)) {
			for(String key : result.keySet()) {
				if(key.startsWith("user:")) {
					keylistToRemove.remove(key);
				}
			}
		}
		
		for(String key : keylistToRemove) {
			result.remove(key);
		}
		
		return result;
	}
}
