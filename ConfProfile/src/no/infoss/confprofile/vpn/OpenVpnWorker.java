package no.infoss.confprofile.vpn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import no.infoss.confprofile.util.MiscUtils;
import android.util.Log;

public class OpenVpnWorker implements Runnable {
	public static final String TAG = OpenVpnWorker.class.getSimpleName();
	public static final String MINIVPN = "minivpn";
	
    public static final int M_FATAL = (1 << 4);
    public static final int M_NONFATAL = (1 << 5);
    public static final int M_WARN = (1 << 6);
    public static final int M_DEBUG = (1 << 7);
    
    private String[] mArgv;
	private Process mProcess;
	private OpenVpnTunnel mTunnel;
	private Map<String, String> mProcessEnv;

	public OpenVpnWorker(OpenVpnTunnel tunnel, String[] argv, Map<String, String> processEnv) {
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
			Log.i(TAG, "Starting openvpn");
			startOpenVpnWorkerArgs(mArgv, mProcessEnv);
			Log.i(TAG, "Giving up");
		} catch (Exception e) {
			Log.e(TAG, "OpenVpnWorker got " + e.toString(), e);
		} finally {
			int exitvalue = 0;
			
			try {
				if(mProcess!=null) {
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

	private void startOpenVpnWorkerArgs(String[] argv, Map<String, String> env) {
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
					return;
				}

                // 1380308330.240114 18000002 Send to HTTP proxy: 'X-Online-Host: bla.blabla.com'

                Pattern p = Pattern.compile("(\\d+).(\\d+) ([0-9a-f])+ (.*)");
                Matcher m = p.matcher(logline);
                if(m.matches()) {
                    int flags = Integer.parseInt(m.group(3),16);
                    String msg = m.group(4);
                    int logLevel = flags & 0x0F;

                    int logStatus = VpnTunnel.LOG_INFO;

                    if((flags & M_FATAL) != 0) {
                        logStatus = VpnTunnel.LOG_ERROR;
                    } else if((flags & M_NONFATAL) != 0) {
                        logStatus = VpnTunnel.LOG_WARN;
                    } else if((flags & M_WARN) != 0) {
                        logStatus = VpnTunnel.LOG_WARN;  
                    } else if((flags & M_DEBUG) != 0) {
                        logStatus = VpnTunnel.LOG_DEBUG;
                    }
                    
                    if(msg.startsWith("MANAGEMENT: CMD")) {
                        logLevel = Math.max(4, logLevel);
                    }

                    //TODO: print OpenVPN log level if needed 
                    mTunnel.mLogger.log(logStatus, logline);
                } else {
                    mTunnel.mLogger.log(VpnTunnel.LOG_INFO, logline);
                }
			}
		} catch (IOException e) {
			Log.e(TAG, "Error reading from output of OpenVPN process" , e);
			stopProcess();
		}

	}
}