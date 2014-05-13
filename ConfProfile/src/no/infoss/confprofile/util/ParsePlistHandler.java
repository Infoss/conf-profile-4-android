package no.infoss.confprofile.util;

import java.lang.ref.WeakReference;
import java.util.LinkedList;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import no.infoss.confprofile.format.Plist;
import no.infoss.confprofile.task.ParsePlistTask;
import no.infoss.confprofile.task.TaskError;
import no.infoss.confprofile.task.ParsePlistTask.ParsePlistTaskListener;

public class ParsePlistHandler implements ParsePlistTaskListener {
	public static final String TAG = ParsePlistHandler.class.getSimpleName();
	
	private boolean mIsRequestComplete;
	private boolean mIsRequestFailed;
	private int mErrorCode;
	private LinkedList<WeakReference<ParsePlistTaskListener>> mSubscribers;
	
	private ParsePlistTask mTask;
	private Uri mUri;
	private Plist mPlist;
	
	public ParsePlistHandler() {
		mSubscribers = new LinkedList<WeakReference<ParsePlistTaskListener>>();
		reset();
	}
	
	public void reset() {
		mErrorCode = TaskError.SUCCESS; 
		mIsRequestComplete = false;
		mIsRequestFailed = false;
		
		synchronized(mSubscribers) {
			mSubscribers.clear();
		}
		
		mUri = null;
		mPlist = null;
	}
	
	public void setTask(ParsePlistTask task) {
		if(mTask == null || !mTask.equals(task)) {
			reset();
			mTask = task;
		}
	}
	
	public Plist getPlist() {
		return mPlist;
	}
	
	public void parseDataByUri(Context ctx, Uri uri, ParsePlistTaskListener listener) {
		if(ctx == null || uri == null) {
			return;
		}
		
		if(uri.equals(mUri)) {
			subscribe(listener);
		} else {
			ParsePlistTask task = new ParsePlistTask(ctx, this);
			setTask(task);
			mUri = uri;
			subscribe(listener);
			task.execute(uri);
		}
	}
	
	public boolean subscribe(ParsePlistTaskListener listener) {
		boolean result = true;
		if(listener == null) {
			result = false;
		} else {
			synchronized(mSubscribers) {
				for(WeakReference<ParsePlistTaskListener> ref : mSubscribers) {
					ParsePlistTaskListener subscriber = ref.get();
					if(listener.equals(subscriber)) {
						result = false;
						break;
					}
				}
				
				if(result) {
					mSubscribers.add(new WeakReference<ParsePlistTaskListener>(listener));
					result = true;
				}
			}
			
			if(result && mIsRequestComplete) {
				if(mIsRequestFailed) {
					listener.onParsePlistFailed(mTask, mErrorCode);
				} else {
					listener.onParsePlistComplete(mTask, mPlist);
				}
			}
		}
		
		return result;
	}
	
	public boolean unsubscribe(ParsePlistTaskListener listener) {
		boolean result = false;
		if(listener != null) {
			synchronized(mSubscribers) {
				WeakReference<ParsePlistTaskListener> refToRemove = null;
				
				for(WeakReference<ParsePlistTaskListener> ref : mSubscribers) {
					ParsePlistTaskListener subscriber = ref.get();
					if(listener.equals(subscriber)) {
						refToRemove = ref;
						break;
					}
				}
				
				if(refToRemove != null) {
					mSubscribers.remove(refToRemove);
					result = true;
				}
			}
		}
		return result;
	}
	
	@Override
	public void onParsePlistFailed(ParsePlistTask task, int taskErrorCode) {
		synchronized(mSubscribers) {
			for(WeakReference<ParsePlistTaskListener> ref : mSubscribers) {
				ParsePlistTaskListener subscriber = ref.get();
				if(subscriber != null) {
					subscriber.onParsePlistFailed(task, taskErrorCode);
				}
			}
			
			mIsRequestComplete = true;
			mIsRequestFailed = true;
		}

		Log.e(TAG, "ParsePlistTask failed with error code " + taskErrorCode + ", http status " + task.getHttpStatusCode());
	}

	@Override
	public void onParsePlistComplete(ParsePlistTask task, Plist plist) {
		mPlist = plist;
		
		synchronized(mSubscribers) {
			for(WeakReference<ParsePlistTaskListener> ref : mSubscribers) {
				ParsePlistTaskListener subscriber = ref.get();
				if(subscriber != null) {
					subscriber.onParsePlistComplete(task, plist);
				}
			}
			
			mIsRequestComplete = true;
			mIsRequestFailed = false;
		}
		
		Log.d(TAG, "ParsePlistTask finished with plist " + plist.toString());
	}

}
