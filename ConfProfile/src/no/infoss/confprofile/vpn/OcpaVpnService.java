package no.infoss.confprofile.vpn;

import no.infoss.confprofile.Main;
import no.infoss.confprofile.util.SimpleServiceBindKit;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

public class OcpaVpnService extends VpnService implements OcpaVpnInterface {
	public static final String TAG = OcpaVpnService.class.getSimpleName();
	
	private Binder mBinder = new Binder() {
		@Override
		protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) 
				throws RemoteException {
			if(code == IBinder.LAST_CALL_TRANSACTION) {
				onRevoke();
				return true;
			}
			
			return super.onTransact(code, data, reply, flags);
		}
	};
	
	private SimpleServiceBindKit<VpnManagerService> mBindKit;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		VpnManagerService vpnMgr = mBindKit.lock();
		try {
			if(vpnMgr != null) {
				vpnMgr.notifyVpnServiceStarted();
			} else {
				Log.e(TAG, "Can't call notifyVpnServiceStarted(). VpnManagerService is now bound");
			}
		} finally {
			mBindKit.unlock();
		}
		
		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		mBindKit = new SimpleServiceBindKit<VpnManagerService>(this, VpnManagerInterface.TAG);
		if(!mBindKit.bind(VpnManagerService.class, BIND_AUTO_CREATE)) {
			Log.e(TAG, "Can't bind VpnManagerService");
		}
	}

	@Override
	public void onRevoke() {
		Log.d(TAG, "onRevoke()");
		VpnManagerInterface vpnMgr = mBindKit.lock();
		try {
			if (vpnMgr != null) {
				vpnMgr.notifyVpnServiceRevoked();
			}
		} finally {
			mBindKit.unlock();
		}
	}

	@Override
	public void onDestroy() {		
		mBindKit.unbind();
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
