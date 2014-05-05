package no.infoss.confprofile.fragment;

import org.apache.http.HttpStatus;

import android.app.Fragment;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import no.infoss.confprofile.R;
import no.infoss.confprofile.format.ConfigurationProfile;
import no.infoss.confprofile.format.Plist;
import no.infoss.confprofile.task.ParsePlistTask;
import no.infoss.confprofile.task.InstallConfigurationTask;
import no.infoss.confprofile.task.TaskError;
import no.infoss.confprofile.task.ParsePlistTask.ParsePlistTaskListener;
import no.infoss.confprofile.util.ParsePlistHandler;

public class AddProfileFragment extends Fragment implements ParsePlistTaskListener {
	public static final String TAG = AddProfileFragment.class.getSimpleName(); 
	
	private static final String FSK_STATE = TAG.concat(":state");
	private static final String FSK_NAME = TAG.concat(":name");
	private static final String FSK_ORGANIZATION = TAG.concat(":organization");
	private static final String FSK_DESC = TAG.concat(":desc");
	private static final String FSK_ERR_CODE = TAG.concat(":errCode");
	private static final String FSK_HTTP_ERR_CODE = TAG.concat(":httpErrCode");
	
	private static final int STATE_LOADING = 0;
	private static final int STATE_PARSED = 1;
	private static final int STATE_ERROR = 2;
	
	private static final ParsePlistHandler HANDLER = new ParsePlistHandler();
	
	private int mState;
	
	private String mName;
	private String mOrganization;
	private String mDescription;
	
	private int mErrCode;
	private int mHttpErrCode;
	
	private Plist mPlist; //TODO: fix this to avoid extra link here
	
	public AddProfileFragment() {
		super();
		
		resetFields();
	}
	
	private void resetFields() {
		mState = STATE_LOADING;
		
		mName = null;
		mOrganization = null;
		mDescription = null;
		
		mErrCode = TaskError.SUCCESS;
		mHttpErrCode = HttpStatus.SC_OK;
	}
	
	public void parseDataByUri(Context ctx, Uri uri) {
		HANDLER.parseDataByUri(ctx, uri, this);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if(savedInstanceState != null) {
			mState = savedInstanceState.getInt(FSK_STATE, mState);
			
			mName = savedInstanceState.getString(FSK_NAME, mName);
			mOrganization = savedInstanceState.getString(FSK_ORGANIZATION, mOrganization);
			mDescription = savedInstanceState.getString(FSK_DESC, mDescription);
			
			mErrCode = savedInstanceState.getInt(FSK_ERR_CODE, mErrCode);
			mHttpErrCode = savedInstanceState.getInt(FSK_HTTP_ERR_CODE, mHttpErrCode);
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		
		return createViewViewForState(inflater);
	}
	
	private View createViewViewForState(LayoutInflater inflater) {
		View view = null;
		
		if(inflater == null) {
			if(getActivity() == null) {
				return null;
			}
			
			inflater = getActivity().getLayoutInflater();
		}
		
		switch (mState) {
		case STATE_PARSED: {
			view = inflater.inflate(R.layout.fragment_add_profile_info, null);
			fillProfileInfo(view);
			break;
		}
		case STATE_ERROR: {
			view = inflater.inflate(R.layout.fragment_add_profile_wait, null);
			break;
		}
		case STATE_LOADING:
		default: {
			view = inflater.inflate(R.layout.fragment_add_profile_wait, null);
			break;
		}
		}
		
		return view;
	}
	
	private void fillProfileInfo(View view) {
		if(view == null) {
			return;
		}
		
		TextView name = (TextView) view.findViewById(R.id.name);
		if(name != null) {
			name.setText(mName);
		}
		
		TextView organization = (TextView) view.findViewById(R.id.organization);
		if(organization != null) {
			organization.setText(mOrganization);
		}
		
		TextView desc = (TextView) view.findViewById(R.id.desc);
		if(desc != null) {
			desc.setText(mDescription);
		}
		
		Button btnOk = (Button) view.findViewById(R.id.btnOk);
		if(btnOk != null) {
			btnOk.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					new InstallConfigurationTask(AddProfileFragment.this.getActivity(), null).execute(mPlist);
				}
			});
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		outState.putInt(FSK_STATE, mState);
		
		outState.putString(FSK_NAME, mName);
		outState.putString(FSK_ORGANIZATION, mOrganization);
		outState.putString(FSK_DESC, mDescription);
		
		outState.putInt(FSK_ERR_CODE, mErrCode);
		outState.putInt(FSK_HTTP_ERR_CODE, mHttpErrCode);
	}
	
	@Override
	public void onParsePlistFailed(ParsePlistTask task, int taskErrorCode) {
		mState = STATE_PARSED;

		mName = null;
		mOrganization = null;
		mDescription = null;
		
		mErrCode = taskErrorCode;
		mHttpErrCode = task.getHttpStatusCode();
		
		ViewGroup rootView = (ViewGroup) getView();
		if(rootView == null) {
			return;
		}
		
		rootView.removeAllViews();
		rootView.addView(createViewViewForState(null));
	}

	@Override
	public void onParsePlistComplete(ParsePlistTask task, Plist plist) {
		mState = STATE_PARSED;
		
		mName = plist.getString(ConfigurationProfile.KEY_PAYLOAD_DISPLAY_NAME, null);
		mOrganization = plist.getString(ConfigurationProfile.KEY_PAYLOAD_ORGANIZATION, null);
		mDescription = plist.getString(ConfigurationProfile.KEY_PAYLOAD_DESCRIPTION, null);
		
		mErrCode = TaskError.SUCCESS;
		mHttpErrCode = task.getHttpStatusCode();
		
		mPlist = plist;
		
		ViewGroup rootView = (ViewGroup) getView();
		if(rootView == null) {
			return;
		}
		
		rootView.removeAllViews();
		rootView.addView(createViewViewForState(null));
	}

}
