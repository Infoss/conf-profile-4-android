package no.infoss.confprofile.vpn;

import java.net.InetAddress;
import java.util.List;

import no.infoss.confprofile.BuildConfig;
import no.infoss.confprofile.util.LocalNetworkConfig;
import no.infoss.confprofile.util.NetUtils;
import no.infoss.confprofile.util.PcapOutputStream;
import no.infoss.confprofile.vpn.OcpaVpnService.BuilderAdapter;
import no.infoss.confprofile.vpn.interfaces.Debuggable;
import android.util.Log;

/*package*/ class RouterLoop implements Runnable, Debuggable {
	public static final String TAG = RouterLoop.class.getSimpleName();
	
	private int mMtu;
	private VpnManagerInterface mVpnMgr;
	private BuilderAdapter mBuilder;
	private long mRouterCtx; //native
	
	private Thread mThread;
	
	public RouterLoop(VpnManagerInterface vpnMgr, BuilderAdapter builder) {
		mMtu = 1500;
		mVpnMgr = vpnMgr;
		mBuilder = builder;
		mThread = new Thread(this, TAG);
	}
	
	public Thread getThread() {
		return mThread;
	}
	
	public void startLoop() {
		mThread.start();
	}
	
	@Override
	public void run() {
		mRouterCtx = initIpRouter();
		if(mRouterCtx == 0) {
			Log.e(TAG, "Can't initialize router");
			return;
		}
		
		pause(true);
		
		synchronized (this) {
			if(!mBuilder.setMtu(mMtu)) {
				Log.d(TAG, "Can't set MTU=".concat(String.valueOf(1500)));
			}
			
			/*
			 * Here we set local address as 172.31.255.254.
			 * 172.31.255.253 will be used by usernat as "remote" address.
			 */
			LocalNetworkConfig netCfg = mVpnMgr.getLocalNetworkConfig();
			String addr = netCfg.getLocalAddr();
			int mask =netCfg.getSubnetMask();
			if(!mBuilder.addAddress(addr, mask)) {
				Log.d(TAG, "Can't add address=".concat(addr).concat("/").concat(String.valueOf(mask)));
			}
			
			if(!mBuilder.addRoute("0.0.0.0", 0)) {
				Log.d(TAG, "Can't add route=".concat("0.0.0.0/0"));
			}
			
			for(InetAddress inetAddr : mVpnMgr.getLocalNetworkConfig().getDnsAddresses(null)) {
				if(inetAddr != null) {
					Log.d(TAG, "Add DNS=".concat(inetAddr.toString()));
					if(!mBuilder.addDnsServer(inetAddr.getHostAddress())) {
						Log.d(TAG, "Can't add DNS=".concat(inetAddr.getHostAddress()));
					}
				}
			}
			
			setMasqueradeIp4(mRouterCtx, NetUtils.ip4StrToInt("172.31.255.254"));
			setMasqueradeIp4Mode(mRouterCtx, true);
			
			int result = routerLoop(mRouterCtx, mBuilder);
			Log.d(TAG, String.format("Router loop returned %d as exit code", result));
		}
		deinitIpRouter(mRouterCtx);
		mRouterCtx = 0;
	}
	
	public boolean isPaused() {
		return isPausedRouterLoop(mRouterCtx);
	}
	
	public boolean pause(boolean pause) {
		return pauseRouterLoop(mRouterCtx, pause);
	}
	
	public void terminate() {
		terminateRouterLoop(mRouterCtx);
		mRouterCtx = 0;
	}
	
	public void route4(VpnTunnel tunnel, String address, int mask) {
		if(!checkTunnel(tunnel, "IPv4 route")) {
			return;
		}
		
		addRoute4(mRouterCtx, NetUtils.ip4StrToInt(address), mask, tunnel.mVpnTunnelCtx);
	}
	
	public void defaultRoute4(VpnTunnel tunnel) {
		if(!checkTunnel(tunnel, "default IPv4 route")) {
			return;
		}
		
		defaultRoute4(mRouterCtx, tunnel.mVpnTunnelCtx);
	}
	
	public List<Route4> getRoutes4() {
		return getRoutes4(mRouterCtx);
	}
	
	public void defaultRoute6(VpnTunnel tunnel) {
		if(tunnel.mVpnTunnelCtx == 0) {
			Log.w(TAG, "Tunnel " + tunnel.getTunnelId() + " is unitialized and can't be used as default route");
			return;
		}
		//TODO: implement this
		//defaultRoute6(mRouterCtx, tunnel.mVpnTunnelCtx);
	}
	
	public void removeTunnel(VpnTunnel tunnel) {
		if(tunnel == null) {
			Log.w(TAG, "Trying to remove null tunnel");
			return;
		}
		
		removeTunnel(mRouterCtx, tunnel.mVpnTunnelCtx);
	}
	
	public int getMtu() {
		return mMtu;
	}

	/*package*/ long getRouterCtx() {
		return mRouterCtx;
	}
	
	@Override
	public boolean debugRestartPcap(PcapOutputStream pos) {
		debugRestartPcap(mRouterCtx, pos);
		return true;
	}
	
	@Override
	public boolean debugStopPcap() {
		debugStopPcap(mRouterCtx);
		return true;
	}
	
	@Override
	public String generatePcapFilename() {
		if(BuildConfig.DEBUG) {
			return String.format("router-%d.pcap", System.currentTimeMillis());
		}
		
		return null;
	}

	private void closeConnection() {
		synchronized (this) {
			deinitIpRouter(mRouterCtx);
			Log.i(TAG, "vpn connection stopped");
		}
	}
	
	private boolean protect(int sock) {
		return mVpnMgr.protect(sock);
	}
	
	private native long initIpRouter();
	private native void deinitIpRouter(long routerCtx);
	
	private native int routerLoop(long routerCtx, BuilderAdapter builder);
	
	/*package*/ native void addRoute4(long routerCtx, int ip4, int mask, long tunCtx);
	/*package*/ native void defaultRoute4(long routerCtx, long tunCtx);
	/*package*/ native void removeRoute4(long routerCtx, int ip4, int mask);
	/*package*/ native List<Route4> getRoutes4(long routerCtx);
	/*package*/ native void removeTunnel(long routerCtx, long tunCtx);
	/*package*/ native boolean isPausedRouterLoop(long routerCtx);
	/*package*/ native boolean pauseRouterLoop(long routerCtx, boolean pause);
	/*package*/ native void terminateRouterLoop(long routerCtx);
	
	//BEGIN debug methods
	/*package*/ native void debugRestartPcap(long routerCtx, PcapOutputStream pos);
	/*package*/ native void debugStopPcap(long routerCtx);
	//END debug methods
	
	private native void setMasqueradeIp4Mode(long routerCtx, boolean isOn);
	private native void setMasqueradeIp4(long routerCtx, int ip4);
	
	public static abstract class Route {
		protected long mVpnTunnelCtx;
		
		protected Route(long vpnTunnelCtx) {
			mVpnTunnelCtx = vpnTunnelCtx;
		}
	}
	
	private boolean checkTunnel(VpnTunnel tunnel) {
		return checkTunnel(tunnel, null);
	}
	
	private boolean checkTunnel(VpnTunnel tunnel, String purpose) {
		if(tunnel.mVpnTunnelCtx == 0) {
			Log.w(TAG, "Tunnel is null and can't be used".
					concat(purpose == null ? "" : "as ".concat(purpose)));
			return false;
		}
		
		if(tunnel.mVpnTunnelCtx == 0) {
			Log.w(TAG, "Tunnel ".
					concat(tunnel.getTunnelId()).
					concat(" is unitialized and can't be used").
					concat(purpose == null ? "" : "as ".concat(purpose)));
			return false;
		}
		
		return true;
	}
	
	public static class Route4 extends Route {
		protected int mIp4;
		protected int mMask4;

		protected Route4(long vpnTunnelCtx, int ip4, int mask4) {
			super(vpnTunnelCtx);
			mIp4 = ip4;
			mMask4 = mask4;
		}
		
		@Override
		public String toString() {
			int b1 = mIp4 >>> 24;
			int b2 = (mIp4 >>> 16) & 0x000000ff;
			int b3 = (mIp4 >>> 8) & 0x000000ff;
			int b4 = mIp4 & 0x000000ff;
			
			return String.format("%d.%d.%d.%d/%d [tun ctx %#016x]", b1, b2, b3, b4, mMask4, mVpnTunnelCtx);
		}
	}

}
