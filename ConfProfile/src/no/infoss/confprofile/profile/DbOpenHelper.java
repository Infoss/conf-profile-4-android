package no.infoss.confprofile.profile;

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
 
	public DbOpenHelper(Context context) {
		super(context, TARGET_DB_NAME, null, DB_VERSION);
	}
 
	@Override
	public void onCreate(SQLiteDatabase db) {
		try {
			db.execSQL(SQL_PROFILES, new Object[0]);
		} catch(Exception e) {
			Log.e(TAG, "SQL_PROFILES error", e);
		}
		
		try {
			db.execSQL(SQL_LINKED_OBJECTS, new Object[0]);
		} catch(Exception e) {
			Log.e(TAG, "SQL_LINKED_OBJECTS error", e);
		}
		
		try {
			db.execSQL(SQL_PAYLOADS, new Object[0]);
		} catch(Exception e) {
			Log.e(TAG, "SQL_PAYLOADS error", e);
		}
	}
	
	@Override
	public void onOpen(SQLiteDatabase db) {
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
 
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
 
	}

}
