package no.infoss.confprofile.vpn;

import java.io.File;
import java.net.Socket;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import no.infoss.confprofile.BuildConfig;
import no.infoss.confprofile.R;
import no.infoss.confprofile.StartVpn;
import no.infoss.confprofile.task.ObtainOnDemandVpns;
import no.infoss.confprofile.task.ObtainOnDemandVpns.ObtainOnDemandVpnsListener;
import no.infoss.confprofile.util.MiscUtils;
import no.infoss.confprofile.util.PcapOutputStream;
import no.infoss.confprofile.util.SimpleServiceBindKit;
import no.infoss.confprofile.vpn.RouterLoop.Route4;
import no.infoss.jcajce.InfossJcaProvider;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class VpnManagerService extends Service implements VpnManagerInterface, ObtainOnDemandVpnsListener {
	public static final String TAG = VpnManagerService.class.getSimpleName();
	
	static {
		Security.addProvider(new InfossJcaProvider());
	}
	
	private NotificationManager mNtfMgr;
	private Binder mBinder = new Binder();
	private NetworkStateListener mNetworkListener;
	
	private SimpleServiceBindKit<OcpaVpnInterface> mBindKit;
	private boolean mIsVpnServiceStarted = false;
	
	private RouterLoop mRouterLoop = null;
	
	private VpnTunnel mCurrentTunnel = null;
	private VpnTunnel mUsernatTunnel = null;
	
	private boolean mIsRequestActive = false;
	private boolean mReevaluateOnRequest = false;
	private List<VpnConfigInfo> mConfigInfos;
	
	private NetworkConfig mSavedNetworkConfig;
	private boolean mSavedFailoverFlag;
	
	private int[] mVpnManagerIcons;
	private int[] mVpnManagerErrorIcons;
	
	@Override
	public void onCreate() {
		super.onCreate();
		mNetworkListener = new NetworkStateListener(getApplicationContext(), this);
		obtainOnDemandVpns();
		initIcons();
		
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
		if(!mIsVpnServiceStarted) {
			Intent intent = new Intent(this, StartVpn.class);
			intent.setAction(StartVpn.ACTION_CALL_PREPARE);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		}
	}
	
	@Override
	public void notifyVpnServiceStarted() {
		if(mRouterLoop != null) {
			mRouterLoop.terminate();
		}
		
		OcpaVpnInterface vpnService = mBindKit.lock();
		try {
			mRouterLoop = new RouterLoop(VpnManagerService.this, vpnService.createBuilderAdapter("OCPA"));
			updateCurrentConfiguration();
			mRouterLoop.startLoop();
		} finally {
			mBindKit.unlock();
		}
		
		mIsVpnServiceStarted = true;
		
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
		mIsVpnServiceStarted = false;
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
	public boolean debugStartPcap() {
		if(BuildConfig.DEBUG) {
			if(!MiscUtils.isExternalStorageWriteable()) {
				return false;
			}
			
			File externalFilesDir = getExternalFilesDir(null);
			if(externalFilesDir == null) {
				//error: storage error
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
				try {
					pcapFileName = String.format(
							"tun(%s)-%d.pcap", 
							mCurrentTunnel.getTunnelId(), 
							System.currentTimeMillis());
					os = new PcapOutputStream(
							new File(externalFilesDir, pcapFileName), 
							mtu, 
							PcapOutputStream.LINKTYPE_RAW);
					mCurrentTunnel.debugRestartPcap(os);
				} catch(Exception e) {
					Log.e(TAG, "Restart pcap error", e);
				}
			}
			
			//capture from usernat
			if(mUsernatTunnel != null) {
				try {
					pcapFileName = String.format(
							"usernat(%s)-%d.pcap", 
							mUsernatTunnel.getTunnelId(), 
							System.currentTimeMillis());
					os = new PcapOutputStream(
							new File(externalFilesDir, pcapFileName), 
							mtu, 
							PcapOutputStream.LINKTYPE_RAW);
					mUsernatTunnel.debugRestartPcap(os);
				} catch(Exception e) {
					Log.e(TAG, "Restart pcap error", e);
				}
			}
		}
		return true;
	}
	
	@Override
	public boolean debugStopPcap() {
		if(BuildConfig.DEBUG) {
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
		}
		return true;
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
