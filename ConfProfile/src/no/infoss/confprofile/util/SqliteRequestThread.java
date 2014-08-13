package no.infoss.confprofile.util;

import java.util.concurrent.ConcurrentLinkedQueue;

import no.infoss.confprofile.db.Insert;
import no.infoss.confprofile.db.Request;
import no.infoss.confprofile.db.RequestWithAffectedRows;
import no.infoss.confprofile.profile.DbOpenHelper;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class SqliteRequestThread extends Thread {
	public static final String TAG = SqliteRequestThread.class.getSimpleName();
	private static final SqliteRequestThread INSTANCE = new SqliteRequestThread();
	
	private final ConcurrentLinkedQueue<RequestData> mQueue;
	private final Handler mHandler = new Handler(Looper.getMainLooper());
	private SQLiteDatabase mDb = null;
	
	private SqliteRequestThread() {
		super(SqliteRequestThread.class.getSimpleName());
		mQueue = new ConcurrentLinkedQueue<RequestData>();
	}
	
	public synchronized void start(Context ctx) {
		if(getState() == State.NEW) {
			DbOpenHelper helper = DbOpenHelper.getInstance(ctx);
			mDb = helper.getWritableDatabase();
			start();
		}
	}
	
	@Override
	public synchronized void start() {
		if(mDb == null) {
			throw new IllegalArgumentException("Database isn't ready");
		}
		
		super.start();
	}
	
	@Override
	public void run() {
		while(!interrupted()) {
			final RequestData data = mQueue.poll();
			
			if(data == null) {
				synchronized(this) {
					try {
						wait();
					} catch(InterruptedException e) {
						//wait interrupted
					}
					
					continue;
				}
			}
			
			try {
				data.request.perform(mDb);
				if(data.callback != null) {
					postCallbacks(data);
				}
				if(data.isSyncRequest) {
					synchronized(data) {
						data.notify();
					}
				}
			} catch(Exception e) {
				Log.e(TAG, "Exception while performing request", e);
				
				if(data.callback != null) {
					mHandler.post(new Runnable() {
						
						@Override
						public void run() {
							data.callback.sqliteResuestError(data.request);
						}
					});
				} 
			}
			
		} //while(!interrupted())
		
		try {
			mDb.close();
		} catch(Exception e) {
			Log.d(TAG, "Error while closing database", e);
		} finally {
			mDb = null;
			mQueue.clear();
		}
	}
	
	private void postCallbacks(final RequestData data) {
		if(data.request instanceof RequestWithAffectedRows) {
			mHandler.post(new Runnable() {
				
				@Override
				public void run() {
					SqliteUpdateDeleteCallback cb = (SqliteUpdateDeleteCallback) data.callback;
					cb.onSqliteUpdateDeleteSuccess((RequestWithAffectedRows) data.request);
				}
			});
			
		} else if(data.request instanceof Insert) {
			mHandler.post(new Runnable() {
				
				@Override
				public void run() {
					SqliteInsertCallback cb = (SqliteInsertCallback) data.callback;
					cb.onSqliteInsertSuccess((Insert) data.request);
				}
			});
		} else {
			mHandler.post(new Runnable() {
				
				@Override
				public void run() {
					data.callback.onSqliteRequestSuccess(data.request);
				}
			});
		}
	}
	
	/**
	 * Perform synchronous request
	 * @param request
	 */
	public void request(Request request) {
		if(request == null) {
			return;
		}
		
		RequestData data = new RequestData();
		data.request = request;
		data.callback = null;
		data.isSyncRequest = true;
		mQueue.add(data);
		synchronized(data) {
			while(true) {
				try {
					data.wait();
					break;
				} catch(Exception e) {
					//wait interrupted
				}
			}
		}
	}
	
	/**
	 * Perform asynchronous request
	 * @param request
	 * @param callback
	 */
	public synchronized void request(Request request, SqliteRequestCallback callback) {
		if(request == null) {
			return;
		}
		
		RequestData data = new RequestData();
		data.request = request;
		data.callback = callback;
		mQueue.add(data);
		notify();
	}
	

	public static final SqliteRequestThread getInstance() {
		return INSTANCE;
	}
	
	public abstract class SqliteRequestCallback {
		public final void sqliteResuestSuccess(Request request) {
			onSqliteRequestSuccess(request);
		}
		
		public final void sqliteResuestError(Request request) {
			onSqliteRequestError(request);
		}
		
		protected abstract void onSqliteRequestSuccess(Request request);
		protected abstract void onSqliteRequestError(Request request);
	}
	
	public abstract class SqliteInsertCallback extends SqliteRequestCallback {
		public final void sqliteInsertSuccess(Insert request) {
			onSqliteInsertSuccess(request);
			sqliteResuestSuccess(request);
		}
		
		protected abstract void onSqliteInsertSuccess(Insert request);
	}
	
	public abstract class SqliteUpdateDeleteCallback extends SqliteRequestCallback {
		public final void sqliteUpdateDeleteSuccess(RequestWithAffectedRows request) {
			onSqliteUpdateDeleteSuccess(request);
			sqliteResuestSuccess(request);
		}
		
		protected abstract void onSqliteUpdateDeleteSuccess(RequestWithAffectedRows request);
	}
	
	private static class RequestData {
		public Request request;
		public SqliteRequestCallback callback;
		public boolean isSyncRequest = false;
	}
}
