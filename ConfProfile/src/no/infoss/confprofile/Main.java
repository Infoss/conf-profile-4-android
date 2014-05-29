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

import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadsCursorLoader;
import no.infoss.confprofile.profile.ProfilesCursorLoader;
import no.infoss.confprofile.profile.ProfilesCursorLoader.ProfileInfo;
import no.infoss.confprofile.task.BackupTask;
import no.infoss.confprofile.vpn.OcpaVpnService;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.litecoding.classkit.view.LazyCursorList;
import com.litecoding.classkit.view.ObjectAdapter;
import com.litecoding.classkit.view.ObjectAdapter.ObjectMapper;

public class Main extends Activity implements LoaderCallbacks<Cursor> {
	public static final String TAG = Main.class.getSimpleName();
	
	public static final String ACTION_CALL_PREPARE = Main.class.getSimpleName().concat(".CALL_PREPARE");
	public static final int REQUEST_CODE_PREPARE = 0;
	public static final int RESULT_VPN_LOCKED = 2;
	public static final int RESULT_VPN_UNSUPPORTED = 3;
	
	
	private LazyCursorList<ProfileInfo> mProfileInfoList;
	private DbOpenHelper mDbHelper;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		mProfileInfoList = new LazyCursorList<ProfileInfo>(ProfilesCursorLoader.PROFILE_CURSOR_MAPPER);
		mDbHelper = DbOpenHelper.getInstance(this);
		
		ListAdapter profileAdapter = new ObjectAdapter<ProfileInfo>(
				getLayoutInflater(), 
				mProfileInfoList, 
				R.layout.profile_item, 
				new ProfileInfoMapper());
		GridView grid = (GridView) findViewById(R.id.profileGrid);
		grid.setEmptyView(findViewById(android.R.id.empty));
		grid.setAdapter(profileAdapter);
		grid.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				@SuppressWarnings("unchecked")
				ObjectAdapter<ProfileInfo> adapter = (ObjectAdapter<ProfileInfo>) parent.getAdapter();
				ProfileInfo info = (ProfileInfo) adapter.getItem(position);
				Intent intent = new Intent(Main.this, ProfileDetails.class);
				intent.putExtra(PayloadsCursorLoader.P_PROFILE_ID, info.id);
				Main.this.startActivity(intent);
			}
		});
		
		//and prepare VPN!
		prepareVpn();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		getLoaderManager().restartLoader(0, null, this);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if(BuildConfig.DEBUG) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.main, menu);
			return true;
		}
		
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    if(BuildConfig.DEBUG) {
		    switch (item.getItemId()) {
		        case R.id.menu_item_backup_all:
		            backupData();
		            return true;
		        case R.id.menu_item_restore_all:
		            restoreData();
		            return true;
		        default:
		            return super.onOptionsItemSelected(item);
		    }
	    }
	    
	    return super.onOptionsItemSelected(item);
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
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_CODE_PREPARE: {
			Intent callerIntent = getIntent();
			
			if(resultCode == RESULT_OK) {
				Intent intent = new Intent(this, OcpaVpnService.class);
				startService(intent);
			}
			
			if(callerIntent != null && ACTION_CALL_PREPARE.equals(callerIntent.getAction())) {
				setResult(resultCode, null);
				finish();
			}
			break;
		}
		default: {
			super.onActivityResult(requestCode, resultCode, data);
			break;
		}
		}
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
	
	private void backupData() {
		new BackupTask(this).execute();
	}
	
	private void restoreData() {
		
	}
	
	private static class ProfileInfoMapper implements ObjectMapper<ProfileInfo> {

		@Override
		public View prepareView(int position, View convertView) {
			if(convertView == null) {
				return convertView;
			}
			
			int viewId = convertView.getId();
			if(viewId != R.id.profileListItem) {
				return null;
			}
			
			TextView text;
			
			text = (TextView) convertView.findViewById(R.id.profileName);
			text.setText(null);
			
			text = (TextView) convertView.findViewById(R.id.profileDetails);
			text.setText(null);
			
			return convertView;
		}
		
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
