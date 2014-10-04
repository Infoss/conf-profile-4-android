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
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import no.infoss.confprofile.db.Expressions;
import no.infoss.confprofile.db.Request;
import no.infoss.confprofile.db.RequestWithAffectedRows;
import no.infoss.confprofile.db.Transaction;
import no.infoss.confprofile.db.Update;
import no.infoss.confprofile.db.Expressions.Expression;
import no.infoss.confprofile.model.CompositeListItemModel;
import no.infoss.confprofile.model.ImageViewModel;
import no.infoss.confprofile.model.ListItemModel;
import no.infoss.confprofile.model.Model;
import no.infoss.confprofile.model.SimpleListItemModel;
import no.infoss.confprofile.model.SwitchModel;
import no.infoss.confprofile.model.SwitchModel.OnCheckedChangeListener;
import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.VpnDataCursorLoader;
import no.infoss.confprofile.profile.data.ListItem;
import no.infoss.confprofile.profile.data.MutableListItem;
import no.infoss.confprofile.profile.data.VpnData;
import no.infoss.confprofile.task.BackupTask;
import no.infoss.confprofile.util.SimpleServiceBindKit;
import no.infoss.confprofile.util.SqliteRequestThread;
import no.infoss.confprofile.util.VpnEventReceiver;
import no.infoss.confprofile.util.VpnEventReceiver.VpnEventListener;
import no.infoss.confprofile.vpn.VpnManagerInterface;
import no.infoss.confprofile.vpn.VpnManagerService;
import no.infoss.confprofile.vpn.VpnTunnel.TunnelInfo;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.litecoding.classkit.view.HeaderObjectAdapter;
import com.litecoding.classkit.view.HeaderObjectAdapter.HeaderObjectMapper;
import com.litecoding.classkit.view.LazyCursorList;

public class Main extends Activity implements LoaderCallbacks<Cursor>, ServiceConnection, VpnEventListener {
	public static final String TAG = Main.class.getSimpleName();
	
	public static final String EXTRA_RESTART_LOADERS = Main.class.getCanonicalName().concat(".RESTART_LOADERS");
	
	private static final String ACTION_SERVICE_INFO = Main.class.getCanonicalName().concat(".SERVICE_INFO");
	private static final String ACTION_VPN_INFO = Main.class.getCanonicalName().concat(".VPN_INFO");
	private static final String ACTION_EXIT = Main.class.getCanonicalName().concat(".EXIT");
	
	private static final String EXTRA_VPN_PAYLOAD_UUID = Main.class.getCanonicalName().concat(".VPN_PAYLOAD_UUID");
	private static final String EXTRA_VPN_IS_ON_DEMAND = Main.class.getCanonicalName().concat(".VPN_IS_ON_DEMAND");
	
	private static final List<String> HEADER_LIST = new ArrayList<String>(2);
	private static final List<List<ListItem>> DATA_LIST = new LinkedList<List<ListItem>>();
	private static final CompositeListItemModel VPN_LIST_ITEM_MODEL = new CompositeListItemModel();
	private static final CompositeListItemModel STATUS_LIST_ITEM_MODEL = new CompositeListItemModel();
	
	private static final List<String> SINGLE_EMPTY_HEADER_LIST = new ArrayList<String>(1);
	private static final List<String> DOUBLE_EMPTY_HEADER_LIST = new ArrayList<String>(2);
	private static final List<List<ListItem>> VPN_DATA_LIST = new LinkedList<List<ListItem>>();
	private static final List<List<ListItem>> STATUS_LIST = new LinkedList<List<ListItem>>();
	
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
	
	private final Stack<Intent> mIntentStack = new Stack<Intent>();
	private VpnEventReceiver mVpnEvtReceiver;
	private GridView mGrid;
	private HeaderObjectAdapter<ListItem, String> mPayloadAdapter;
	private HeaderObjectAdapter<ListItem, String> mPayloadInfoAdapter;
	private HeaderObjectAdapter<ListItem, String> mStatusAdapter;
	private ListItem mVpnListItem;
	private ListItem mStatusListItem;
	private MutableListItem mStatusServerListItem;
	private MutableListItem mStatusConnectTimeListItem;
	private MutableListItem mStatusConnectedToListItem;
	private MutableListItem mStatusIpAddressListItem;
	private LazyCursorList<? extends ListItem> mVpnInfoList;
	private DbOpenHelper mDbHelper;
	private SimpleServiceBindKit<VpnManagerInterface> mBindKit;
	private boolean mDebugPcapEnabled = false;
	private Thread mUpdateTimerThread;
	
