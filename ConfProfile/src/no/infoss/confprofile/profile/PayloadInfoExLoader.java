package no.infoss.confprofile.profile;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import no.infoss.confprofile.format.ConfigurationProfileException;
import no.infoss.confprofile.format.PayloadFactory;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.json.PlistTypeAdapterFactory;
import no.infoss.confprofile.profile.PayloadsCursorLoader.PayloadsPerformance;
import no.infoss.confprofile.profile.data.PayloadInfo;
import no.infoss.confprofile.profile.data.PayloadInfoEx;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.litecoding.classkit.view.LazyCursorList;

public class PayloadInfoExLoader extends AsyncTaskLoader<List<List<PayloadInfoEx>>> {
	public static final String TAG = PayloadInfoExLoader.class.getSimpleName();
	
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
