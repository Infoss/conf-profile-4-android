package no.infoss.confprofile.fragment;

import java.util.LinkedList;
import java.util.List;

import no.infoss.confprofile.R;
import no.infoss.confprofile.format.ConfigurationProfile;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadInfoExLoader;
import no.infoss.confprofile.profile.PayloadsCursorLoader;
import no.infoss.confprofile.profile.data.PayloadInfoEx;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.Switch;
import android.widget.TextView;

import com.litecoding.classkit.view.ExpandableObjectAdapter;
import com.litecoding.classkit.view.ObjectAdapter.ObjectMapper;

public class ProfileDetailsFragment extends Fragment implements LoaderCallbacks<List<List<PayloadInfoEx>>>  {
	public static final String TAG = ProfileDetailsFragment.class.getSimpleName(); 
	
	private static final String FSK_NAME = TAG.concat(":name");
	private static final String FSK_ORGANIZATION = TAG.concat(":organization");
	private static final String FSK_DESC = TAG.concat(":desc");
	
	private String mName;
	private String mOrganization;
	private String mDescription;
	
	private DbOpenHelper mDbHelper;
	private String mProfileId;
	private ConfigurationProfile mProfile;
	
	private final PayloadInfoMapper mInfoMapper;
	private final PayloadGroupInfoMapper mGroupInfoMapper;
	private final PayloadInfoAdapter mPayloadAdapter;
	private final List<List<PayloadInfoEx>> mData;
	
	public ProfileDetailsFragment() {
		super();
		resetFields();
		
		mInfoMapper = new PayloadInfoMapper();
		mGroupInfoMapper = new PayloadGroupInfoMapper();
		mData = new LinkedList<List<PayloadInfoEx>>();
		mPayloadAdapter = new PayloadInfoAdapter( 
				mData, 
				android.R.layout.simple_list_item_2,
				mInfoMapper, 
				mData, 
				android.R.layout.simple_expandable_list_item_2, 
				mGroupInfoMapper);
	}
	
	public ProfileDetailsFragment(DbOpenHelper dbHelper) {
		this();
		mDbHelper = dbHelper;
	}

	private void resetFields() {
		mName = null;
		mOrganization = null;
		mDescription = null;
	}

