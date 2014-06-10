package no.infoss.confprofile.fragment;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import no.infoss.confprofile.R;
import no.infoss.confprofile.format.ConfigurationProfile;
import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.format.ConfigurationProfileException;
import no.infoss.confprofile.format.PayloadFactory;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.json.PlistTypeAdapterFactory;
import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadsCursorLoader;
import no.infoss.confprofile.profile.PayloadsCursorLoader.PayloadInfo;
import no.infoss.confprofile.profile.PayloadsCursorLoader.PayloadsPerformance;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.litecoding.classkit.view.ExpandableObjectAdapter;
import com.litecoding.classkit.view.LazyCursorList;
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
	private LazyCursorList<PayloadInfo> mPayloadInfoList;
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
		
		mPayloadInfoList = new LazyCursorList<PayloadInfo>(PayloadsCursorLoader.PAYLOAD_CURSOR_MAPPER);
		
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
	
	private static class PayloadInfoExLoader extends AsyncTaskLoader<List<List<PayloadInfoEx>>> {
		protected int mId;
		protected DbOpenHelper mDbHelper;
		protected PayloadsPerformance mPerformance;

		public PayloadInfoExLoader(Context ctx, int id, Bundle params, DbOpenHelper dbHelper) {
			super(ctx);
			mId = id;
			mDbHelper = dbHelper;
			
			mPerformance = new PayloadsPerformance(getContext(), mId, params, mDbHelper);
		}

		@Override
		public List<List<PayloadInfoEx>> loadInBackground() {
			Cursor dbResult = mPerformance.perform();
			
			final LazyCursorList<PayloadInfo> payloadInfoList = 
					new LazyCursorList<PayloadInfo>(PayloadsCursorLoader.PAYLOAD_CURSOR_MAPPER);
			payloadInfoList.populateFrom(dbResult, true);
			
			GsonBuilder gsonBuilder = new GsonBuilder();
			gsonBuilder.registerTypeAdapterFactory(new PlistTypeAdapterFactory());
			Gson gson = gsonBuilder.create();
			
			Map<String, List<PayloadInfoEx>> converted = new HashMap<String, List<PayloadInfoEx>>();
			for(PayloadInfo info : payloadInfoList) {
				PayloadInfoEx infoEx = new PayloadInfoEx(info);
				try {
					infoEx.payload = PayloadFactory.createPayload(gson.fromJson(info.data, Dictionary.class));
					List<PayloadInfoEx> list = converted.get(infoEx.payload.getPayloadType());
					if(list == null) {
						list = new LinkedList<PayloadInfoEx>();
						converted.put(infoEx.payload.getPayloadType(), list);
					}
					
					list.add(infoEx);
				} catch (ConfigurationProfileException e) {
					Log.e(TAG, "Invalid payload data", e);
				} catch (JsonSyntaxException e) {
					Log.e(TAG, "Invalid json", e);
				}
			}
			
			List<List<PayloadInfoEx>> result = new LinkedList<List<PayloadInfoEx>>();
			result.addAll(converted.values());
			converted.clear();
			
			return result;
		}
		
	}

}

class PayloadInfoEx extends PayloadInfo {
	public Payload payload;
	
	public PayloadInfoEx(PayloadInfo info) {
		this.profileId = info.profileId;
		this.payloadUuid = info.payloadUuid;
		this.data = info.data;
	}
}
