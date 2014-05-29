package no.infoss.confprofile.vpn;

import java.util.Map;

import no.infoss.confprofile.vpn.VpnManagerService.VpnConfigInfo;
import android.content.Context;

public abstract class VpnTunnel implements Runnable {
	
	protected Thread mThread;
	protected Context mCtx;
	protected VpnConfigInfo mCfg;
	protected long mVpnServiceCtx; //native
	protected long mVpnTunnelCtx; //native
	
	public VpnTunnel(Context ctx, VpnConfigInfo cfg) {
		mThread = new Thread(this);
		mCtx = ctx;
		mCfg = cfg;
	}
	
	protected abstract String getThreadName();
	public abstract String getTunnelId();
	public abstract void establishConnection(Map<String, Object> options);
	public abstract void terminateConnection();
	
	public void startLoop() {
		mThread.setName(getThreadName());
		mThread.start();
	}
	
	/*package*/ long getTunnelCtx() {
		return mVpnTunnelCtx;
	}
}
