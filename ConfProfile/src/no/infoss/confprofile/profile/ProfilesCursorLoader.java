package no.infoss.confprofile.profile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
import android.util.Log;

import com.litecoding.classkit.view.LazyCursorList.CursorMapper;

public class ProfilesCursorLoader extends BaseQueryCursorLoader {
	public static final String TAG = ProfilesCursorLoader.class.getSimpleName();
	
	public static final String TABLE = "profiles";
	public static final String COL_ID = "id";
	public static final String COL_NAME = "name";
	public static final String COL_DESCRIPTION = "description";
	public static final String COL_ORGANIZATION = "organization";
	public static final String COL_DATA = "data";
	public static final String COL_CREATED_AT = "created_at";
	
	public static final String P_PREFIX = PREFIX.concat(TABLE).concat(".");
	public static final String P_ID = P_PREFIX.concat("P_ID");
	public static final String P_NAME = P_PREFIX.concat("P_NAME");
	public static final String P_DESCRIPTION = P_PREFIX.concat("P_DESCRIPTION");
	public static final String P_ORGANIZATION = P_PREFIX.concat("P_ORGANIZATION");
	public static final String P_DATA = P_PREFIX.concat("P_DATA");
	
	public static final CursorMapper<ProfileInfo> PROFILE_CURSOR_MAPPER = new CursorMapper<ProfileInfo>() {

		@Override
		public ProfileInfo mapRowToObject(Cursor cursor) {
			SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
			
			ProfileInfo profile = new ProfileInfo();
			profile.id = cursor.getString(0);
			profile.name = cursor.getString(1);
			profile.description = cursor.getString(2);
			profile.organization = cursor.getString(3);
			profile.data = cursor.getString(4);
			profile.addedAt = null;
			
			String addedAt = cursor.getString(5);
			if(addedAt != null) {
				try {
					profile.addedAt = dateFmt.parse(addedAt);
				} catch(Exception e) {
					Log.e(TAG, "Can't parse date: " + String.valueOf(addedAt));
				}
			}
			
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
		public String description;
		public String organization;
		public String data;
		public Date addedAt;
	}
	
	public static class ProfilesPerformance extends LoaderQueryPerformance {
		private String mNewId[] = null;
		private String mNewName[] = null;
		private String mNewDescription[] = null;
		private String mNewOrganization[] = null;
		private String mNewData[] = null;
		
		public ProfilesPerformance(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
			super(context, id, params, dbHelper);
			
			if(params != null) {
				if(params.containsKey("P_BATCH_MODE")) {
					mNewId = params.getStringArray(P_ID);
					mNewName = params.getStringArray(P_NAME);
					mNewDescription = params.getStringArray(P_DESCRIPTION);
					mNewOrganization = params.getStringArray(P_ORGANIZATION);
					mNewData = params.getStringArray(P_DATA);
				} else {
					mNewId = new String[] { params.getString(P_ID) };
					mNewName = new String[] { params.getString(P_NAME) };
					mNewDescription = new String[] { params.getString(P_DESCRIPTION) };
					mNewOrganization = new String[] { params.getString(P_ORGANIZATION) };
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
						values.put(COL_DESCRIPTION, mNewDescription[i]);
						values.put(COL_ORGANIZATION, mNewOrganization[i]);
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
