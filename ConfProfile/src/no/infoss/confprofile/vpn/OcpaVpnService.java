package no.infoss.confprofile.vpn;

import no.infoss.confprofile.Main;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class OcpaVpnService extends VpnService implements OcpaVpnInterface {
	public static final String TAG = OcpaVpnService.class.getSimpleName();

	static {
		System.loadLibrary("ocpa");
	}
	
	private Binder mBinder = new Binder();
	
	private VpnManagerInterface mVpnMgr;
	private final Object mVpnMgrLock = new Object();
	
	private final ServiceConnection mServiceConnection = new ServiceConnection() {
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.d(TAG, "Service disconnected: " + name.flattenToString());
			synchronized (mVpnMgrLock) {
				mVpnMgr = null;
			}
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.d(TAG, "Service connected: " + name.flattenToString());
			synchronized (mVpnMgrLock) {
				mVpnMgr = (VpnManagerInterface) service.queryLocalInterface(VpnManagerInterface.TAG);
			}
		}
	};

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			Bundle bundle = intent.getExtras();
			if (bundle != null) {
				
			}
		}
		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		bindService(new Intent(this, VpnManagerService.class), mServiceConnection, Service.BIND_AUTO_CREATE);
	}

	@Override
	public void onRevoke() {
		Log.d(TAG, "onRevoke()");
		synchronized (mVpnMgrLock) {
			if (mVpnMgr != null) {
				mVpnMgr.notifyVpnServiceRevoked();
			}
		}
	}

	@Override
	public void onDestroy() {		
		if (mVpnMgr != null) {
			unbindService(mServiceConnection);
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		mBinder.attachInterface(this, OcpaVpnInterface.TAG);
		return mBinder;
	}

	@Override
	public IBinder asBinder() {
		return mBinder;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		return false;
	}

	@Override
	public BuilderAdapter createBuilderAdapter(String displayName) {
		return new BuilderAdapter(displayName);
	}
	

	public class BuilderAdapter {
		private final String mName;
		private VpnService.Builder mBuilder;

		public BuilderAdapter(String name) {
			mName = name;
			mBuilder = createBuilder(name);
		}

		private VpnService.Builder createBuilder(String name) {
			VpnService.Builder builder = new OcpaVpnService.Builder();
			builder.setSession(mName);

			Context context = getApplicationContext();
			Intent intent = new Intent(context, Main.class);
			PendingIntent pending = PendingIntent.getActivity(context, 0, intent,
															  PendingIntent.FLAG_UPDATE_CURRENT);
			builder.setConfigureIntent(pending);
			return builder;
		}

		public synchronized boolean addAddress(String address, int prefixLength) {
			try {
				mBuilder.addAddress(address, prefixLength);
			} catch (IllegalArgumentException e) {
				return false;
			}
			return true;
		}

		public synchronized boolean addDnsServer(String address) {
			try {
				mBuilder.addDnsServer(address);
			} catch (IllegalArgumentException ex) {
				return false;
			}
			return true;
		}

		public synchronized boolean addRoute(String address, int prefixLength) {
			try {
				mBuilder.addRoute(address, prefixLength);
			} catch (IllegalArgumentException ex) {
				return false;
			}
			return true;
		}

		public synchronized boolean addSearchDomain(String domain) {
			try {
				mBuilder.addSearchDomain(domain);
			} catch (IllegalArgumentException ex) {
				return false;
			}
			return true;
		}

		public synchronized boolean setMtu(int mtu) {
			try {
				mBuilder.setMtu(mtu);
			} catch (IllegalArgumentException ex) {
				return false;
			}
			return true;
		}

		public synchronized int establish() {
			ParcelFileDescriptor fd;
			try {
				fd = mBuilder.establish();
			} catch (Exception e) {
				Log.e(TAG, "BuilderAdapter.establish() exception", e); 
				return -1;
			}
			
			if (fd == null) {
				return -1;
			}
			
			/* now that the TUN device is created we don't need the current
			 * builder anymore, but we might need another when reestablishing */
			mBuilder = createBuilder(mName);
			return fd.detachFd();
		}
	}

}
