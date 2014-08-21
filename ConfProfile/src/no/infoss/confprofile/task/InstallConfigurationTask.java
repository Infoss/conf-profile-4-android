package no.infoss.confprofile.task;

import java.lang.ref.WeakReference;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import no.infoss.confprofile.R;
import no.infoss.confprofile.crypto.CertificateManager;
import no.infoss.confprofile.db.Insert;
import no.infoss.confprofile.format.ConfigurationProfile;
import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.format.RootCertPayload;
import no.infoss.confprofile.format.ScepPayload;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.format.json.PlistTypeAdapterFactory;
import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadsCursorLoader;
import no.infoss.confprofile.profile.ProfilesCursorLoader;
import no.infoss.confprofile.profile.VpnDataCursorLoader;
import no.infoss.confprofile.util.CryptoUtils;
import no.infoss.confprofile.util.ScepUtils;
import no.infoss.confprofile.util.ScepUtils.ScepStruct;
import no.infoss.confprofile.util.SqliteRequestThread;
import android.content.ContentValues;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class InstallConfigurationTask extends AsyncTask<ConfigurationProfile, Void, Integer> {
	public static final String TAG = InstallConfigurationTask.class.getSimpleName();
	
	private Context mCtx;
	private DbOpenHelper mDbHelper;
	private WeakReference<InstallConfigurationTaskListener> mListener;
	
	private String mUserAgent;
	private List<Action> mActions = new LinkedList<Action>();
	
	public InstallConfigurationTask(Context ctx, DbOpenHelper dbHelper, InstallConfigurationTaskListener listener) {
		mCtx = ctx;
		mDbHelper = dbHelper;
		mListener = new WeakReference<InstallConfigurationTaskListener>(listener);
		
		mUserAgent = mCtx.getString(R.string.idevice_ua);
	}
	
	@Override
	protected Integer doInBackground(ConfigurationProfile... profiles) {
		ConfigurationProfile profile = profiles[0];
		
		try {
			CertificateManager mgr = CertificateManager.getManagerSync(mCtx, CertificateManager.MANAGER_INTERNAL);
			
			//RESUME Phase 3
			List<ConfigurationProfile> confProfiles = new ArrayList<ConfigurationProfile>(2);
			confProfiles.add(profile);
			
			String profileId = profile.getPayloadIdentifier();
			InstallProfile act = new InstallProfile();
			act.profile = profile;
			mActions.add(act);
			
			while(confProfiles.size() > 0) {
				ConfigurationProfile confProfile = confProfiles.remove(0);
				
				List<Payload> payloads = confProfile.getPayloads();
				for(Payload payload : payloads) {
					if(payload instanceof ScepPayload) {
						ScepStruct scep = ScepUtils.doScep((ScepPayload) payload, mCtx, mUserAgent);
						if(!scep.isFailed && !scep.isPending) {
							InstallPrivateKey action = new InstallPrivateKey();
							action.chain = scep.certs;
							action.privateKey = CryptoUtils.getRSAPrivateKey(scep.keyPair);
							action.alias = payload.getPayloadUUID();
							action.certificateMgrId = CertificateManager.MANAGER_INTERNAL;
							mActions.add(action);
						}
					} else if(payload instanceof VpnPayload) {
						InstallPayload action = new InstallPayload();
						action.payload = (VpnPayload) payload;
						mActions.add(action);
					} else if(payload instanceof RootCertPayload) {
						InstallCertificate action = new InstallCertificate();
						action.certificate = ((RootCertPayload) payload).getPayloadContent();
						action.alias = payload.getPayloadUUID();
						action.certificateMgrId = CertificateManager.MANAGER_ANDROID_RAW;
						mActions.add(action);
						
						action = new InstallCertificate();
						action.certificate = ((RootCertPayload) payload).getPayloadContent();
						action.alias = payload.getPayloadUUID();
						action.certificateMgrId = CertificateManager.MANAGER_INTERNAL;
						mActions.add(action);
					} else {
						Log.d(TAG, payload.toString());
					}
				}			
			}
			//END Phase 3
			SqliteRequestThread.getInstance().start(mCtx);
			
			//perform some actions in background
			List<Action> actions = new LinkedList<Action>();
			actions.addAll(mActions);
			mActions.clear();
			for(Action action : actions) {
				
				//filter out actions with user interaction
				if(action instanceof InstallCertificate) {
					InstallCertificate instAction = (InstallCertificate) action;
					if(CertificateManager.MANAGER_ANDROID_RAW.equals(instAction.certificateMgrId)) {
						mActions.add(instAction);
						continue;
					}
				}
				
				//TODO: fight with spaghetti again 
				if(action instanceof InstallProfile) {
					InstallProfile instAction = (InstallProfile) action;
					BaseQueryCursorLoader.perform(
							ProfilesCursorLoader.create(mCtx, 0, instAction.asBundle(), mDbHelper));
				} else if(action instanceof InstallPrivateKey) {
					InstallPrivateKey instAction = (InstallPrivateKey) action;
					mgr = CertificateManager.getManager(mCtx, instAction.certificateMgrId);
					if(mgr == null) {
						Log.e(TAG, "Unknown certificate manager ".concat(instAction.certificateMgrId));
						continue;
					}
					
					while(!mgr.isLoaded()) {
						try{
							Thread.sleep(1000);
						} catch(InterruptedException e) {
							//nothing to do here
						}
					}
					
					mgr.putKey(instAction.alias, instAction.privateKey, null, instAction.chain);
					mgr.store();
				} else if(action instanceof InstallCertificate) {
					InstallCertificate instAction = (InstallCertificate) action;
					mgr = CertificateManager.getManager(mCtx, instAction.certificateMgrId);
					if(mgr == null) {
						Log.e(TAG, "Unknown certificate manager ".concat(instAction.certificateMgrId));
						continue;
					}
					
					while(!mgr.isLoaded()) {
						try{
							Thread.sleep(1000);
						} catch(InterruptedException e) {
							//nothing to do here
						}
					}
					
					mgr.putCertificate(instAction.alias, instAction.certificate);
					mgr.store();
				} else if(action instanceof InstallPayload) {
					InstallPayload instAction = (InstallPayload) action;
					instAction.profileId = profileId;
					BaseQueryCursorLoader.perform(
							PayloadsCursorLoader.create(mCtx, 0, instAction.asBundle(), mDbHelper));
					
					if(instAction.payload instanceof VpnPayload) {
						VpnPayload vpnPayload = (VpnPayload) instAction.payload;
						
						//TODO: do somethind gith these strange stray keys
						int overridePrimary = 0;
						if(vpnPayload.getIpv4() != null) {
							overridePrimary = vpnPayload.getIpv4().getInteger(VpnPayload.KEY_OVERRIDE_PRIMARY, 0);
						}
						int onDemandEnabled = 0; 
						if(vpnPayload.getVpn() != null) {
							onDemandEnabled = vpnPayload.getVpn().getInteger(VpnPayload.KEY_ON_DEMAND_ENABLED, 0);
						}
						
						ContentValues values = new ContentValues();
						values.put(VpnDataCursorLoader.COL_PAYLOAD_UUID, vpnPayload.getPayloadUUID());
						values.put(VpnDataCursorLoader.COL_USER_DEFINED_NAME, vpnPayload.getUserDefinedName());
						values.put(VpnDataCursorLoader.COL_OVERRIDE_PRIMARY, overridePrimary);
						values.put(VpnDataCursorLoader.COL_ON_DEMAND_ENABLED, onDemandEnabled);
						values.put(VpnDataCursorLoader.COL_ON_DEMAND_ENABLED_BY_USER, onDemandEnabled);
						Insert request = Insert.insert().into(VpnDataCursorLoader.TABLE).values(values);
						
						SqliteRequestThread.getInstance().request(request, null);
					}
				}
			}
			actions.clear();
			
		} catch(Exception e) {
			Log.e(TAG, "Unexpected exception", e);
			return TaskError.INTERNAL;
		}
		
		return TaskError.SUCCESS;
	}
	
	@Override
	protected void onPostExecute(Integer result) {
		InstallConfigurationTaskListener listener = mListener.get();
		if(listener != null) {
			if(result == TaskError.SUCCESS) {
				listener.onInstallConfigurationComplete(this, mActions);
			} else {
				listener.onInstallConfigurationFailed(this, result);
			}
		}
		
		mCtx = null;
	}	
	
	public interface InstallConfigurationTaskListener {
		public void onInstallConfigurationFailed(InstallConfigurationTask task, int taskErrorCode);
		public void onInstallConfigurationComplete(InstallConfigurationTask task, List<Action> actions);
	}
	
	public abstract static class Action {
		public abstract Bundle asBundle();
	}
	
	public static class InstallCertificate extends Action {
		public Certificate certificate;
		public String alias;
		public String certificateMgrId;
		
		@Override
		public Bundle asBundle() {
			return null;
		}
	}
	
	public static class InstallPrivateKey extends InstallCertificate {
		public PrivateKey privateKey;
		public Certificate[] chain;
		
		@Override
		public Bundle asBundle() {
			return null;
		}
	}
	
	public static class InstallProfile extends Action {
		public ConfigurationProfile profile;
		
		@Override
		public Bundle asBundle() {
			Bundle bundle = new Bundle();
			bundle.putInt(BaseQueryCursorLoader.STMT_TYPE, BaseQueryCursorLoader.STMT_INSERT);
			bundle.putString(ProfilesCursorLoader.P_ID, profile.getPayloadIdentifier());
			bundle.putString(ProfilesCursorLoader.P_NAME, profile.getPayloadDisplayName());
			
			GsonBuilder gsonBuilder = new GsonBuilder();
			gsonBuilder.registerTypeAdapterFactory(new PlistTypeAdapterFactory());
			Gson gson = gsonBuilder.create();
			
			bundle.putString(ProfilesCursorLoader.P_DATA, gson.toJson(profile));
			return bundle;
		}
	}
	
	public static class InstallPayload extends Action {
		public String profileId;
		public Payload payload;
		
		@Override
		public Bundle asBundle() {
			Bundle bundle = new Bundle();
			bundle.putInt(BaseQueryCursorLoader.STMT_TYPE, BaseQueryCursorLoader.STMT_INSERT);
			bundle.putString(PayloadsCursorLoader.P_PROFILE_ID, profileId);
			bundle.putString(PayloadsCursorLoader.P_PAYLOAD_UUID, payload.getPayloadUUID());
			bundle.putString(PayloadsCursorLoader.P_PAYLOAD_TYPE, payload.getPayloadType());
			
			GsonBuilder gsonBuilder = new GsonBuilder();
			gsonBuilder.registerTypeAdapterFactory(new PlistTypeAdapterFactory());
			Gson gson = gsonBuilder.create();
			
			bundle.putString(PayloadsCursorLoader.P_DATA, gson.toJson(payload.getDictionary()));
			return bundle;
		}
	}
}
