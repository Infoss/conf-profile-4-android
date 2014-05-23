package no.infoss.confprofile.vpn;

import java.util.HashMap;
import java.util.Map;

import no.infoss.confprofile.profile.BaseQueryCursorLoader.AsyncPerformance;
import no.infoss.confprofile.profile.BaseQueryCursorLoader.AsyncPerformanceListener;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadsCursorLoader.PayloadsPerformance;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class VpnManagerService extends Service implements VpnManagerInterface, AsyncPerformanceListener {
	public static final String TAG = VpnManagerService.class.getSimpleName();
	
	private Binder mBinder = new Binder();
	private NetworkStateListener mNetworkListener;
	
	private OcpaVpnInterface mVpnService;
	private final Object mVpnServiceLock = new Object();
	private final ServiceConnection mVpnServiceConn = new ServiceConnection() {
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.d(TAG, "Service disconnected: " + name.flattenToString());
			synchronized (mVpnServiceLock) {
				mVpnService = null;
				if(mRouterLoop != null) {
					mRouterLoop.terminate();
				}
				mRouterLoop = null;
			}
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.d(TAG, "Service connected: " + name.flattenToString());
			synchronized (mVpnServiceLock) {
				mVpnService = (OcpaVpnInterface) service.queryLocalInterface(OcpaVpnInterface.TAG);
				if(mRouterLoop != null) {
					mRouterLoop.terminate();
				}
				mRouterLoop = new RouterLoop(VpnManagerService.this, mVpnService.createBuilderAdapter("TEST"));
				mRouterLoop.startLoop();
			}
		}
	};
	
	private RouterLoop mRouterLoop = null;
	
	private final Map<String, VpnTunnel> mTuns = new HashMap<String, VpnTunnel>();
	
	private PayloadsPerformance mPayloadsPerformance = null;
	private boolean mIsRequestActive = false;
	
	@Override
	public void onCreate() {
		super.onCreate();
		mNetworkListener = new NetworkStateListener(getApplicationContext(), this);
		DbOpenHelper dbHelper = new DbOpenHelper(getApplicationContext());
		mPayloadsPerformance = new PayloadsPerformance(getApplicationContext(), 0, null, dbHelper);
		mIsRequestActive = true;
		new AsyncPerformance(mPayloadsPerformance, this).execute((Void[]) null);
		bindService(new Intent(this, OcpaVpnService.class), mVpnServiceConn, Service.BIND_AUTO_CREATE);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		mNetworkListener = null;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		mBinder.attachInterface(this, VpnManagerInterface.TAG);
		if(mNetworkListener != null) {
			mNetworkListener.register();
		}
		return mBinder;
	}

	@Override
	public IBinder asBinder() {
		return mBinder;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		if(mNetworkListener != null) {
			mNetworkListener.unregister();
		}
		return false;
	}
	
	@Override
	public void notifyVpnServiceRevoked() {
		//TODO: show system notification
	}
	
	public void notifyConnectivityLost(NetworkConfig netConfig, boolean isFailover) {
		Log.d(TAG, "lost " + netConfig.toString() + (isFailover ? ", failover" : ""));
	}
	
	public void notifyConnectivityChanged(NetworkConfig netConfig, boolean isFailover) {
		Log.d(TAG, "changed to " + netConfig.toString() + (isFailover ? ", failover" : ""));
	}
	
	@Override
	public boolean protect(int socket) {
		boolean res = false;
		synchronized(mVpnServiceLock) {
			if(mVpnService != null) {
				res = mVpnService.protect(socket);
			} else {
				Log.w(TAG, "Can't protect socket due to unavailable OcpaVpnService");
			}
		}
		return res;
	}

	/*package*/ RouterLoop getRouterLoop() {
		return mRouterLoop;
	}

	@Override
	public void onAsyncPerformanceSuccess(AsyncPerformance task, Cursor result) {
		// TODO Auto-generated method stub
		mIsRequestActive = false;
	}

	@Override
	public void onAsyncPerformanceError(AsyncPerformance task) {
		// TODO Auto-generated method stub
		mIsRequestActive = false;
	}
	
	
}
