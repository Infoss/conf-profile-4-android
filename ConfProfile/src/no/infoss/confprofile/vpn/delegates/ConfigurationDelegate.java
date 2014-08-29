package no.infoss.confprofile.vpn.delegates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.VpnDataCursorLoader;
import no.infoss.confprofile.profile.VpnDataCursorLoader.VpnDataPerformance;
import no.infoss.confprofile.profile.data.VpnData;
import no.infoss.confprofile.profile.data.VpnDataEx;
import no.infoss.confprofile.profile.data.VpnOnDemandConfig;
import no.infoss.confprofile.util.HelperThread.Callback;
import no.infoss.confprofile.util.HelperThread.Performer;
import no.infoss.confprofile.vpn.NetworkConfig;
import no.infoss.confprofile.vpn.VpnManagerHelperThread;
import no.infoss.confprofile.vpn.VpnManagerHelperThread.OnDemandVpnListPerformer;
import no.infoss.confprofile.vpn.VpnManagerHelperThread.OnDemandVpnListRequest;
import no.infoss.confprofile.vpn.VpnManagerService;
import no.infoss.confprofile.vpn.VpnTunnel;
import no.infoss.confprofile.vpn.VpnTunnelFactory;
import no.infoss.confprofile.vpn.conn.ApacheSocketFactory;

public class ConfigurationDelegate extends VpnManagerDelegate {
	public static final String TAG = ConfigurationDelegate.class.getSimpleName();
	
	private boolean mIsRequestActive = false;
	private boolean mReevaluateOnRequest = false;
	private List<VpnDataEx> mConfigInfoList;
	private Object mConfigInfoLock = new Object();
	
	private VpnDataEx mCurrentVpnData;
	private VpnOnDemandConfig mCurrentConfig;
	private String mCurrentUuid;
	private NetworkConfig mSavedNetworkConfig;
	private boolean mSavedFailoverFlag;
	
	private volatile boolean mOnDemandEnabled;
	
	private ApacheSocketFactory mApacheSocketFactory;

	public ConfigurationDelegate(VpnManagerService vpnMgr) {
		super(vpnMgr);
		
		mCurrentConfig = null;
		mApacheSocketFactory = new ApacheSocketFactory(getVpnManager());
		
		mOnDemandEnabled = false;
		updateVpnProfiles();
	}
	
	
	public void notifyConnectivityLost(NetworkConfig netConfig, boolean isFailover) {
		Log.d(TAG, "lost " + netConfig.toString() + (isFailover ? ", failover" : ""));
		mSavedNetworkConfig = netConfig;
		mSavedFailoverFlag = isFailover;
		
		updateCurrentConfiguration(false);
	}
	
	public void notifyConnectivityChanged(NetworkConfig netConfig, boolean isFailover) {
		Log.d(TAG, "changed to " + netConfig.toString() + (isFailover ? ", failover" : ""));
		if(mIsRequestActive) {
			mReevaluateOnRequest = true;
		}
		
		mSavedNetworkConfig = netConfig;
		mSavedFailoverFlag = isFailover;
		
		updateCurrentConfiguration(false);
	}

