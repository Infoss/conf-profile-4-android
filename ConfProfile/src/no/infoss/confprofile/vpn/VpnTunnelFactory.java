package no.infoss.confprofile.vpn;

import no.infoss.confprofile.vpn.VpnManagerService.VpnConfigInfo;
import android.content.Context;

public class VpnTunnelFactory {
	public static VpnTunnel getTunnel(Context ctx, VpnManagerService mgr, VpnConfigInfo cfg) {
		VpnTunnel tunnel = null;
		
		if(OpenVpnTunnel.VPN_TYPE.equals(cfg.vpnType)) {
			RouterLoop routerLoop = mgr.getRouterLoop();
			if(routerLoop != null) {
				tunnel = new OpenVpnTunnel(ctx, routerLoop.getRouterCtx(), mgr, cfg);
			}
		}
		
		return tunnel;
	}
}
