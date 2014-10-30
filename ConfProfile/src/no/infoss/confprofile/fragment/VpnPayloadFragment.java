package no.infoss.confprofile.fragment;

import java.util.ArrayList;
import java.util.List;

import no.infoss.confprofile.R;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadInfoExLoader;
import no.infoss.confprofile.profile.PayloadsCursorLoader;
import no.infoss.confprofile.profile.data.PayloadInfoEx;
import no.infoss.confprofile.util.ConfigUtils;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;

import com.litecoding.classkit.view.HeaderObjectAdapter;
import com.litecoding.classkit.view.HeaderObjectAdapter.HeaderObjectMapper;

public class VpnPayloadFragment extends Fragment implements LoaderCallbacks<List<List<PayloadInfoEx>>>  {
	public static final String TAG = VpnPayloadFragment.class.getSimpleName(); 
	
	private static final String TAB_L2TP = "l2tp";
	private static final String TAB_PPTP = "pptp";
	private static final String TAB_IPSEC = "ipsec";
	
	private final List<List<PayloadInfoEx>> mPayloads = new ArrayList<List<PayloadInfoEx>>();
	private HeaderObjectAdapter<PayloadInfoEx, List<PayloadInfoEx>> mAdapter;
	
	private DbOpenHelper mDbHelper;
	private String mPayloadUuid;
	
	public VpnPayloadFragment() {
		super();
		resetFields();
	}
	
	public VpnPayloadFragment(DbOpenHelper dbHelper) {
		this();
		mDbHelper = dbHelper;
	}

	private void resetFields() {
	}

	public void setPayloadUuid(String payloadUuid) {
		mPayloadUuid = payloadUuid;
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if(mDbHelper == null) {
			mDbHelper = DbOpenHelper.getInstance(activity);
		}
		
		mAdapter = new HeaderObjectAdapter<PayloadInfoEx, List<PayloadInfoEx>>(activity.getLayoutInflater(), 
				mPayloads, 
				R.layout.simple_list_item_1_header, 
				mPayloads, 
				R.layout.payload_item, 
				new HeaderObjectMapper<PayloadInfoEx, List<PayloadInfoEx>>() {

					@Override
					public View prepareView(int position, View convertView, PayloadInfoEx data) {
						return null;
					}
					
					@Override
					public void mapData(int position, View view, PayloadInfoEx data) {
						ImageView imageView = null;
						
						imageView = (ImageView) view.findViewById(R.id.icon);
						if(imageView != null) {
							imageView.setImageResource(R.drawable.profiles);
						}
						
						TextView textView = null;
						
						textView = (TextView) view.findViewById(R.id.payloadName);
						if(textView != null) {
							textView.setText(data.payload.getPayloadDisplayName());
						}
						
						if(data.payload instanceof VpnPayload) {
							textView = (TextView) view.findViewById(R.id.payloadDetails1);
							if(textView != null) {
								textView.setVisibility(View.GONE);
							}
							
							textView = (TextView) view.findViewById(R.id.payloadDetails2);
							if(textView != null) {
								textView.setVisibility(View.GONE);
							}
						}
					}

					@Override
					public View prepareHeaderView(int position, View convertView, List<PayloadInfoEx> header) {
						return null;
					}

					@Override
					public void mapHeader(int position, View view, List<PayloadInfoEx> header) {
						Activity activity = getActivity();
						if(activity != null && header.size() > 0) {
							TextView textView = (TextView) view.findViewById(android.R.id.text1);
							textView.setText(ConfigUtils.getPayloadNameByType(activity, header.get(0).payloadType));
						}
					}
			
				});
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		View view = inflater.inflate(R.layout.fragment_vpn_payload, container, false);
		ListView list = (ListView) view.findViewById(android.R.id.list);
		if(list != null) {
			list.setAdapter(mAdapter);
		}
		
		Activity activity = getActivity();
		
		TabHost tabHost = (TabHost) view.findViewById(android.R.id.tabhost);

		tabHost.setup();

		TabHost.TabSpec tabSpec = null;
		
		tabSpec = tabHost.newTabSpec(TAB_L2TP);
		tabSpec.setIndicator(activity.getString(R.string.fragment_vpn_payload_l2tp_tab));
		tabHost.addTab(tabSpec);

		tabSpec = tabHost.newTabSpec(TAB_PPTP);
		tabSpec.setIndicator(activity.getString(R.string.fragment_vpn_payload_pptp_tab));
		tabHost.addTab(tabSpec);

		tabSpec = tabHost.newTabSpec(TAB_IPSEC);
		tabSpec.setIndicator(activity.getString(R.string.fragment_vpn_payload_ipsec_tab));
		tabHost.addTab(tabSpec);

		tabHost.setOnTabChangedListener(new OnTabChangeListener() {
			
			@Override
			public void onTabChanged(String tabId) {
				// TODO Auto-generated method stub
				
			}
		});
		
		tabHost.setCurrentTab(0);
		
		return view;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		Bundle request = null;
		
		request = new Bundle();
		request.putInt(BaseQueryCursorLoader.STMT_TYPE, BaseQueryCursorLoader.STMT_SELECT);
		request.putString(BaseQueryCursorLoader.P_SELECT_BY, PayloadsCursorLoader.COL_PROFILE_ID);
		request.putString(BaseQueryCursorLoader.P_SELECT_VALUE, mPayloadUuid);
		getLoaderManager().initLoader(mPayloadUuid.hashCode(), request, this);
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	public Loader<List<List<PayloadInfoEx>>> onCreateLoader(int id, Bundle params) {
		return new PayloadInfoExLoader(getActivity(), id, params, mDbHelper);
	}

	@Override
	public void onLoadFinished(Loader<List<List<PayloadInfoEx>>> loader, List<List<PayloadInfoEx>> data) {
		showPayloadsDetails(data);
	}

	@Override
	public void onLoaderReset(Loader<List<List<PayloadInfoEx>>> loader) {
		// nothing to do here
	}
	
	private void showPayloadsDetails(List<List<PayloadInfoEx>> data) {
		if(data != null) {
			mPayloads.clear();
			mPayloads.addAll(data);
			mAdapter.notifyDataSetChanged();
			//TODO: add onItemClickListener to the grid
		}
	}
}
