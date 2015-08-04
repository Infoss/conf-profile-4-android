package no.infoss.confprofile.vpn;

import java.net.InetAddress;
import java.net.Socket;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import com.crashlytics.android.Crashlytics;

import no.infoss.confprofile.StartVpn;
import no.infoss.confprofile.profile.data.VpnDataEx;
import no.infoss.confprofile.util.AndroidProperties;
import no.infoss.confprofile.util.LocalNetworkConfig;
import no.infoss.confprofile.util.MiscUtils;
import no.infoss.confprofile.util.SimpleServiceBindKit;
import no.infoss.confprofile.vpn.RouterLoop.Route4;
import no.infoss.confprofile.vpn.VpnTunnel.ConnectionStatus;
import no.infoss.confprofile.vpn.VpnTunnel.TunnelInfo;
import no.infoss.confprofile.vpn.delegates.ConfigurationDelegate;
import no.infoss.confprofile.vpn.delegates.DebugDelegate;
import no.infoss.confprofile.vpn.delegates.NotificationDelegate;
import no.infoss.jcajce.InfossJcaProvider;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

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
		configs.add(new LocalNetworkConfig("172.31.255.254")); //add 172.31.255.254/29
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
	
	private LocalNetworkConfig mCurrLocalNetConfig = null;
	
	private VpnManagerHelperThread mHelperThread;
	private DebugDelegate mDebugDelegate;
	private NotificationDelegate mNtfDelegate;
	private ConfigurationDelegate mCfgDelegate;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(intent != null) { 
			if(ACTION_NOTIFY_VPN_SERVICE_STARTED.equals(intent.getAction())) {
				notifyVpnServiceStarted();
				Log.d(TAG, "notifyVpnServiceStarted()");
			} else if(ACTION_NOTIFY_VPN_SERVICE_REVOKED.equals(intent.getAction())) {
				notifyVpnServiceRevoked();
				Log.d(TAG, "notifyVpnServiceRevoked()");
			}
		}
		
		return START_NOT_STICKY;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		mHelperThread = new VpnManagerHelperThread();
		mHelperThread.start();
		
		mDebugDelegate = new DebugDelegate(this);
		mNtfDelegate = new NotificationDelegate(this);
		mCfgDelegate = new ConfigurationDelegate(this);
		
		mNetworkListener = new NetworkStateListener(getApplicationContext(), mCfgDelegate);
		
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
	public void cancelAllNotifications() {
		mNtfDelegate.cancelAllNotifications();
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
			
			Intent intent = new Intent(this,  VpnManagerService.class);
			intent.setAction(OcpaVpnInterface.ACTION_TERMINATE_VPN_SERVICE);
			startService(intent);
		}
		
		mNtfDelegate.cancelNotification();
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
			InetAddress[] dnsAddrs = mCurrLocalNetConfig.getDnsAddresses(null);
			ArrayList<String> virtualDnsAddrs = new ArrayList<String>(dnsAddrs.length);
			for(InetAddress addr : dnsAddrs) {
				if(addr != null) {
					virtualDnsAddrs.add(addr.getHostAddress());
				}
			}
			
			mRouterLoop = new RouterLoop(this, vpnService.createBuilderAdapter("OpenProfile"));
			mRouterLoop.startLoop();
			
			mUsernatTunnel = new UsernatTunnel(getApplicationContext(), this);
			mUsernatTunnel.establishConnection();
			mUsernatTunnel.setVirtualDnsAddrs(virtualDnsAddrs.toArray(new String[virtualDnsAddrs.size()]));
			mUsernatTunnel.setDnsAddrs(AndroidProperties.getInstance().getNetworkSpecificDnsAddrs(this));
			mRouterLoop.defaultRoute4(mUsernatTunnel);
			mRouterLoop.defaultRoute6(mUsernatTunnel);
			Log.d(TAG, "mRouterLoop.route4(): " + mCurrLocalNetConfig.getSubnetAddr() + "/" + mCurrLocalNetConfig.getSubnetMask());
			//mRouterLoop.route4(mUsernatTunnel, mCurrLocalNetConfig.getSubnetAddr(), mCurrLocalNetConfig.getSubnetMask());
			
			if(mDebugDelegate.isDebugPcapEnabled()) {
				debugStartPcap();
			}
			
			List<Route4> routes4 = mRouterLoop.getRoutes4();
			if(routes4 == null) {
				Log.d(TAG, "+ IPv4 routes: none");
			} else {
				Log.d(TAG, "+ IPv4 routes: " + routes4.toString());
			}
		} finally {
			mBindKit.unlock();
		}
		
		if(mPendingVpnTunnelUuid != null) {
			activateVpnTunnel(mPendingVpnTunnelUuid);
		} else {
			mCfgDelegate.setOnDemandEnabled(true);
			mCfgDelegate.updateCurrentConfiguration(true);
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
		
		mCfgDelegate.setOnDemandEnabled(false);
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
	public void notifySelectedTunnelUuidChanged() {
		String tunId = mCfgDelegate.getCurrentUuid();
		
		Intent intent = createBroadcastIntent();
		intent.putExtra(KEY_EVENT_TYPE, TYPE_SELECTED_TUNNEL_ID_CHANGED);
		intent.putExtra(KEY_TUNNEL_ID, tunId);
		if(mCurrentTunnel != null) {
			TunnelInfo info = mCurrentTunnel.getInfo();
			if(info.state == TUNNEL_STATE_CONNECTING || 
					info.state == TUNNEL_STATE_CONNECTED || 
					info.state == TUNNEL_STATE_DISCONNECTING) {
				intent.putExtra(KEY_IS_CONNECTION_UNPROTECTED, false);
			}
		}
				
		sendBroadcast(intent);
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
	public LocalNetworkConfig getLocalNetworkConfig() {
		return mCurrLocalNetConfig;
	}
	
	@Deprecated
	@Override
	public int getLocalAddress4() {
		return mCurrLocalNetConfig.getLocalIp();
	}
	
	@Deprecated
	@Override
	public int getRemoteAddress4() {
		return mCurrLocalNetConfig.getRemoteIp();
	}
	
	@Deprecated
	@Override
	public int getSubnetMask4() {
		return mCurrLocalNetConfig.getSubnetMask();
	}

	public RouterLoop getRouterLoop() {
		return mRouterLoop;
	}
	
	public VpnManagerHelperThread getHelperThread() {
		return mHelperThread;
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
	public String getSelectedVpnTunnelUuid() {
		return mCfgDelegate.getCurrentUuid();
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
		
		mPendingVpnTunnelUuid = null;
		
		if(uuid == null) {
			uuid = mLastVpnTunnelUuid;
		}
		
		if(mCurrentTunnel != null && uuid != null && uuid.equals(mCurrentTunnel.getTunnelId())) {
			Log.d(TAG, "Tunnel " + uuid + " is already activated");
			return;
		}
		
		//find appropriate tunnel and start connection
		VpnDataEx vpnData = mCfgDelegate.getTunnelData(uuid);
		
		VpnTunnel tun = null;
		
		try {
			tun = VpnTunnelFactory.getTunnel(getApplicationContext(), 
					this, 
					vpnData.getVpnType(),
					vpnData.getPayloadUuid(),
					vpnData.getOnDemandCredentials());
		} catch(Exception e) {
			Crashlytics.log("uuid is " + 
					(uuid == null ? "" : "not") + 
					" null, vpnData is " + 
					(vpnData == null ? "" : "not") +
					" null.");
			Crashlytics.logException(e);
		}
		
		if(tun == null) {
			Log.d(TAG, "Can't create tun " + vpnData.getVpnType() + " with id=" + vpnData.getPayloadUuid());
		} else {
			if(mLastVpnTunnelUuid == null) {
				mLastVpnTunnelUuid = tun.getTunnelId();
			}
			
			VpnTunnel oldTun = mCurrentTunnel;
			mCurrentTunnel = tun;
			tun.establishConnection();
			
			InetAddress[] dnsAddrs = mCurrLocalNetConfig.getDnsAddresses(null);
			ArrayList<String> virtualDnsAddrs = new ArrayList<String>(dnsAddrs.length);
			for(InetAddress addr : dnsAddrs) {
				if(addr != null) {
					virtualDnsAddrs.add(addr.getHostAddress());
				}
			}
			
			tun.setVirtualDnsAddrs(virtualDnsAddrs.toArray(new String[virtualDnsAddrs.size()]));
			
			if(mDebugDelegate.isDebugPcapEnabled()) {
				mDebugDelegate.debugStartTunnelPcap(mRouterLoop.getMtu(), tun);
			}
			
			//mRouterLoop.defaultRoute4(tun);
			
			if(oldTun != null) {
				oldTun.terminateConnection();
			}
			
			List<Route4> routes4 = mRouterLoop.getRoutes4();
			if(routes4 != null) {
				Log.d(TAG, "IPv4 routes: " + routes4.toString());
			}
			
			if(mRouterLoop.isPaused()) {
				mRouterLoop.pause(false);
			}
		}
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
	
	public void intlAddTunnelRoute4(VpnTunnel vpnTunnel, String address, int mask) {
		mRouterLoop.route4(vpnTunnel, address, mask);
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
	
	@Override
	public boolean isDebugPcapEnabled() {
		return mDebugDelegate.isDebugPcapEnabled();
	}

	@Override
	public boolean debugStartPcap() {
		if(mRouterLoop != null) {
			return mDebugDelegate.debugStartPcap(
					mRouterLoop.getMtu(), 
					mRouterLoop, 
					mUsernatTunnel, 
					mCurrentTunnel);
		}
		
		return false;
	}

	@Override
	public boolean debugStopPcap() {
		return mDebugDelegate.debugStopPcap(mRouterLoop, mUsernatTunnel, mCurrentTunnel);
	}

	public void onCurrentConfigChanged(String uuid) {
		AndroidProperties props = AndroidProperties.getInstance();
		String netDns[] = props.getNetworkSpecificDnsAddrs(this);
		
		if(uuid == null) {
			Log.w(TAG, "onCurrentConfigChanged(null)");
			return;
		}
		
		if(mCurrentTunnel != null && mCurrentTunnel.getTunnelId().equals(uuid)) {
			String logFmt = "Tunnel with id=%s is up and matches network configuration";
			Log.d(TAG, String.format(logFmt, uuid));
			return;
		}
		
		activateVpnTunnel(uuid);
	}
}
