package no.infoss.confprofile.vpn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.util.Log;
import no.infoss.confprofile.util.MiscUtils;

public abstract class AbstractWorker implements Runnable {
	private final String mLogTag;
	
	protected List<String> mArgs;
	protected Process mProcess;
	protected VpnTunnel mTunnel;
	protected Map<String, String> mProcessEnv;
	
	private BufferedReader mReader;

	public AbstractWorker(VpnTunnel tunnel, List<String> args, Map<String, String> processEnv) {
		mArgs = args;
		mTunnel = tunnel;
		mProcessEnv = processEnv;
		if(mProcessEnv == null) {
			mProcessEnv = new HashMap<String, String>();
		}
		
		//Combining stdout and stderr is needed by all of our workers
		mProcessEnv.put("redirectErrorStream", String.valueOf(true));
		
		mLogTag = getClass().getSimpleName();
	}
	
	public void startProcess() throws IOException {
		mProcess = MiscUtils.startProcess(mTunnel.mCtx, mArgs, mProcessEnv);
		mReader = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
	}
	
	public void stopProcess() {
		if(mReader != null) {
			try {
				mReader.close();
			} catch(Exception ex) {
				//nothing to do here
			}
			
			mReader = null;
		}
		
		if(mProcess != null) {
			mProcess.destroy();
			mProcess = null;
		}
	}
	
	@Override
	public void run() {
		try {
			Log.i(mLogTag, "Starting worker");
			startProcess();
			
			mProcess.getOutputStream().close();
			
			while(logStdoutLine()) {
				//doing something here... or not
			}
			
		} catch (Exception e) {
			Log.e(mLogTag, "Exception while performing a worker loop", e);
			stopProcess();
		} finally {
			int retValue = 0;
			
			try {
				if(mProcess != null) {
					retValue = mProcess.waitFor();
				}
			} catch (Exception e) {
				Log.e(mLogTag, "Can't get return value", e);
			}
			
			if(retValue != 0) {
				Log.w(mLogTag, "Process returned " + retValue);
			}

			mTunnel.processDied();
			Log.i(mLogTag, "Worker was stopped");
		}
	}
	
	protected final String getStdoutLine() throws IOException {
		return mReader.readLine();
	}
	
	protected boolean logStdoutLine() throws IOException {
		String line = getStdoutLine();
		
		if(line == null) {
			return false;
		}
		
		mTunnel.mLogger.log(VpnTunnel.LOG_DEBUG, line);
		return true;
	}
}
