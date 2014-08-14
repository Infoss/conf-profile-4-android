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
import java.util.Stack;

import no.infoss.confprofile.model.CompositeListItemModel;
import no.infoss.confprofile.model.ImageViewModel;
import no.infoss.confprofile.model.ListItemModel;
import no.infoss.confprofile.model.Model;
import no.infoss.confprofile.model.SwitchModel;
import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.VpnDataCursorLoader;
import no.infoss.confprofile.profile.data.ListItem;
import no.infoss.confprofile.profile.data.VpnData;
import no.infoss.confprofile.task.BackupTask;
import no.infoss.confprofile.util.SimpleServiceBindKit;
import no.infoss.confprofile.util.SqliteRequestThread;
import no.infoss.confprofile.util.VpnEventReceiver;
import no.infoss.confprofile.util.VpnEventReceiver.VpnEventListener;
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
import android.widget.TextView;
import android.widget.Toast;

import com.litecoding.classkit.view.HeaderObjectAdapter;
import com.litecoding.classkit.view.HeaderObjectAdapter.HeaderObjectMapper;
import com.litecoding.classkit.view.LazyCursorList;

public class Main extends Activity implements LoaderCallbacks<Cursor>, ServiceConnection, VpnEventListener {
	public static final String TAG = Main.class.getSimpleName();
	
	private static final String ACTION_SERVICE_INFO = Main.class.getCanonicalName().concat(".SERVICE_INFO");
	private static final String ACTION_VPN_INFO = Main.class.getCanonicalName().concat(".VPN_INFO");
	
	private static final List<String> HEADER_LIST = new ArrayList<String>(2);
	private static final List<List<ListItem>> DATA_LIST = new LinkedList<List<ListItem>>();
	private static final CompositeListItemModel VPN_LIST_ITEM_MODEL = new CompositeListItemModel();
	private static final CompositeListItemModel STATUS_LIST_ITEM_MODEL = new CompositeListItemModel();
	
	private static final List<String> SINGLE_EMPTY_HEADER_LIST = new ArrayList<String>(1);
	private static final List<List<ListItem>> VPN_DATA_LIST = new LinkedList<List<ListItem>>();
	
	static {
		SwitchModel switchModel = new SwitchModel(R.id.switchWidget);
		VPN_LIST_ITEM_MODEL.addMapping(switchModel);
		VPN_LIST_ITEM_MODEL.setLayoutId(R.layout.simple_list_item_2_switch);
		VPN_LIST_ITEM_MODEL.setRootViewId(R.id.simple_list_item_2_switch);
		
		ImageViewModel imageViewModel = new ImageViewModel(android.R.id.icon);
		imageViewModel.setImageResourceId(R.drawable.arrow);
		STATUS_LIST_ITEM_MODEL.addMapping(imageViewModel);
		STATUS_LIST_ITEM_MODEL.setLayoutId(R.layout.simple_list_item_2_image);
		STATUS_LIST_ITEM_MODEL.setRootViewId(R.id.simple_list_item_2_image);
	}
	
	private final Stack<Intent> mIntentStack = new Stack<Intent>();;
	private VpnEventReceiver mVpnEvtReceiver;
	private HeaderObjectAdapter<ListItem, String> mPayloadAdapter;
	private ListItem mVpnListItem;
	private ListItem mStatusListItem;
	private LazyCursorList<? extends ListItem> mVpnInfoList;
	private DbOpenHelper mDbHelper;
	private SimpleServiceBindKit<VpnManagerInterface> mBindKit;
	private boolean mDebugPcapEnabled = false;
	
	private volatile boolean mServiceStateReceived;
	private volatile boolean mTunnelStateReceived;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		setActionBarDisplayHomeAsUp(getIntent());
		
		mServiceStateReceived = false;
		mTunnelStateReceived = false;
		
		mVpnEvtReceiver = new VpnEventReceiver(this, this);
		
		SINGLE_EMPTY_HEADER_LIST.clear();
		SINGLE_EMPTY_HEADER_LIST.add("");
		
		HEADER_LIST.clear();
		HEADER_LIST.add("");
		HEADER_LIST.add(getString(R.string.main_choose_config_label));
		
		DATA_LIST.clear();
		
		List<ListItem> cmdList = new ArrayList<ListItem>(2);
		
		//adding VPN list item
		VPN_LIST_ITEM_MODEL.setOnClickListener(new Model.OnClickListener() {
			
			@Override
			public void onClick(Model model, View v) {
				VpnManagerInterface vpnMgr = mBindKit.lock();
				if(vpnMgr != null) {
					vpnMgr.startVpnService();
				}
				mBindKit.unlock();
			}
		});
		
