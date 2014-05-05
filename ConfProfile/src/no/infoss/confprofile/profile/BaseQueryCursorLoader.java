package no.infoss.confprofile.profile;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;

public abstract class BaseQueryCursorLoader extends CursorLoader {
	public static final String STMT_TYPE = "data.STMT_TYPE";
	public static final int STMT_SELECT = 0;
	public static final int STMT_INSERT = 1;
	public static final int STMT_DELETE = 2;
	
	public static final String PREFIX = "no.infoss.data.";
	
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

}
