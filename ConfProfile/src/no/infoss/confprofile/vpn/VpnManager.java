package no.infoss.confprofile.vpn;

import android.os.IInterface;

public interface VpnManager extends IInterface {
	public static final String TAG = VpnManager.class.getSimpleName();
	
	public void notifyConnectionStarted(VpnConnectionProfile profile);
}
