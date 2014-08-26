package no.infoss.confprofile.vpn.delegates;

import no.infoss.confprofile.vpn.VpnManagerService;

public abstract class VpnManagerDelegate {
	private VpnManagerService mVpnMgr;
	
	public VpnManagerDelegate(VpnManagerService vpnMgr) {
		mVpnMgr = vpnMgr;
	}
	
	public final void releaseResources() {
		mVpnMgr = null;
		doReleaseResources();
	}
	
	protected void doReleaseResources() {
		
	}
	
	protected final VpnManagerService getVpnManager() {
		return mVpnMgr;
	}
	
}
