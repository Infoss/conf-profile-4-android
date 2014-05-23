package no.infoss.confprofile.vpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

public class NetworkStateListener extends BroadcastReceiver {
	private final Context mCtx;
	private final ConnectivityManager mConnMgr;
	private final WifiManager mWifiMgr;
	private final NetworkConfig mNetworkConfig;
	
	private boolean mIsRegistered = false;
	private final Object mRegisterLock = new Object();
	private VpnManagerInterface mListener;

	public NetworkStateListener(Context context, VpnManagerInterface listener) {
		mCtx = context;
		mListener = listener;
		mConnMgr = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
		mWifiMgr = (WifiManager) mCtx.getSystemService(Context.WIFI_SERVICE);
		mNetworkConfig = new NetworkConfig(true);
		updateConfig(false);
	}
	
	public NetworkConfig getNetworkConfig() {
		return mNetworkConfig;
	}
	
	public boolean isRegistered() {
		return mIsRegistered;
	}

	public void register() {
		synchronized(mRegisterLock) {
			if(!mIsRegistered) {
				mIsRegistered = true;
				mCtx.registerReceiver(this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
				updateConfig(false);
			}
		}
	}

	public void unregister() {
		synchronized(mRegisterLock) {
			if(mIsRegistered) {
				mCtx.unregisterReceiver(this);
				mIsRegistered = false;
			}
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		updateConfig(intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false));
	}

	private synchronized void updateConfig(boolean isFailover) {
		NetworkInfo info = mConnMgr.getActiveNetworkInfo();
		if(info == null || !info.isConnected()) {
			//disconnected
			//TODO: notify about changed network connectivity
			mNetworkConfig.setActive(false);
			if(mListener != null) {
				mListener.notifyConnectivityLost(mNetworkConfig, isFailover);
			}
			return;
		}
		
		mNetworkConfig.setActive(true);
		switch (info.getType()) {
		case ConnectivityManager.TYPE_MOBILE: {
			mNetworkConfig.setInterfaceType(NetworkConfig.IF_CELL);
			mNetworkConfig.setSsid(null);
			break;
		}
		case ConnectivityManager.TYPE_WIFI: {
			mNetworkConfig.setInterfaceType(NetworkConfig.IF_WIFI);
			mNetworkConfig.setSsid(mWifiMgr.getConnectionInfo().getSSID());
			break;
		}
		case ConnectivityManager.TYPE_ETHERNET: {
			mNetworkConfig.setInterfaceType(NetworkConfig.IF_ETHER);
			mNetworkConfig.setSsid(null);
			break;
		}
		default: {
			mNetworkConfig.setInterfaceType(NetworkConfig.IF_UNSUPPORTED);
			mNetworkConfig.setSsid(null);
			break;
		}
		}
		
		//TODO: add DNS addresses && domains
		if(mListener != null) {
			mListener.notifyConnectivityChanged(mNetworkConfig, isFailover);
		}
	}
}
