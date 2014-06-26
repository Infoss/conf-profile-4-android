package no.infoss.confprofile.vpn;

import java.net.Socket;

import android.os.IInterface;

public interface VpnManagerInterface extends IInterface {
	public static final String TAG = VpnManagerInterface.class.getSimpleName();
	
	public void startVpnService();
	public void notifyVpnServiceStarted();
	public void notifyVpnServiceRevoked();
	
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
	
	//proxy methods to OcpaVpnService
	public boolean protect(int socket);
	public boolean protect(Socket socket);
	
	//debug methods
	public boolean isDebugPcapEnabled();
	public boolean debugStartPcap();
	public boolean debugStopPcap();
}
