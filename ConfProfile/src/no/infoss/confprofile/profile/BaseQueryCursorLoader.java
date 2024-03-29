package no.infoss.confprofile.profile;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

public abstract class BaseQueryCursorLoader extends CursorLoader {
	public static final String STMT_TYPE = "data.STMT_TYPE";
	public static final int STMT_SELECT = 0;
	public static final int STMT_INSERT = 1;
	public static final int STMT_DELETE = 2;
	public static final int STMT_UPDATE = 4;
	
	public static final String PREFIX = "no.infoss.data.";
	public static final String P_BATCH_MODE = PREFIX.concat("P_BATCH_MODE");
	public static final String P_SELECT_BY = PREFIX.concat("P_SELECT_BY");
	public static final String P_SELECT_VALUE = PREFIX.concat("P_SELECT_VALUE");
	
	protected LoaderQueryPerformance mPerformance;
	
	public BaseQueryCursorLoader(Context context, LoaderQueryPerformance performance) {
		super(context);
		
		mPerformance = performance;
	}
	
	@Override
	public int getId() {
		return mPerformance.getId();
	}
	
	@Override
    public Cursor loadInBackground() {		
		return mPerformance.perform();
	}
	
	public static Cursor perform(LoaderQueryPerformance performance) {
		return performance.perform();
	}

	public abstract static class LoaderQueryPerformance {
		protected int mId;
		protected DbOpenHelper mDbHelper;
		protected int mQueryType = STMT_SELECT;
		
		public LoaderQueryPerformance(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
			mId = id;
			mDbHelper = dbHelper;
			
			if(params != null) {
				mQueryType = params.getInt(STMT_TYPE, STMT_SELECT);
			}
		}
		
		public int getId() {
			return mId;
		}
		
		public abstract Cursor perform();
		
	}
	
	public static class AsyncPerformance extends AsyncTask<Void, Void, Cursor> {
		public static final String TAG = AsyncPerformance.class.getSimpleName();
		
		private LoaderQueryPerformance mPerformance;
		private AsyncPerformanceListener mListener;
		
		public AsyncPerformance(LoaderQueryPerformance performance, AsyncPerformanceListener listener) {
			mPerformance = performance;
			mListener = listener;
		}
		
		public LoaderQueryPerformance getPerformance() {
			return mPerformance;
		}
		
		@Override
		protected Cursor doInBackground(Void... params) {
			Cursor result = null;
			try {
				result = mPerformance.perform();
			} catch(Exception e) {
				Log.e(TAG, "Error while performing a query", e);
			}
			return result;
		}
		
		@Override
		protected void onPostExecute(Cursor result) {
			if(result != null) {
				mListener.onAsyncPerformanceSuccess(this, result);
			} else {
				mListener.onAsyncPerformanceError(this);
			}
		}
		
	}
	
	public interface AsyncPerformanceListener {
		public void onAsyncPerformanceSuccess(AsyncPerformance task, Cursor result);
		public void onAsyncPerformanceError(AsyncPerformance task);
	}
}
