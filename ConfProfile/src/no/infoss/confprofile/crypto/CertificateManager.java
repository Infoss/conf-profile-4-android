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
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.operator.OperatorCreationException;

import no.infoss.confprofile.task.TaskError;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

public abstract class CertificateManager {
	public static final String TAG = CertificateManager.class.getSimpleName();
	
	public static final String MANAGER_ANDROID_RAW = "android.raw";
	public static final String MANAGER_ANDROID_SYSTEM = "android.system";
	public static final String MANAGER_ANDROID_USER = "android.user";
	public static final String MANAGER_INTERNAL = "internal";
	public static final String MANAGER_TMP = "tmp";
	
	private static final HashMap<String, CertificateManager> MANAGERS;
	static {
		MANAGERS = new HashMap<String, CertificateManager>();
	}
	
	protected static final CertificateManager getOrCreateManager(Context context, String managerName) {
		CertificateManager result = null;
		
		synchronized(MANAGERS) {
			result = MANAGERS.get(managerName);
		
			if(result == null) {
				try {
					if(MANAGER_ANDROID_RAW.equals(managerName)) {
						result = new AndroidCertificateManager(context);
					} else if(MANAGER_ANDROID_SYSTEM.equals(managerName)) {
						result = new AndroidCertificateManagerWrapper(context, MANAGER_ANDROID_SYSTEM);
					} else if(MANAGER_ANDROID_USER.equals(managerName)) {
						result = new AndroidCertificateManagerWrapper(context, MANAGER_ANDROID_USER);
					} else if(MANAGER_INTERNAL.equals(managerName)) {
						result = new AppCertificateManager(context);
					} else if(MANAGER_TMP.equals(managerName)) {
						result = new TmpCertificateManager(context);
					}
				} catch(NoSuchProviderException e) {
					Log.w(TAG, "Can't find keystore provider", e);
				}  catch(KeyStoreException e) {
					Log.w(TAG, "KeyStore exception ocurred", e);
				}
				
				if(result != null) {
					MANAGERS.put(managerName, result);
				}
			}
		}
		
		return result;
	}
	
	public static final CertificateManager getManagerSync(Context context, String managerName) {
		CertificateManager result = getOrCreateManager(context, managerName);
		if(result != null && !result.isLoaded()) {
			try {
				result.loadSync();
			} catch (Exception e) {
				Log.e(TAG, "Exception while loading certificate manager", e);
			}
		}
		
		return result;
	}
	
	public static final CertificateManager getManager(Context context, String managerName) {
		CertificateManager result = getOrCreateManager(context, managerName);
		if(result != null && !result.isLoaded()) {
			result.load(null);
		}
		
		return result;
	}
	
	protected final Context mContext;
	protected boolean mIsLoaded;
	private long mUpdateRequestedTime;
	private long mUpdateProcessedTime;
	
	protected CertificateManager(Context ctx) {
		mContext = ctx;
		mIsLoaded = false;
		mUpdateRequestedTime = 0;
		mUpdateProcessedTime = 0;
	}

	public abstract Map<String, Certificate> getCertificates();
	
	public abstract Certificate[] getCertificateChain(String alias) throws KeyStoreException;
	
	public abstract Key getKey(String alias) 
			throws UnrecoverableKeyException, 
				   KeyStoreException, 
				   NoSuchAlgorithmException;
	
	public abstract void putCertificate(String alias, Certificate cert) throws KeyStoreException;
	
	public abstract void putKey(String alias, Key key, char[] password, Certificate[] certChain) 
			throws KeyStoreException;
	
	protected abstract void doLoad() 
			throws KeyStoreException, 
				   NoSuchProviderException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   OperatorCreationException, 
				   InvalidKeySpecException, 
				   UnrecoverableKeyException, 
				   IOException;
	
	protected abstract void doReload() 
			throws KeyStoreException, 
				   NoSuchProviderException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   OperatorCreationException, 
				   InvalidKeySpecException,
				   UnrecoverableKeyException, 
				   IOException;
	
	protected abstract void doStore() 
			throws KeyStoreException, 
				   NoSuchProviderException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   OperatorCreationException, 
				   InvalidKeySpecException, 
				   IOException;
	
	/**
	 * Asynchronously performs loading certificates and keys.
	 * If CertificateManager instance was obtained via CertificateManager.getManager(),
	 * this method is already called.
	 * @param listener
	 * @return this for chaining
	 */
	public final CertificateManager load(OnCertificateManagerUpdatedListener listener) {
		new LoadCertsTask(false, listener).execute(this);
		return this;
	}
	
	/**
	 * Asynchronously performs reloading certificates and keys.
	 * @param listener
	 * @return this for chaining
	 */
	public final CertificateManager reload(OnCertificateManagerUpdatedListener listener) {
		new LoadCertsTask(true, listener).execute(this);
		return this;
	}
	
