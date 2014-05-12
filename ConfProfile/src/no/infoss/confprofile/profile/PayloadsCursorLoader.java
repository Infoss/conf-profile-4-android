package no.infoss.confprofile.profile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import com.getbase.android.db.fluentsqlite.Insert;
import com.getbase.android.db.fluentsqlite.QueryBuilder;
import com.litecoding.classkit.view.LazyCursorList.CursorMapper;

public class PayloadsCursorLoader extends BaseQueryCursorLoader {
	public static final String TAG = PayloadsCursorLoader.class.getSimpleName();
	
	public static final String TABLE = "payloads";
	public static final String COL_PROFILE_ID = "profile_id";
	public static final String COL_PAYLOAD_UUID = "payload_uuid";
	public static final String COL_DATA = "data";
	
	public static final String P_PREFIX = PREFIX.concat(TABLE).concat(".");
	public static final String P_PROFILE_ID = P_PREFIX.concat("P_PROFILE_ID");
	public static final String P_PAYLOAD_UUID = P_PREFIX.concat("P_PAYLOAD_UUID");
	public static final String P_DATA = P_PREFIX.concat("P_DATA");
	
	public static final CursorMapper<PayloadInfo> PROFILE_CURSOR_MAPPER = new CursorMapper<PayloadInfo>() {

		@Override
		public PayloadInfo mapRowToObject(Cursor cursor) {
			PayloadInfo profile = new PayloadInfo();
			profile.profileId = cursor.getString(0);
			profile.payloadUuid = cursor.getString(1);
			profile.data = cursor.getString(2);
			return profile;
		}
	};
	
	private String mNewProfileId[] = null;
	private String mNewPayloadUuid[] = null;
	private String mNewData[] = null;
	
	public PayloadsCursorLoader(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
		super(context, id, params, dbHelper);
		
		if(params != null) {
			if(params.containsKey("P_BATCH_MODE")) {
				mNewProfileId = params.getStringArray(P_PROFILE_ID);
				mNewPayloadUuid = params.getStringArray(P_PAYLOAD_UUID);
				mNewData = params.getStringArray(P_DATA);
			} else {
				mNewProfileId = new String[] { params.getString(P_PROFILE_ID) };
				mNewPayloadUuid = new String[] { params.getString(P_PAYLOAD_UUID) };
				mNewData = new String[] { params.getString(P_DATA) };
			}
		}
	}
	
	@Override
    public Cursor loadInBackground() {		
		Cursor result;
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		
		if(mQueryType == STMT_INSERT && 
				mNewProfileId != null && 
				mNewPayloadUuid != null && 
				mNewData != null) {	
			db.beginTransaction();
			try {
				for(int i = 0; i < mNewProfileId.length; i++) {
					ContentValues values = new ContentValues();
					values.put(COL_PROFILE_ID, mNewProfileId[i]);
					values.put(COL_PAYLOAD_UUID, mNewPayloadUuid[i]);
					values.put(COL_DATA, mNewData[i]);
					Insert.insert().into(TABLE).values(values).perform(db);
				}
				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}
		}
		
		//finally always do select to show changes in the UI
		result = QueryBuilder.select().from(TABLE).all().perform(db);
		
		return result;
	}
	
	public static class PayloadInfo {
		public String profileId;
		public String payloadUuid;
		public String data;
	}
}