	private boolean mRestartLoaderOnNextIntent;
	private boolean mIsCurrentItemOnDemandEnabled;
	private VpnData mCurrentVpnData;
	
	private volatile boolean mServiceStateReceived;
	private volatile boolean mTunnelStateReceived;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		setActionBarDisplayHomeAsUp(getIntent());
		
		mBindKit = new SimpleServiceBindKit<VpnManagerInterface>(this, VpnManagerInterface.TAG);
		mDbHelper = DbOpenHelper.getInstance(this);
		SqliteRequestThread.getInstance().start(this);
		
		mServiceStateReceived = false;
		mTunnelStateReceived = false;
		
		mRestartLoaderOnNextIntent = false;
		
		mVpnEvtReceiver = new VpnEventReceiver(this, this);
		
		initModels();
		initHeaders();
		initData();
		initAdapters();
		
		mGrid = (GridView) findViewById(R.id.profileGrid);
		mGrid.setEmptyView(findViewById(android.R.id.empty));
		mGrid.setAdapter(mPayloadAdapter);
		mGrid.setOnItemClickListener(new OnItemClickListener() {

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
		
		mRestartLoaderOnNextIntent = false;
		
		setActionBarDisplayHomeAsUp(intent);
		
		if(ACTION_EXIT.equals(intent.getAction())) {
			finish();
			return;
		} else if(ACTION_SERVICE_INFO.equals(intent.getAction())) {
			mGrid.setAdapter(mStatusAdapter);
			mStatusAdapter.notifyDataSetChanged();
		} else if(ACTION_VPN_INFO.equals(intent.getAction())) {
			List<ListItem> cmdList = VPN_DATA_LIST.get(0);
			ListItem item = new ListItem(getString(R.string.main_vpn_item_on_demand_label), null);
			
			mIsCurrentItemOnDemandEnabled = intent.getBooleanExtra(EXTRA_VPN_IS_ON_DEMAND, false);
			
			CompositeListItemModel model = new CompositeListItemModel();
			SwitchModel swModel = new SwitchModel(R.id.switchWidget);
			swModel.setChecked(mIsCurrentItemOnDemandEnabled);
			model.addMapping(swModel);
			model.setLayoutId(R.layout.simple_list_item_2_switch);
			model.setRootViewId(R.id.simple_list_item_2_switch);
			
			swModel.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				
				@Override
				public void onCheckedChanged(SwitchModel model, Switch buttonView,
						boolean isChecked) {
					mIsCurrentItemOnDemandEnabled = isChecked;
				}
			});
			
			item.setModel(model);
			
			cmdList.clear();
			cmdList.add(item);
			mGrid.setAdapter(mPayloadInfoAdapter);
			mPayloadInfoAdapter.notifyDataSetChanged();
		} else {
			mGrid.setAdapter(mPayloadAdapter);
			mPayloadAdapter.notifyDataSetChanged();
		}
		
		invalidateOptionsMenu();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		Intent intent = mIntentStack.peek();
		
		if(Intent.ACTION_MAIN.equals(intent.getAction())) {
			View v = findViewById(R.id.noProfilesLabel);
			if(v != null) {
				v.setVisibility(View.GONE);
			}
			
			v = findViewById(R.id.progressPanel);
			if(v != null) {
				v.setVisibility(View.VISIBLE);
			}
			
			mGrid.setAdapter(null);
			Bundle params = new Bundle();
			params.putInt(BaseQueryCursorLoader.STMT_TYPE, BaseQueryCursorLoader.STMT_SELECT);
			if(intent.hasExtra(EXTRA_RESTART_LOADERS)) {
				intent.removeExtra(EXTRA_RESTART_LOADERS);
				getLoaderManager().restartLoader(0, params, this);
			} else {
				getLoaderManager().initLoader(0, params, this);
			}
		}
		
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
		
