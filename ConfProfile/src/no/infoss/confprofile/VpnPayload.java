package no.infoss.confprofile;

import no.infoss.confprofile.fragment.VpnPayloadFragment;
import no.infoss.confprofile.profile.VpnDataCursorLoader;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;

public class VpnPayload extends Activity {
	public static final String TAG = VpnPayload.class.getSimpleName();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.vpn_payload);
		
		Intent intent = getIntent();
		if(intent != null) {
			FragmentManager fman = getFragmentManager();
			VpnPayloadFragment f = (VpnPayloadFragment) fman.findFragmentById(R.id.fragmentVpnPayload);
			if(f == null || !f.isAdded()) {
				finish();
				return;
			}
			
			f.setPayloadUuid(intent.getStringExtra(VpnDataCursorLoader.P_PAYLOAD_UUID));
		}		
	}
}

