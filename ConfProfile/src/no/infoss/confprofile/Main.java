/*
 * This file is part of Profile provisioning for Android
 * Copyright (C) 2014  Infoss AS, https://infoss.no, info@infoss.no
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

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
