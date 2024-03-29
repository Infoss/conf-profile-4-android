package no.infoss.confprofile.vpn;

import no.infoss.confprofile.util.BroadcastReceiverEx;
import no.infoss.confprofile.vpn.delegates.ConfigurationDelegate;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

public class NetworkStateListener extends BroadcastReceiverEx<ConfigurationDelegate> {
	public static final IntentFilter FILTER = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
	
	private final ConnectivityManager mConnMgr;
	private final WifiManager mWifiMgr;
	private final NetworkConfig mNetworkConfig;

	public NetworkStateListener(Context context, ConfigurationDelegate listener) {
		super(context, listener);
		
		mConnMgr = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		mWifiMgr = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
		mNetworkConfig = new NetworkConfig(true);
		updateConfig(false);
	}
	
	public NetworkConfig getNetworkConfig() {
		return mNetworkConfig;
	}
	
	public void register() {
		register(FILTER);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		updateConfig(intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false));
	}
	
	@Override
	protected void onRegistered() {
		updateConfig(false);
	}

	private synchronized void updateConfig(boolean isFailover) {
		NetworkInfo info = mConnMgr.getActiveNetworkInfo();
		if(info == null || !info.isConnected()) {
			//disconnected
			//TODO: notify about changed network connectivity
			mNetworkConfig.setActive(false);
			if(getListener() != null) {
				getListener().notifyConnectivityLost(mNetworkConfig, isFailover);
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
		
		mNetworkConfig.setIsRoaming(info.isRoaming());
		
		/*
		 * DNS settings provided as system properties (see build.prop on the rooted device)
		 * 
		 * WiFi default DNS:
		 * net.wlan0.dns1=8.8.8.8
		 * net.wlan0.dns2=8.8.4.4
		 * 
		 * Mobile internet default DNS:
		 * net.pdp0.dns1=8.8.8.8
		 * net.pdp0.dns2=8.8.4.4
		 * 
		 * VPN (?) default DNS:
		 * net.ppp0.dns1=8.8.8.8
		 * net.ppp0.dns2=8.8.4.4
		 * 
		 * Ethernet default  DNS:
		 * net.eth0.dns1=8.8.8.8
		 * net.eth0.dns2=8.8.4.4
		 * 
		 * 
		 * net.gprs.dns1=8.8.8.8
		 * net.gprs.dns2=8.8.4.4
		 * 
		 */
		//TODO: add DNS addresses && domains
		
		if(getListener() != null) {
			getListener().notifyConnectivityChanged(mNetworkConfig, isFailover);
		}
	}
}
