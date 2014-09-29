package no.infoss.confprofile.fragment;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import no.infoss.confprofile.Main;
import no.infoss.confprofile.ProfilePayloads;
import no.infoss.confprofile.R;
import no.infoss.confprofile.db.Delete;
import no.infoss.confprofile.db.Expressions;
import no.infoss.confprofile.db.Request;
import no.infoss.confprofile.db.Expressions.Expression;
import no.infoss.confprofile.db.RequestWithAffectedRows;
import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadsCursorLoader;
import no.infoss.confprofile.profile.ProfilesCursorLoader;
import no.infoss.confprofile.profile.ProfilesCursorLoader.ProfileInfo;
import no.infoss.confprofile.profile.data.PayloadInfo;
import no.infoss.confprofile.util.ConfigUtils;
import no.infoss.confprofile.util.SqliteRequestThread;
import no.infoss.confprofile.util.SqliteRequestThread.SqliteUpdateDeleteCallback;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

public class ProfileDetailsFragment extends Fragment implements LoaderCallbacks<Cursor>  {
	public static final String TAG = ProfileDetailsFragment.class.getSimpleName(); 
	
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
		
		setHasOptionsMenu(true);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		View view = inflater.inflate(R.layout.fragment_profile_details, container, false);
		View subView = view.findViewById(R.id.list_item_1_images);
		if(subView != null) {
			subView.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					Activity activity = getActivity();
					if(activity != null) {
						Intent intent = new Intent(activity, ProfilePayloads.class);
						intent.putExtra(ProfilesCursorLoader.P_ID, mProfileId);
						activity.startActivity(intent);
					}
				}
			});
		}
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
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.profile_details, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.menu_item_delete: {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle(R.string.dialog_confirm_profile_removal_label);
			builder.setMessage(R.string.dialog_confirm_profile_removal_text);
			builder.setPositiveButton(R.string.dialog_confirm_profile_removal_btn_ok, 
					new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Expression expr = Expressions.column(ProfilesCursorLoader.COL_ID).eq(Expressions.literal(mProfileId));
					Delete request = Delete.delete().from(ProfilesCursorLoader.TABLE).where(expr, (Object[]) null);
					SqliteRequestThread.getInstance().request(request, new SqliteUpdateDeleteCallback() {
						@Override
						protected void onSqliteUpdateDeleteSuccess(RequestWithAffectedRows request) {
							Toast.makeText(getActivity(), 
									R.string.toast_profile_removal_success, 
									Toast.LENGTH_SHORT).show();
						}
						
						@Override
						protected void onSqliteRequestSuccess(Request request) {
							//nothing to do here
						}
						
						@Override
						protected void onSqliteRequestError(Request request) {
							Toast.makeText(getActivity(), 
									R.string.toast_profile_removal_success, 
									Toast.LENGTH_SHORT).show();
						}
					});
					
					Intent intent = new Intent(getActivity(), Main.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intent.putExtra(Main.EXTRA_RESTART_LOADERS, true);
					getActivity().startActivity(intent);
				}
			});
			
			builder.setNegativeButton(R.string.dialog_confirm_profile_removal_btn_cancel, 
					new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			
			builder.create().show();
			return true;
		}
		}
		
		return super.onOptionsItemSelected(item);
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
			
			
			//hide details and show buttons
			View v = view.findViewById(R.id.list_item_1_images);
			if(v != null) {
				v.setVisibility(View.VISIBLE);
			}
			
			v = view.findViewById(R.id.buttonsSection);
			if(v != null) {
				v.setVisibility(View.GONE);
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
