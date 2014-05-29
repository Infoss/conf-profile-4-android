package no.infoss.confprofile.task;

import java.security.PrivateKey;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import no.infoss.confprofile.crypto.CertificateManager;
import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.format.PayloadFactory;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.format.json.PlistTypeAdapterFactory;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.PayloadsCursorLoader.PayloadsPerformance;
import no.infoss.confprofile.util.ConfigUtils;
import no.infoss.confprofile.vpn.VpnManagerService.VpnConfigInfo;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

public class ObtainOnDemandVpns extends AsyncTask<Void, Void, Integer> {
	public static final String TAG = ObtainOnDemandVpns.class.getSimpleName();
	
	private Context mCtx;
	private PayloadsPerformance mPayloadsPerformance;
	private ObtainOnDemandVpnsListener mListener;
	private List<VpnConfigInfo> mResult;
	
	public ObtainOnDemandVpns(Context ctx, ObtainOnDemandVpnsListener listener) {
		mCtx = ctx.getApplicationContext();
		DbOpenHelper dbHelper = DbOpenHelper.getInstance(mCtx);
		mPayloadsPerformance = new PayloadsPerformance(mCtx, 0, null, dbHelper);
		mListener = listener;
	}

	@Override
	protected Integer doInBackground(Void... params) {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapterFactory(new PlistTypeAdapterFactory());
		Gson gson = gsonBuilder.create();
		
		CertificateManager mgr = CertificateManager.getManagerSync(mCtx, CertificateManager.MANAGER_INTERNAL);
		
		try {
			mResult = new LinkedList<VpnConfigInfo>();
			Cursor payloads = mPayloadsPerformance.perform();
			if(payloads.moveToFirst()) {
				while(!payloads.isAfterLast()) {
					String data = payloads.getString(2);
					Payload payload = PayloadFactory.createPayload(gson.fromJson(data, Dictionary.class));
					if(payload instanceof VpnPayload) {
						VpnPayload vpnPayload = (VpnPayload) payload;
						if(vpnPayload.isOnDemandEnabled()) {
							Dictionary testDict;
							
							testDict = vpnPayload.getIpsec();
							if(testDict == null) {
								testDict = vpnPayload.getVpn();
							}
							if(testDict == null) {
								testDict = vpnPayload.getPpp();
							}
							
							if(testDict != null) {
								VpnConfigInfo configInfo = new VpnConfigInfo();
								configInfo.configId = vpnPayload.getPayloadUUID();
								configInfo.networkConfig = ConfigUtils.build(vpnPayload);
								configInfo.params = new HashMap<String, Object>();
								
								Dictionary tmpDict;
								tmpDict = vpnPayload.getIpsec();
								if(tmpDict != null) {
									configInfo.params.put(VpnConfigInfo.PARAMS_IPSEC, tmpDict.asMap());
								}
								
								tmpDict = vpnPayload.getPpp();
								if(tmpDict != null) {
									configInfo.params.put(VpnConfigInfo.PARAMS_PPP, tmpDict.asMap());
								}
								
								tmpDict = vpnPayload.getVendorConfig();
								if(tmpDict != null) {
									configInfo.params.put(VpnConfigInfo.PARAMS_CUSTOM, tmpDict.asMap());
								}
								
								if(VpnPayload.VPN_TYPE_CUSTOM.equals(vpnPayload.getVpnType())) {
									configInfo.vpnType = vpnPayload.getVpnSubType();
								} else {
									configInfo.vpnType = vpnPayload.getVpnType();
								}
								
								String method = testDict.getString(VpnPayload.KEY_AUTHENTICATION_METHOD);
								if(VpnPayload.AUTH_METHOD_CERTIFICATE.equals(method)) {
									String uuid = testDict.getString(VpnPayload.KEY_PAYLOAD_CERTIFICATE_UUID);
									configInfo.certificates = mgr.getCertificateChain(uuid);
									configInfo.privateKey = (PrivateKey) mgr.getKey(uuid);
								}
								
								mResult.add(configInfo);
							}
						}
					}
					payloads.moveToNext();
				}
			}
		} catch(Exception e) {
			Log.e(TAG, "", e);
			return TaskError.INTERNAL;
		}
		
		return TaskError.SUCCESS;
	}
	
	@Override
	protected void onPostExecute(Integer result) {
		if(result == TaskError.SUCCESS) {
			mListener.obtainOnDemandVpnsSuccess(this, mResult);
		} else {
			mListener.obtainOnDemandVpnsError(this);
		}
	}

	public interface ObtainOnDemandVpnsListener {
		public void obtainOnDemandVpnsSuccess(ObtainOnDemandVpns task, List<VpnConfigInfo> result);
		public void obtainOnDemandVpnsError(ObtainOnDemandVpns task);
	}
}
