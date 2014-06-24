package no.infoss.confprofile.vpn;

import java.util.Map;

import no.infoss.confprofile.util.PcapOutputStream;
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
	public abstract void establishConnection(Map<String, Object> options);
	public abstract void terminateConnection();
	
	public final String getTunnelId() {
		if(mCfg == null) {
			return null;
		}
		
		return mCfg.configId;
	}
	
	public final Context getContext() {
		return mCtx;
	}
	
	public void startLoop() {
		mThread.setName(getThreadName());
		mThread.start();
	}
	
	/*package*/ long getTunnelCtx() {
		return mVpnTunnelCtx;
	}
	
	/*package*/ void processDied() {
		//default implementation
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
	
	protected void debugRestartPcap(PcapOutputStream pos) {
		debugRestartPcap(mVpnTunnelCtx, pos);
	}
	
	protected void debugStopPcap() {
		debugStopPcap(mVpnTunnelCtx);
	}
	
	private native void setMasqueradeIp4Mode(long vpnTunnelCtx, boolean isOn);
	private native void setMasqueradeIp4(long vpnTunnelCtx, int ip4);
	private native void setMasqueradeIp6Mode(long vpnTunnelCtx, boolean isOn);
	private native void setMasqueradeIp6(long vpnTunnelCtx, byte[] ip6);
	
	private native void debugRestartPcap(long vpnTunnelCtx, PcapOutputStream pos);
	private native void debugStopPcap(long vpnTunnelCtx);
}
