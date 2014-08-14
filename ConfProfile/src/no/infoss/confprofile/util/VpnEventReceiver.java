package no.infoss.confprofile.util;

import no.infoss.confprofile.vpn.VpnManagerInterface;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class VpnEventReceiver extends BroadcastReceiverEx<VpnEventReceiver.VpnEventListener> {

	public VpnEventReceiver(Context context, VpnEventListener listener) {
		super(context, listener);
		
	}
	
	public void register() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(VpnManagerInterface.BROADCAST_VPN_EVENT);
		
		register(filter);
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		String evtType = intent.getStringExtra(VpnManagerInterface.KEY_EVENT_TYPE);
		if(VpnManagerInterface.TYPE_SERVICE_STATE_CHANGED.equals(evtType)) {
			int serviceState = intent.getIntExtra(
					VpnManagerInterface.KEY_SERVICE_STATE, 
					VpnManagerInterface.SERVICE_STATE_REVOKED);
			getListener().onReceivedServiceState(serviceState, true);
		} else if(VpnManagerInterface.TYPE_TUNNEL_STATE_CHANGED.equals(evtType)) {
			String tunnelId = intent.getStringExtra(VpnManagerInterface.KEY_TUNNEL_ID);
			int state = intent.getIntExtra(
					VpnManagerInterface.KEY_TUNNEL_STATE, 
					VpnManagerInterface.TUNNEL_STATE_DISCONNECTED);
			getListener().onReceivedTunnelState(tunnelId, state, true);
		}
	}

	public interface VpnEventListener {
		public void onReceivedServiceState(int state, boolean isBroadcast);
		public void onReceivedTunnelState(String tunnelId, int state, boolean isBroadcast);
	}
}
