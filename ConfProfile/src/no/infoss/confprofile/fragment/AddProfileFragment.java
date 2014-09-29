package no.infoss.confprofile.fragment;

import java.util.List;

import no.infoss.confprofile.Main;
import no.infoss.confprofile.R;
import no.infoss.confprofile.format.ConfigurationProfile;
import no.infoss.confprofile.format.Plist;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.task.InstallConfigurationTask;
import no.infoss.confprofile.task.InstallConfigurationTask.Action;
import no.infoss.confprofile.task.InstallConfigurationTask.InstallConfigurationTaskListener;
import no.infoss.confprofile.task.ParsePlistTask;
import no.infoss.confprofile.task.ParsePlistTask.ParsePlistTaskListener;
import no.infoss.confprofile.task.RetrieveConfigurationTask;
import no.infoss.confprofile.task.RetrieveConfigurationTask.RetrieveConfigurationTaskListener;
import no.infoss.confprofile.task.TaskError;
import no.infoss.confprofile.util.ParsePlistHandler;

import org.apache.http.HttpStatus;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class AddProfileFragment extends Fragment 
	implements ParsePlistTaskListener,
			   RetrieveConfigurationTaskListener,
			   InstallConfigurationTaskListener {
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
	
	private DbOpenHelper mDbHelper;
	private ConfigurationProfile mProfile;
	
	public AddProfileFragment() {
		super();
		resetFields();
	}
	
	public AddProfileFragment(DbOpenHelper dbHelper) {
		this();
		mDbHelper = dbHelper;
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
	
	private void switchToState(int state) {
		mState = state;
		
		ViewGroup rootView = (ViewGroup) getView();
		if(rootView == null) {
			return;
		}
		
		rootView.removeAllViews();
		rootView.addView(createViewViewForState(null));
	}
	
	private View createViewViewForState(LayoutInflater inflater) {
		View view = null;
		
		if(inflater == null) {
			if(getActivity() == null) {
				return null;
			}
			
			inflater = getActivity().getLayoutInflater();
		}
		
		ViewGroup rootView = (ViewGroup) getView();
		
		switch (mState) {
		case STATE_PARSED: {
			view = inflater.inflate(R.layout.fragment_add_profile_info, rootView, false);
			fillProfileInfo(view);
			break;
		}
		case STATE_ERROR: {
			view = inflater.inflate(R.layout.fragment_add_profile_error, rootView, false);
			
			TextView errorLabel = (TextView) view.findViewById(R.id.errorLabel);
			if(errorLabel != null) {
				errorLabel.setText(getErrorMessageId());
			}
			break;
		}
		case STATE_LOADING:
		default: {
			view = inflater.inflate(R.layout.fragment_add_profile_wait, rootView, false);
			break;
		}
		}
		
		return view;
	}
	
	private int getErrorMessageId() {
		int result = 0;
		
		switch (mErrCode) {
		case TaskError.HTTP_FAILED: {
			result = R.string.error_add_profile_http_failed;
			break;
		}
		
		case TaskError.SCEP_FAILED:
		case TaskError.SCEP_TIMEOUT: {
			result = R.string.error_add_profile_scep_failed;
			break;
		}
		
		case TaskError.MISSING_SCEP_PAYLOAD: {
			result = R.string.error_add_profile_missing_scep;
			break;
		}
		
		case TaskError.INTERNAL:
		default: {
			result = R.string.error_add_profile_generic;
			break;
		}
		}
		
		return result;
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
					v.setEnabled(false);
					new InstallConfigurationTask(
							AddProfileFragment.this.getActivity(), 
							mDbHelper, 
							AddProfileFragment.this).execute(mProfile);
				}
			});
		}
		
		Button btnCancel = (Button) view.findViewById(R.id.btnCancel);
		if(btnCancel != null) {
			btnCancel.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					Activity activity = AddProfileFragment.this.getActivity();
					if(activity != null) {
						activity.finish();
					}
				}
			});
		}
	}
	
	private void showPlistDetails(Plist config) {	
		View view = getView();
		if(config != null && view != null) {
			TextView text = null;
			
			text = (TextView) view.findViewById(R.id.profileName);
			if(text != null) {
				text.setText(config.getString(ConfigurationProfile.KEY_PAYLOAD_DISPLAY_NAME, ""));
			}
			
			text = (TextView) view.findViewById(R.id.profileOrganization);
			if(text != null) {
				text.setText(config.getString(ConfigurationProfile.KEY_PAYLOAD_ORGANIZATION, ""));
			}
			
			text = (TextView) view.findViewById(R.id.profileDescription);
			if(text != null) {
				text.setText(config.getString(ConfigurationProfile.KEY_PAYLOAD_DESCRIPTION, ""));
			}
			
			text = (TextView) view.findViewById(R.id.profileSigned);
			if(text != null) {
				text.setText("<<FIXME>>");
			}

			
			//hide row for "received" and "contains" field
			View v = view.findViewById(R.id.rowProfileReceived);
			if(v != null) {
				v.setVisibility(View.GONE);
			}
			
			v = view.findViewById(R.id.rowProfileContains);
			if(v != null) {
				v.setVisibility(View.GONE);
			}
			
			
			//hide details and show buttons
			v = view.findViewById(R.id.list_item_1_images);
			if(v != null) {
				v.setVisibility(View.GONE);
			}
			
			v = view.findViewById(R.id.buttonsSection);
			if(v != null) {
				v.setVisibility(View.VISIBLE);
			}
		}
	}
	
	private void showProfileDetails(ConfigurationProfile profile) {	
		View view = getView();
		if(profile != null && view != null) {
			TextView text = null;
			
			text = (TextView) view.findViewById(R.id.profileName);
			if(text != null) {
				text.setText(profile.getPayloadDisplayName());
			}
			
			text = (TextView) view.findViewById(R.id.profileOrganization);
			if(text != null) {
				text.setText(profile.getPayloadOrganization());
			}
			
			text = (TextView) view.findViewById(R.id.profileDescription);
			if(text != null) {
				text.setText(profile.getPayloadDescription());
			}
			
			text = (TextView) view.findViewById(R.id.profileSigned);
			if(text != null) {
				text.setText("<<FIXME>>");
			}

			
			//hide row for "received" and "contains" field
			View v = view.findViewById(R.id.rowProfileReceived);
			if(v != null) {
				v.setVisibility(View.GONE);
			}
			
			v = view.findViewById(R.id.rowProfileContains);
			if(v != null) {
				v.setVisibility(View.GONE);
			}
			
			
			//hide details and show buttons
			v = view.findViewById(R.id.list_item_1_images);
			if(v != null) {
				v.setVisibility(View.GONE);
			}
			
			v = view.findViewById(R.id.buttonsSection);
			if(v != null) {
				v.setVisibility(View.VISIBLE);
			}
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
		//step 1 finished
		mName = null;
		mOrganization = null;
		mDescription = null;
		
		mErrCode = taskErrorCode;
		mHttpErrCode = task.getHttpStatusCode();
		
		switchToState(STATE_ERROR);
	}

	@Override
	public void onParsePlistComplete(ParsePlistTask task, Plist plist) {
		//step 1 finished
		mName = plist.getString(ConfigurationProfile.KEY_PAYLOAD_DISPLAY_NAME, null);
		mOrganization = plist.getString(ConfigurationProfile.KEY_PAYLOAD_ORGANIZATION, null);
		mDescription = plist.getString(ConfigurationProfile.KEY_PAYLOAD_DESCRIPTION, null);
		
		mErrCode = TaskError.SUCCESS;
		mHttpErrCode = task.getHttpStatusCode();
		
		new RetrieveConfigurationTask(
				AddProfileFragment.this.getActivity(), 
				AddProfileFragment.this).execute(plist);
	}
	
	@Override
	public void onRetrieveConfigurationFailed(RetrieveConfigurationTask task,
			int taskErrorCode) {
		//step 2 finished
		mErrCode = taskErrorCode;
		mHttpErrCode = task.getHttpStatusCode();
		
		switchToState(STATE_ERROR);
	}

	@Override
	public void onRetrieveConfigurationComplete(RetrieveConfigurationTask task,
			ConfigurationProfile profile) {
		//step 2 finished
		mName = profile.getPayloadDisplayName();
		mOrganization = profile.getPayloadOrganization();
		mDescription = profile.getPayloadDescription();
		
		mErrCode = TaskError.SUCCESS;
		mHttpErrCode = task.getHttpStatusCode();
		
		mProfile = profile;
		
		switchToState(STATE_PARSED);
	}

	@Override
	public void onInstallConfigurationFailed(InstallConfigurationTask task,
			int taskErrorCode) {
		mErrCode = taskErrorCode;
		
		switchToState(STATE_ERROR);
	}

	@Override
	public void onInstallConfigurationComplete(InstallConfigurationTask task,
			List<Action> actions) {
		Activity activity = getActivity();
		if(activity != null) {
			Intent intent = new Intent(activity, Main.class);
			activity.startActivity(intent);
			activity.finish();
		}
	}

}
