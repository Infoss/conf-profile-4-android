package no.infoss.confprofile.vpn;

import java.util.List;

import no.infoss.confprofile.util.NetUtils;
import no.infoss.confprofile.util.PcapOutputStream;
import no.infoss.confprofile.vpn.OcpaVpnService.BuilderAdapter;
import android.util.Log;

/*package*/ class RouterLoop implements Runnable {
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
			if(!mBuilder.addAddress("172.31.255.254", 30)) {
				Log.d(TAG, "Can't add address=".concat("172.31.255.254/30"));
			}
			
			if(!mBuilder.addRoute("0.0.0.0", 0)) {
				Log.d(TAG, "Can't add route=".concat("0.0.0.0/0"));
			}
			
			setMasqueradeIp4(mRouterCtx, NetUtils.ip4StrToInt("172.31.255.254"));
			setMasqueradeIp4Mode(mRouterCtx, true);
			
			int result = routerLoop(mRouterCtx, mBuilder);
			Log.d(TAG, String.format("Router loop returned %d as exit code", result));
		}
		deinitIpRouter(mRouterCtx);
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
	
	public void defaultRoute4(VpnTunnel tunnel) {
		if(tunnel.mVpnTunnelCtx == 0) {
			Log.w(TAG, "Tunnel " + tunnel.getTunnelId() + " is unitialized and can't be used as default route");
			return;
		}
		defaultRoute4(mRouterCtx, tunnel.mVpnTunnelCtx);
	}
	
	public List<Route4> getRoutes4() {
		return getRoutes4(mRouterCtx);
	}
	
	public int getMtu() {
		return mMtu;
	}

	/*package*/ long getRouterCtx() {
		return mRouterCtx;
	}
	
	/*package*/ void debugRestartPcap(PcapOutputStream pos) {
		debugRestartPcap(mRouterCtx, pos);
	}
	
	/*package*/ void debugStopPcap() {
		debugStopPcap(mRouterCtx);
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
