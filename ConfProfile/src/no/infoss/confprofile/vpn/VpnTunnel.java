package no.infoss.confprofile.vpn;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import no.infoss.confprofile.BuildConfig;
import no.infoss.confprofile.format.json.BuiltinTypeAdapterFactory;
import no.infoss.confprofile.util.MiscUtils;
import no.infoss.confprofile.util.NetUtils;
import no.infoss.confprofile.util.PcapOutputStream;
import no.infoss.confprofile.vpn.interfaces.Debuggable;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public abstract class VpnTunnel implements Runnable, Debuggable {
	public static final String TAG = VpnTunnel.class.getSimpleName();
	
	public static final int LOG_VERBOSE = 2;
	public static final int LOG_DEBUG = 3;
	public static final int LOG_INFO = 4;
	public static final int LOG_WARN = 5;
	public static final int LOG_ERROR = 6;
	public static final int LOG_FATAL = 7;
	
	protected final String mInstanceLogTag;
	protected final Logger mLogger;
	protected final Handler mHandler;
	private final List<TunnelStateListener> mListeners;
	protected final VpnManagerInterface mVpnMgr;
	private final String mTunnelUuid;
	
	protected Thread mThread;
	protected Context mCtx;
	protected String mCfgStr;
	private ConnectionStatus mConnectionStatus;
	protected ConnectionError mConnectionError;
	private Date mConnectedSince;
	private String mServerName;
	private String mLocalAddress;
	private String mRemoteAddress;
	
	protected long mVpnTunnelCtx; //native
	
	enum ConnectionStatus {
		DISCONNECTED,
		CONNECTING,
		CONNECTED,
		DISCONNECTING,
		TERMINATED
	}
	
	enum ConnectionError {
		NO_ERROR,
		GENERIC_ERROR,
		GENERIC_NETWORK_ERROR,
		AUTH_FAILED
	}
	
	public VpnTunnel(Context ctx, String uuid, String cfg, VpnManagerInterface vpnMgr) {
		mThread = new Thread(this);
		mCtx = ctx;
		
		mInstanceLogTag = String.format("%s (id=%d)", getClass().getSimpleName(), mThread.getId());
		mLogger = new Logger();
		mHandler = new Handler(Looper.getMainLooper());
		mListeners = new ArrayList<TunnelStateListener>();
		
		mConnectionStatus = ConnectionStatus.DISCONNECTED;
		mConnectionError = ConnectionError.NO_ERROR;
		mConnectedSince = null;
		mServerName = null;
		mLocalAddress = null;
		mRemoteAddress = null;
		
		mVpnMgr = vpnMgr;

		mTunnelUuid = uuid;
		mCfgStr = cfg;
	}
	
	
	
	protected abstract String getThreadName();
	
	public final void establishConnection() {
		if(isTerminated()) {
			return;
		}
		
		if(getConnectionStatus() != ConnectionStatus.DISCONNECTED) {
			return;
		}
		
		doEstablishConnection();
	}
	
	protected abstract void doEstablishConnection();
	
	@SuppressWarnings("unchecked")
	protected final Map<String, Object> doParseCredentials() {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapterFactory(new BuiltinTypeAdapterFactory());
		Gson gson = gsonBuilder.create();
		
		return gson.fromJson(mCfgStr, Map.class);
	}
	
	public abstract void terminateConnection();
	
	public final String getTunnelId() {
		return mTunnelUuid;
	}
	
	public final Context getContext() {
		return mCtx;
	}
	
	public final TunnelInfo getInfo() {
		return new TunnelInfo(getTunnelId(), 
				VpnManagerService.connectionStatusToInt(getConnectionStatus()), 
				mConnectedSince, 
				mServerName, 
				mLocalAddress, 
				mRemoteAddress);
	}
	
	public final ConnectionStatus getConnectionStatus() {
		return mConnectionStatus;
	}
	
	public final ConnectionError getConnectionError() {
		return mConnectionError;
	}
	
	public final boolean isTerminated() {
		return (mConnectionStatus == ConnectionStatus.TERMINATED);
	}
	
	public final void startLoop() {
		mThread.setName(getThreadName());
		mThread.start();
	}
	
	/*package*/ long getTunnelCtx() {
		return mVpnTunnelCtx;
	}
	
	/*package*/ void processDied() {
		//default implementation
	}
	
	protected boolean setConnectionStatus(ConnectionStatus status) {
		if(status != mConnectionStatus) {
			if(status == ConnectionStatus.CONNECTED) {
				mConnectedSince = new Date();
			}
			mConnectionStatus = status;
			mVpnMgr.notifyTunnelStateChanged();
			return true;
		}
		
		return false;
	}
	
	protected void setServerName(String serverName) {
		mServerName = serverName;
	}
	
	protected void setLocalAddress(String localAddress) {
		mLocalAddress = localAddress;
	}
	
	protected void setRemoteAddress(String remoteAddress) {
		mRemoteAddress = remoteAddress;
	}
	
	protected void setDnsAddress(int idx, int addr) {
		setDnsIp4(mVpnTunnelCtx, idx, addr);
	}
	
	protected void setMasqueradeIp4Mode(boolean isOn) {
		setMasqueradeIp4Mode(mVpnTunnelCtx, isOn);
	}
	
	protected void setMasqueradeIp4(int ip4) {
		setMasqueradeIp4(mVpnTunnelCtx, ip4);
	}
	
	protected void setMasqueradeIp6Mode(boolean isOn) {
		setMasqueradeIp6Mode(mVpnTunnelCtx, isOn);
	}
	
	protected void setMasqueradeIp6(byte[] ip6) {
		if(ip6 == null || ip6.length != 16) {
			return;
		}
		setMasqueradeIp6(mVpnTunnelCtx, ip6);
	}
	
	public void setDnsAddrs(String[] addrs) {
		int i = 0;
		
		for(; i < 4; i++) {
			setDnsIp4(mVpnTunnelCtx, i, 0);
		}
		
		i = 0;
		for(String addr : addrs) {
			if(addr != null && !addr.isEmpty()) {
				try {
					setDnsIp4(mVpnTunnelCtx, i, NetUtils.ip4StrToInt(addr));
				} catch(Exception e) {
					//suppress this
				}
			}
			i++;
		}
	}
	
	public void setVirtualDnsAddrs(String[] addrs) {
		int i = 0;
		
		for(; i < 4; i++) {
			setVirtualDnsIp4(mVpnTunnelCtx, i, 0);
		}
		
		i = 0;
		for(String addr : addrs) {
			if(addr != null && !addr.isEmpty()) {
				try {
					setVirtualDnsIp4(mVpnTunnelCtx, i, NetUtils.ip4StrToInt(addr));
				} catch(Exception e) {
					//suppress this
				}
			}
			i++;
		}
	}
	
	/**
	 * Shortcut method for calling from the native code
	 * @param fd socket descriptor
	 * @return true if success
	 */
	protected boolean protectSocket(int fd) {
		if(mVpnMgr == null) {
			return false;
		}
		
		return mVpnMgr.protect(fd);
	}
	
	/*from IpSecVpnStateService*/
	/**
	 * Update state and notify all listeners about the change. By using a Handler
	 * this is done from the main UI thread and not the initial reporter thread.
	 * Also, in doing the actual state change from the main thread, listeners
	 * see all changes and none are skipped.
	 *
	 * @param change the state update to perform before notifying listeners, returns true if state changed
	 */
	protected final void notifyListeners(final Callable<Boolean> change) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				try {
					if(change.call()) {
						/* otherwise there is no need to notify the listeners */
						for(TunnelStateListener listener : mListeners) {
							listener.stateChanged();
						}
					}
				} catch(Exception e) {
					Log.e(TAG, "Error while notifying listeners", e);
				}
			}
		});
	}
	
	public final boolean debugRestartPcap(PcapOutputStream pos) {
		debugRestartPcap(mVpnTunnelCtx, pos);
		return true;
	}
	
	public final boolean debugStopPcap() {
		debugStopPcap(mVpnTunnelCtx);
		return true;
	}
	
	@Override
	public String generatePcapFilename() {
		if(BuildConfig.DEBUG) {
			return String.format(
					"tun(%s)-%d.pcap", 
					getTunnelId(), 
					System.currentTimeMillis());
		}
		
		return null;
	}
	
	private native void setDnsIp4(long vpnTunnelCtx, int idx, int ip4);
	private native void setVirtualDnsIp4(long vpnTunnelCtx, int idx, int ip4);
	private native void setMasqueradeIp4Mode(long vpnTunnelCtx, boolean isOn);
	private native void setMasqueradeIp4(long vpnTunnelCtx, int ip4);
	private native void setMasqueradeIp6Mode(long vpnTunnelCtx, boolean isOn);
	private native void setMasqueradeIp6(long vpnTunnelCtx, byte[] ip6);
	
	private native void debugRestartPcap(long vpnTunnelCtx, PcapOutputStream pos);
	private native void debugStopPcap(long vpnTunnelCtx);
	
	public interface TunnelStateListener {
		public void stateChanged();
	}
	
	private final void debugLogMessage(int level, String msg) {
		switch (level) {
		case LOG_FATAL: 
		case LOG_ERROR: {
			Log.e(mInstanceLogTag, msg);
			break;
		}
		case LOG_WARN: {
			Log.w(mInstanceLogTag, msg);
			break;
		}
		case LOG_INFO: {
			Log.i(mInstanceLogTag, msg);
			break;
		}
		case LOG_DEBUG: {
			Log.d(mInstanceLogTag, msg);
			break;
		}
		case LOG_VERBOSE: {
			Log.v(mInstanceLogTag, msg);
			break;
		}
		default: {
			//silently drop this message
			break;
		}
		}
	}
	
	/*package*/ class Logger {
		private static final String LOG_FMT = "[%s] [level=%d] %s\n";
		
		private boolean mIsInitialized = false;
		private boolean mIsDeactivated = false;
		private SimpleDateFormat mFmt = new SimpleDateFormat("HH:mm:ss.SSS");
		private File mLogFile;
		private OutputStream mOs;
		private PrintWriter mWriter;
		
		private synchronized void init() {
			if(mIsInitialized || mIsDeactivated) {
				return;
			}
			
			try {
				if(!MiscUtils.isExternalStorageWriteable()) {
					Log.e(mInstanceLogTag, "Logger is unable to write external storage (storage is RO or unmounted");
					mIsDeactivated = true;
					return;
				}
				
				File externalFilesDir = VpnTunnel.this.getContext().getExternalFilesDir(null);
				if(externalFilesDir == null) {
					//error: storage error
					Log.e(mInstanceLogTag, "Logger is unable to write external storage (storage error)");
					return;
				}
				
				String logFileName = String.format("report-%d-(%s).log", 
						System.currentTimeMillis(), 
						VpnTunnel.this.getTunnelId());
				mLogFile = new File(externalFilesDir, logFileName);
				FileOutputStream fos = new FileOutputStream(mLogFile);
				mOs = new BufferedOutputStream(fos, 128 * 1024);
				
				String headerFmt = "Current date is %s\n";
				SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
				mOs.write(String.format(headerFmt, dateFmt.format(new Date())).getBytes("UTF-8"));
				
				mWriter = new PrintWriter(mOs);
				
				mIsInitialized = true;
			} catch(Exception e) {
				Log.e(mInstanceLogTag, "Can't init logger", e);
				close();
			}
		}
		
		public synchronized void log(int level, String data) {
			if(BuildConfig.DEBUG) {
				VpnTunnel.this.debugLogMessage(level, data);
			}
			
			if(mIsDeactivated) {
				return;
			}
			
			if(!mIsInitialized) {
				init();
			}
			
			if(!mIsInitialized) {
				return;
			}
			
			try {
				synchronized(mFmt) {
					String logMsg = String.format(LOG_FMT, mFmt.format(new Date()), level, data);
					mOs.write(logMsg.getBytes("UTF-8"));
					mOs.flush();
				}
			} catch(Exception e) {
				Log.e(mInstanceLogTag, "Can't log a message, closing a logger", e);
				close();
			}
		}
		
		public synchronized void logException(int level, String data, Exception ex) {
			if(BuildConfig.DEBUG) {
				VpnTunnel.this.debugLogMessage(level, data);
			}
			
			if(mIsDeactivated) {
				return;
			}
			
			if(!mIsInitialized) {
				init();
			}
			
			if(!mIsInitialized) {
				return;
			}
			
			try {
				synchronized(mFmt) {
					String logMsg = String.format(LOG_FMT, mFmt.format(new Date()), level, data);
					mOs.write(logMsg.getBytes("UTF-8"));
					if(ex != null) {
						mWriter.print("\n");
						ex.printStackTrace(mWriter);
						mWriter.print("\n");
						mWriter.flush();
					}
				}
			} catch(Exception e) {
				Log.e(mInstanceLogTag, "Can't log a message, closing a logger", e);
				close();
			}
		}
		
		public void close() {
			mIsInitialized = false;
			mIsDeactivated = true;
		}
	}
	
	public static class TunnelInfo {
		public final String uuid;
		public final int state;
		public final Date connectedSince;
		public final String serverName;
		public final String localAddress;
		public final String remoteAddress;
		
		public TunnelInfo(String uuid, 
				int state, 
				Date connectedSince, 
				String serverName, 
				String localAddress, 
				String remoteAddress) {
			this.uuid = uuid;
			this.state = state;
			if(connectedSince != null) {
				this.connectedSince = new Date(connectedSince.getTime());
			} else {
				this.connectedSince = null;
			}
			this.serverName = serverName;
			this.localAddress = localAddress;
			this.remoteAddress = remoteAddress;
		}
	}
}
