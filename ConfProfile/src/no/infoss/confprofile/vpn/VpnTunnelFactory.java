package no.infoss.confprofile.vpn;

import android.content.Context;

public class VpnTunnelFactory {
	public VpnTunnel getTunnel(Context ctx, VpnManagerService mgr, String type) {
		VpnTunnel tunnel = null;
		
		if(OpenVpnTunnel.VPN_TYPE.equals(type)) {
			RouterLoop routerLoop = mgr.getRouterLoop();
			if(routerLoop != null) {
				tunnel = new OpenVpnTunnel(ctx, routerLoop.getRouterCtx(), mgr);
			}
		}
		
		return tunnel;
	}
}
