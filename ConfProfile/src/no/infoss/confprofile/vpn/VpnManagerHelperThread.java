package no.infoss.confprofile.vpn;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.VpnDataCursorLoader;
import no.infoss.confprofile.profile.VpnDataCursorLoader.VpnDataPerformance;
import no.infoss.confprofile.profile.data.VpnData;
import no.infoss.confprofile.profile.data.VpnDataEx;
import no.infoss.confprofile.profile.data.VpnOnDemandConfig;
import no.infoss.confprofile.util.HelperThread;
import no.infoss.confprofile.util.HelperThread.Callback;
import no.infoss.confprofile.util.HelperThread.Performer;

public class VpnManagerHelperThread extends HelperThread<VpnManagerHelperThread.OnDemandVpnListRequest, 
		VpnManagerHelperThread.OnDemandVpnListPerformer, 
		Callback<VpnManagerHelperThread.OnDemandVpnListRequest, 
				VpnManagerHelperThread.OnDemandVpnListPerformer>> {
	public VpnManagerHelperThread() {
		super();
	}
	
	@Override
	protected void checkConditionsToRun() throws IllegalStateException {
	// TODO Auto-generated method stub
	
	}
	
	@Override
	protected void freeResources() {
	// TODO Auto-generated method stub
	
	}
	
	public static class OnDemandVpnListRequest {
		public Bundle request;
		public List<VpnDataEx> result;
	}
	
	public static class OnDemandVpnListPerformer implements Performer<OnDemandVpnListRequest> {
		private Context mCtx;
		
		public OnDemandVpnListPerformer(Context ctx) {
			mCtx = ctx;
		}

		@Override
		public boolean perform(OnDemandVpnListRequest request) {
			DbOpenHelper dbHelper = DbOpenHelper.getInstance(mCtx);
			VpnDataPerformance vpnDataPerformance = new VpnDataPerformance(mCtx, 0, request.request, dbHelper);
			
			try {
				request.result = new ArrayList<VpnDataEx>(10);
				Cursor payloads = vpnDataPerformance.perform();
				if(payloads.moveToFirst()) {
					while(!payloads.isAfterLast()) {
						VpnData vpnData = VpnDataCursorLoader.VPN_DATA_CURSOR_MAPPER.mapRowToObject(payloads);
						VpnDataEx vpnDataEx = new VpnDataEx(vpnData);
						vpnDataEx.setOnDemandConfiguration(VpnOnDemandConfig.fromJson(vpnDataEx.getOnDemandRules()));			
						request.result.add(vpnDataEx);
						payloads.moveToNext();
					}
				}
			} catch(Exception e) {
				Log.e(TAG, "", e);
				return false;
			}
			
			return true;
		}
		
	}
}
