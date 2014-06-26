package no.infoss.confprofile.profile;

import no.infoss.confprofile.db.Delete;
import no.infoss.confprofile.db.Expressions;
import no.infoss.confprofile.db.Expressions.Expression;
import no.infoss.confprofile.db.Insert;
import no.infoss.confprofile.db.QueryBuilder;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import com.litecoding.classkit.view.LazyCursorList.CursorMapper;

public class ProfilesCursorLoader extends BaseQueryCursorLoader {
	public static final String TAG = ProfilesCursorLoader.class.getSimpleName();
	
	public static final String TABLE = "profiles";
	public static final String COL_ID = "id";
	public static final String COL_NAME = "name";
	public static final String COL_DATA = "data";
	
	public static final String P_PREFIX = PREFIX.concat(TABLE).concat(".");
	public static final String P_ID = P_PREFIX.concat("P_ID");
	public static final String P_NAME = P_PREFIX.concat("P_NAME");
	public static final String P_DATA = P_PREFIX.concat("P_DATA");
	
	public static final CursorMapper<ProfileInfo> PROFILE_CURSOR_MAPPER = new CursorMapper<ProfileInfo>() {

		@Override
		public ProfileInfo mapRowToObject(Cursor cursor) {
			ProfileInfo profile = new ProfileInfo();
			profile.id = cursor.getString(0);
			profile.name = cursor.getString(1);
			profile.data = cursor.getString(2);
			return profile;
		}
	};
	
	public ProfilesCursorLoader(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
		super(context, create(context, id, params, dbHelper));
	}
	
	public static ProfilesPerformance create(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
		return new ProfilesPerformance(context, id, params, dbHelper);
	}
	
	public static class ProfileInfo {
		public String id;
		public String name;
		public String data;
	}
	
	public static class ProfilesPerformance extends LoaderQueryPerformance {
		private String mNewId[] = null;
		private String mNewName[] = null;
		private String mNewData[] = null;
		
		public ProfilesPerformance(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
			super(context, id, params, dbHelper);
			
			if(params != null) {
				if(params.containsKey("P_BATCH_MODE")) {
					mNewId = params.getStringArray(P_ID);
					mNewName = params.getStringArray(P_NAME);
					mNewData = params.getStringArray(P_DATA);
				} else {
					mNewId = new String[] { params.getString(P_ID) };
					mNewName = new String[] { params.getString(P_NAME) };
					mNewData = new String[] { params.getString(P_DATA) };
				}
			}
		}

		@Override
		public Cursor perform() {
			Cursor result;
			SQLiteDatabase db = mDbHelper.getWritableDatabase();
			
			if(mQueryType == STMT_INSERT && 
					mNewId != null && 
					mNewName != null && 
					mNewData != null) {	
				db.beginTransaction();
				try {
					for(int i = 0; i < mNewId.length; i++) {
						ContentValues values = new ContentValues();
						values.put(COL_ID, mNewId[i]);
						values.put(COL_NAME, mNewName[i]);
						values.put(COL_DATA, mNewData[i]);
						Insert.insert().into(TABLE).values(values).perform(db);
					}
					db.setTransactionSuccessful();
				} finally {
					db.endTransaction();
				}
			} else if(mQueryType == STMT_DELETE && 
					mNewId != null) {
				db.beginTransaction();
				try {
					for(int i = 0; i < mNewId.length; i++) {
						Expression expr = Expressions.column(COL_ID).eq(Expressions.literal(mNewId[i]));
						Delete.delete().from(TABLE).where(expr, (Object[]) null).perform(db);
					}
					db.setTransactionSuccessful();
				} finally {
					db.endTransaction();
				}
			}
			
			//finally always do select to show changes in the UI
			result = QueryBuilder.select().from(TABLE).all().perform(db).getCursor();
			
			return result;
		}
	}
}
