package no.infoss.confprofile.vpn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import no.infoss.confprofile.util.MiscUtils;
import android.util.Log;

public class UsernatWorker implements Runnable {
	public static final String TAG = UsernatWorker.class.getSimpleName();
	public static final String USERNAT = "usernat";
	public static final String SOCAT = "ocpasocat";
    
    private String[] mArgv;
	private Process mProcess;
	private UsernatTunnel mTunnel;
	private Map<String, String> mProcessEnv;

	public UsernatWorker(UsernatTunnel tunnel, String[] argv, Map<String, String> processEnv) {
		mArgv = argv;
		mTunnel = tunnel;
		mProcessEnv = processEnv;
	}

	public void stopProcess() {
		mProcess.destroy();
	}

	@Override
	public void run() {
		try {
			Log.i(TAG, "Starting usernat");
			startUsernatWorkerArgs(mArgv, mProcessEnv);
			Log.i(TAG, "Giving up");
		} catch (Exception e) {
			Log.e(TAG, "UsernatWorker got " + e.toString(), e);
		} finally {
			int exitvalue = 0;
			
			try {
				if(mProcess != null) {
					exitvalue = mProcess.waitFor();
				}
			} catch (IllegalThreadStateException e) {
				Log.e(TAG, "Illegal thread state", e);
			} catch (InterruptedException e) {
				Log.e(TAG, "Thread interrupted", e);
			}
			
			if(exitvalue != 0) {
				Log.w(TAG, "Process exited with exit value " + exitvalue);
			}

			mTunnel.processDied();
			Log.i(TAG, "Exiting");
		}
	}

	private void startUsernatWorkerArgs(String[] argv, Map<String, String> env) {
		LinkedList<String> argvlist = new LinkedList<String>();

        Collections.addAll(argvlist, argv);

		ProcessBuilder pb = new ProcessBuilder(argvlist);
		// Hack O rama
		pb.environment().put("LD_LIBRARY_PATH", MiscUtils.genLibraryPath(mTunnel.mCtx, pb));

		// Add extra variables
		for(Entry<String,String> e:env.entrySet()){
			pb.environment().put(e.getKey(), e.getValue());
		}
		pb.redirectErrorStream(true);
		try {
			mProcess = pb.start();
			// Close the output, since we don't need it
			mProcess.getOutputStream().close();
			InputStream in = mProcess.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			while(true) {
				String logline = br.readLine();
				if(logline == null) {
					in = mProcess.getErrorStream();
					br = new BufferedReader(new InputStreamReader(in));
					logline = br.readLine();
					
					//TODO: fix this weird debugging code
					while(logline != null) {
						Log.d(TAG, logline);
					}
					return;
				}
				mTunnel.mLogger.log(VpnTunnel.LOG_DEBUG, logline);
			}
		} catch (IOException e) {
			Log.e(TAG, "Error reading from output of usernat process" , e);
			stopProcess();
		}

	}
}