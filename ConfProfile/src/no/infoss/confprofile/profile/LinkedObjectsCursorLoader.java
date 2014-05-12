package no.infoss.confprofile.profile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;

import com.getbase.android.db.fluentsqlite.Insert;
import com.getbase.android.db.fluentsqlite.QueryBuilder;
import com.litecoding.classkit.view.LazyCursorList.CursorMapper;

public class LinkedObjectsCursorLoader extends BaseQueryCursorLoader {
	public static final String TAG = LinkedObjectsCursorLoader.class.getSimpleName();
	
	public static final String TABLE = "linked_objects";
	public static final String COL_PROFILE_ID = "profile_id";
	public static final String COL_OBJECT_ID = "object_id"; 
	public static final String COL_MANAGER_ID = "manager_id";
	public static final String COL_MANAGER_KEY = "manager_key";
	
	public static final String LO_PREFIX = PREFIX.concat(TABLE).concat(".");
	public static final String LO_PROFILE_ID = LO_PREFIX.concat("LO_PROFILE_ID");
	public static final String LO_OBJECT_ID = LO_PREFIX.concat("LO_OBJECT_ID");
	public static final String LO_MANAGER_ID = LO_PREFIX.concat("LO_MANAGER_ID");
	public static final String LO_MANAGER_KEY = LO_PREFIX.concat("LO_MANAGER_KEY");
	
	public static final CursorMapper<LinkedObjectInfo> OBJECT_CURSOR_MAPPER = new CursorMapper<LinkedObjectInfo>() {

		@Override
		public LinkedObjectInfo mapRowToObject(Cursor cursor) {
			LinkedObjectInfo lobject = new LinkedObjectInfo();
			lobject.profileId = cursor.getString(0);
			lobject.objectId = cursor.getString(1);
			lobject.managerId = cursor.getString(2);
			lobject.managerKey = cursor.getString(3); 
			return lobject;
		}
	};
	
	public LinkedObjectsCursorLoader(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
		super(context, create(context, id, params, dbHelper));
	}
	
	public static LinkedObjectsPerformance create(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
		return new LinkedObjectsPerformance(context, id, params, dbHelper);
	}
	
	public static class LinkedObjectInfo {
		public String profileId;
		public String objectId; 
		public String managerId;
		public String managerKey;
	}
	
	public static class LinkedObjectsPerformance extends LoaderQueryPerformance {
		private String mNewProfileId = null;
		private String mNewObjectId = null;
		private String mNewManagerId = null;
		private String mNewManagerKey = null;
		
		public LinkedObjectsPerformance(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
			super(context, id, params, dbHelper);
			
			if(params != null) {
				mNewProfileId = params.getString(LO_PROFILE_ID);
				mNewObjectId = params.getString(LO_OBJECT_ID);
				mNewManagerId = params.getString(LO_MANAGER_ID);
				mNewManagerKey = params.getString(LO_MANAGER_KEY);
			}
		}
		
		@Override
	    public Cursor perform() {
			Cursor result;
			if(mQueryType == STMT_INSERT && 
					mNewProfileId != null && 
					mNewObjectId != null && 
					mNewManagerId != null &&
					mNewManagerKey != null) {
				ContentValues values = new ContentValues();
				values.put(COL_PROFILE_ID, mNewProfileId);
				values.put(COL_OBJECT_ID, mNewObjectId);
				values.put(COL_MANAGER_ID, mNewManagerId);
				values.put(COL_MANAGER_KEY, mNewManagerKey);
				Insert.insert().into(TABLE).values(values).perform(mDbHelper.getWritableDatabase());
			}
			
			//finally always do select
			result = QueryBuilder.select().from(TABLE).all().perform(mDbHelper.getWritableDatabase());
			
			return result;
		}
	}
}
