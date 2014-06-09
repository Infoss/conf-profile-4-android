package no.infoss.confprofile.vpn;

import java.net.Socket;

import android.os.IInterface;

public interface VpnManagerInterface extends IInterface {
	public static final String TAG = VpnManagerInterface.class.getSimpleName();
	
	public void notifyVpnServiceRevoked();
	
	public void notifyConnectivityLost(NetworkConfig netConfig, boolean isFailover);
	public void notifyConnectivityChanged(NetworkConfig netConfig, boolean isFailover);
	
	//proxy methods to OcpaVpnService
	public boolean protect(int socket);
	public boolean protect(Socket socket);
}
