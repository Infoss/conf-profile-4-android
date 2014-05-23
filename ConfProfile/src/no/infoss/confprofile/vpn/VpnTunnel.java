package no.infoss.confprofile.vpn;

import java.util.Map;

import android.content.Context;

public abstract class VpnTunnel implements Runnable {
	
	protected Thread mThread;
	protected Context mCtx;
	protected long mVpnServiceCtx; //native
	protected long mVpnTunnelCtx; //native
	
	public VpnTunnel(Context ctx) {
		mThread = new Thread(this);
		mCtx = ctx;
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
