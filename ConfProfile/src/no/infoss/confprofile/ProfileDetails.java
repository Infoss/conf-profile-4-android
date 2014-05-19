package no.infoss.confprofile;

import no.infoss.confprofile.crypto.CertificateManager;
import no.infoss.confprofile.fragment.ProfileDetailsFragment;
import no.infoss.confprofile.profile.PayloadsCursorLoader;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;

public class ProfileDetails extends Activity {
	public static final String TAG = InstallProfile.class.getSimpleName();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.profile_details);
		
		Intent intent = getIntent();
		if(intent != null) {
			FragmentManager fman = getFragmentManager();
			ProfileDetailsFragment f = (ProfileDetailsFragment) fman.findFragmentById(R.id.fragmentProfileDetails);
			if(f == null || !f.isAdded()) {
				finish();
				return;
			}
			
			f.setProfileId(intent.getStringExtra(PayloadsCursorLoader.P_PROFILE_ID));
		}		
	}
}