		mVpnListItem = new ListItem(getString(R.string.main_item_vpn_label), null);
		mVpnListItem.setModel(VPN_LIST_ITEM_MODEL);
		cmdList.add(mVpnListItem);
		
		//adding VPN list item
		STATUS_LIST_ITEM_MODEL.setOnClickListener(new Model.OnClickListener() {
			
			@Override
			public void onClick(Model model, View v) {
				Toast.makeText(Main.this, "STATUS_LIST_ITEM_MODEL onClick()", Toast.LENGTH_SHORT).show();
				Intent intent = new Intent(Main.this, Main.class);
				intent.setAction(ACTION_SERVICE_INFO);
				startActivity(intent);
			}
		});
		
		mStatusListItem = new ListItem(getString(R.string.main_item_status_label), null);
		mStatusListItem.setModel(STATUS_LIST_ITEM_MODEL);
		cmdList.add(mStatusListItem);
		
		DATA_LIST.add(cmdList);
		
		mVpnInfoList = new LazyCursorList<VpnData>(VpnDataCursorLoader.VPN_DATA_CURSOR_MAPPER);
		DATA_LIST.add((LazyCursorList<ListItem>) mVpnInfoList);
		
		mDbHelper = DbOpenHelper.getInstance(this);
		SqliteRequestThread.getInstance().start(this);
		mBindKit = new SimpleServiceBindKit<VpnManagerInterface>(this, VpnManagerInterface.TAG);
		
