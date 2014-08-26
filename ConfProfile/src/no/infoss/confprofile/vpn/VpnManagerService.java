package no.infoss.confprofile.vpn;

import java.net.Socket;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import no.infoss.confprofile.StartVpn;
import no.infoss.confprofile.crypto.CertificateManager;
import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.format.PayloadFactory;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.format.json.PlistTypeAdapterFactory;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadsCursorLoader.PayloadsPerformance;
import no.infoss.confprofile.util.ConfigUtils;
import no.infoss.confprofile.util.HelperThread;
import no.infoss.confprofile.util.HelperThread.Callback;
import no.infoss.confprofile.util.HelperThread.Performer;
import no.infoss.confprofile.util.MiscUtils;
import no.infoss.confprofile.util.NetUtils;
import no.infoss.confprofile.util.SimpleServiceBindKit;
import no.infoss.confprofile.vpn.RouterLoop.Route4;
import no.infoss.confprofile.vpn.VpnTunnel.ConnectionStatus;
import no.infoss.confprofile.vpn.VpnTunnel.TunnelInfo;
import no.infoss.confprofile.vpn.delegates.DebugDelegate;
import no.infoss.confprofile.vpn.delegates.NotificationDelegate;
import no.infoss.jcajce.InfossJcaProvider;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class VpnManagerService extends Service implements VpnManagerInterface {
	public static final String TAG = VpnManagerService.class.getSimpleName();
	
	private static final String PREF_LAST_TUN_UUID = "VpnManagerSevice_lastTunnelUuid";
	
	private static final List<LocalNetworkConfig> LOCAL_NET_CONFIGS;
	
	static {
		System.loadLibrary("strongswan");

		if(IpSecTunnel.BYOD) {
			System.loadLibrary("tncif");
			System.loadLibrary("tnccs");
			System.loadLibrary("imcv");
			System.loadLibrary("pts");
		}
		
		System.loadLibrary("hydra");
		System.loadLibrary("charon");
		System.loadLibrary("ipsec");
		
		System.loadLibrary("ocpa");
		
		Security.addProvider(new InfossJcaProvider());
		
		List<LocalNetworkConfig> configs = new LinkedList<LocalNetworkConfig>();
		configs.add(new LocalNetworkConfig("172.31.255.254")); //add 172.31.255.254/30
		LOCAL_NET_CONFIGS = Collections.unmodifiableList(configs);
	}
	
	
	private Binder mBinder = new Binder();
	private NetworkStateListener mNetworkListener;
	
	private SimpleServiceBindKit<OcpaVpnInterface> mBindKit;
	private int mVpnServiceState;
	
	private RouterLoop mRouterLoop = null;
	
	private VpnTunnel mCurrentTunnel = null;
	private VpnTunnel mUsernatTunnel = null;
	private String mLastVpnTunnelUuid = null;
	private String mPendingVpnTunnelUuid = null;
	
	private boolean mIsRequestActive = false;
	private boolean mReevaluateOnRequest = false;
	private List<VpnConfigInfo> mConfigInfos;
	
	private NetworkConfig mSavedNetworkConfig;
	private boolean mSavedFailoverFlag;
	
	private LocalNetworkConfig mCurrLocalNetConfig = null;
	
	private VpnManagerHelperThread mHelperThread;
	private DebugDelegate mDebugDelegate;
	private NotificationDelegate mNtfDelegate;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		mDebugDelegate = new DebugDelegate(this);
		mNtfDelegate = new NotificationDelegate(this);
		
		mNetworkListener = new NetworkStateListener(getApplicationContext(), this);
		mHelperThread = new VpnManagerHelperThread();
		mHelperThread.start();
		obtainOnDemandVpns();
		
		
		mVpnServiceState = SERVICE_STATE_REVOKED;
		mNtfDelegate.notifyPreparing();
		
		mBindKit = new SimpleServiceBindKit<OcpaVpnInterface>(this, OcpaVpnInterface.TAG);
		if(!mBindKit.bind(OcpaVpnService.class, BIND_AUTO_CREATE)) {
			Log.e(TAG, "Can't bind OcpaVpnService");
		}
		
		SharedPreferences prefs = getSharedPreferences(MiscUtils.PREFERENCE_FILE, MODE_PRIVATE);
		mLastVpnTunnelUuid = prefs.getString(PREF_LAST_TUN_UUID, null);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		mDebugDelegate.releaseResources();
		mDebugDelegate = null;
		
		mNtfDelegate.releaseResources();
		mNtfDelegate = null;
		
		mNetworkListener = null;
		mNtfDelegate.cancelNotification();
		
		if(mHelperThread != null) {
			mHelperThread.interrupt();
		}
		mHelperThread = null;
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
	public void startVpnService() {
		if(mVpnServiceState != SERVICE_STATE_STARTED) {
			Intent intent = new Intent(this, StartVpn.class);
			intent.setAction(StartVpn.ACTION_CALL_PREPARE);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		}
	}
	
	@Override
	public void stopVpnService() {
		if(mVpnServiceState == SERVICE_STATE_STARTED) {
			if(mUsernatTunnel != null) {
				mUsernatTunnel.terminateConnection();
			}
			mUsernatTunnel = null;
			
			if(mCurrentTunnel != null) {
				mCurrentTunnel.terminateConnection();
			}
			mCurrentTunnel = null;
			
			if(mRouterLoop != null) {
				mRouterLoop.terminate();
			}
			mRouterLoop = null;
			
			mNtfDelegate.cancelNotification();
			/*
			OcpaVpnInterface vpnService = mBindKit.lock();
			if(vpnService != null) {
				vpnService.stopVpnService();
			}
			mBindKit.unlock();
			*/
		}
	}
	
	@Override
	public int getVpnServiceState() {
		return mVpnServiceState;
	}
	
	@Override
	public void notifyVpnServiceStarted() {
		mVpnServiceState = SERVICE_STATE_STARTED;
		
		Intent intent = createBroadcastIntent();
		intent.putExtra(KEY_EVENT_TYPE, TYPE_SERVICE_STATE_CHANGED);
		intent.putExtra(KEY_SERVICE_STATE, SERVICE_STATE_STARTED);
		sendBroadcast(intent);
		
		if(mUsernatTunnel != null) {
			mUsernatTunnel.terminateConnection();
		}
		
		if(mCurrentTunnel != null) {
			mCurrentTunnel.terminateConnection();
		}
		
		if(mRouterLoop != null) {
			mRouterLoop.terminate();
		}
		
		OcpaVpnInterface vpnService = mBindKit.lock();
		try {
			mCurrLocalNetConfig = LOCAL_NET_CONFIGS.get(0);
			mRouterLoop = new RouterLoop(this, vpnService.createBuilderAdapter("OpenProfile"));
			mRouterLoop.startLoop();
			
			mUsernatTunnel = new UsernatTunnel(getApplicationContext(), mRouterLoop, this);
			mUsernatTunnel.establishConnection(null);
			mRouterLoop.defaultRoute4(mUsernatTunnel);
			mRouterLoop.defaultRoute6(mUsernatTunnel);
			
			if(mDebugDelegate.isDebugPcapEnabled()) {
				debugStartPcap();
			}
			
			List<Route4> routes4 = mRouterLoop.getRoutes4();
			if(routes4 == null) {
				Log.d(TAG, "+ IPv4 routes: none");
			} else {
				Log.d(TAG, "+ IPv4 routes: " + routes4.toString());
			}
			
			updateCurrentConfiguration();
		} finally {
			mBindKit.unlock();
		}
		
	}

	@Override
	public void notifyVpnServiceRevoked() {
		mVpnServiceState = SERVICE_STATE_REVOKED;
		
		Intent intent = createBroadcastIntent();
		intent.putExtra(KEY_EVENT_TYPE, TYPE_SERVICE_STATE_CHANGED);
		intent.putExtra(KEY_SERVICE_STATE, SERVICE_STATE_REVOKED);
		sendBroadcast(intent);
		
		if(mCurrentTunnel != null) {
			mCurrentTunnel.terminateConnection();
		}
		mCurrentTunnel = null;
		
		if(mUsernatTunnel != null) {
			mUsernatTunnel.terminateConnection();
		}
		mUsernatTunnel = null;
		
		if(mRouterLoop != null) {
			mRouterLoop.terminate();
		}
		mRouterLoop = null;
		
		mNtfDelegate.notifyRevoked();
	}
	
	@Override
	public void notifyTunnelStateChanged() {
		int tunStatus = connectionStatusToInt(ConnectionStatus.DISCONNECTED);
		ConnectionStatus natStatus = ConnectionStatus.DISCONNECTED;
		Intent intent = createBroadcastIntent();
		
		String tunId = null;
		String usernatId = null;
		
		if(mCurrentTunnel != null) {
			TunnelInfo info = mCurrentTunnel.getInfo();
			tunId = info.uuid;
			tunStatus = info.state;
			
			intent.putExtra(KEY_CONNECTED_SINCE, info.connectedSince);
			intent.putExtra(KEY_SERVER_NAME, info.serverName);
			intent.putExtra(KEY_REMOTE_ADDRESS, info.remoteAddress);
			intent.putExtra(KEY_LOCAL_ADDRESS, info.localAddress);
			
			if(info.state == TUNNEL_STATE_CONNECTED) {
				mLastVpnTunnelUuid = info.uuid;
				
				SharedPreferences prefs = getSharedPreferences(MiscUtils.PREFERENCE_FILE, MODE_PRIVATE);
				Editor editor = prefs.edit();
				editor.putString(PREF_LAST_TUN_UUID, mLastVpnTunnelUuid);
				editor.commit();
			}
		}
		
		if(mUsernatTunnel != null) {
			natStatus = mUsernatTunnel.getConnectionStatus();
			usernatId = mUsernatTunnel.getTunnelId();
		}
		
		
		intent.putExtra(KEY_EVENT_TYPE, TYPE_TUNNEL_STATE_CHANGED);
		intent.putExtra(KEY_TUNNEL_ID, tunId);
		intent.putExtra(KEY_TUNNEL_STATE, tunStatus);
		sendBroadcast(intent);
		
		switch(tunStatus) {
		case TUNNEL_STATE_CONNECTING: {
			mNtfDelegate.notifyConnecting();
			break;
		}
		
		case TUNNEL_STATE_CONNECTED: {
			mNtfDelegate.notifyConnected();
			break;
		}
		
		case TUNNEL_STATE_DISCONNECTING:
		case TUNNEL_STATE_DISCONNECTED:
		case TUNNEL_STATE_TERMINATED:
		default: {
			mNtfDelegate.notifyDisconnected();
			break;
		}
		}
	}

	@Override
	public void notifyConnectivityLost(NetworkConfig netConfig, boolean isFailover) {
		Log.d(TAG, "lost " + netConfig.toString() + (isFailover ? ", failover" : ""));
		mSavedNetworkConfig = netConfig;
		mSavedFailoverFlag = isFailover;
		
		updateCurrentConfiguration();
	}
	
	@Override
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
		if(mSavedNetworkConfig == null || mConfigInfos == null || mRouterLoop == null) {
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
			if(info.configId == null || info.configId.isEmpty()) {
				Log.w(TAG, "Skipping VpnConfigInfo with null or empty configId");
				continue;
			}
			
			if(mSavedNetworkConfig.match(info.networkConfig)) {
				Log.d(TAG, "MATCHED==========");
				Log.d(TAG, mSavedNetworkConfig.toString());
				Log.d(TAG, info.networkConfig.toString());
				Log.d(TAG, "=================");
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
		
		if(mRouterLoop.isPaused()) {
			mRouterLoop.pause(false);
		}
	}
	
	@Override
	public void notifyVpnLockedBySystem() {
		mVpnServiceState = SERVICE_STATE_LOCKED;
		
		Intent intent = createBroadcastIntent();
		intent.putExtra(KEY_EVENT_TYPE, TYPE_SERVICE_STATE_CHANGED);
		intent.putExtra(KEY_SERVICE_STATE, SERVICE_STATE_LOCKED);
		sendBroadcast(intent);
		
		mNtfDelegate.notifyLockedBySystem();
	}
	
	@Override
	public void notifyVpnIsUnsupported() {
		mVpnServiceState = SERVICE_STATE_UNSUPPORTED;
		
		Intent intent = createBroadcastIntent();
		intent.putExtra(KEY_EVENT_TYPE, TYPE_SERVICE_STATE_CHANGED);
		intent.putExtra(KEY_SERVICE_STATE, SERVICE_STATE_UNSUPPORTED);
		sendBroadcast(intent);
		
		mNtfDelegate.notifyUnsupported();
	}
	
	@Override
	public boolean protect(int socket) {
		boolean res = false;
		OcpaVpnInterface vpnService = mBindKit.lock();
		try {
			if(vpnService != null) {
				res = vpnService.protect(socket);
			} else {
				Log.w(TAG, "Can't protect socket due to unavailable OcpaVpnService");
			}
		} finally {
			mBindKit.unlock();
		}

		return res;
	}
	
	@Override
	public boolean protect(Socket socket) {
		boolean res = false;
		OcpaVpnInterface vpnService = mBindKit.lock();
		try {
			if(vpnService != null) {
				res = vpnService.protect(socket);
			} else {
				Log.w(TAG, "Can't protect socket due to unavailable OcpaVpnService");
			}
		} finally {
			mBindKit.unlock();
		}
		
		return res;
	}
	
	@Override
	public int getLocalAddress4() {
		return mCurrLocalNetConfig.localIp;
	}
	
	@Override
	public int getRemoteAddress4() {
		return mCurrLocalNetConfig.remoteIp;
	}
	
	@Override
	public int getSubnetMask4() {
		return mCurrLocalNetConfig.subnetMask;
	}

	public RouterLoop getRouterLoop() {
		return mRouterLoop;
	}
	
	public void obtainOnDemandVpns() {
		if(!mIsRequestActive && mHelperThread != null) {
			mIsRequestActive = true;
			mHelperThread.request(new OnDemandVpnListRequest(), 
					new OnDemandVpnListPerformer(getApplicationContext()), 
					new Callback<VpnManagerService.OnDemandVpnListRequest, 
							VpnManagerService.OnDemandVpnListPerformer>() {

						@Override
						public void onSuccess(OnDemandVpnListRequest request,
								OnDemandVpnListPerformer performer) {
							mIsRequestActive = false;
							if(mConfigInfos != null) {
								mConfigInfos.clear();
							}
							
							mConfigInfos = request.result;
							
							if(mConfigInfos != null) {
								Collections.reverse(mConfigInfos);
							}
							
							updateCurrentConfiguration();
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
	
	@Override
	public TunnelInfo getVpnTunnelInfo() {
		VpnTunnel tun = mCurrentTunnel;
		if(tun == null) {
			return null;
		}
		
		return tun.getInfo();
	}
	
	/**
	 * @param uuid null will match last tunnel uuid
	 */
	@Override
	public void activateVpnTunnel(String uuid) {
		if(mVpnServiceState != SERVICE_STATE_STARTED) {
			mPendingVpnTunnelUuid = uuid;
			startVpnService();
			return;
		}
		
		if(uuid == null) {
			uuid = mLastVpnTunnelUuid;
		}
		
		if(mCurrentTunnel != null && uuid != null && uuid.equals(mCurrentTunnel.getTunnelId())) {
			Log.d(TAG, "Tunnel " + uuid + " is already activated");
			return;
		}
		
		//find appropriate tunnel and start connection
	}
	
	@Override
	public void deactivateVpnTunnel() {
		if(mCurrentTunnel != null) {
			mCurrentTunnel.terminateConnection();
		}
		mCurrentTunnel = null;
	}
	
	public void intlRemoveTunnel(VpnTunnel vpnTunnel) {
		mRouterLoop.removeTunnel(vpnTunnel);
	}
	
	private Intent createBroadcastIntent() {
		Intent intent = new Intent(BROADCAST_VPN_EVENT);
		intent.setPackage(getPackageName());
		intent.putExtra(KEY_EVENT_DATE, new Date());
		
		return intent;
	}
	
	public static final int connectionStatusToInt(ConnectionStatus status) {
		int retVal = TUNNEL_STATE_DISCONNECTED;
		switch (status) {
		case CONNECTING: {
			retVal = TUNNEL_STATE_CONNECTING;
			break;
		}
		case CONNECTED: {
			retVal = TUNNEL_STATE_CONNECTED;
			break;
		}
		case DISCONNECTING: {
			retVal = TUNNEL_STATE_DISCONNECTING;
			break;
		}
		case TERMINATED: {
			retVal = TUNNEL_STATE_TERMINATED;
			break;
		}
		case DISCONNECTED:
		default: {
			retVal = TUNNEL_STATE_DISCONNECTED;
			break;
		}
		}
		
		return retVal;
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
	
	private static class LocalNetworkConfig {
		public final int subnetIp;
		public final int subnetMask;
		public final int localIp;
		public final int remoteIp;
		
		public final String subnetAddr;
		public final String localAddr;
		public final String remoteAddr;
		
		public LocalNetworkConfig(String subnet, int mask, String local, String remote) {
			subnetAddr = subnet;
			subnetMask = mask;
			localAddr = local;
			remoteAddr = remote;
			
			subnetIp = NetUtils.ip4StrToInt(subnet);
			localIp = NetUtils.ip4StrToInt(local);
			remoteIp = NetUtils.ip4StrToInt(remote);
		}
		
		public LocalNetworkConfig(int subnet, int mask, int local, int remote) {
			subnetIp = subnet;
			subnetMask = mask;
			localIp = local;
			remoteIp = remote;
			
			subnetAddr = NetUtils.ip4IntToStr(subnet);
			localAddr = NetUtils.ip4IntToStr(local);
			remoteAddr = NetUtils.ip4IntToStr(remote);
		}
		
		public LocalNetworkConfig(String subnet) {
			this(applyMask(subnet, 30));
		}
		
		private LocalNetworkConfig(int subnet) {
			this(subnet, 30, subnet + 2, subnet + 1);
		}
		
		private static int applyMask(String addr, int mask) {
			int ip = NetUtils.ip4StrToInt(addr);
			int bitmask = ((int)0xffffffff >>> (32 - mask)) << (32 - mask);
			return ip & bitmask;
		}
	}
	
	public class VpnManagerHelperThread extends 
			HelperThread<OnDemandVpnListRequest, 
					OnDemandVpnListPerformer, 
					Callback<OnDemandVpnListRequest, 
					OnDemandVpnListPerformer>> {
		public VpnManagerHelperThread() {
			super();
		}

		@Override
		protected void checkConditionsToRun() throws IllegalStateException {
			// TODO Auto-generated method stub
			
		}

		@Override
		protected void freeResources() {
			// TODO Auto-generated method stub
			
		}
	}
	
	public class OnDemandVpnListRequest {
		public Bundle request;
		public List<VpnConfigInfo> result;
	}
	
	public class OnDemandVpnListPerformer implements Performer<OnDemandVpnListRequest> {
		private Context mCtx;
		
		public OnDemandVpnListPerformer(Context ctx) {
			mCtx = ctx;
		}

		@Override
		public boolean perform(OnDemandVpnListRequest request) {
			DbOpenHelper dbHelper = DbOpenHelper.getInstance(mCtx);
			PayloadsPerformance payloadsPerformance = new PayloadsPerformance(mCtx, 0, request.request, dbHelper);
			GsonBuilder gsonBuilder = new GsonBuilder();
			gsonBuilder.registerTypeAdapterFactory(new PlistTypeAdapterFactory());
			Gson gson = gsonBuilder.create();
			
			CertificateManager mgr = CertificateManager.getManagerSync(mCtx, CertificateManager.MANAGER_INTERNAL);
			
			try {
				request.result = new LinkedList<VpnConfigInfo>();
				Cursor payloads = payloadsPerformance.perform();
				if(payloads.moveToFirst()) {
					while(!payloads.isAfterLast()) {
						String data = payloads.getString(3);
						Payload payload = PayloadFactory.createPayload(gson.fromJson(data, Dictionary.class));
						if(payload instanceof VpnPayload) {
							VpnPayload vpnPayload = (VpnPayload) payload;
							if(vpnPayload.isOnDemandEnabled()) {
								Dictionary testDict;
								
								testDict = vpnPayload.getIpsec();
								if(testDict == null) {
									testDict = vpnPayload.getVpn();
								}
								if(testDict == null) {
									testDict = vpnPayload.getPpp();
								}
								
								if(testDict != null) {
									VpnConfigInfo configInfo = new VpnConfigInfo();
									configInfo.configId = vpnPayload.getPayloadUUID();
									configInfo.networkConfig = ConfigUtils.buildNetworkConfig(vpnPayload);
									configInfo.params = new HashMap<String, Object>();
									
									Dictionary tmpDict;
									tmpDict = vpnPayload.getIpsec();
									if(tmpDict != null) {
										configInfo.params.put(VpnConfigInfo.PARAMS_IPSEC, tmpDict.asMap());
									}
									
									tmpDict = vpnPayload.getPpp();
									if(tmpDict != null) {
										configInfo.params.put(VpnConfigInfo.PARAMS_PPP, tmpDict.asMap());
									}
									
									tmpDict = vpnPayload.getVendorConfig();
									if(tmpDict != null) {
										configInfo.params.put(VpnConfigInfo.PARAMS_CUSTOM, tmpDict.asMap());
									}
									
									if(VpnPayload.VPN_TYPE_CUSTOM.equals(vpnPayload.getVpnType())) {
										configInfo.vpnType = vpnPayload.getVpnSubType();
									} else {
										configInfo.vpnType = vpnPayload.getVpnType();
									}
									
									String method = testDict.getString(VpnPayload.KEY_AUTHENTICATION_METHOD);
									if(VpnPayload.AUTH_METHOD_CERTIFICATE.equals(method)) {
										String uuid = testDict.getString(VpnPayload.KEY_PAYLOAD_CERTIFICATE_UUID);
										configInfo.certificates = mgr.getCertificateChain(uuid);
										configInfo.privateKey = (PrivateKey) mgr.getKey(uuid);
									}
									
									request.result.add(configInfo);
								}
							}
						}
						payloads.moveToNext();
					}
				}
			} catch(Exception e) {
				Log.e(TAG, "", e);
				return false;
			}
			
			return true;
		}
		
	}

	@Override
	public boolean isDebugPcapEnabled() {
		return mDebugDelegate.isDebugPcapEnabled();
	}

	@Override
	public boolean debugStartPcap() {
		return mDebugDelegate.debugStartPcap(
				mRouterLoop.getMtu(), 
				mRouterLoop, 
				mUsernatTunnel, 
				mCurrentTunnel);
	}

	@Override
	public boolean debugStopPcap() {
		return mDebugDelegate.debugStopPcap(mRouterLoop, mUsernatTunnel, mCurrentTunnel);
	}
}
