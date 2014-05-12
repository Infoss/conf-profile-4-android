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
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import no.infoss.confprofile.util.CryptoUtils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

/**
* CertificateManager used as a KeyStore for this application.
* @author Dmitry Vorobiev
*
*/
public class AppCertificateManager extends CertificateManager {
	public static final String TAG = AppCertificateManager.class.getSimpleName();
	private static final String PREF_PASSWORD = "AppCertificateManager_password";
	private static final String STORAGE = "storage.jks";
	
	private final KeyStore mKeyStore;

	protected AppCertificateManager(Context context) throws KeyStoreException, NoSuchProviderException {
		super(context.getApplicationContext());
		
		if(Security.getProvider("BC") == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
		
		mKeyStore = KeyStore.getInstance("BKS", "BC");
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
		char[] password = getPassword();
		InputStream is = null;
		try {
			is = mContext.openFileInput(STORAGE);
		} catch(FileNotFoundException e) {
			Log.d(TAG, "Cert storage doesn't exist", e);
			createEmpty(password);
		}
		mKeyStore.load(is, password);
	}
	
	@Override
	protected void doReload() 
			throws KeyStoreException, 
				   NoSuchProviderException, 
				   UnrecoverableKeyException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   IOException {
		char[] password = getPassword();
		InputStream is = mContext.openFileInput(STORAGE);
		mKeyStore.load(is, password);
	}
	
	protected void doStore() 
			throws KeyStoreException, 
				   NoSuchProviderException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   OperatorCreationException, 
				   InvalidKeySpecException, 
				   IOException {		
		char[] password = getPassword();
		
		OutputStream os = null;
		try {
			os = mContext.openFileOutput(STORAGE, Context.MODE_PRIVATE);
			mKeyStore.store(os, password);
		} finally {
			if(os != null) {
				os.flush();
				os.close();
			}
		}
		
		InputStream is = null;
		try {
			is = mContext.openFileInput(STORAGE);
			mKeyStore.load(is, password);
		} finally {
			if(is != null) {
				is.close();
			}
		}
		
	}
	
	private void createEmpty(char[] password) 
			throws NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException, OperatorCreationException, InvalidKeySpecException {
		mKeyStore.load(null, password);
		
		OutputStream stream = mContext.openFileOutput(STORAGE, Context.MODE_PRIVATE);
		try {
			mKeyStore.store(stream, password);
		} finally {
			stream.flush();
			stream.close();
		}
		
	}
	
	private char[] getPassword() {
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
		return password.toCharArray();
	}
	
	private char[] getPasswordForKey(String alias) {
		//TODO: improve this
		return getPassword();
	}

	@Override
	public Map<String, Certificate> getCertificates() {
		Map<String, Certificate> certs = new HashMap<String, Certificate>();
		
		try {
			Enumeration<String> aliases = mKeyStore.aliases(); 
			while(aliases.hasMoreElements()) {
				String alias = aliases.nextElement();
				if(mKeyStore.isCertificateEntry(alias)) {
					certs.put(alias, mKeyStore.getCertificate(alias));
				}
			}
		} catch (KeyStoreException e) {
			Log.e(TAG, "Error while fetching certs", e);
		}
		
		return Collections.unmodifiableMap(certs);
	}
	
	@Override
	public Key getKey(String alias) 
			throws UnrecoverableKeyException, 
				   KeyStoreException, 
				   NoSuchAlgorithmException {
		return mKeyStore.getKey(alias, getPasswordForKey(alias));
	}
	
	@Override
	public Certificate[] getCertificateChain(String alias) throws KeyStoreException {
		return mKeyStore.getCertificateChain(alias);
	}
	
	@Override
	public void putCertificate(String alias, Certificate cert) 
			throws KeyStoreException {
		mKeyStore.setCertificateEntry(alias, cert);
	}
	
	@Override
	public void putKey(String alias, Key key, char[] password, Certificate[] certChain) 
			throws KeyStoreException {
		mKeyStore.setKeyEntry(alias, key, getPasswordForKey(alias), certChain);
	}

}
