package no.infoss.confprofile.vpn;

import no.infoss.confprofile.vpn.VpnManagerService.VpnConfigInfo;
import android.content.Context;

public class VpnTunnelFactory {
	
	@Deprecated
	public static VpnTunnel getTunnel(Context ctx, VpnManagerService mgr, VpnConfigInfo cfg) {
		VpnTunnel tunnel = null;
		
		RouterLoop routerLoop = mgr.getRouterLoop();
		
		if(IpSecTunnel.VPN_TYPE.equals(cfg.vpnType)) {
			if(routerLoop != null) {
				tunnel = new IpSecTunnel(ctx, routerLoop.getRouterCtx(), mgr, cfg);
			}
		} else if(OpenVpnTunnel.VPN_TYPE.equals(cfg.vpnType)) {
			if(routerLoop != null) {
				tunnel = new OpenVpnTunnel(ctx, routerLoop.getRouterCtx(), mgr, cfg);
			}
		} else if(L2tpTunnel.VPN_TYPE.equals(cfg.vpnType)) {
			if(routerLoop != null) {
				tunnel = new L2tpTunnel(ctx, routerLoop.getRouterCtx(), mgr, cfg);
			}
		}  
		
		return tunnel;
	}
	
	public static VpnTunnel getTunnel(Context ctx, VpnManagerService mgr, String vpnType, String uuid, String cfg) {
		VpnTunnel tunnel = null;
		
		RouterLoop routerLoop = mgr.getRouterLoop();
		
		if(IpSecTunnel.VPN_TYPE.equals(vpnType)) {
			if(routerLoop != null) {
				tunnel = new IpSecTunnel(ctx, mgr, uuid, cfg);
			}
		} else if(OpenVpnTunnel.VPN_TYPE.equals(vpnType)) {
			if(routerLoop != null) {
				tunnel = new OpenVpnTunnel(ctx, mgr, uuid, cfg);
			}
		} else if(L2tpTunnel.VPN_TYPE.equals(vpnType)) {
			if(routerLoop != null) {
				tunnel = new L2tpTunnel(ctx, mgr, uuid, cfg);
			}
		}  
		
		return tunnel;
	}
}