		mPayloadAdapter = new HeaderObjectAdapter<ListItem, String>(
				getLayoutInflater(), 
				HEADER_LIST,
				R.layout.simple_list_item_1_header,
				DATA_LIST,
				R.layout.list_item_1_images,
				new ListItemMapper(this));
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
				ListItemModel model = item.getModel();
				if(model instanceof OnItemClickListener) {
					((OnItemClickListener) model).onItemClick(parent, view, position, id);
				}
			}
		});
		
		if(!mBindKit.bind(VpnManagerService.class, this, Context.BIND_AUTO_CREATE)) {
			Log.e(TAG, "Can't bind VpnManagerService");
		}
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		
		setActionBarDisplayHomeAsUp(intent);
		
		if(ACTION_SERVICE_INFO.equals(intent.getAction())) {
			
		} else if(ACTION_VPN_INFO.equals(intent.getAction())) {
			
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		Intent intent = mIntentStack.peek();
		
		if(Intent.ACTION_MAIN.equals(intent.getAction())) {
			Bundle params = new Bundle();
			params.putInt(BaseQueryCursorLoader.STMT_TYPE, BaseQueryCursorLoader.STMT_SELECT);
			getLoaderManager().restartLoader(0, params, this);
		}
		
		//registerReceiver(mVpnEvtReceiver, INTENT_FILTER);
		mVpnEvtReceiver.register();
		receiveServiceState();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		mVpnEvtReceiver.unregister();
		mServiceStateReceived = false;
		mTunnelStateReceived = false;
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
		case android.R.id.home: {
			mIntentStack.pop(); //pop current intent and resend parent intent
			intent = mIntentStack.pop();
			if(intent != null) {
				startActivity(intent);
			}
			return true;
		}
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
		receiveServiceState();
		receiveTunnelState();
		
		VpnManagerInterface vpnMgr = mBindKit.lock();
		try {
			if(vpnMgr != null) {
				//TODO: start or not to start VPN tunnel immediately, that is the question
				//vpnMgr.startVpnService();
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
	
	
	private void setActionBarDisplayHomeAsUp(Intent intent) {
		if(mIntentStack.size() == 0){
			intent = new Intent(this, Main.class);
			intent.setAction(Intent.ACTION_MAIN);
			getActionBar().setDisplayHomeAsUpEnabled(false);
		} else {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
		
		mIntentStack.push(intent);
	}
	
	private void receiveServiceState() {
		VpnManagerInterface vpnMgr = mBindKit.lock();
		try {
			if(vpnMgr != null) {
				onReceivedServiceState(vpnMgr.getVpnServiceState(), false);
			}
		} catch(Exception e) {
			Log.e(TAG, "Exception while receiving service state", e);
		} finally {
			mBindKit.unlock();
		}
	}
	
	private void receiveTunnelState() {
		VpnManagerInterface vpnMgr = mBindKit.lock();
		try {
			if(vpnMgr != null) {
				onReceivedTunnelState(vpnMgr.getVpnTunnelId(), vpnMgr.getVpnTunnelState(), false);
			}
		} catch(Exception e) {
			Log.e(TAG, "Exception while receiving service state", e);
		} finally {
			mBindKit.unlock();
		}
	}
	
	@Override
	public void onReceivedServiceState(int state, boolean isBroadcast) {
		if(!isBroadcast && mServiceStateReceived) {
			return;
		}
		
		mServiceStateReceived = true;
		
		SwitchModel swModel = (SwitchModel) VPN_LIST_ITEM_MODEL.getMapping(R.id.switchWidget);
		
		switch(state) {
		case VpnManagerInterface.SERVICE_STATE_STARTED: {
			VPN_LIST_ITEM_MODEL.setEnabled(true);
			VPN_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_vpn_enabled_label));
			swModel.setEnabled(true);
			break;
		}
		case VpnManagerInterface.SERVICE_STATE_REVOKED: {
			VPN_LIST_ITEM_MODEL.setEnabled(false);
			VPN_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_vpn_disabled_label));
			swModel.setChecked(false);
			swModel.setEnabled(true);
			break;
		}
		case VpnManagerInterface.SERVICE_STATE_LOCKED: {
			VPN_LIST_ITEM_MODEL.setEnabled(false);
			VPN_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_vpn_locked_label));
			swModel.setChecked(false);
			swModel.setEnabled(true);
			break;
		}
		case VpnManagerInterface.SERVICE_STATE_UNSUPPORTED: {
			VPN_LIST_ITEM_MODEL.setEnabled(false);
			VPN_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_vpn_unsupported_label));
			swModel.setChecked(false);
			swModel.setEnabled(false);
			break;
		}
		default: {
			Log.e(TAG, "Received unexpected service state (" + 
					state + 
					(isBroadcast ? ") by broadcast" : ") from binder"));
			break;
		}
		}
		
		VPN_LIST_ITEM_MODEL.applyModel();
	}
	
	@Override
	public void onReceivedTunnelState(String tunnelId, int state, boolean isBroadcast) {
		if(!isBroadcast && mTunnelStateReceived) {
			return;
		}
		
		mTunnelStateReceived = true;
		
		SwitchModel swModel = (SwitchModel) VPN_LIST_ITEM_MODEL.getMapping(R.id.switchWidget);
		
		switch(state) {
		case VpnManagerInterface.TUNNEL_STATE_TERMINATED:
		case VpnManagerInterface.TUNNEL_STATE_DISCONNECTED: {
			STATUS_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_status_disconnected_label));
			swModel.setChecked(false);
			break;
		}
		case VpnManagerInterface.TUNNEL_STATE_CONNECTING: {
			STATUS_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_status_connecting_label));
			swModel.setChecked(true);
			break;
		}
		case VpnManagerInterface.TUNNEL_STATE_CONNECTED: {
			STATUS_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_status_connected_label));
			swModel.setChecked(true);
			break;
		}
		case VpnManagerInterface.TUNNEL_STATE_DISCONNECTING: {
			STATUS_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_status_disconnecting_label));
			break;
		}
		default: {
			Log.e(TAG, "Received unexpected tunnel state (" + 
					state + 
					(isBroadcast ? ") by broadcast" : ") from binder"));
			break;
		}
		}
		
		VPN_LIST_ITEM_MODEL.applyModel();
		STATUS_LIST_ITEM_MODEL.applyModel();
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
	
	private static class ListItemMapper implements HeaderObjectMapper<ListItem, String> {
		private Context mCtx;
		
		public ListItemMapper(Context ctx) {
			mCtx = ctx;
		}

		@Override
		public View prepareView(int position, View convertView, ListItem data) {
			int viewId = 0;
			
			LayoutInflater inflater = (LayoutInflater) mCtx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			boolean viewAlreadyCreated = false;
			
			if(convertView == null) {
				//We don't have a view to convert, but...
				if(data == null || data.getModel() == null || data.getModel().getLayoutId() == 0) {
					//We don't know what kind of view should be created,
					//adapter will create default view
					return null;
				} else {
					convertView = inflater.inflate(data.getModel().getLayoutId(), null);
					viewAlreadyCreated = true;
				}
			}
			
			viewId = convertView.getId();
			if(!viewAlreadyCreated) {
				if((data == null || data.getModel() == null || data.getModel().getLayoutId() == 0)) {
					return null;
				} else if(data.getModel().getLayoutId() != viewId) {
					convertView = inflater.inflate(data.getModel().getLayoutId(), null);
				}
			}
			
			return convertView;
		}
		
		@Override
		public void mapData(int position, View view, ListItem data) {
			data.getModel().bind(view);			
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
		
	}

}
