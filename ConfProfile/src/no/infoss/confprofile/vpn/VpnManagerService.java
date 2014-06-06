package no.infoss.confprofile.vpn;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.infoss.confprofile.task.ObtainOnDemandVpns;
import no.infoss.confprofile.task.ObtainOnDemandVpns.ObtainOnDemandVpnsListener;
import no.infoss.confprofile.vpn.RouterLoop.Route4;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class VpnManagerService extends Service implements VpnManagerInterface, ObtainOnDemandVpnsListener {
	public static final String TAG = VpnManagerService.class.getSimpleName();
	
	private Binder mBinder = new Binder();
	private NetworkStateListener mNetworkListener;
	
	private OcpaVpnInterface mVpnService;
	private final Object mVpnServiceLock = new Object();
	private final ServiceConnection mVpnServiceConn = new ServiceConnection() {
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.d(TAG, "Service disconnected: " + name.flattenToString());
			synchronized(mVpnServiceLock) {
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
			synchronized(mVpnServiceLock) {
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
	
	private boolean mIsRequestActive = false;
	private boolean mReevaluateOnRequest = false;
	private List<VpnConfigInfo> mConfigInfos;
	
	private NetworkConfig mSavedNetworkConfig;
	private boolean mSavedFailoverFlag;
	
	@Override
	public void onCreate() {
		super.onCreate();
		mNetworkListener = new NetworkStateListener(getApplicationContext(), this);
		obtainOnDemandVpns();
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
		mSavedNetworkConfig = netConfig;
		mSavedFailoverFlag = isFailover;
		
		updateCurrentConfiguration();
	}
	
	public void notifyConnectivityChanged(NetworkConfig netConfig, boolean isFailover) {
		Log.d(TAG, "changed to " + netConfig.toString() + (isFailover ? ", failover" : ""));
		if(mIsRequestActive) {
			mReevaluateOnRequest = true;
		}
		
		mSavedNetworkConfig = netConfig;
		mSavedFailoverFlag = isFailover;
		
		updateCurrentConfiguration();
	}
	
	private synchronized void updateCurrentConfiguration() {
		if(mSavedNetworkConfig == null || mConfigInfos == null) {
			return;
		}
		
		List<Route4> routes4 = mRouterLoop.getRoutes4();
		if(routes4 == null) {
			Log.d(TAG, "IPv4 routes: none");
		} else {
			Log.d(TAG, "IPv4 routes: " + routes4.toString());
		}
		
		VpnTunnel tun = null;
		
		for(VpnConfigInfo info : mConfigInfos) {
			if(mSavedNetworkConfig.match(info.networkConfig)) {
				Log.d(TAG, "MATCHED==========");
				Log.d(TAG, mSavedNetworkConfig.toString());
				Log.d(TAG, info.networkConfig.toString());
				Log.d(TAG, "=================");
				//TODO: check routes
				if(!mTuns.containsKey(info.configId)) {
					tun = VpnTunnelFactory.getTunnel(getApplicationContext(), this, info);
					if(tun != null) {
						mTuns.put(info.configId, tun);
						tun.establishConnection(info.params);
						mRouterLoop.defaultRoute4(tun);
						
						routes4 = mRouterLoop.getRoutes4();
						Log.d(TAG, "IPv4 routes: " + routes4.toString());
						
						break;
					}
				}
			} else {
				Log.d(TAG, "UNMATCHED========");
				Log.d(TAG, mSavedNetworkConfig.toString());
				Log.d(TAG, info.networkConfig.toString());
				Log.d(TAG, "=================");
				
				if(mTuns.containsKey(info.configId)) {
					tun = mTuns.remove(info.configId);
					tun.terminateConnection();
				}
			}
		}
		
		if(mRouterLoop.isPaused()) {
			mRouterLoop.pause(false);
		}
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
	
	public void obtainOnDemandVpns() {
		if(!mIsRequestActive) {
			mIsRequestActive = true;
			new ObtainOnDemandVpns(this, this).execute((Void[]) null);
		}
	}

	@Override
	public void obtainOnDemandVpnsSuccess(ObtainOnDemandVpns task, List<VpnConfigInfo> result) {
		mIsRequestActive = false;
		if(mConfigInfos != null) {
			mConfigInfos.clear();
		}
		mConfigInfos = result;
		updateCurrentConfiguration();
	}

	@Override
	public void obtainOnDemandVpnsError(ObtainOnDemandVpns task) {
		// TODO Auto-generated method stub
		mIsRequestActive = false;
		Log.e(TAG, "Error while getting On-Demand VPN configuration");
	}
	
	public static class VpnConfigInfo {
		public static final String PARAMS_IPSEC = "IPSec";
		public static final String PARAMS_PPP = "PPP";
		public static final String PARAMS_CUSTOM = "Custom";
		
		public String configId;
		public String vpnType;
		public NetworkConfig networkConfig;
		public Map<String, Object> params;
		public Certificate[] certificates;
		public PrivateKey privateKey;
	}
}
