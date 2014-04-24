/*
 * This file is part of Profile provisioning for Android
 * Copyright (C) 2014  Infoss AS, https://infoss.no, info@infoss.no
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

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

/**
 * Wrapper for AndroidCertificateManager instance for separating access to built-in and user certificates.
 * @author Dmitry Vorobiev
 *
 */
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
