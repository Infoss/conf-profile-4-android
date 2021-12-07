package no.infoss.confprofile.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import no.infoss.confprofile.BuildConfig;
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
	public static final int INVALID_ID = -1;
	private static final SqliteRequestThread INSTANCE = new SqliteRequestThread();
	
	private final AtomicInteger mCounter = new AtomicInteger();
	private final AtomicInteger mCurrentRequestId = new AtomicInteger();
	private final ConcurrentLinkedQueue<RequestData> mQueue;
	private final SortedSet<WeakListener> mListeners;
	private final Handler mHandler = new Handler(Looper.getMainLooper());
	private SQLiteDatabase mDb = null;
	
	private SqliteRequestThread() {
		super(SqliteRequestThread.class.getSimpleName());
		mQueue = new ConcurrentLinkedQueue<RequestData>();
		mListeners = Collections.synchronizedSortedSet(new TreeSet<WeakListener>(new WeakListenerComparator()));
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
			int currRequestId = mCurrentRequestId.get();
			List<WeakListener> deleteItems = new ArrayList<WeakListener>(10);
			for(WeakListener listener : mListeners) {
				if(listener.mRequestId > currRequestId) {
					break;
				}
				
				Log.d(TAG, "run(): post listener callback for request " + listener.mRequestId);
				postListenerCallback(listener.get(), listener.mRequestId);
				deleteItems.add(listener);
			}
			mListeners.removeAll(deleteItems);
			deleteItems.clear();
			
			final RequestData data = mQueue.poll();
			
			if(data == null) {
				synchronized(this) {
					try {
						wait(1000);
					} catch(InterruptedException e) {
						//wait interrupted
					}
					
					continue;
				}
			}
			
			try {
				Log.d(TAG, "run(): performing id " + data.requestId);
				mCurrentRequestId.set(data.requestId);
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
							data.callback.sqliteRequestError(data.request);
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
	
	private void postListenerCallback(final SqliteRequestStatusListener listener, final int requestId) {
		if(listener != null && requestId > 0) {
			mHandler.post(new Runnable() {
				
				@Override
				public void run() {
					listener.onSqliteRequestExecuted(listener, requestId);
				}
			});
		}
	}
	
	/**
	 * Perform synchronous request
	 * @param request
	 * @return the same object for chaining
	 */
	public Request request(Request request) {
		if(request == null) {
			return request;
		}
		
		RequestData data = new RequestData(mCounter.incrementAndGet());
		data.request = request;
		data.callback = null;
		data.isSyncRequest = true;
		mQueue.add(data);
		synchronized(data) {
			while(true) {
				try {
					synchronized(this) {
						notify();
					}
					data.wait();
					break;
				} catch(Exception e) {
					//wait interrupted
				}
			}
		}
		
		return request;
	}
	
	/**
	 * Perform asynchronous request
	 * @param request
	 * @param callback
	 * @return
	 */
	public synchronized int request(Request request, SqliteRequestCallback callback) {
		if(request == null) {
			return INVALID_ID;
		}
		
		int id = mCounter.incrementAndGet();
		RequestData data = new RequestData(id);
		data.request = request;
		data.callback = callback;
		mQueue.add(data);
		Log.d(TAG, "request(): added request " + id);
		notify();
		
		return id;
	}
	
	public boolean subscribe(SqliteRequestStatusListener listener, int requestId) {
		if(requestId > mCounter.get() || requestId <= 0) {
			Log.d(TAG, "subscribe(): ignore request " + requestId + "(counter: " + mCounter.get() + ")");
			return false;
		}
		
		if(requestId < mCurrentRequestId.get()) {
			Log.d(TAG, "subscribe(): post listener callback for request " + requestId);
			postListenerCallback(listener, requestId);
			return true;
		}
		
		WeakListener weakListener = new WeakListener(listener, requestId);
		mListeners.add(weakListener);
		Log.d(TAG, "subscribe(): wait for request " + requestId);
		return true;
	}
	
	public void unsubscribe(SqliteRequestStatusListener listener, int requestId) {
		if(requestId > mCounter.get() || requestId <= 0) {
			return;
		}
		
		Log.d(TAG, "unsubscribe(): forget about request " + requestId);
		
		WeakListener listenerToDelete = null;
		for(WeakListener weakListener : mListeners) {
			if(weakListener.mRequestId > requestId) {
				break;
			}
			
			if(weakListener.mRequestId == requestId) {
				if(listener.equals(weakListener.get())) {
					listenerToDelete = weakListener;
					break;
				}
			}
		}
		
		if(listenerToDelete != null) {
			mListeners.remove(listenerToDelete);
		}
	}
	

	public static final SqliteRequestThread getInstance() {
		return INSTANCE;
	}
	
	public interface SqliteRequestStatusListener {
		public void onSqliteRequestExecuted(SqliteRequestStatusListener listener, int requestId);
	}
	
	public static abstract class SqliteRequestCallback {
		public final void sqliteRequestSuccess(Request request) {
			onSqliteRequestSuccess(request);
		}
		
		public final void sqliteRequestError(Request request) {
			onSqliteRequestError(request);
		}
		
		protected abstract void onSqliteRequestSuccess(Request request);
		protected abstract void onSqliteRequestError(Request request);
	}
	
	public static abstract class SqliteInsertCallback extends SqliteRequestCallback {
		public final void sqliteInsertSuccess(Insert request) {
			onSqliteInsertSuccess(request);
			sqliteRequestSuccess(request);
		}
		
		protected abstract void onSqliteInsertSuccess(Insert request);
	}
	
	public static abstract class SqliteUpdateDeleteCallback extends SqliteRequestCallback {
		public final void sqliteUpdateDeleteSuccess(RequestWithAffectedRows request) {
			onSqliteUpdateDeleteSuccess(request);
			sqliteRequestSuccess(request);
		}
		
		protected abstract void onSqliteUpdateDeleteSuccess(RequestWithAffectedRows request);
	}
	
	private static class RequestData {
		public int requestId;
		public Request request;
		public SqliteRequestCallback callback;
		public boolean isSyncRequest = false;
		
		public RequestData(int id) {
			requestId = id;
		}
	}
	
	private static class WeakListener extends WeakReference<SqliteRequestStatusListener> {
		public static final ReferenceQueue<? super SqliteRequestStatusListener> QUEUE = new ReferenceQueue<SqliteRequestStatusListener>();
		private int mRequestId;

		public WeakListener(SqliteRequestStatusListener r, int requestId) {
			super(r, QUEUE);
			mRequestId = requestId;
		}
		
		@Override
		public boolean equals(Object object) {
			boolean eq = super.equals(object);
			if(eq) {
				eq = (mRequestId == ((WeakListener) object).mRequestId);
			}
			
			return eq;
		}
		
	}
	
	private static class WeakListenerComparator implements Comparator<WeakListener> {

		@Override
		public int compare(WeakListener lhs, WeakListener rhs) {
			if(lhs == rhs) {
				return 0;
			}
			
			if(lhs == null) {
				return -1;
			}
			
			if(rhs == null) {
				return 1;
			}
			
			return lhs.mRequestId - rhs.mRequestId;
		}
		
	}
}
