package no.infoss.confprofile.vpn;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public class VpnManagerService extends Service implements VpnManager {
	public static final String TAG = VpnManagerService.class.getSimpleName();
	
	private Binder mBinder = new Binder();

	@Override
	public void notifyConnectionStarted(VpnConnectionProfile profile) {
		
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		mBinder.attachInterface(this, VpnManager.TAG);
		return mBinder;
	}

	@Override
	public IBinder asBinder() {
		return mBinder;
	}

}
