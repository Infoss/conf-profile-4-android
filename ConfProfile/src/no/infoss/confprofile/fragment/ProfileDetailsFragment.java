package no.infoss.confprofile.fragment;

import java.util.List;

import no.infoss.confprofile.R;
import no.infoss.confprofile.format.ConfigurationProfile;
import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadInfoExLoader;
import no.infoss.confprofile.profile.PayloadsCursorLoader;
import no.infoss.confprofile.profile.data.PayloadInfoEx;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
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
	
	public ProfileDetailsFragment() {
		super();
		resetFields();
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
		return inflater.inflate(R.layout.fragment_profile_details, null);
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
					//TODO: use selected profile
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
		Activity activity = getActivity();
		
		if(activity != null) {
			ExpandableListAdapter payloadAdapter = new ExpandableObjectAdapter<PayloadInfoEx, List<PayloadInfoEx>>(
					activity.getLayoutInflater(), 
					data, 
					android.R.layout.simple_list_item_2, 
					new PayloadInfoMapper(), 
					data, 
					android.R.layout.simple_expandable_list_item_2, 
					new PayloadGroupInfoMapper());
			ExpandableListView list = (ExpandableListView) getView().findViewById(R.id.list);
			list.setAdapter(payloadAdapter);
		}
	}

	@Override
	public void onLoaderReset(Loader<List<List<PayloadInfoEx>>> loader) {
		// nothing to do here
	}

	private static class PayloadInfoMapper implements ObjectMapper<PayloadInfoEx> {

		@Override
		public View prepareView(int position, View view) {
			// TODO Auto-generated method stub
			return null;
		}
		
		@Override
		public void mapData(int position, View view, PayloadInfoEx data) {
			TextView text;
			
			text = (TextView) view.findViewById(android.R.id.text1);
			text.setText(data.payload.getPayloadDisplayName());
			
			text = (TextView) view.findViewById(android.R.id.text2);
			text.setText(String.valueOf(data.payload.getPayloadDescription()));
		}

	}
	
	private static class PayloadGroupInfoMapper implements ObjectMapper<List<PayloadInfoEx>> {

		@Override
		public View prepareView(int position, View view) {
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
