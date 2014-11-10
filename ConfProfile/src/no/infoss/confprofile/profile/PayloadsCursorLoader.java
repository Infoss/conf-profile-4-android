package no.infoss.confprofile.profile;

import no.infoss.confprofile.db.Expressions;
import no.infoss.confprofile.db.Expressions.Expression;
import no.infoss.confprofile.db.Insert;
import no.infoss.confprofile.db.QueryBuilder;
import no.infoss.confprofile.db.Select;
import no.infoss.confprofile.profile.data.PayloadInfo;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import com.litecoding.classkit.view.LazyCursorList.CursorMapper;

public class PayloadsCursorLoader extends BaseQueryCursorLoader {
	public static final String TAG = PayloadsCursorLoader.class.getSimpleName();
	
	public static final String TABLE = "payloads";
	public static final String COL_PROFILE_ID = "profile_id";
	public static final String COL_PAYLOAD_UUID = "payload_uuid";
	public static final String COL_PAYLOAD_TYPE = "payload_type";
	public static final String COL_PAYLOAD_DISPLAY_NAME = "payload_display_name";
	public static final String COL_DATA = "data";
	
	public static final String P_PREFIX = PREFIX.concat(TABLE).concat(".");
	public static final String P_PROFILE_ID = P_PREFIX.concat("P_PROFILE_ID");
	public static final String P_PAYLOAD_UUID = P_PREFIX.concat("P_PAYLOAD_UUID");
	public static final String P_PAYLOAD_TYPE = P_PREFIX.concat("P_PAYLOAD_TYPE");
	public static final String P_PAYLOAD_DISPLAY_NAME = P_PREFIX.concat("P_PAYLOAD_DISPLAY_NAME");
	public static final String P_DATA = P_PREFIX.concat("P_DATA");
	
	public static final CursorMapper<PayloadInfo> PAYLOAD_CURSOR_MAPPER = new CursorMapper<PayloadInfo>() {

		@Override
		public PayloadInfo mapRowToObject(Cursor cursor) {
			PayloadInfo profile = new PayloadInfo();
			profile.profileId = cursor.getString(0);
			profile.payloadUuid = cursor.getString(1);
			profile.payloadType = cursor.getString(2);
			profile.payloadDisplayName = cursor.getString(3);
			profile.data = cursor.getString(4);
			return profile;
		}
	};
	
	public PayloadsCursorLoader(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
		super(context, create(context, id, params, dbHelper));
	}
	
	public static PayloadsPerformance create(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
		return new PayloadsPerformance(context, id, params, dbHelper);
	}
	
	public static class PayloadsPerformance extends LoaderQueryPerformance {
		private String mNewProfileId[] = null;
		private String mNewPayloadUuid[] = null;
		private String mNewPayloadType[] = null;
		private String mNewPayloadDisplayName[] = null;
		private String mNewData[] = null;
		private String mSelectBy = null; 
		private String mSelectValue = null;
		
		public PayloadsPerformance(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
			super(context, id, params, dbHelper);
			
			if(params != null) {
				if(params.containsKey(P_BATCH_MODE)) {
					mNewProfileId = params.getStringArray(P_PROFILE_ID);
					mNewPayloadUuid = params.getStringArray(P_PAYLOAD_UUID);
					mNewPayloadType = params.getStringArray(P_PAYLOAD_TYPE);
					mNewPayloadDisplayName = params.getStringArray(P_PAYLOAD_DISPLAY_NAME);
					mNewData = params.getStringArray(P_DATA);
				} else {
					mNewProfileId = new String[] { params.getString(P_PROFILE_ID) };
					mNewPayloadUuid = new String[] { params.getString(P_PAYLOAD_UUID) };
					mNewPayloadType = new String[] { params.getString(P_PAYLOAD_TYPE) };
					mNewPayloadDisplayName = new String[] { params.getString(P_PAYLOAD_DISPLAY_NAME) };
					mNewData = new String[] { params.getString(P_DATA) };
				}
				
				if(params.containsKey(P_SELECT_BY)) {
					mSelectBy = params.getString(P_SELECT_BY);
				}
				
				if(params.containsKey(P_SELECT_VALUE)) {
					mSelectValue = params.getString(P_SELECT_VALUE);
				}
			}
		}
		
		@Override
	    public Cursor perform() {		
			Cursor result;
			SQLiteDatabase db = mDbHelper.getWritableDatabase();
			
			if(mQueryType == STMT_INSERT && 
					mNewProfileId != null && 
					mNewPayloadUuid != null &&
					mNewPayloadType != null &&
					mNewPayloadDisplayName != null && 
					mNewData != null) {	
				db.beginTransaction();
				try {
					for(int i = 0; i < mNewProfileId.length; i++) {
						ContentValues values = new ContentValues();
						values.put(COL_PROFILE_ID, mNewProfileId[i]);
						values.put(COL_PAYLOAD_UUID, mNewPayloadUuid[i]);
						values.put(COL_PAYLOAD_TYPE, mNewPayloadType[i]);
						values.put(COL_PAYLOAD_DISPLAY_NAME, mNewPayloadDisplayName[i]);
						values.put(COL_DATA, mNewData[i]);
						Insert.insert().into(TABLE).values(values).perform(db);
					}
					db.setTransactionSuccessful();
				} finally {
					db.endTransaction();
				}
			}
			
			Select select = QueryBuilder.select().from(TABLE);
			if(mSelectBy == null) {
				select = select.all();
			} else {
				Expression expr = Expressions.column(mSelectBy).eq(Expressions.literal(mSelectValue));
				select.where(expr, new Object[0]);
			}
			
			//finally always do select to show changes in the UI
			result = select.perform(db).getCursor();
			
			return result;
		}
	}
}
