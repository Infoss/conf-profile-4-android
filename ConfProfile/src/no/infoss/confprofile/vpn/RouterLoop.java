package no.infoss.confprofile.vpn;

import java.util.List;

import no.infoss.confprofile.vpn.OcpaVpnService.BuilderAdapter;
import android.util.Log;

/*package*/ class RouterLoop implements Runnable {
	public static final String TAG = RouterLoop.class.getSimpleName();
	
	private VpnManagerInterface mVpnMgr;
	private BuilderAdapter mBuilder;
	private long mRouterCtx; //native
	
	private Thread mThread;
	
	public RouterLoop(VpnManagerInterface vpnMgr, BuilderAdapter builder) {
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
		
		while (true) {
			synchronized (this) {
				if(!mBuilder.setMtu(1500)) {
					Log.d(TAG, "Can't set MTU=".concat(String.valueOf(1500)));
				}
				if(!mBuilder.addAddress("172.31.255.254", 32)) {
					Log.d(TAG, "Can't add address=".concat("172.31.255.254/32"));
				}
				if(!mBuilder.addRoute("0.0.0.0", 0)) {
					Log.d(TAG, "Can't add route=".concat("0.0.0.0/0"));
				}
				routerLoop(mRouterCtx, mBuilder);
				break;
			}
		} //while
		deinitIpRouter(mRouterCtx);
	}
	
	public void terminate() {
		//TODO: termination here
	}

	/*package*/ long getRouterCtx() {
		return mRouterCtx;
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
	private native long createVpnServiceContext();
	private native void freeVpnServiceContext(long ctx);
	
	private native int routerLoop(long routerCtx, BuilderAdapter builder);
	
	/*package*/ native void addRoute4(long routerCtx, int ip4, long tunCtx);
	/*package*/ native void defaultRoute4(long routerCtx, long tunCtx);
	/*package*/ native void removeRoute4(long routerCtx, int ip4);
	/*package*/ native List<Route4> getRoutes4(long routerCtx);
	
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
			
			return String.format("%d\\.%d\\.%d\\.%d/%d", b1, b2, b3, b4, mMask4);
		}
	}
}
