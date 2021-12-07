package no.infoss.confprofile.db;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/**
 * Class for performing Insert request, partially compatible with 
 * corresponding class from android-db-commons.
 * @author Dmitry Vorobiev
 *
 */
public class Insert extends Request {
	private ContentValues mContentValues = null;
	private long mRowId = -1;
	
	private Insert() {
		
	}
	
	public Insert into(String tableName) {
		setTableName(tableName);
		return this;
	}
	
	public Insert values(ContentValues contentValues) {
		mContentValues = contentValues;
		return this;
	}
	
	@Override
	public Insert perform(SQLiteDatabase db) {
		if(mIsAlreadyPerformed) {
			throw new IllegalStateException("This request was already performed");
		}
		
		mRowId = db.insert(mTableName, null, mContentValues);
		mIsAlreadyPerformed = true;
		return this;
	}
	
	public long getRowId() {
		return mRowId;
	}
	
	public static final Insert insert() {
		return new Insert();
	}
}
