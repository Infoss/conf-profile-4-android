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

public class L2tpWorker implements Runnable {
	public static final String TAG = L2tpWorker.class.getSimpleName();
	public static final String MINIVPN = "ocpamtpd";
	
    private String[] mArgv;
	private Process mProcess;
	private L2tpTunnel mTunnel;
	private Map<String, String> mProcessEnv;

	public L2tpWorker(L2tpTunnel tunnel, String[] argv, Map<String, String> processEnv) {
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
			Log.i(TAG, "Starting l2tp");
			startOpenVPNThreadArgs(mArgv, mProcessEnv);
			Log.i(TAG, "Giving up");
		} catch (Exception e) {
			Log.e(TAG, "Error while starting L2TP thread", e);
		} finally {
			int exitvalue = 0;
			try {
				if (mProcess != null) {
					exitvalue = mProcess.waitFor();
				}
			} catch ( IllegalThreadStateException e) {
				Log.d(TAG, "Illegal thread state", e);
			} catch (InterruptedException e) {
				Log.d(TAG, "Thread was interrupted", e);
			}
			
			if(exitvalue != 0) {
				Log.e(TAG, "Process exited with exit value " + exitvalue);
			}

			mTunnel.processDied();
			Log.i(TAG, "Exiting");
		}
	}

	private void startOpenVPNThreadArgs(String[] argv, Map<String, String> env) {
		LinkedList<String> argvlist = new LinkedList<String>();

        Collections.addAll(argvlist, argv);

		ProcessBuilder pb = new ProcessBuilder(argvlist);
		pb.environment().put("LD_LIBRARY_PATH", MiscUtils.genLibraryPath(mTunnel.mCtx, pb));

		// Add extra variables
		for(Entry<String,String> e:env.entrySet()){
			pb.environment().put(e.getKey(), e.getValue());
		}
		pb.redirectErrorStream(true);
		try {
			mProcess = pb.start();

			mProcess.getOutputStream().close();
			InputStream in = mProcess.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			while(true) {
				String logline = br.readLine();
				if(logline == null) {
					return;
				}

				Log.w(TAG, logline);
			}
			
		} catch (IOException e) {
			Log.e(TAG, "Error reading from output of OpenVPN process" , e);
			stopProcess();
		}

	}
	
}
