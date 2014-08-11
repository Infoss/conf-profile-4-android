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

import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.litecoding.classkit.view.HeaderObjectAdapter;
import com.litecoding.classkit.view.HeaderObjectAdapter.HeaderObjectMapper;
import com.litecoding.classkit.view.LazyCursorList;

public class Main extends Activity implements LoaderCallbacks<Cursor>, ServiceConnection {
	public static final String TAG = Main.class.getSimpleName();
	
	private static final List<String> HEADER_LIST = new ArrayList<String>(2);
	private static final List<List<ListItem>> DATA_LIST = new LinkedList<List<ListItem>>();
	
	private HeaderObjectAdapter<ListItem, String> mPayloadAdapter;
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
		
		ListItem listItem;
		List<ListItem> cmdList = new ArrayList<ListItem>(2);
		
		//adding VPN list item
		listItem = new ListItem(getString(R.string.main_item_vpn_label), null);
		listItem.setLayoutId(R.layout.simple_list_item_2_switch);
		listItem.setRootViewId(R.id.simple_list_item_2_switch);
		cmdList.add(listItem);
		
		//adding VPN list item
		listItem = new ListItem(getString(R.string.main_item_status_label), null);
		listItem.setLayoutId(R.layout.simple_list_item_2_image);
		listItem.setRootViewId(R.id.simple_list_item_2_image);
		cmdList.add(listItem);
		
		DATA_LIST.add(cmdList);
		
		mVpnInfoList = new LazyCursorList<VpnData>(VpnDataCursorLoader.VPN_DATA_CURSOR_MAPPER);
		DATA_LIST.add((LazyCursorList<ListItem>) mVpnInfoList);
		
		mDbHelper = DbOpenHelper.getInstance(this);
		SqliteRequestThread.getInstance().start(this);
		mBindKit = new SimpleServiceBindKit<VpnManagerInterface>(this, VpnManagerInterface.TAG);
		
		mPayloadAdapter = new HeaderObjectAdapter<ListItem, String>(
				getLayoutInflater(), 
				HEADER_LIST,
				android.R.layout.simple_list_item_1,
				DATA_LIST, 
				R.layout.profile_item, 
				new PayloadInfoMapper(this));
		GridView grid = (GridView) findViewById(R.id.profileGrid);
		grid.setEmptyView(findViewById(android.R.id.empty));
		grid.setAdapter(mPayloadAdapter);
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
		return new VpnDataCursorLoader(this, id, params, mDbHelper);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		mVpnInfoList.populateFrom(data, true);
		mPayloadAdapter.notifyDataSetChanged();
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
		private Context mCtx;
		
		public PayloadInfoMapper(Context ctx) {
			mCtx = ctx;
		}

		@Override
		public View prepareView(int position, View convertView, ListItem data) {
			int viewId = 0;
			
			LayoutInflater inflater = (LayoutInflater) mCtx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			boolean viewAlreadyCreated = false;
			
			if(convertView == null) {
				//We don't have a view to convert, but...
				if(data == null || data.getLayoutId() == 0) {
					//We don't know what kind of view should be created,
					//adapter will create default view
					return null;
				} else {
					convertView = inflater.inflate(data.getLayoutId(), null);
					viewAlreadyCreated = true;
				}
			}
			
			viewId = convertView.getId();
			if(!viewAlreadyCreated) {
				if((data == null || data.getLayoutId() == 0) && viewId != R.id.profileListItem) {
					return null;
				} else if(data.getLayoutId() != viewId) {
					convertView = inflater.inflate(data.getLayoutId(), null);
				}
			}
			
			setText(convertView, R.id.profileName, null);
			setText(convertView, R.id.profileDetails, null);
			
			return convertView;
		}
		
		@Override
		public void mapData(int position, View view, ListItem data) {
			switch(view.getId()) {
			case R.id.simple_list_item_2_switch: {
				setText(view, android.R.id.text1, data.getMainText());
				setText(view, android.R.id.text2, data.getSubText());
				break;
			}
			case R.id.simple_list_item_2_image: {
				setText(view, android.R.id.text1, data.getMainText());
				setText(view, android.R.id.text2, data.getSubText());
				break;
			}
			case R.id.profileListItem:
			default: {
				setText(view, R.id.profileName, data.getMainText());
				setText(view, R.id.profileDetails, data.getSubText());
				break;
			}
			}
			
		}
		
		@Override
		public View prepareHeaderView(int position, View convertView, String data) {
			// always create a new view
			return null;
		}

		@Override
		public void mapHeader(int position, View view, String data) {
			setText(view, android.R.id.text1, data);
		}
		
		private TextView setText(View rootView, int id, String text) {
			TextView result = null;
			
			View testView = rootView.findViewById(id);
			if(testView != null && testView instanceof TextView) {
				result = (TextView) testView;
				result.setText(text);
			}
			
			return result;
		}
		
		private ImageView setImage(View rootView, int id, int resourceId) {
			ImageView result = null;
			
			View testView = rootView.findViewById(id);
			if(testView != null && testView instanceof ImageView) {
				result = (ImageView) testView;
				result.setImageResource(resourceId);
			}
			
			return result;
		}
		
		
	}

}