	public final CertificateManager store() {
		//TODO: fix this (direct call)
		try {
			storeSync();
		} catch(Exception e) {
			Log.e(TAG, "Error while storing keystore", e);
		}
		return this;
	}
	
	/**
	 * Synchronously loads certificated and keys from the backed keystore.
	 * Used for initial loading. Does not affect if data was already loaded.
	 * Do not run on the UI thread to avoid ANR.
	 * @throws KeyStoreException
	 * @throws NoSuchProviderException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws OperatorCreationException
	 * @throws InvalidKeySpecException
	 * @throws IOException
	 */
	protected final void loadSync() 
			throws KeyStoreException, 
				   NoSuchProviderException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   OperatorCreationException, 
				   InvalidKeySpecException, 
				   UnrecoverableKeyException, 
				   IOException {
		if(!mIsLoaded) {
			mUpdateRequestedTime = System.currentTimeMillis();
			doLoad();
			mUpdateProcessedTime = System.currentTimeMillis();
			mIsLoaded = true;
		}
	}
	
	/**
	 * Synchronously reloads certificates and keys from the backed keystore.
	 * Do not run on the UI thread to avoid ANR.
	 * @throws KeyStoreException
	 * @throws NoSuchProviderException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws OperatorCreationException
	 * @throws InvalidKeySpecException
	 * @throws IOException
	 */
	protected final void reloadSync() 
			throws KeyStoreException, 
				   NoSuchProviderException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   OperatorCreationException, 
				   InvalidKeySpecException,
				   UnrecoverableKeyException,
				   IOException {
		mUpdateRequestedTime = System.currentTimeMillis();
		if(!mIsLoaded) {
			doLoad();
		} else {
			doReload();
		}
		mUpdateProcessedTime = System.currentTimeMillis();
	}
	
	protected final void storeSync() 
			throws KeyStoreException, 
				   NoSuchProviderException, 
				   NoSuchAlgorithmException, 
				   CertificateException, 
				   OperatorCreationException, 
				   InvalidKeySpecException, 
				   IOException {
		doStore();
	}
	
	public boolean isLoaded() {
		return mIsLoaded;
	}
	
	public long getUpdateRequestedTime() {
		return mUpdateRequestedTime;
	}
	
	public long getUpdateProcessedTime() {
		return mUpdateProcessedTime;
	}
	
	public interface OnCertificateManagerUpdatedListener {
		public void onCertificateManagerUpdateSuccess(CertificateManager manager);
		public void onCertificateManagerUpdateFailed(CertificateManager manager, int errCode);
	}
	
	private static class LoadCertsTask extends AsyncTask<CertificateManager, Void, Integer[]> {
		public static final String TAG = LoadCertsTask.class.getSimpleName();
		
		private CertificateManager[] mManagers;
		private boolean mDoReload;
		private OnCertificateManagerUpdatedListener mListener;
		
		public LoadCertsTask(boolean doReload, OnCertificateManagerUpdatedListener listener) {
			mDoReload = doReload;
			mListener = listener;
		}
		
		@Override
		protected Integer[] doInBackground(CertificateManager... params) {
			mManagers = params;
			Integer result[] = new Integer[mManagers.length];
			
			for(int i = 0; i < mManagers.length; i++) {
				try {
					if(mDoReload) {
						mManagers[i].reloadSync();
					} else {
						mManagers[i].loadSync();
					}
					result[i] = TaskError.SUCCESS;
				} catch (KeyStoreException e) {
					Log.e(TAG, "", e);
					result[i] = TaskError.INTERNAL;
				} catch (NoSuchProviderException e) {
					Log.e(TAG, "", e);
					result[i] = TaskError.INTERNAL;
				} catch (NoSuchAlgorithmException e) {
					Log.e(TAG, "", e);
					result[i] = TaskError.INTERNAL;
				} catch (CertificateException e) {
					Log.e(TAG, "", e);
					result[i] = TaskError.INTERNAL;
				} catch (OperatorCreationException e) {
					Log.e(TAG, "", e);
					result[i] = TaskError.INTERNAL;
				} catch (InvalidKeySpecException e) {
					Log.e(TAG, "", e);
					result[i] = TaskError.INTERNAL;
				} catch (IOException e) {
					Log.e(TAG, "", e);
					result[i] = TaskError.INTERNAL;
				}  catch (Exception e) {
					Log.e(TAG, "", e);
					result[i] = TaskError.INTERNAL;
				}
			}
			
			return result;
		}
		
		@Override
		protected void onPostExecute(Integer[] result) {
			if(mListener != null) {
				for(int i = 0; i < mManagers.length; i++) {
					if(result[i] != TaskError.SUCCESS) {
						mListener.onCertificateManagerUpdateFailed(mManagers[i], result[i]);
					} else {
						mListener.onCertificateManagerUpdateSuccess(mManagers[i]);
					}
				}
			}
			
			mListener = null;
			mManagers = null;
		}

	}

}