	public void setProfileId(String profileId) {
		mProfileId = profileId;
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if(mDbHelper == null) {
			mDbHelper = DbOpenHelper.getInstance(activity);
		}
		
		mInfoMapper.setLayoutInflater(activity.getLayoutInflater());
		mPayloadAdapter.setLayoutInflater(activity.getLayoutInflater());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if(savedInstanceState != null) {
			mName = savedInstanceState.getString(FSK_NAME, mName);
			mOrganization = savedInstanceState.getString(FSK_ORGANIZATION, mOrganization);
			mDescription = savedInstanceState.getString(FSK_DESC, mDescription);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		
		View view = inflater.inflate(R.layout.fragment_profile_details, null);
		ExpandableListView list = (ExpandableListView) view.findViewById(R.id.list);
		list.setAdapter(mPayloadAdapter);
		
		return view;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		ExpandableListView list = (ExpandableListView) getView().findViewById(R.id.list);
		list.setOnChildClickListener(new OnChildClickListener() {
			
			@Override
			public boolean onChildClick(ExpandableListView parent, View v,
					int groupPosition, int childPosition, long id) {
				PayloadInfoEx info = (PayloadInfoEx) parent.getExpandableListAdapter().getChild(groupPosition, childPosition);
				if(info != null) {
					//TODO: show payload details
					return true;
				}
				return false;
			}
		});
		
		
		Bundle request = new Bundle();
		request.putInt(BaseQueryCursorLoader.STMT_TYPE, BaseQueryCursorLoader.STMT_SELECT);
		request.putString(PayloadsCursorLoader.P_SELECT_BY, PayloadsCursorLoader.COL_PROFILE_ID);
		request.putString(PayloadsCursorLoader.P_PROFILE_ID, mProfileId);
		getLoaderManager().restartLoader(0, request, this);
	}
	
	@Override
	public void onDetach() {
		mInfoMapper.setLayoutInflater(null);
		mPayloadAdapter.setLayoutInflater(null);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		outState.putString(FSK_NAME, mName);
		outState.putString(FSK_ORGANIZATION, mOrganization);
		outState.putString(FSK_DESC, mDescription);
	}

	@Override
	public Loader<List<List<PayloadInfoEx>>> onCreateLoader(int id, Bundle params) {
		return new PayloadInfoExLoader(getActivity(), id, params, mDbHelper);
	}

	@Override
	public void onLoadFinished(Loader<List<List<PayloadInfoEx>>> loader, List<List<PayloadInfoEx>> data) {
		mData.clear();
		if(data != null) {
			mData.addAll(data);
		}
		mPayloadAdapter.notifyDataSetChanged();
	}

	@Override
	public void onLoaderReset(Loader<List<List<PayloadInfoEx>>> loader) {
		// nothing to do here
	}
	
	protected void connectToVpnManually(PayloadInfoEx data, boolean isChecked) {
		// TODO Auto-generated method stub
		
	}
	
	private class PayloadInfoAdapter extends ExpandableObjectAdapter<PayloadInfoEx, List<PayloadInfoEx>> {
		private LayoutInflater mLayoutInflater;
		private ObjectMapper<PayloadInfoEx> mChildMapper;
		private ObjectMapper<List<PayloadInfoEx>> mGroupMapper;
		private int mChildResourceId;
		private int mGroupResourceId;
		
		public PayloadInfoAdapter(
				List<List<PayloadInfoEx>> children, 
				int childResourceId,
				ObjectMapper<PayloadInfoEx> childMapper,
				List<List<PayloadInfoEx>> groups, 
				int groupResourceId,
				ObjectMapper<List<PayloadInfoEx>> groupMapper) {
			super(null, children, 0, null, groups, 0, null);
			
			mChildMapper = childMapper;
			mGroupMapper = groupMapper;
			mChildResourceId = childResourceId;
			mGroupResourceId = groupResourceId;
		}
		
		@Override
		public View getChildView(
				int groupPosition, 
				int childPosition, 
				boolean isLastChild, 
				View convertView, 
				ViewGroup parent) {
			PayloadInfoEx data = children.get(groupPosition).get(childPosition);
			View currentView = mChildMapper.prepareView(childPosition, convertView, data);
			if(currentView == null) {
				LayoutInflater infl = null;
		
				if(mLayoutInflater == null && parent != null) {
					infl = (LayoutInflater) parent.getContext().
							getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				} else {
					infl = mLayoutInflater;
				}
				currentView = infl.inflate(mChildResourceId, parent, false);
			}
	
			mChildMapper.mapData(childPosition, currentView, data);
	
			return currentView;
		}
		
		@Override
		public View getGroupView(
				int groupPosition, 
				boolean isExpanded, 
				View convertView, 
				ViewGroup parent) {
			List<PayloadInfoEx> data = groups.get(groupPosition);
			View currentView = mGroupMapper.prepareView(groupPosition, convertView, data);
			
			if(currentView == null) {
				LayoutInflater infl = null;
	
				if(mLayoutInflater == null && parent != null) {
					infl = (LayoutInflater) parent.getContext().
							getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				} else {
					infl = mLayoutInflater;
				}
				
				currentView = infl.inflate(mGroupResourceId, parent, false);
			}
	
			mGroupMapper.mapData(groupPosition, currentView, data);
	
			return currentView;
		}
		
		public void setLayoutInflater(LayoutInflater layoutInflater) {
			mLayoutInflater = layoutInflater;
		}
	}

	private class PayloadInfoMapper implements ObjectMapper<PayloadInfoEx> {
		private LayoutInflater mLayoutInflater = null;
		
		@Override
		public View prepareView(int position, View view, final PayloadInfoEx data) {
			View resultView = null;
			
			if(data == null || data.payload == null) {
				return resultView;
			}
			
			if(VpnPayload.VALUE_PAYLOAD_TYPE.equals(data.payload.getPayloadType())) {
				resultView = view;
				if(view == null || view.findViewById(R.id.simple_list_item_2_switch) == null) {
					if(mLayoutInflater != null) {
						resultView = mLayoutInflater.inflate(R.layout.simple_list_item_2_switch, null);
					} else {
						Log.w(TAG, "No layout inflater was set, default view will be created");
						resultView = null;
					}
				}
				
				if(resultView != null) {
					Switch switchWidget = (Switch) resultView.findViewById(R.id.switchWidget);
					if(switchWidget != null) {
						switchWidget.setOnCheckedChangeListener(new OnCheckedChangeListener() {
							
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								ProfileDetailsFragment.this.connectToVpnManually(data, isChecked);
							}
						});
					}
				}
			}
			
			return resultView;
		}
		
		@Override
		public void mapData(int position, View view, PayloadInfoEx data) {
			TextView text;
			
			text = (TextView) view.findViewById(android.R.id.text1);
			text.setText(data.payload.getPayloadDisplayName());
			
			text = (TextView) view.findViewById(android.R.id.text2);
			text.setText(String.valueOf(data.payload.getPayloadDescription()));
		}

		public void setLayoutInflater(LayoutInflater inflater) {
			mLayoutInflater = inflater;
		}
	}
	
	private class PayloadGroupInfoMapper implements ObjectMapper<List<PayloadInfoEx>> {

		@Override
		public View prepareView(int position, View view, List<PayloadInfoEx> data) {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public void mapData(int position, View view, List<PayloadInfoEx> data) {
			TextView text;
			
			text = (TextView) view.findViewById(android.R.id.text1);
			text.setText(data.get(0).payload.getPayloadType());
			
			text = (TextView) view.findViewById(android.R.id.text2);
			text.setText(String.valueOf(data.size()));
		}
		
	}

}
