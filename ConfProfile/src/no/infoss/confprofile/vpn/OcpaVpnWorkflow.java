package no.infoss.confprofile.vpn;

import no.infoss.confprofile.vpn.OcpaVpnService.BuilderAdapter;
import android.util.Log;

/*package*/ class OcpaVpnWorkflow implements Runnable {
	public static final String TAG = OcpaVpnWorkflow.class.getSimpleName();
	
	private VpnConnectionProfile mProfile;
	private long mProfileTs;
	private OcpaVpnService mVpnService;
	private long mRouterCtx; //native
	
	public OcpaVpnWorkflow(OcpaVpnService vpnService) {
		mVpnService = vpnService;
	}
	
	/*package*/ void updateProfile(VpnConnectionProfile newProfile) {
		mProfile = newProfile;
		mProfileTs = System.currentTimeMillis();
		this.notifyAll();
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
				try {
					long savedTs = mProfileTs;
					while(savedTs >= mProfileTs) {
						wait();
					}
					
					closeConnection();
					if(mProfile == null) {
							break;
					} else {
						mVpnService.notifyConnectionStarted(mProfile);

						BuilderAdapter builder = mVpnService.getBuilderForProfile(mProfile);
						builder.setMtu(1500);
						routerLoop(builder.establish(), 1500);

					}
				} catch (InterruptedException e) {
					closeConnection();
				}
			}
		} //while
		deinitIpRouter(mRouterCtx);
	}


	private void closeConnection() {
		synchronized (this) {
			if (mProfile != null) {
				deinitIpRouter(mRouterCtx);
				Log.i(TAG, "vpn connection stopped");
				mProfile = null;
			}
		}
	}
	
	private boolean protect(int sock) {
		return mVpnService.protect(sock);
	}
	
	private native long initIpRouter();
	private native void deinitIpRouter(long routerCtx);
	private native long createVpnServiceContext();
	private native void freeVpnServiceContext(long ctx);
	
	private native int routerLoop(int fd, int mtu);
}
