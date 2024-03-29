package no.infoss.confprofile.vpn;

import java.net.DatagramSocket;
import java.net.Socket;

import no.infoss.confprofile.vpn.OcpaVpnService.BuilderAdapter;
import android.os.IInterface;

public interface OcpaVpnInterface extends IInterface {
	public static final String TAG = OcpaVpnInterface.class.getSimpleName();
	
	public static final String ACTION_TERMINATE_VPN_SERVICE = "TERMINATE_VPN_SERVICE";
	
	public BuilderAdapter createBuilderAdapter(String name);
	public boolean protect(int socket);
	public boolean protect(DatagramSocket socket);
	public boolean protect(Socket socket);

}
