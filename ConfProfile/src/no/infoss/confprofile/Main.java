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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadsCursorLoader;
import no.infoss.confprofile.profile.VpnDataCursorLoader;
import no.infoss.confprofile.profile.data.ListItem;
import no.infoss.confprofile.profile.data.VpnData;
import no.infoss.confprofile.task.BackupTask;
import no.infoss.confprofile.util.SimpleServiceBindKit;
import no.infoss.confprofile.util.SqliteRequestThread;
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
import android.widget.TextView;
import android.widget.Toast;

import com.litecoding.classkit.view.HeaderObjectAdapter;
import com.litecoding.classkit.view.HeaderObjectAdapter.HeaderObjectMapper;
import com.litecoding.classkit.view.LazyCursorList;

public class Main extends Activity implements LoaderCallbacks<Cursor>, ServiceConnection {
	public static final String TAG = Main.class.getSimpleName();
	
	private static final List<String> HEADER_LIST = new ArrayList<String>(2);
	private static final List<List<ListItem>> DATA_LIST = new LinkedList<List<ListItem>>();
	
	private LazyCursorList<? extends ListItem> mVpnInfoList;
	private DbOpenHelper mDbHelper;
	private SimpleServiceBindKit<VpnManagerInterface> mBindKit;
	private boolean mDebugPcapEnabled = false;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		HEADER_LIST.clear();
		HEADER_LIST.add("");
		HEADER_LIST.add(getString(R.string.main_choose_config_label));
		
		DATA_LIST.clear();
		
		List<ListItem> cmdList = new ArrayList<ListItem>(2);
		cmdList.add(new ListItem(getString(R.string.main_item_vpn_label), null));
		DATA_LIST.add(cmdList);
		
		mVpnInfoList = new LazyCursorList<VpnData>(VpnDataCursorLoader.VPN_DATA_CURSOR_MAPPER);
		DATA_LIST.add((LazyCursorList<ListItem>) mVpnInfoList);
		
		mDbHelper = DbOpenHelper.getInstance(this);
		SqliteRequestThread.getInstance().start(this);
		mBindKit = new SimpleServiceBindKit<VpnManagerInterface>(this, VpnManagerInterface.TAG);
		
		HeaderObjectAdapter<ListItem, String> payloadAdapter = new HeaderObjectAdapter<ListItem, String>(
				getLayoutInflater(), 
				HEADER_LIST,
				android.R.layout.simple_list_item_1,
				DATA_LIST, 
				R.layout.profile_item, 
				new PayloadInfoMapper());
		GridView grid = (GridView) findViewById(R.id.profileGrid);
		grid.setEmptyView(findViewById(android.R.id.empty));
		grid.setAdapter(payloadAdapter);
		grid.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				@SuppressWarnings("unchecked")
				HeaderObjectAdapter<ListItem, String> adapter = (HeaderObjectAdapter<ListItem, String>) parent.getAdapter();
				ListItem item = (ListItem) adapter.getItem(position);
				
				if(item instanceof VpnData) {
					VpnManagerInterface vpnMgr = mBindKit.lock();
					vpnMgr.activateVpnTunnel(((VpnData) item).getPayloadUuid());
					mBindKit.unlock();
				}
			}
		});
		
		if(!mBindKit.bind(VpnManagerService.class, this, Context.BIND_AUTO_CREATE)) {
			Log.e(TAG, "Can't bind VpnManagerService");
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		Bundle params = new Bundle();
		params.putInt(BaseQueryCursorLoader.STMT_TYPE, BaseQueryCursorLoader.STMT_SELECT);
		params.putString(BaseQueryCursorLoader.P_SELECT_BY, PayloadsCursorLoader.COL_PAYLOAD_TYPE);
		params.putString(BaseQueryCursorLoader.P_SELECT_VALUE, VpnPayload.VALUE_PAYLOAD_TYPE);
		getLoaderManager().restartLoader(0, params, this);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mBindKit.unbind();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		MenuItem item;
		
		inflater.inflate(R.menu.main, menu);
		
		if(BuildConfig.DEBUG) {
			
			menu.setGroupVisible(R.id.menu_group_debug, true);
			menu.setGroupEnabled(R.id.menu_group_debug, true);
			
			item = menu.findItem(R.id.menu_item_start_capture);
			if(item != null) {
				item.setEnabled(!mDebugPcapEnabled);
			}
			
			item = menu.findItem(R.id.menu_item_stop_capture);
			if(item != null) {
				item.setEnabled(mDebugPcapEnabled);
			}
			
			
			return true;
		}
		
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent = null;
		
		switch(item.getItemId()) {
		case R.id.menu_item_go_profiles: {
			return true;
		}
		
		case R.id.menu_item_go_info: {
			intent = new Intent(this, About.class);
			startActivity(intent);
			return true;
		}
		}
		
		/*
		 * While building release version the following block of code 
		 * should be removed by javac or proguard as dead block.
		 */
	    if(BuildConfig.DEBUG) {
		    switch(item.getItemId()) {
	        case R.id.menu_item_backup_all: {
	            backupData();
	            return true;
	        }
	        case R.id.menu_item_start_capture:
	        case R.id.menu_item_stop_capture: {
	        	startStopDebugPcap();
	        	return true;
	        }
	        default: {
	            return super.onOptionsItemSelected(item);
	        }
		    }
	    }
	    
	    return super.onOptionsItemSelected(item);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle params) {
		return new PayloadsCursorLoader(this, id, params, mDbHelper);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		mVpnInfoList.populateFrom(data, true);
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
				mDebugPcapEnabled = vpnMgr.isDebugPcapEnabled();
				invalidateOptionsMenu();
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
		if(BuildConfig.DEBUG) {
			new BackupTask(this).execute();
		}
	}
	
	private void startStopDebugPcap() {
		VpnManagerInterface vpnMgr = mBindKit.lock();
		if(vpnMgr == null) {
			Toast.makeText(this, "Service is busy, try again later",  Toast.LENGTH_SHORT).show();
		} else {
			boolean changed = false;
			if(mDebugPcapEnabled) {
				//stop
				changed = vpnMgr.debugStopPcap();
			} else {
				//start
				changed = vpnMgr.debugStartPcap();
			}
			
			if(changed) {
				mDebugPcapEnabled = vpnMgr.isDebugPcapEnabled();
			}
			invalidateOptionsMenu();
		}
		
		mBindKit.unlock();
	}
	
	private static class PayloadInfoMapper implements HeaderObjectMapper<ListItem, String> {

		@Override
		public View prepareView(int position, View convertView, ListItem data) {
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
		public void mapData(int position, View view, ListItem data) {
			TextView text;
			text = (TextView) view.findViewById(R.id.profileName);
			if(text != null) {
				text.setText(data.getMainText());
			}
			
			text = (TextView) view.findViewById(R.id.profileDetails);
			if(text != null) {
				text.setText("");
			}
		}
		
		@Override
		public View prepareHeaderView(int position, View convertView, String data) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void mapHeader(int position, View view, String data) {
			View v = view.findViewById(android.R.id.text1);
			if(v != null && v instanceof TextView) {
				((TextView) v).setText(data);
			}
		}
		
	}

}
