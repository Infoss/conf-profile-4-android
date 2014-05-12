package no.infoss.confprofile.profile;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.os.Bundle;

public abstract class BaseQueryCursorLoader extends CursorLoader {
	public static final String STMT_TYPE = "data.STMT_TYPE";
	public static final int STMT_SELECT = 0;
	public static final int STMT_INSERT = 1;
	public static final int STMT_DELETE = 2;
	
	public static final String PREFIX = "no.infoss.data.";
	public static final String P_BATCH_MODE = PREFIX.concat("P_BATCH_MODE");
	
	protected int mId;
	protected DbOpenHelper mDbHelper;
	protected int mQueryType = STMT_SELECT;
	
	public BaseQueryCursorLoader(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
		super(context);
		
		mId = id;
		mDbHelper = dbHelper;
		
		if(params != null) {
			mQueryType = params.getInt(STMT_TYPE, STMT_SELECT);
		}
	}
	
	@Override
	public int getId() {
		return mId;
	}
	
	public static <T extends CursorLoader> Cursor perform(T loader) {
		return new LoaderQueryPerformer(loader).perform();
	}

	public static class LoaderQueryPerformer {
		private CursorLoader mLoader;
		public <T extends CursorLoader> LoaderQueryPerformer(T loader) {
			mLoader = loader;
		}
		
		public Cursor perform() {
			return mLoader.loadInBackground();
		}
		
	}
}
