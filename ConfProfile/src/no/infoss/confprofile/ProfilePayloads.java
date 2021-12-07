package no.infoss.confprofile;

import no.infoss.confprofile.fragment.ProfilePayloadsFragment;
import no.infoss.confprofile.profile.ProfilesCursorLoader;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;

public class ProfilePayloads extends Activity {
	public static final String TAG = ProfilePayloads.class.getSimpleName();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.profile_payloads);
		
		Intent intent = getIntent();
		if(intent != null) {
			FragmentManager fman = getFragmentManager();
			ProfilePayloadsFragment f = (ProfilePayloadsFragment) fman.findFragmentById(R.id.fragmentProfilePayloads);
			if(f == null || !f.isAdded()) {
				finish();
				return;
			}
			
			f.setProfileId(intent.getStringExtra(ProfilesCursorLoader.P_ID));
		}		
	}
}
