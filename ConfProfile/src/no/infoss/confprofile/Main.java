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
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.ProfilesCursorLoader;
import no.infoss.confprofile.profile.ProfilesCursorLoader.ProfileInfo;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.litecoding.classkit.view.LazyCursorList;
import com.litecoding.classkit.view.ObjectAdapter;
import com.litecoding.classkit.view.ObjectAdapter.ObjectMapper;

public class Main extends Activity implements LoaderCallbacks<Cursor>{
	public static final String TAG = Main.class.getSimpleName();
	
	private LazyCursorList<ProfileInfo> mProfileInfoList;
	private DbOpenHelper mDbHelper;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		mProfileInfoList = new LazyCursorList<ProfileInfo>(ProfilesCursorLoader.PROFILE_CURSOR_MAPPER);
		mDbHelper = new DbOpenHelper(this);
		
		ListAdapter profileAdapter = new ObjectAdapter<ProfileInfo>(
				getLayoutInflater(), 
				mProfileInfoList, 
				R.layout.profile_item, 
				new ProfileInfoMapper());
		GridView grid = (GridView) findViewById(R.id.profileGrid);
		grid.setEmptyView(findViewById(android.R.id.empty));
		grid.setAdapter(profileAdapter);
		
		Intent intent = getIntent();
		if(intent != null) {
			if(Intent.ACTION_VIEW.equals(intent.getAction())) {
				Fragment addProfile = getFragmentManager().findFragmentById(R.id.popupFragmentPanel);
				if(addProfile == null || !(addProfile instanceof AddProfileFragment)) {
					addProfile = new AddProfileFragment(mDbHelper);
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
	
	@Override
	protected void onResume() {
		super.onResume();
		
		getLoaderManager().restartLoader(0, null, this);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle params) {
		return new ProfilesCursorLoader(this, id, params, mDbHelper);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		mProfileInfoList.populateFrom(data, true);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		// nothing to do here
	}
	
	private static class ProfileInfoMapper implements ObjectMapper<ProfileInfo> {

		@Override
		public void mapData(int position, View view, ProfileInfo data) {
			TextView text;
			text = (TextView) view.findViewById(R.id.profileName);
			if(text != null) {
				text.setText(data.name);
			}
			
			text = (TextView) view.findViewById(R.id.profileDetails);
			if(text != null) {
				text.setText(data.id);
			}
		}
		
	}
}
