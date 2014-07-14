package no.infoss.confprofile.profile;

import no.infoss.confprofile.BuildConfig;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DbOpenHelper extends SQLiteOpenHelper {
	private static final String TAG = DbOpenHelper.class.getSimpleName();
	
	private static final String TARGET_DB_NAME = "profiles.sqlite";
	private static final int DB_VERSION = 1;
	
	private static final String SQL_PROFILES = "CREATE TABLE profiles (" + 
			"id TEXT PRIMARY KEY NOT NULL, " + 
			"name TEXT NOT NULL, " + 
			"data TEXT NOT NULL);";
	private static final String SQL_LINKED_OBJECTS = "CREATE TABLE linked_objects (" + 
			"profile_id TEXT NOT NULL REFERENCES profiles (id), " + 
			"object_id TEXT PRIMARY KEY NOT NULL, " + 
			"manager_id  TEXT NOT NULL, " + 
			"manager_key TEXT NOT NULL);";
	private static final String SQL_PAYLOADS = "CREATE TABLE payloads (" + 
			"profile_id TEXT NOT NULL REFERENCES profiles (id), " + 
			"payload_uuid TEXT PRIMARY KEY NOT NULL, " + 
			"data TEXT NOT NULL);";
	private static final String SQL_TRIGGER_DELETE_PROFILE = "CREATE TRIGGER delete_profile " + 
			"BEFORE DELETE ON profiles " + 
			"BEGIN " + 
				"DELETE FROM linked_objects WHERE profile_id = old.id; " + 
				"DELETE FROM payloads WHERE profile_id = old.id;" + 
			"END;";
	
	private static DbOpenHelper INSTANCE = null;
 
	private DbOpenHelper(Context context) {
		super(context.getApplicationContext(), TARGET_DB_NAME, null, DB_VERSION);
	}
 
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.beginTransaction();
		try {
			db.execSQL(SQL_PROFILES, new Object[0]);
			db.execSQL(SQL_LINKED_OBJECTS, new Object[0]);
			db.execSQL(SQL_PAYLOADS, new Object[0]);
			db.execSQL(SQL_TRIGGER_DELETE_PROFILE, new Object[0]);
			db.setTransactionSuccessful();
		} catch(Exception e) {
			Log.e(TAG, "SQL error", e);
		} finally {
			db.endTransaction();
		}
	}
	
	@Override
	public void onOpen(SQLiteDatabase db) {
		if(BuildConfig.DEBUG) {
			traceTables(db);
		}
	}
 
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
 
	}
	
	public static final void traceTables(SQLiteDatabase db) {
		Cursor tables = db.rawQuery("SELECT * FROM sqlite_master WHERE type='table'", null);
		while(tables.moveToNext()) {
			Log.d(TAG, "===");
			for(int i = 0; i < tables.getColumnCount(); i++) {
				String data = "<unknown>";
				boolean match = false;
				
				if(!match) {
					try {
						data = "f: ".concat(String.valueOf(tables.getFloat(i)));
						match = true;
					} catch(Exception e) {
						match = false;
					}
				}
				
				if(!match) {
					try {
						data = "i: ".concat(String.valueOf(tables.getInt(i)));
						match = true;
					} catch(Exception e) {
						match = false;
					}
				}
				
				try {
					data = "s: ".concat(tables.getString(i));
					match = true;
				} catch(Exception e) {
					match = false;
				}
				

				Log.d(TAG, tables.getColumnName(i) + ": " + data);
			}
		}
	}
	
	public static synchronized DbOpenHelper getInstance(Context ctx) {
		if(INSTANCE == null) {
			INSTANCE = new DbOpenHelper(ctx);
		}
		
		return INSTANCE;
	}

}
