package no.infoss.confprofile.vpn;

import java.util.Map;

import no.infoss.confprofile.vpn.VpnManagerService.VpnConfigInfo;
import android.content.Context;

public class UsernatTunnel extends VpnTunnel {
	public static final String TAG = UsernatTunnel.class.getSimpleName();
	
	private static final VpnConfigInfo VPN_CFG_INFO;
	static {
		VPN_CFG_INFO = new VpnConfigInfo();
	}

	public UsernatTunnel(Context ctx) {
		super(ctx, VPN_CFG_INFO);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub

	}

	@Override
	protected String getThreadName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void establishConnection(Map<String, Object> options) {
		// TODO Auto-generated method stub

	}

	@Override
	public void terminateConnection() {
		// TODO Auto-generated method stub

	}
}
