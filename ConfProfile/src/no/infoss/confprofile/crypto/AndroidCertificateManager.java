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
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.operator.OperatorCreationException;

import android.content.Context;
import android.util.Log;

/**
 * Common class for accessing a copy of built-in Android KeyStore
 * @author Dmitry Vorobiev
 *
 */
public class AndroidCertificateManager extends CertificateManager {
	private final KeyStore mKeyStore;
	
	protected AndroidCertificateManager(Context context) 
			throws KeyStoreException {
		super(context);
		mKeyStore = KeyStore.getInstance("AndroidCAStore");
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
	public Certificate[] getCertificateChain(String alias) throws KeyStoreException {
		return mKeyStore.getCertificateChain(alias);
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
		mKeyStore.load(null, null);
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
	
	@Override
	protected void doStore() throws KeyStoreException, NoSuchProviderException,
			NoSuchAlgorithmException, CertificateException,
			OperatorCreationException, InvalidKeySpecException, IOException {
		throw new UnsupportedOperationException("Can't store this type of keystore");
	}

	@Override
	public Key getKey(String alias) throws UnrecoverableKeyException,
			KeyStoreException, NoSuchAlgorithmException {
		throw new UnsupportedOperationException("Can't get a key from this type of keystore");
	}

}