		Intent intent = mIntentStack.peek();
		if(intent.hasExtra(EXTRA_VPN_PAYLOAD_UUID)) {
			inflater.inflate(R.menu.main_vpn_info, menu);
		} else {
			inflater.inflate(R.menu.main, menu);
		}
		
		/*
		 * While building release version the following block of code 
		 * should be removed by javac or proguard as dead block.
		 */
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
			navigateHomeAsUp();
			return true;
		}
		case R.id.menu_item_go_profiles: {
			intent = new Intent(this, Profiles.class);
			startActivity(intent);
			return true;
		}
		
		case R.id.menu_item_go_info: {
			intent = new Intent(this, About.class);
			startActivity(intent);
			return true;
		}
		
		case R.id.menu_item_exit: {
			VpnManagerInterface vpnMgr = mBindKit.lock();
			if(vpnMgr != null) {
				vpnMgr.stopVpnService();
			}
			mBindKit.unlock();
			
			intent = createExitIntent(this);
			startActivity(intent);
			return true;
		}
		
		case R.id.menu_item_apply: {
			intent = mIntentStack.peek();
			if(intent.hasExtra(EXTRA_VPN_PAYLOAD_UUID)) {
				//update
				Expression expr = Expressions.
						column(VpnDataCursorLoader.COL_PAYLOAD_UUID).
						eq(Expressions.literal(intent.getStringExtra(EXTRA_VPN_PAYLOAD_UUID)));
				ContentValues values = new ContentValues();
				values.put(VpnDataCursorLoader.COL_ON_DEMAND_ENABLED_BY_USER, mIsCurrentItemOnDemandEnabled);
				Update request = Update.
						update().
						table(VpnDataCursorLoader.TABLE).
						values(values).
						where(expr, new Object[0]);
				
				Transaction transaction = new Transaction();
				transaction.addRequest(request);
				SqliteRequestThread.getInstance().request(transaction, new SqliteRequestThread.SqliteUpdateDeleteCallback() {
					
					@Override
					protected void onSqliteRequestSuccess(Request request) {
						//ignore this
					}
					
					@Override
					protected void onSqliteRequestError(Request request) {
						mRestartLoaderOnNextIntent = true;
					}
					
					@Override
					protected void onSqliteUpdateDeleteSuccess(RequestWithAffectedRows request) {
						mRestartLoaderOnNextIntent = true;
					}
				});
				
				mCurrentVpnData.setOnDemandEnabledByUser(mIsCurrentItemOnDemandEnabled);
				
				mRestartLoaderOnNextIntent = true;
				
				navigateHomeAsUp();
				return true;
			}
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
		
		for(ListItem item : mVpnInfoList) {
			final VpnData vpnData = (VpnData) item;
			CompositeListItemModel model = (CompositeListItemModel) vpnData.getModel();
			model.setOnClickListener(new Model.OnClickListener() {
				
				@Override
				public void onClick(Model model, View v) {
					VpnManagerInterface vpnMgr = mBindKit.lock();
					if(vpnMgr != null) {
						vpnMgr.activateVpnTunnel(vpnData.getPayloadUuid());
					}
					mBindKit.unlock();
				}
			});

			ImageViewModel imgModel = (ImageViewModel) model.getMapping(android.R.id.icon2);
			if(vpnData.isOnDemandEnabled()) {
				imgModel.setOnClickListener(new Model.OnClickListener() {
					
					@Override
					public void onClick(Model model, View v) {
						mCurrentVpnData = vpnData;
						
						Intent intent = new Intent(Main.this, Main.class);
						intent.setAction(ACTION_VPN_INFO);
						intent.putExtra(EXTRA_VPN_PAYLOAD_UUID, vpnData.getPayloadUuid());
						intent.putExtra(EXTRA_VPN_IS_ON_DEMAND, vpnData.isOnDemandEnabledByUser());
						startActivity(intent);
					}
				});
			}
		}
		
		mPayloadAdapter.notifyDataSetChanged();
		
		if(mVpnInfoList.size() > 0) {
			mGrid.setAdapter(mPayloadAdapter);
		}
		
		View v = findViewById(R.id.noProfilesLabel);
		if(v != null) {
			v.setVisibility(View.VISIBLE);
		}
		
		v = findViewById(R.id.progressPanel);
		if(v != null) {
			v.setVisibility(View.GONE);
		}
		
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		mVpnInfoList.populateFrom(null, true);
	}
	
	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		receiveServiceState();
		receiveTunnelState();
		
		VpnManagerInterface vpnMgr = mBindKit.lock();
		try {
			if(vpnMgr != null) {
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
	
	private void navigateHomeAsUp() {
		Intent intent = null;
		if(mIntentStack.size() >= 2) {
			mIntentStack.pop(); //pop current intent and resend parent intent
			intent = mIntentStack.pop();
			if(intent != null) {
				if(mRestartLoaderOnNextIntent) {
					intent.putExtra(EXTRA_RESTART_LOADERS, true);
					mRestartLoaderOnNextIntent = false;
				}
				
				startActivity(intent);
			}
		}
	}
	
	private void initHeaders() {
		SINGLE_EMPTY_HEADER_LIST.clear();
		SINGLE_EMPTY_HEADER_LIST.add("");
		
		DOUBLE_EMPTY_HEADER_LIST.clear();
		DOUBLE_EMPTY_HEADER_LIST.add("");
		DOUBLE_EMPTY_HEADER_LIST.add("");
		
		HEADER_LIST.clear();
		HEADER_LIST.add("");
		HEADER_LIST.add(getString(R.string.main_choose_config_label));
	}
	
	private void initModels() {
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
		
		SwitchModel swModel = (SwitchModel) VPN_LIST_ITEM_MODEL.getMapping(R.id.switchWidget);
		swModel.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(SwitchModel model, Switch buttonView,
					boolean isChecked) {
				VpnManagerInterface vpnMgr = mBindKit.lock();
				if(vpnMgr != null) {
					if(isChecked) { 
						vpnMgr.activateVpnTunnel(null);
					} else {
						vpnMgr.deactivateVpnTunnel();
					}
				}
				mBindKit.unlock();
			}
		});
		
		//adding VPN list item
		STATUS_LIST_ITEM_MODEL.setOnClickListener(new Model.OnClickListener() {
			
			@Override
			public void onClick(Model model, View v) {
				Intent intent = new Intent(Main.this, Main.class);
				intent.setAction(ACTION_SERVICE_INFO);
				startActivity(intent);
			}
		});
	}
	
	private void initData() {
		initVpnData();
		initVpnInfoData();
		initStatusData();
	}
	
	private void initVpnData() {
		DATA_LIST.clear();
		
		List<ListItem> cmdList = new ArrayList<ListItem>(2);
		
		mVpnListItem = new ListItem(getString(R.string.main_item_vpn_label), null);
		mVpnListItem.setModel(VPN_LIST_ITEM_MODEL);
		cmdList.add(mVpnListItem);
		
		mStatusListItem = new ListItem(getString(R.string.main_item_status_label), null);
		mStatusListItem.setModel(STATUS_LIST_ITEM_MODEL);
		//cmdList.add(mStatusListItem);
		
		DATA_LIST.add(cmdList);
		
		mVpnInfoList = new LazyCursorList<VpnData>(VpnDataCursorLoader.VPN_DATA_CURSOR_MAPPER);
		DATA_LIST.add((LazyCursorList<ListItem>) mVpnInfoList);
	}
	
	private void initVpnInfoData() {
		VPN_DATA_LIST.clear();
		
		List<ListItem> cmdList = new ArrayList<ListItem>(1);
		
		VPN_DATA_LIST.add(cmdList);
	}
	
	private void initStatusData() {
		STATUS_LIST.clear();
		
		List<ListItem> cmdList = null; 
		cmdList = new ArrayList<ListItem>(1);
		mStatusServerListItem = new MutableListItem(getString(R.string.main_status_item_server_label), "");
		mStatusServerListItem.setModel(new SimpleListItemModel());
		cmdList.add(mStatusServerListItem);
		
		STATUS_LIST.add(cmdList);
		
		cmdList = new ArrayList<ListItem>(3);
		mStatusConnectTimeListItem = new MutableListItem(getString(R.string.main_status_item_connect_time_label), "");
		mStatusConnectTimeListItem.setModel(new SimpleListItemModel());
		cmdList.add(mStatusConnectTimeListItem);
		
		mStatusConnectedToListItem = new MutableListItem(getString(R.string.main_status_item_connected_to_label), "");
		mStatusConnectedToListItem.setModel(new SimpleListItemModel());
		cmdList.add(mStatusConnectedToListItem);
		
		mStatusIpAddressListItem = new MutableListItem(getString(R.string.main_status_item_ip_address_label), "");
		mStatusIpAddressListItem.setModel(new SimpleListItemModel());
		cmdList.add(mStatusIpAddressListItem);
		
		STATUS_LIST.add(cmdList);
	}
	
	private void initAdapters() {
		ListItemMapper mapper = new ListItemMapper(this);
		mPayloadAdapter = new HeaderObjectAdapter<ListItem, String>(
				getLayoutInflater(), 
				HEADER_LIST,
				R.layout.simple_list_item_1_header,
				DATA_LIST,
				R.layout.list_item_1_images,
				mapper);
		
		mPayloadInfoAdapter = new HeaderObjectAdapter<ListItem, String>(
				getLayoutInflater(), 
				SINGLE_EMPTY_HEADER_LIST,
				R.layout.simple_list_item_1_header,
				VPN_DATA_LIST,
				R.layout.list_item_1_images,
				mapper);
		
		mStatusAdapter = new HeaderObjectAdapter<ListItem, String>(
				getLayoutInflater(), 
				DOUBLE_EMPTY_HEADER_LIST,
				R.layout.simple_list_item_1_header,
				STATUS_LIST,
				R.layout.simple_list_item_2_image,
				mapper);
		
	}
	
	
	private void setActionBarDisplayHomeAsUp(Intent intent) {
		if(intent == null) {
			return;
		}
		
		if(Intent.ACTION_MAIN.equals(intent.getAction())) {
			//clear intent stack on new main intent
			//this is critical when navigating Main -> other 1 -> other 2 -> Main
			mIntentStack.clear();
		}
		
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
				TunnelInfo info = vpnMgr.getVpnTunnelInfo();
				if(info != null) {
					onReceivedTunnelState(
							info.uuid, 
							info.state, 
							info.connectedSince,
							info.serverName,
							info.remoteAddress,
							info.localAddress,
							false);
				} else {
					onReceivedTunnelState(
							null, 
							VpnManagerService.TUNNEL_STATE_DISCONNECTED, 
							null,
							null,
							null,
							null,
							false);
				}
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
			swModel.setVisible(View.VISIBLE);
			break;
		}
		case VpnManagerInterface.SERVICE_STATE_REVOKED: {
			VPN_LIST_ITEM_MODEL.setEnabled(true);
			VPN_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_vpn_disabled_label));
			swModel.setChecked(false);
			swModel.setEnabled(true);
			swModel.setVisible(View.GONE);
			break;
		}
		case VpnManagerInterface.SERVICE_STATE_LOCKED: {
			VPN_LIST_ITEM_MODEL.setEnabled(false);
			VPN_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_vpn_locked_label));
			swModel.setChecked(false);
			swModel.setEnabled(false);
			swModel.setVisible(View.GONE);
			break;
		}
		case VpnManagerInterface.SERVICE_STATE_UNSUPPORTED: {
			VPN_LIST_ITEM_MODEL.setEnabled(false);
			VPN_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_vpn_unsupported_label));
			swModel.setChecked(false);
			swModel.setEnabled(false);
			swModel.setVisible(View.GONE);
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
	public void onReceivedTunnelState(final String tunnelId, 
			final int state, 
			final Date date,
			String serverName,
			String remoteAddress,
			String localAddress,
			final boolean isBroadcast) {
		if(!isBroadcast && mTunnelStateReceived) {
			return;
		}
		
		mTunnelStateReceived = true;
		
		if(tunnelId != null) {
			List<ListItem> items = new ArrayList<ListItem>(mVpnInfoList);
			for(ListItem item : items) {
				CompositeListItemModel model = (CompositeListItemModel) item.getModel();
				ImageViewModel imgModel = (ImageViewModel) model.getMapping(android.R.id.icon1);
						
				if(tunnelId.equals(((VpnData) item).getPayloadUuid()) && 
						(state == VpnManagerInterface.TUNNEL_STATE_CONNECTING || 
						state == VpnManagerInterface.TUNNEL_STATE_CONNECTED)) {
					imgModel.setImageResourceId(R.drawable.check);
				} else {
					imgModel.setImageResourceId(0);
				}
				
				imgModel.applyModel();
			}
			
			items.clear();
			//mVpnInfoList.notifyChanged();
		}
		
		SwitchModel swModel = (SwitchModel) VPN_LIST_ITEM_MODEL.getMapping(R.id.switchWidget);
		List<ListItem> subList = DATA_LIST.get(0);
		
		switch(state) {
		case VpnManagerInterface.TUNNEL_STATE_TERMINATED:
		case VpnManagerInterface.TUNNEL_STATE_DISCONNECTED: {
			STATUS_LIST_ITEM_MODEL.setEnabled(false);
			STATUS_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_status_disconnected_label));
			swModel.setChecked(false);
			subList.remove(mStatusListItem);
			break;
		}
		case VpnManagerInterface.TUNNEL_STATE_CONNECTING: {
			STATUS_LIST_ITEM_MODEL.setEnabled(false);
			STATUS_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_status_connecting_label));
			swModel.setChecked(true);
			subList.remove(mStatusListItem);
			break;
		}
		case VpnManagerInterface.TUNNEL_STATE_CONNECTED: {
			STATUS_LIST_ITEM_MODEL.setEnabled(true);
			STATUS_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_status_connected_label));
			swModel.setChecked(true);
			
			subList.add(mStatusListItem);
			
			mStatusServerListItem.setSubText(serverName);
			mStatusConnectTimeListItem.setSubText("");
			mStatusConnectedToListItem.setSubText(remoteAddress);
			mStatusIpAddressListItem.setSubText(localAddress);
			
			
			if(mUpdateTimerThread != null) {
				mUpdateTimerThread.interrupt();
				mUpdateTimerThread = null;
			}
			
			mUpdateTimerThread = new Thread(new Runnable() {
				
				private Handler mHandler = new Handler(getMainLooper());
				
				@Override
				public void run() {
					while(!Thread.interrupted()) {
						try {
							long diff = ((new Date()).getTime() - date.getTime()) / 1000;
							long diffSec = diff % 60;
							long diffMin = ((diff - diffSec) / 60) % 60;
							long diffHrs = (((diff - diffSec) / 60) - diffMin) / 60;
							
							String result = null;
							if(diffHrs > 0) {
								result = String.format("%d:%02d:%02d", diffHrs, diffMin, diffSec);
							} else { 
								result = String.format("%d:%02d", diffMin, diffSec);
							}
							
							final String time = result;
							
							mHandler.post(new Runnable() {
								
								@Override
								public void run() {
									mStatusConnectTimeListItem.setSubText(time);
									mStatusConnectTimeListItem.applyData();
									mStatusAdapter.notifyDataSetChanged();
								}
							});
							
							Thread.sleep(1000);
						} catch(Exception e) {
							//suppress this
						}
					}
				}
			});
			mUpdateTimerThread.start();
			
			break;
		}
		case VpnManagerInterface.TUNNEL_STATE_DISCONNECTING: {
			STATUS_LIST_ITEM_MODEL.setEnabled(false);
			STATUS_LIST_ITEM_MODEL.setSubText(getString(R.string.main_item_status_disconnecting_label));
			subList.remove(mStatusListItem);
			break;
		}
		default: {
			Log.e(TAG, "Received unexpected tunnel state (" + 
					state + 
					(isBroadcast ? ") by broadcast" : ") from binder"));
			break;
		}
		}
		
		mPayloadAdapter.notifyDataSetChanged();
		
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
	
	public static final Intent createExitIntent(Context ctx) {
		Intent intent = new Intent(ctx, Main.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.setAction(ACTION_EXIT);
		
		return intent;
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
