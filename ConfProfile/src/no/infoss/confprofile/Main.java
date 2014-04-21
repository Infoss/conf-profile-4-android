package no.infoss.confprofile;

import no.infoss.confprofile.crypto.CertificateManager;
import no.infoss.confprofile.fragment.AddProfileFragment;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class Main extends Activity {
	public static final String TAG = Main.class.getSimpleName();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		Intent intent = getIntent();
		if(intent != null) {
			if(Intent.ACTION_VIEW.equals(intent.getAction())) {
				Fragment addProfile = getFragmentManager().findFragmentById(R.id.popupFragmentPanel);
				if(addProfile == null || !(addProfile instanceof AddProfileFragment)) {
					addProfile = new AddProfileFragment();
				}
				
				FragmentTransaction ft = getFragmentManager().beginTransaction();
				ft.replace(R.id.popupFragmentPanel, addProfile);
				ft.commit();
				
				View popupPanel = findViewById(R.id.popupFragmentPanel);
				popupPanel.setVisibility(View.VISIBLE);
				
				((AddProfileFragment) addProfile).parseDataByUri(this, intent.getData());
			}
			
			CertificateManager.getManager(this, CertificateManager.MANAGER_INTERNAL);
		}		
	}

}
