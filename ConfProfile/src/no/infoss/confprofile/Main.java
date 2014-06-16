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
import no.infoss.confprofile.util.SimpleServiceBindKit;
import no.infoss.confprofile.vpn.VpnManagerInterface;
import no.infoss.confprofile.vpn.VpnManagerService;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
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

public class Main extends Activity implements LoaderCallbacks<Cursor>, ServiceConnection {
	public static final String TAG = Main.class.getSimpleName();
	
	private LazyCursorList<ProfileInfo> mProfileInfoList;
	private DbOpenHelper mDbHelper;
	private SimpleServiceBindKit<VpnManagerInterface> mBindKit;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		mProfileInfoList = new LazyCursorList<ProfileInfo>(ProfilesCursorLoader.PROFILE_CURSOR_MAPPER);
		mDbHelper = DbOpenHelper.getInstance(this);
		mBindKit = new SimpleServiceBindKit<VpnManagerInterface>(this, VpnManagerInterface.TAG);
		
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
		
		if(!mBindKit.bind(VpnManagerService.class, this, Context.BIND_AUTO_CREATE)) {
			Log.e(TAG, "Can't bind VpnManagerService");
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		getLoaderManager().restartLoader(0, null, this);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mBindKit.unbind();
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
	public void onServiceConnected(ComponentName name, IBinder service) {
		VpnManagerInterface vpnMgr = mBindKit.lock();
		try {
			if(vpnMgr != null) {
				vpnMgr.startVpnService();
			}
		} finally {
			mBindKit.unlock();
		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		// nothing to do here
	}
	
	private void backupData() {
		new BackupTask(this).execute();
	}
	
	private void restoreData() {
		
	}
	
	private static class ProfileInfoMapper implements ObjectMapper<ProfileInfo> {

		@Override
		public View prepareView(int position, View convertView, ProfileInfo data) {
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