	public synchronized void updateCurrentConfiguration(boolean notifyIfUnchanged) {
		if(mSavedNetworkConfig == null || mConfigInfoList == null) {
			return;
		}
		
		if(mCurrentConfig != null) {
			if(mCurrentConfig.match(mSavedNetworkConfig)) {
				if(mOnDemandEnabled) {
					getVpnManager().onCurrentConfigChanged(mCurrentVpnData);
				}
				return;
			}
		}
		
		boolean configFound = false;
		VpnDataEx vpnData = null;
		
		synchronized(mConfigInfoLock) {
			for(VpnDataEx info : mConfigInfoList) {
				if(info.getPayloadUuid() == null || info.getPayloadUuid().isEmpty()) {
					Log.w(TAG, "Skipping VpnDataEx with null or empty uuid");
					continue;
				}
				
				if(!info.isOnDemandEnabled() || !info.isOnDemandEnabledByUser()) {
					continue;
				}
				
				for(VpnOnDemandConfig cfg : info.getOnDemandConfiguration()) {
					if(mSavedNetworkConfig.match(cfg.getNetworkConfig())) {
						Log.d(TAG, "MATCHED==========");
						Log.d(TAG, mSavedNetworkConfig.toString());
						Log.d(TAG, cfg.getNetworkConfig().toString());
						Log.d(TAG, "=================");
						
						if(!cfg.probe(mApacheSocketFactory)) {
							Log.d(TAG, "URL probe failed");
							continue;
						}
						
						mCurrentVpnData = info;
						mCurrentConfig = cfg;
						mCurrentUuid = info.getPayloadUuid();
						configFound = true;
						break;
					}
				}
				
				if(configFound) {
					break;
				}
			}
		}
		
		if(mOnDemandEnabled && configFound) {
			getVpnManager().onCurrentConfigChanged(vpnData);
		}
		
		/*
		for(VpnDataEx info : mConfigInfoList) {
			if(info.getPayloadUuid() == null || info.getPayloadUuid().isEmpty()) {
				Log.w(TAG, "Skipping VpnDataEx with null or empty uuid");
				continue;
			}
			
			if(!info.isOnDemandEnabled() || !info.isOnDemandEnabledByUser()) {
				continue;
			}
			
			for(VpnOnDemandConfig cfg : info.getOnDemandConfiguration()) {
				if(mSavedNetworkConfig.match(cfg.getNetworkConfig())) {
					Log.d(TAG, "MATCHED==========");
					Log.d(TAG, mSavedNetworkConfig.toString());
					Log.d(TAG, cfg.getNetworkConfig().toString());
					Log.d(TAG, "=================");
					
					if(!cfg.probe(mApacheSocketFactory)) {
						Log.d(TAG, "URL probe failed");
						continue;
					}
					
					mCurrentConfig = cfg;
					break;
					//TODO: check routes
					if(mCurrentTunnel != null && info.configId.equals(mCurrentTunnel.getTunnelId())) {
						//current tunnel is up and matches network configuration
						String logFmt = "Tunnel with id=%s is up and matches network configuration";
						Log.d(TAG, String.format(logFmt, info.configId));
						break;
					} else {
						tun = VpnTunnelFactory.getTunnel(getApplicationContext(), this, info);
						if(tun == null) {
							Log.d(TAG, "Can't create tun " + info.vpnType + " with id=" + info.configId);
						} else {
							if(mLastVpnTunnelUuid == null) {
								mLastVpnTunnelUuid = tun.getTunnelId();
							}
							
							VpnTunnel oldTun = mCurrentTunnel;
							mCurrentTunnel = tun;
							tun.establishConnection(info.params);
							
							if(mDebugDelegate.isDebugPcapEnabled()) {
								mDebugDelegate.debugStartTunnelPcap(mRouterLoop.getMtu(), tun);
							}
							
							mRouterLoop.defaultRoute4(tun);
							
							if(oldTun != null) {
								oldTun.terminateConnection();
							}
							
							routes4 = mRouterLoop.getRoutes4();
							if(routes4 != null) {
								Log.d(TAG, "IPv4 routes: " + routes4.toString());
							}
							
							break;
						}
					}
				}
			}
			
		}
		
		if(mRouterLoop.isPaused()) {
			mRouterLoop.pause(false);
		}
		*/
	}
	
	protected void updateVpnProfiles() {
		VpnManagerHelperThread helperThread = getVpnManager().getHelperThread();
		
		if(!mIsRequestActive && helperThread != null) {
			mIsRequestActive = true;
			helperThread.request(new OnDemandVpnListRequest(), 
					new OnDemandVpnListPerformer(getVpnManager().getApplicationContext()), 
					new Callback<OnDemandVpnListRequest, 
							OnDemandVpnListPerformer>() {

						@Override
						public void onSuccess(OnDemandVpnListRequest request,
								OnDemandVpnListPerformer performer) {
							mIsRequestActive = false;
							
							synchronized (mConfigInfoLock) {
								if(mConfigInfoList != null) {
									mConfigInfoList.clear();
								}
								
								mConfigInfoList = request.result;
								
								if(mConfigInfoList != null) {
									Collections.reverse(mConfigInfoList);
								}
							}
							
							updateCurrentConfiguration(false);
						}

						@Override
						public void onError(OnDemandVpnListRequest request,
								OnDemandVpnListPerformer performer) {
							// TODO Auto-generated method stub
							mIsRequestActive = false;
							Log.e(TAG, "Error while getting On-Demand VPN configuration");
						}
					});
			//new ObtainOnDemandVpns(this, this).execute((Void[]) null);
		}
	}
	
	public void updateVpnProfile(String uuid) {
		
	}
	
	public String getTunnelCredentials(String uuid) {
		if(uuid == null) {
			return null;
		}
		
		String result = null;
		
		synchronized(mConfigInfoLock) {
			for(VpnDataEx data : mConfigInfoList) {
				if(uuid.equals(data.getPayloadUuid())) {
					result = data.getOnDemandCredentials();
					break;
				}
			}
		}
		
		return result;
	}


	public boolean isOnDemandEnabled() {
		return mOnDemandEnabled;
	}


	public void setOnDemandEnabled(boolean onDemandEnabled) {
		this.mOnDemandEnabled = onDemandEnabled;
	}
}
