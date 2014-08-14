package no.infoss.confprofile.vpn;

import java.net.Socket;

import android.os.IInterface;

public interface VpnManagerInterface extends IInterface {
	public static final String TAG = VpnManagerInterface.class.getSimpleName();
	
	public static final String BROADCAST_VPN_EVENT = 
			VpnManagerInterface.class.getCanonicalName().concat(".VPN_EVENT");
	
	public static final String KEY_EVENT_TYPE = "EVENT_TYPE";
	public static final String TYPE_SERVICE_STATE_CHANGED = "SERVICE_STATE_CHANGED";
	public static final String TYPE_TUNNEL_STATE_CHANGED = "TUNNEL_STATE_CHANGED";
	
	public static final String KEY_SERVICE_STATE = "SERVICE_STATE";
	public static final int SERVICE_STATE_REVOKED = 0;
	public static final int SERVICE_STATE_STARTED = 1;
	public static final int SERVICE_STATE_LOCKED = -1;
	public static final int SERVICE_STATE_UNSUPPORTED = -2;
	
	public static final String KEY_TUNNEL_ID = "TUNNEL_ID";
	public static final String KEY_TUNNEL_STATE = "TUNNEL_STATE";
	public static final int TUNNEL_STATE_DISCONNECTED = 0;
	public static final int TUNNEL_STATE_CONNECTING = 1;
	public static final int TUNNEL_STATE_CONNECTED = 2;
	public static final int TUNNEL_STATE_DISCONNECTING = 3;
	public static final int TUNNEL_STATE_TERMINATED = 4;
	
	public void startVpnService();
	public int getVpnServiceState();
	public void notifyVpnServiceStarted();
	public void notifyVpnServiceRevoked();
	public void notifyTunnelStateChanged();
	
	public void notifyConnectivityLost(NetworkConfig netConfig, boolean isFailover);
	public void notifyConnectivityChanged(NetworkConfig netConfig, boolean isFailover);
	
	/**
	 * Called by StartVpn activity when system can't prepare VPN tunnel due to "VPN Always on" feature 
	 * (Android 4.2+). Just turn this feature off to make this application work correctly.
	 */
	public void notifyVpnLockedBySystem();
	
	/**
	 * Called by StartVpn activity when system image doesn't contain Android VPN API.
	 * Usually it happens with modified/custom firmware or by vendor's solution.
	 * Unfortunately, this is NOT Android due to incomplete API. 
	 */
	public void notifyVpnIsUnsupported();
	
	public void activateVpnTunnel(String uuid);
	public String getVpnTunnelId();
	public int getVpnTunnelState();
	
	public int getLocalAddress4();
	public int getRemoteAddress4();
	public int getSubnetMask4();
	
	//proxy methods to OcpaVpnService
	public boolean protect(int socket);
	public boolean protect(Socket socket);
	
	//debug methods
	public boolean isDebugPcapEnabled();
	public boolean debugStartPcap();
	public boolean debugStopPcap();
}
