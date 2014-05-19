package no.infoss.confprofile.vpn;

import no.infoss.confprofile.Main;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

public class OcpaVpnService extends VpnService {
	public static final String TAG = OcpaVpnService.class.getSimpleName();

	static {
		System.loadLibrary("ocpa");
	}
	
	private Thread mVpnThread;
	private VpnManager mVpnMgr;
	private final Object mVpnMgrLock = new Object();
	
	private final ServiceConnection mServiceConnection = new ServiceConnection() {
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
			/* since the service is local this is theoretically only called when the process is terminated */
			synchronized (mVpnMgrLock) {
				mVpnMgr = null;
			}
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			synchronized (mVpnMgrLock) {
				mVpnMgr = (VpnManager) service.queryLocalInterface(VpnManager.TAG);
			}
			/* we are now ready to start the handler thread */
			mVpnThread.start();
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
		mVpnThread = new Thread(new OcpaVpnWorkflow(this));
		/* the thread is started when the service is bound */
		bindService(new Intent(this, VpnManagerService.class), mServiceConnection, Service.BIND_AUTO_CREATE);
	}

	@Override
	public void onRevoke() {
		/* the system revoked the rights grated with the initial prepare() call.
		 * called when the user clicks disconnect in the system's VPN dialog */
	}

	@Override
	public void onDestroy() {
		try {
			mVpnThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if (mVpnMgr != null) {
			unbindService(mServiceConnection);
		}
	}

	/**
	 * Notify the state service about a new connection attempt.
	 * Called by the handler thread.
	 *
	 * @param profile currently active VPN profile
	 */
	/*package*/ void notifyConnectionStarted(VpnConnectionProfile profile) {
		synchronized (mVpnMgrLock) {
			if (mVpnMgr != null) {
				mVpnMgr.notifyConnectionStarted(profile);
			}
		}
	}

	/*package*/ BuilderAdapter getBuilderForProfile(VpnConnectionProfile profile) {
		return new BuilderAdapter(profile.getName());
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
			} catch (Exception ex) {
				ex.printStackTrace();
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
