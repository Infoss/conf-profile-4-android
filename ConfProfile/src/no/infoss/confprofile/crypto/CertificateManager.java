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
	public static final String MANAGER_ANDROID_RAW = "android.raw";
	public static final String MANAGER_ANDROID_SYSTEM = "android.system";
	public static final String MANAGER_ANDROID_USER = "android.user";
	public static final String MANAGER_INTERNAL = "internal";
	
	private static final HashMap<String, CertificateManager> MANAGERS;
	static {
		MANAGERS = new HashMap<String, CertificateManager>();
	}
	
	public static final CertificateManager getManager(Context context, String managerName) {
		CertificateManager result = null;
		
		synchronized(MANAGERS) {
			result = MANAGERS.get(managerName);
		
			if(result == null) {
				if(MANAGER_ANDROID_RAW.equals(managerName)) {
					result = new AndroidCertificateManager(context);
				} else if(MANAGER_ANDROID_SYSTEM.equals(managerName)) {
					result = new AndroidCertificateManagerWrapper(context, MANAGER_ANDROID_SYSTEM);
				} else if(MANAGER_ANDROID_USER.equals(managerName)) {
					result = new AndroidCertificateManagerWrapper(context, MANAGER_ANDROID_USER);
				} else if(MANAGER_INTERNAL.equals(managerName)) {
					result = new AppCertificateManager(context);
				}
				
				if(result != null) {
					MANAGERS.put(managerName, result);
					result.load(null);
				}
			}
		}
		
		return result;
	}
	
	protected boolean mIsLoaded;
	private long mUpdateRequestedTime;
	private long mUpdateProcessedTime;
	
	protected CertificateManager() {
		mIsLoaded = false;
		mUpdateRequestedTime = 0;
		mUpdateProcessedTime = 0;
	}

	public abstract Map<String, Certificate> getCertificates();
	public abstract Map<String, Key> getKeys();
	
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
