package no.infoss.confprofile.vpn.delegates;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import no.infoss.confprofile.profile.data.VpnDataEx;
import no.infoss.confprofile.profile.data.VpnOnDemandConfig;
import no.infoss.confprofile.util.HelperThread.Callback;
import no.infoss.confprofile.vpn.NetworkConfig;
import no.infoss.confprofile.vpn.VpnManagerHelperThread;
import no.infoss.confprofile.vpn.VpnManagerHelperThread.OnDemandVpnListPerformer;
import no.infoss.confprofile.vpn.VpnManagerHelperThread.OnDemandVpnListRequest;
import no.infoss.confprofile.vpn.VpnManagerService;
import no.infoss.confprofile.vpn.conn.ApacheSocketFactory;
import android.util.Log;

public class ConfigurationDelegate extends VpnManagerDelegate {
	public static final String TAG = ConfigurationDelegate.class.getSimpleName();
	
	private final Map<String, VpnDataEx> mConfigInfoMap;
	private final List<VpnDataEx> mConfigInfoList = new ArrayList<VpnDataEx>();
	private final Object mConfigInfoLock = new Object();
	
	private boolean mIsRequestActive = false;
	private boolean mReevaluateOnRequest = false;
	
	private VpnOnDemandConfig mCurrentConfig;
	private String mCurrentUuid;
	private NetworkConfig mSavedNetworkConfig;
	private boolean mSavedFailoverFlag;
	
	private volatile boolean mOnDemandEnabled;
	
	private ApacheSocketFactory mApacheSocketFactory;

	public ConfigurationDelegate(VpnManagerService vpnMgr) {
		super(vpnMgr);
		
		mConfigInfoMap = new ConcurrentHashMap<String, VpnDataEx>();
		
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
					getVpnManager().onCurrentConfigChanged(mCurrentUuid);
				}
				return;
			}
		}
		
		boolean configFound = false;
		
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
		
		if(configFound) {
			getVpnManager().notifySelectedTunnelUuidChanged();
			
			if(mOnDemandEnabled) {
				getVpnManager().onCurrentConfigChanged(mCurrentUuid);
			}
		}
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
								mConfigInfoList.clear();
								
								List<String> keys = new ArrayList<String>(mConfigInfoMap.keySet());
								for(VpnDataEx item : request.result) {
									keys.remove(item.getPayloadUuid());
									//adding items in reverse order
									mConfigInfoList.add(0, item);
									mConfigInfoMap.put(item.getPayloadUuid(), item);
								}
								
								for(String obsoletedKey : keys) {
									mConfigInfoMap.remove(obsoletedKey);
								}
								
								if(request.request != null) {
									request.request.clear();
								}
								
								if(request.result != null) {
									request.result.clear();
								}
							}
							
							updateCurrentConfiguration(false);
						}

						@Override
						public void onError(OnDemandVpnListRequest request,
								OnDemandVpnListPerformer performer) {
							mIsRequestActive = false;
							Log.e(TAG, "Error while getting On-Demand VPN configuration");
						}
					});
		}
	}
	
	public void updateVpnProfile(String uuid) {
		
	}
	
	public String getTunnelCredentials(String uuid) {
		if(uuid == null) {
			return null;
		}
		
		String result = null;
		
		VpnDataEx data = mConfigInfoMap.get(uuid);
		if(data != null) {
			result = data.getOnDemandCredentials();
		}
		
		return result;
	}
	
	public VpnDataEx getTunnelData(String uuid) {
		if(uuid == null) {
			return null;
		}
		
		return mConfigInfoMap.get(uuid);
	}


	public boolean isOnDemandEnabled() {
		return mOnDemandEnabled;
	}


	public void setOnDemandEnabled(boolean onDemandEnabled) {
		this.mOnDemandEnabled = onDemandEnabled;
	}
	
	public String getCurrentUuid() {
		return mCurrentUuid;
	}
}
