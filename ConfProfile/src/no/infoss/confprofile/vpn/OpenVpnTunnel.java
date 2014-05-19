package no.infoss.confprofile.vpn;

public class OpenVpnTunnel extends VpnTunnel {
	public static final String TAG = OpenVpnTunnel.class.getSimpleName();
	
	public static final String VPN_TYPE = "net.openvpn.OpenVPN-Connect.vpnplugin";
	
	private long mVpnServiceCtx; //native
	
	/*package*/ OpenVpnTunnel(long vpnServiceCtx) {
		mVpnServiceCtx = vpnServiceCtx;
	}

	@Override
	public String getTunnelId() {
		return VPN_TYPE;
	}

}
