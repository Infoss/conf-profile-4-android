package no.infoss.confprofile.vpn;

import no.infoss.confprofile.Main;
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

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(intent != null && !ACTION_TERMINATE_VPN_SERVICE.equals(intent.getAction())) {
			//notify about started service
			startService(getVpnMgrNotifyIntent(VpnManagerInterface.ACTION_NOTIFY_VPN_SERVICE_STARTED));
			Log.d(TAG, "notifyVpnServiceStarted()");
		}
		
		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();
	}

	@Override
	public void onRevoke() {
		super.onRevoke();
		
		Log.d(TAG, "onRevoke()");
		startService(getVpnMgrNotifyIntent(VpnManagerInterface.ACTION_NOTIFY_VPN_SERVICE_REVOKED));
		Log.d(TAG, "notifyVpnServiceRevoked()");
	}

	@Override
	public void onDestroy() {		
		super.onDestroy();
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
	
	private Intent getVpnMgrNotifyIntent(String action) {
		Intent intent = new Intent(this,  VpnManagerService.class);
		intent.setAction(action);
		return intent;
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
