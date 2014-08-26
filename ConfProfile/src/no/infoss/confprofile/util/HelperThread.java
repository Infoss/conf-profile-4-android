package no.infoss.confprofile.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public abstract class HelperThread<R, 
		P extends HelperThread.Performer<R>, 
		CB extends HelperThread.Callback<R, P>> extends Thread {
	public static final String TAG = SqliteRequestThread.class.getSimpleName();
	
	private static final int REQUEST_DATA_POOL_SIZE = 5;
	
	private final ConcurrentLinkedQueue<RequestData> mQueue;
	private final ArrayBlockingQueue<RequestData> mRequestDataPool;
	private final Handler mHandler = new Handler(Looper.getMainLooper());
	
	protected HelperThread() {
		super(HelperThread.class.getSimpleName());
		mQueue = new ConcurrentLinkedQueue<RequestData>();
		mRequestDataPool = new ArrayBlockingQueue<RequestData>(REQUEST_DATA_POOL_SIZE);
		for(int i = 0; i < REQUEST_DATA_POOL_SIZE; i++) {
			mRequestDataPool.add(new RequestData());
		}
	}
	
	@Override
	public synchronized void start() {
		checkConditionsToRun();
		
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
				boolean result = data.performer.perform(data.request);
				if(!data.isSyncRequest) {
					if(result) {
						postCallbacks(data, false);
					} else {
						postCallbacks(data, true);
					}
				}
			} catch(Exception e) {
				Log.e(TAG, "Exception while performing request", e);
				if(!data.isSyncRequest) {
					postCallbacks(data, true);
				}
			}
			
		} //while(!interrupted())
		
		freeResources();
	}
	
	private void postCallbacks(RequestData data, boolean isError) {
		final R request = data.request;
		final P performer = data.performer;
		final CB callback = data.callback;
		
		clearRequestData(data);
		returnRequestDataToPool(data);
		data = null;
		
		if(!isError) {
			mHandler.post(new Runnable() {
				
				@Override
				public void run() {
					try {
						callback.onSuccess(request, performer);
					} catch(Exception e) {
						Log.e(TAG, "Error while calling onSuccess() callback", e); 
					}
				}
			});
			
		} else {
			mHandler.post(new Runnable() {
				
				@Override
				public void run() {
					try {
						callback.onError(request, performer);
					} catch(Exception e) {
						Log.e(TAG, "Error while calling onError() callback", e);
					}
				}
			});
			
		}
	}
	
	/**
	 * Perform synchronous request
	 * @param request
	 * @return the same object for chaining
	 */
	public R request(R request, P performer) {
		if(request == null) {
			return request;
		}
		
		RequestData data;
		data = mRequestDataPool.poll();
		if(data == null) {
			data = new RequestData();
		}
		
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
					
					clearRequestData(data);
					returnRequestDataToPool(data);
					
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
	 */
	public synchronized void request(R request, P performer, CB callback) {
		if(request == null) {
			return;
		}
		
		RequestData data = new RequestData();
		data.request = request;
		data.performer = performer;
		data.callback = callback;
		data.isSyncRequest = false;
		mQueue.add(data);
		notify();
	}
	
	protected abstract void checkConditionsToRun() throws IllegalStateException;
	protected abstract void freeResources();
	
	private void clearRequestData(RequestData data) {
		data.request = null;
		data.performer = null;
		data.callback = null;
		data.isSyncRequest = false;
	}
	
	private void returnRequestDataToPool(RequestData data) {
		try {
			mRequestDataPool.add(data);
		} catch(Exception e) {
			//suppress this exception
		}
	}
	
	public interface Performer<R> {
		public boolean perform(R request);
	}
	
	public interface Callback<R, P> {
		public void onSuccess(R request, P performer);
		public void onError(R request, P performer);
	}
	
	private class RequestData {
		public R request;
		public P performer;
		public CB callback;
		public boolean isSyncRequest = false;
	}
}
