package no.infoss.confprofile.vpn;

import java.io.File;
import java.net.Socket;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import no.infoss.confprofile.BuildConfig;
import no.infoss.confprofile.R;
import no.infoss.confprofile.StartVpn;
import no.infoss.confprofile.task.ObtainOnDemandVpns;
import no.infoss.confprofile.task.ObtainOnDemandVpns.ObtainOnDemandVpnsListener;
import no.infoss.confprofile.util.MiscUtils;
import no.infoss.confprofile.util.NetUtils;
import no.infoss.confprofile.util.PcapOutputStream;
import no.infoss.confprofile.util.SimpleServiceBindKit;
import no.infoss.confprofile.vpn.RouterLoop.Route4;
import no.infoss.confprofile.vpn.VpnTunnel.ConnectionStatus;
import no.infoss.confprofile.vpn.VpnTunnel.TunnelInfo;
import no.infoss.jcajce.InfossJcaProvider;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.TypedArray;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class VpnManagerService extends Service implements VpnManagerInterface, ObtainOnDemandVpnsListener {
	public static final String TAG = VpnManagerService.class.getSimpleName();
	
	private static final String PREF_DEBUG_PCAP = "VpnManagerSevice_debugPcapEnabled";
	private static final String PCAP_TUN_FILENAME_FMT = "tun(%s)-%d.pcap";
	private static final String PCAP_NAT_FILENAME_FMT = "usernat(%s)-%d.pcap";
	
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
	
	private NotificationManager mNtfMgr;
	private Binder mBinder = new Binder();
	private NetworkStateListener mNetworkListener;
	
	private SimpleServiceBindKit<OcpaVpnInterface> mBindKit;
	private int mVpnServiceState;
	
	private RouterLoop mRouterLoop = null;
	
	private VpnTunnel mCurrentTunnel = null;
	private VpnTunnel mUsernatTunnel = null;
	private String mPendingVpnTunnelUuid = null;
	
	private boolean mIsRequestActive = false;
	private boolean mReevaluateOnRequest = false;
	private List<VpnConfigInfo> mConfigInfos;
	
	private NetworkConfig mSavedNetworkConfig;
	private boolean mSavedFailoverFlag;
	private boolean mDebugPcapEnabled = false;
	
	private int[] mVpnManagerIcons;
	private int[] mVpnManagerErrorIcons;
	
	private LocalNetworkConfig mCurrLocalNetConfig = null;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		mNetworkListener = new NetworkStateListener(getApplicationContext(), this);
		obtainOnDemandVpns();
		initIcons();
		
		mVpnServiceState = SERVICE_STATE_REVOKED;
		
		mNtfMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		String title = getResources().getString(R.string.notification_title_preparing);
		String text = getResources().getString(R.string.notification_text_preparing);
		Notification notification = buildNotification(
				mVpnManagerIcons[0], 
				mVpnManagerIcons[0], 
				title, 
				text);
		mNtfMgr.notify(R.string.app_name, notification);
		
		mBindKit = new SimpleServiceBindKit<OcpaVpnInterface>(this, OcpaVpnInterface.TAG);
		if(!mBindKit.bind(OcpaVpnService.class, BIND_AUTO_CREATE)) {
			Log.e(TAG, "Can't bind OcpaVpnService");
		}
		
		if(BuildConfig.DEBUG) {
			SharedPreferences prefs = getSharedPreferences(MiscUtils.PREFERENCE_FILE, MODE_PRIVATE);
			mDebugPcapEnabled = prefs.getBoolean(PREF_DEBUG_PCAP, false);
		}
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
	public void startVpnService() {
		if(mVpnServiceState != SERVICE_STATE_STARTED) {
			Intent intent = new Intent(this, StartVpn.class);
			intent.setAction(StartVpn.ACTION_CALL_PREPARE);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
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
			
			if(mDebugPcapEnabled) {
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
		
		String title = getResources().getString(R.string.notification_title_connecting);
		String text = getResources().getString(R.string.notification_text_connecting);
		Notification notification = buildNotification(
				mVpnManagerIcons[1], 
				mVpnManagerIcons[1], 
				title, 
				text);
		mNtfMgr.notify(R.string.app_name, notification);
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
		
		String title = getResources().getString(R.string.notification_title_error_revoked);
		String text = getResources().getString(R.string.notification_text_error_revoked);
		Notification notification = buildNotification(
				mVpnManagerErrorIcons[2], 
				mVpnManagerErrorIcons[2], 
				title, 
				text);
		mNtfMgr.notify(R.string.app_name, notification);
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
		}
		
		if(mUsernatTunnel != null) {
			natStatus = mUsernatTunnel.getConnectionStatus();
			usernatId = mUsernatTunnel.getTunnelId();
		}
		
		
		intent.putExtra(KEY_EVENT_TYPE, TYPE_TUNNEL_STATE_CHANGED);
		intent.putExtra(KEY_TUNNEL_ID, tunId);
		intent.putExtra(KEY_TUNNEL_STATE, tunStatus);
		sendBroadcast(intent);
		
		String title;
		String text;
		int smallIconId;
		int largeIconId;
		//TODO: implement notification based on the connection status
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
						VpnTunnel oldTun = mCurrentTunnel;
						mCurrentTunnel = tun;
						tun.establishConnection(info.params);
						
						if(mDebugPcapEnabled) {
							debugStartTunnelPcap(PCAP_TUN_FILENAME_FMT, tun);
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
		
		String title = getResources().getString(R.string.notification_title_error_always_on);
		String text = getResources().getString(R.string.notification_text_error_always_on);
		Notification notification = buildNotification(
				mVpnManagerErrorIcons[1], 
				mVpnManagerErrorIcons[1], 
				title, 
				text);
		mNtfMgr.notify(R.string.app_name, notification);
	}
	
	@Override
	public void notifyVpnIsUnsupported() {
		mVpnServiceState = SERVICE_STATE_UNSUPPORTED;
		
		Intent intent = createBroadcastIntent();
		intent.putExtra(KEY_EVENT_TYPE, TYPE_SERVICE_STATE_CHANGED);
		intent.putExtra(KEY_SERVICE_STATE, SERVICE_STATE_UNSUPPORTED);
		sendBroadcast(intent);
		
		String title = getResources().getString(R.string.notification_title_error_unsupported);
		String text = getResources().getString(R.string.notification_text_error_unsupported);
		Notification notification = buildNotification(
				mVpnManagerErrorIcons[0], 
				mVpnManagerErrorIcons[0], 
				title, 
				text);
		mNtfMgr.notify(R.string.app_name, notification);
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
	
	@Override
	public boolean isDebugPcapEnabled() {
		return mDebugPcapEnabled;
	}
	
	@Override
	public boolean debugStartPcap() {
		if(BuildConfig.DEBUG) {
			if(!MiscUtils.isExternalStorageWriteable()) {
				mDebugPcapEnabled = false;
				storeDebugPcapEnabled(mDebugPcapEnabled);
				return false;
			}
			
			File externalFilesDir = getExternalFilesDir(null);
			if(externalFilesDir == null) {
				//error: storage error
				mDebugPcapEnabled = false;
				storeDebugPcapEnabled(mDebugPcapEnabled);
				return false;
			}
			
			String pcapFileName;
			PcapOutputStream os = null;
			
			int mtu = 1500;
			
			//capture from router loop
			if(mRouterLoop != null) {
				try {
					pcapFileName = String.format("router-%d.pcap", System.currentTimeMillis());
					mtu = mRouterLoop.getMtu();
					os = new PcapOutputStream(
							new File(externalFilesDir, pcapFileName), 
							mtu, 
							PcapOutputStream.LINKTYPE_RAW);
					mRouterLoop.debugRestartPcap(os);
				} catch(Exception e) {
					Log.e(TAG, "Restart pcap error", e);
				}
			}
			
			//capture from tunnel
			if(mCurrentTunnel != null) {
				debugStartTunnelPcap(PCAP_TUN_FILENAME_FMT, mCurrentTunnel);
			}
			
			if(mUsernatTunnel != null) {
				debugStartTunnelPcap(PCAP_NAT_FILENAME_FMT, mUsernatTunnel);
			}
			
			mDebugPcapEnabled = true;
			storeDebugPcapEnabled(mDebugPcapEnabled);
			return true;
		}
		
		storeDebugPcapEnabled(false);
		return false;
	}
	
	@Override
	public boolean debugStopPcap() {
		if(BuildConfig.DEBUG) {
			mDebugPcapEnabled = false;
			storeDebugPcapEnabled(mDebugPcapEnabled);
			
			if(mRouterLoop != null) {
				try {
					mRouterLoop.debugStopPcap();
				} catch(Exception e) {
					Log.e(TAG, "Stop pcap error", e);
				}
			}
			
			if(mCurrentTunnel != null) {
				try {
					mCurrentTunnel.debugStopPcap();
				} catch(Exception e) {
					Log.e(TAG, "Stop pcap error", e);
				}
			}
			
			if(mUsernatTunnel != null) {
				try {
					mUsernatTunnel.debugStopPcap();
				} catch(Exception e) {
					Log.e(TAG, "Stop pcap error", e);
				}
			}
			
			return true;
		}
		
		storeDebugPcapEnabled(false);
		return false;
	}
	
	private void storeDebugPcapEnabled(boolean enabled) {
		SharedPreferences prefs = getSharedPreferences(MiscUtils.PREFERENCE_FILE, MODE_PRIVATE);
		Editor editor = prefs.edit();
		editor.putBoolean(PREF_DEBUG_PCAP, enabled);
		editor.commit();
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
		
		if(mConfigInfos != null) {
			Collections.reverse(mConfigInfos);
		}
		
		updateCurrentConfiguration();
	}

	@Override
	public void obtainOnDemandVpnsError(ObtainOnDemandVpns task) {
		// TODO Auto-generated method stub
		mIsRequestActive = false;
		Log.e(TAG, "Error while getting On-Demand VPN configuration");
	}
	
	@Override
	public TunnelInfo getVpnTunnelInfo() {
		VpnTunnel tun = mCurrentTunnel;
		if(tun == null) {
			return null;
		}
		
		return tun.getInfo();
	}
	
	@Override
	public void activateVpnTunnel(String uuid) {
		if(mVpnServiceState != SERVICE_STATE_STARTED) {
			mPendingVpnTunnelUuid = uuid;
			startVpnService();
			return;
		}
	}
	
	private void initIcons() {
		TypedArray a;
		a = getResources().obtainTypedArray(R.array.vpn_manager_icons);
		mVpnManagerIcons = new int[a.length()];
		for(int i = 0; i < a.length(); i++) {
			mVpnManagerIcons[i] = a.getResourceId(i, 0);
		}
		a.recycle();
		
		a = getResources().obtainTypedArray(R.array.vpn_manager_error_icons);
		mVpnManagerErrorIcons = new int[a.length()];
		for(int i = 0; i < a.length(); i++) {
			mVpnManagerErrorIcons[i] = a.getResourceId(i, 0);
		}
		a.recycle();
	}
	
	private Notification buildNotification(int smallIconId, int largeIconId, String title, String text) {
		NotificationCompat.Builder compatBuilder = new NotificationCompat.Builder(this);
		if(smallIconId > 0) {
			compatBuilder.setSmallIcon(smallIconId);
		}
		
		if(largeIconId > 0) {
			Drawable d = getResources().getDrawable(largeIconId);
			if(d instanceof BitmapDrawable) {
				compatBuilder.setLargeIcon(((BitmapDrawable) d).getBitmap());
			}
		}
		
		compatBuilder.setContentTitle(title);
		compatBuilder.setContentText(text);
		compatBuilder.setOngoing(true);
		
		return compatBuilder.build();
	}
	
	private boolean debugStartTunnelPcap(String fileNameFmt, VpnTunnel tunnel) {
		if(BuildConfig.DEBUG) {
			if(tunnel == null || fileNameFmt == null || fileNameFmt.isEmpty()) {
				return false;
			}
			
			if(!MiscUtils.isExternalStorageWriteable()) {
				return false;
			}
			
			File externalFilesDir = getExternalFilesDir(null);
			if(externalFilesDir == null) {
				//error: storage error
				return false;
			}
			
			
			PcapOutputStream os = null;
			try {
				String pcapFileName = String.format(
						fileNameFmt, 
						tunnel.getTunnelId(), 
						System.currentTimeMillis());
				os = new PcapOutputStream(
						new File(externalFilesDir, pcapFileName), 
						mRouterLoop.getMtu(), 
						PcapOutputStream.LINKTYPE_RAW);
				tunnel.debugRestartPcap(os);
			} catch(Exception e) {
				Log.e(TAG, "Restart pcap error", e);
				if(os != null) {
					try {
						os.close();
					} catch(Exception ex) {
						//nothing to do here
					}
				}
				return false;
			}
			
			return true;
		}
		
		return false; //BuildConfig.DEBUG is false
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
}
