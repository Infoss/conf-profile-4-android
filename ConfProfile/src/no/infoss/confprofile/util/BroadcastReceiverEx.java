package no.infoss.confprofile.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class BroadcastReceiverEx<T> extends BroadcastReceiver {
	private Context mCtx;
	
	private boolean mIsRegistered = false;
	private final Object mRegisterLock = new Object();
	private T mListener;

	public BroadcastReceiverEx(Context context, T listener) {
		mCtx = context;
		mListener = listener;
	}
	
	public final boolean isRegistered() {
		return mIsRegistered;
	}

	public final void register(IntentFilter filter) {
		synchronized(mRegisterLock) {
			if(!mIsRegistered) {
				mIsRegistered = true;
				mCtx.registerReceiver(this, filter);
				onRegistered();
			}
		}
	}

	public final void unregister() {
		synchronized(mRegisterLock) {
			if(mIsRegistered) {
				mCtx.unregisterReceiver(this);
				mIsRegistered = false;
				onUnregistered();
			}
		}
	}
	
	protected final Context getContext() { 
		return mCtx;
	}
	
	protected final T getListener() {
		return mListener;
	}

	/**
	 * Override this method to process received intent
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		
	}
	
	/**
	 * Override this method to do something right after receiver was registered
	 */
	protected void onRegistered() {
		
	}
	
	/**
	 * Override this method to do something right after receiver was unregistered
	 */
	protected void onUnregistered() {
		
	}

}

