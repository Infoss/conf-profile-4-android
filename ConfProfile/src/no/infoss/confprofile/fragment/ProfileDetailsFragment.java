package no.infoss.confprofile.fragment;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import no.infoss.confprofile.R;
import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadsCursorLoader;
import no.infoss.confprofile.profile.ProfilesCursorLoader;
import no.infoss.confprofile.profile.ProfilesCursorLoader.ProfileInfo;
import no.infoss.confprofile.profile.data.PayloadInfo;
import no.infoss.confprofile.util.ConfigUtils;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class ProfileDetailsFragment extends Fragment implements LoaderCallbacks<Cursor>  {
	public static final String TAG = ProfileDetailsFragment.class.getSimpleName(); 
	
	private static final String FSK_NAME = TAG.concat(":name");
	private static final String FSK_ORGANIZATION = TAG.concat(":organization");
	private static final String FSK_DESC = TAG.concat(":desc");
	
	private String mName;
	private String mOrganization;
	private String mDescription;
	private ProfileInfo mData;
	
	private DbOpenHelper mDbHelper;
	private String mProfileId;
	
	public ProfileDetailsFragment() {
		super();
		resetFields();
		
		mData = null;
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
		View view = inflater.inflate(R.layout.fragment_profile_details, container, false);
		return view;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		Bundle request = null;
		
		request = new Bundle();
		request.putInt(BaseQueryCursorLoader.STMT_TYPE, BaseQueryCursorLoader.STMT_SELECT);
		request.putString(BaseQueryCursorLoader.P_SELECT_BY, ProfilesCursorLoader.COL_ID);
		request.putString(BaseQueryCursorLoader.P_SELECT_VALUE, mProfileId);
		getLoaderManager().initLoader(0, request, this);
		
		request = new Bundle();
		request.putInt(BaseQueryCursorLoader.STMT_TYPE, BaseQueryCursorLoader.STMT_SELECT);
		request.putString(BaseQueryCursorLoader.P_SELECT_BY, PayloadsCursorLoader.COL_PROFILE_ID);
		request.putString(BaseQueryCursorLoader.P_SELECT_VALUE, mProfileId);
		getLoaderManager().initLoader(1, request, this);
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		outState.putString(FSK_NAME, mName);
		outState.putString(FSK_ORGANIZATION, mOrganization);
		outState.putString(FSK_DESC, mDescription);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle params) {
		if(id == 0) {
			return new ProfilesCursorLoader(getActivity(), id, params, mDbHelper);
		} else if(id == 1) {
			return new PayloadsCursorLoader(getActivity(), id, params, mDbHelper);
		}
		
		return null;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		if(loader.getId() == 0) {
			showProfileDetails(data);
		} else if(loader.getId() == 1) {
			showPayloadsDetails(data);
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		// nothing to do here
	}

	private void showProfileDetails(Cursor data) {
		if(data.moveToFirst()) {
			mData = ProfilesCursorLoader.PROFILE_CURSOR_MAPPER.mapRowToObject(data);
		}
		
		View view = getView();
		if(mData != null && view != null) {
			TextView text = null;
			
			text = (TextView) view.findViewById(R.id.profileName);
			if(text != null) {
				text.setText(mData.name);
			}
			
			text = (TextView) view.findViewById(R.id.profileOrganization);
			if(text != null) {
				text.setText(mData.organization);
			}
			
			text = (TextView) view.findViewById(R.id.profileDescription);
			if(text != null) {
				text.setText(mData.description);
			}
			
			text = (TextView) view.findViewById(R.id.profileSigned);
			if(text != null) {
				text.setText("<<FIXME>>");
			}
			
			text = (TextView) view.findViewById(R.id.profileReceived);
			if(text != null) {
				String dateFmtStr = getString(R.string.fragment_profile_details_date_label_fmt);
				SimpleDateFormat dateFmt = new SimpleDateFormat(dateFmtStr);
				text.setText(dateFmt.format(mData.addedAt));
			}
		}
	}
	
	private void showPayloadsDetails(Cursor data) {
		Map<String, Integer> payloadCount = new HashMap<String, Integer>();
		PayloadInfo currPayload = null;
		if(data.moveToFirst()) {
			do {
				currPayload = PayloadsCursorLoader.PAYLOAD_CURSOR_MAPPER.mapRowToObject(data);
				
				int cnt = 0;
				if(payloadCount.containsKey(currPayload.payloadType)) {
					cnt = payloadCount.get(currPayload.payloadType);
				}
				
				cnt++;
				
				payloadCount.put(currPayload.payloadType, cnt);
			} while(data.moveToNext());
		}
		
		View view = getView();
		if(payloadCount.size() > 0) {
			TextView text = null;
			
			text = (TextView) view.findViewById(R.id.profileContains);
			if(text != null) {
				StringBuilder builder = new StringBuilder();
				for(Entry<String, Integer> entry : payloadCount.entrySet()) {
					builder.append(String.format("%d %s\n", 
							entry.getValue(), 
							ConfigUtils.getPayloadNameByType(getActivity(), entry.getKey())));
				}
				
				text.setText(builder.toString());
			}
			
			payloadCount.clear();
		}
	}
}
