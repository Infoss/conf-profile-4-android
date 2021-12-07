package no.infoss.confprofile.util;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

public class SimpleServiceBindKit<T> {
	public static final String TAG = SimpleServiceBindKit.class.getSimpleName();
	
	private final ReentrantReadWriteLock mRwl = new ReentrantReadWriteLock();
	private Context mCtx;
	private String mLocalIfDesc;
	private T mInterface;
	private boolean mIsBound = false;
	private ServiceConnection mWrappedServiceConn = null;
	private final ServiceConnection mServiceConn = new ServiceConnection() {
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
				mRwl.writeLock().lock();
				mInterface = null;
				mIsBound = false;
				mRwl.writeLock().unlock();
				
				if(mWrappedServiceConn != null) {
					mWrappedServiceConn.onServiceDisconnected(name);
				}
		}

		@SuppressWarnings("unchecked")
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mRwl.writeLock().lock();
			try {
				mInterface = (T) service.queryLocalInterface(mLocalIfDesc);
				mIsBound = true;
			} catch(Exception e) {
				Log.e(TAG, "Error while quering local interface", e);
			} finally {
				mRwl.writeLock().unlock();
			}
			
			if(mWrappedServiceConn != null) {
				mWrappedServiceConn.onServiceConnected(name, service);
			}
		}
	};
	
	public SimpleServiceBindKit(Context ctx, String localIfDesc) {
		mCtx = ctx.getApplicationContext();
		mLocalIfDesc = localIfDesc;
	}
	
	public T lock() {
		mRwl.readLock().lock();
		return mInterface;
	}
	
	public void unlock() {
		mRwl.readLock().unlock();
	}
	
	public boolean bind(Class<?> component, int connFlags) {
		return mCtx.bindService(new Intent(mCtx, component), mServiceConn, connFlags);
	}
	
	public boolean bind(Class<?> component, ServiceConnection wrappedServiceConn, int connFlags) {
		mWrappedServiceConn = wrappedServiceConn;
		return mCtx.bindService(new Intent(mCtx, component), mServiceConn, connFlags);
	}
	
	public void unbind() {
		mCtx.unbindService(mServiceConn);
	}
	
	public boolean isBound() {
		return mIsBound;
	}
}
