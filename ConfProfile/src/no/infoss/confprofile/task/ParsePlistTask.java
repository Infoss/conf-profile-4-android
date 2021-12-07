package no.infoss.confprofile.task;

import java.io.InputStream;
import java.lang.ref.WeakReference;

import no.infoss.confprofile.R;
import no.infoss.confprofile.format.Plist;
import no.infoss.confprofile.util.HttpUtils;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.bouncycastle.cms.CMSSignedData;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

public class ParsePlistTask extends AsyncTask<Uri, Void, Integer> {
	public static final String TAG = ParsePlistTask.class.getSimpleName();
	
	private Context mCtx;
	private WeakReference<ParsePlistTaskListener> mListener;
	private Plist mPlist;
	private int mHttpStatusCode = HttpStatus.SC_OK;
	
	public ParsePlistTask(Context ctx, ParsePlistTaskListener listener) {
		mCtx = ctx;
		mListener = new WeakReference<ParsePlistTaskListener>(listener);
		mPlist = null;
	}
	
	public int getHttpStatusCode() {
		return mHttpStatusCode;
	}
	
	@Override
	protected Integer doInBackground(Uri... params) {
		try {
			Uri uri = params[0];
			String userAgent = mCtx.getString(R.string.idevice_ua);
			InputStream stream = null;
			String scheme = uri.getScheme();
			
			if(ContentResolver.SCHEME_CONTENT.equals(scheme) || 
					ContentResolver.SCHEME_FILE.equals(scheme)) {
				stream = mCtx.getContentResolver().openInputStream(uri);
			} else {
				HttpClient client = null;
				
				if("http".equals(scheme)) {
					client = HttpUtils.getHttpClient();
				} else if("https".equals(scheme)) {
					client = HttpUtils.getHttpsClient();
				}
				
				HttpGet getRequest = new HttpGet(uri.toString());
				getRequest.setHeader("User-Agent", userAgent);
				getRequest.setHeader("Connection", "close");
				
				HttpResponse resp = client.execute(getRequest);
				mHttpStatusCode = resp.getStatusLine().getStatusCode();
				if(mHttpStatusCode != HttpStatus.SC_OK) {
					return TaskError.HTTP_FAILED;
				}
				
				stream = resp.getEntity().getContent();
			} 
			
			CMSSignedData data = new CMSSignedData(stream);
			mPlist = new Plist(data);
		} catch(Exception e) {
			Log.e(TAG, "Generic error while processing ParsePlistTask", e);
			return TaskError.INTERNAL;
		}
		
		return TaskError.SUCCESS;
	}
	
	@Override
	protected void onPostExecute(Integer result) {
		ParsePlistTaskListener listener = mListener.get();
		if(listener != null) {
			if(result == TaskError.SUCCESS) {
				listener.onParsePlistComplete(this, mPlist);
			} else {
				listener.onParsePlistFailed(this, result);
			}
		}
		
		mPlist = null;
		mCtx = null;
	}

	public interface ParsePlistTaskListener {
		public void onParsePlistFailed(ParsePlistTask task, int taskErrorCode);
		public void onParsePlistComplete(ParsePlistTask task, Plist plist);
	}
}
