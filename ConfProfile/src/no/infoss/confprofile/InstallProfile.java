package no.infoss.confprofile;

import no.infoss.confprofile.crypto.CertificateManager;
import no.infoss.confprofile.fragment.AddProfileFragment;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;

public class InstallProfile extends Activity {
	public static final String TAG = InstallProfile.class.getSimpleName();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.install_profile);
		
		Intent intent = getIntent();
		if(intent != null) {
			if(Intent.ACTION_VIEW.equals(intent.getAction())) {
				FragmentManager fman = getFragmentManager();
				AddProfileFragment f = (AddProfileFragment) fman.findFragmentById(R.id.fragmentAddProfile);
				if(f == null || !f.isAdded()) {
					finish();
					return;
				}
				
				f.parseDataByUri(this, intent.getData());
			}
			
			CertificateManager.getManager(this, CertificateManager.MANAGER_INTERNAL);
		}		
	}
}
