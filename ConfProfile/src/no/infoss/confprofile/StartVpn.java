package no.infoss.confprofile;

import no.infoss.confprofile.util.SimpleServiceBindKit;
import no.infoss.confprofile.vpn.OcpaVpnService;
import no.infoss.confprofile.vpn.VpnManagerInterface;
import no.infoss.confprofile.vpn.VpnManagerService;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Special activity without any layout that is called from VpnaManagerService only.
 * The main goal of this activity is perform VPN preparation and return result to the service.
 * 
 * @author Dmitry
 *
 */
public class StartVpn extends Activity {
	public static final String TAG = StartVpn.class.getSimpleName();
	
	public static final String ACTION_CALL_PREPARE = Main.class.getSimpleName().concat(".CALL_PREPARE");
	public static final int REQUEST_CODE_PREPARE = 0;
	public static final int RESULT_VPN_LOCKED = 2;
	public static final int RESULT_VPN_UNSUPPORTED = 3;
	
	private SimpleServiceBindKit<VpnManagerInterface> mBindKit;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if(getIntent() == null || !ACTION_CALL_PREPARE.equals(getIntent().getAction())) {
			//Intent is null or action is wrong. Returning.
			finish();
		}
		
		mBindKit = new SimpleServiceBindKit<VpnManagerInterface>(this, VpnManagerInterface.TAG);
		try {
			if(!mBindKit.bind(VpnManagerService.class, 0)) {
				//Can't bind to the service
				Log.e(TAG, "Can't bind VpnManagerService");
				finish();
			}
		} catch(Exception e) {
			Log.e(TAG, "Can't bind VpnManagerService", e);
			finish();
		}
		
		prepareVpn();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mBindKit.unbind();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		VpnManagerInterface vpnMgr = mBindKit.lock();
		
		try {
			switch (requestCode) {
			case REQUEST_CODE_PREPARE: {	
				switch (resultCode) {
				case RESULT_CANCELED: {
					if(vpnMgr != null) {
						vpnMgr.notifyVpnServiceRevoked();
					} else {
						Log.e(TAG, "Can't call notifyVpnRevoked(). VpnManagerService is not bound");
					}
					break;
				}
				case RESULT_OK: {
					Intent intent = new Intent(this, OcpaVpnService.class);
					startService(intent);
					break;
				}
				case RESULT_VPN_LOCKED: {
					if(vpnMgr != null) {
						vpnMgr.notifyVpnLockedBySystem();
					} else {
						Log.e(TAG, "Can't call notifyVpnLockedBySystem(). VpnManagerService is not bound");
					}
					break;
				}
				case RESULT_VPN_UNSUPPORTED: {
					if(vpnMgr != null) {
						vpnMgr.notifyVpnIsUnsupported();
					} else {
						Log.e(TAG, "Can't call notifyVpnIsUnsupported(). VpnManagerService is not bound");
					}
					break;
				}
				default: {
					if(vpnMgr != null) {
						vpnMgr.notifyVpnServiceRevoked();
					} else {
						Log.e(TAG, "Can't call notifyVpnRevoked(). VpnManagerService is not bound");
					}
					Log.e(TAG, "Weird result code " + String.valueOf(resultCode));
					break;
				}
				}
	
				break;
			}
			default: {
				super.onActivityResult(requestCode, resultCode, data);
				break;
			}
			}
		} catch(Exception e) {
			Log.e(TAG, "Error while processing activity result", e);
		} finally {
			mBindKit.unlock();
		}
		
		finish();
	}
	
	private void prepareVpn() {
		try {
			Intent prepareIntent = OcpaVpnService.prepare(this);
			if(prepareIntent == null) {
				onActivityResult(REQUEST_CODE_PREPARE, RESULT_OK, null);
			} else {
				startActivityForResult(prepareIntent, REQUEST_CODE_PREPARE);
			}
		} catch(IllegalStateException e) {
			Log.e(TAG, "VPN service is locked by system", e);
			onActivityResult(REQUEST_CODE_PREPARE, RESULT_VPN_LOCKED, null);
		} catch(ActivityNotFoundException e) {
			Log.e(TAG, "VPN service isn't supported by system", e);
			onActivityResult(REQUEST_CODE_PREPARE, RESULT_VPN_UNSUPPORTED, null);
		}
	}

}
