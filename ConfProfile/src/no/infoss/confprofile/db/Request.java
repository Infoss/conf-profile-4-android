package no.infoss.confprofile.db;

import android.database.sqlite.SQLiteDatabase;

public abstract class Request {
	protected boolean mIsAlreadyPerformed = false;
	protected String mTableName = null;
	
	public void setTableName(String tableName) {
		mTableName = tableName;
	}
	
	public abstract Request perform(SQLiteDatabase db);
}

