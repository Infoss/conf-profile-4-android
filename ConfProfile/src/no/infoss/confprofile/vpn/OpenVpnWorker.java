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

import no.infoss.confprofile.vpn.OpenVpnTunnel.VpnStatus;
import android.util.Log;

public class OpenVpnWorker implements Runnable {
	public static final String TAG = OpenVpnWorker.class.getSimpleName();
	public static final String MINIVPN = "minivpn"; //was: miniopenvpn
	
    public static final int M_FATAL = (1 << 4);
    public static final int M_NONFATAL = (1 << 5);
    public static final int M_WARN = (1 << 6);
    public static final int M_DEBUG = (1 << 7);
    private String[] mArgv;
	private Process mProcess;
	private String mNativeDir;
	private OpenVpnTunnel mTunnel;
	private Map<String, String> mProcessEnv;

	public OpenVpnWorker(OpenVpnTunnel tunnel, String[] argv, Map<String, String> processEnv, String nativelibdir) {
		mArgv = argv;
		mNativeDir = nativelibdir;
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
			startOpenVPNThreadArgs(mArgv, mProcessEnv);
			Log.i(TAG, "Giving up");
		} catch (Exception e) {
            VpnStatus.logException("Starting OpenVPN Thread" ,e);
			Log.e(TAG, "OpenVPNThread Got " + e.toString(), e);
		} finally {
			int exitvalue = 0;
			try {
				if (mProcess!=null)
					exitvalue = mProcess.waitFor();
			} catch ( IllegalThreadStateException ite) {
				VpnStatus.logError("Illegal Thread state: " + ite.getLocalizedMessage());
			} catch (InterruptedException ie) {
				VpnStatus.logError("InterruptedException: " + ie.getLocalizedMessage());
			}
			if( exitvalue != 0)
				VpnStatus.logError("Process exited with exit value " + exitvalue);

			VpnStatus.updateStateString("NOPROCESS", "No process running.", "R.string.state_noprocess", "ConnectionStatus.LEVEL_NOTCONNECTED");

			mTunnel.processDied();
			Log.i(TAG, "Exiting");
		}
	}

	private void startOpenVPNThreadArgs(String[] argv, Map<String, String> env) {
		LinkedList<String> argvlist = new LinkedList<String>();

        Collections.addAll(argvlist, argv);

		ProcessBuilder pb = new ProcessBuilder(argvlist);
		// Hack O rama

		String lbpath = genLibraryPath(argv, pb);

		pb.environment().put("LD_LIBRARY_PATH", lbpath);

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
				if(logline==null)
					return;

                // 1380308330.240114 18000002 Send to HTTP proxy: 'X-Online-Host: bla.blabla.com'

                Pattern p = Pattern.compile("(\\d+).(\\d+) ([0-9a-f])+ (.*)");
                Matcher m = p.matcher(logline);
                if(m.matches()) {
                    int flags = Integer.parseInt(m.group(3),16);
                    String msg = m.group(4);
                    int logLevel = flags & 0x0F;

                    VpnStatus.LogLevel logStatus = VpnStatus.LogLevel.INFO;

                    if ((flags & M_FATAL) != 0)
                        logStatus = VpnStatus.LogLevel.ERROR;
                    else if ((flags & M_NONFATAL)!=0)
                        logStatus = VpnStatus.LogLevel.WARNING;
                    else if ((flags & M_WARN)!=0)
                        logStatus = VpnStatus.LogLevel.WARNING;
                    else if ((flags & M_DEBUG)!=0)
                        logStatus = VpnStatus.LogLevel.VERBOSE;

                    if (msg.startsWith("MANAGEMENT: CMD"))
                        logLevel = Math.max(4, logLevel);


                    VpnStatus.logMessageOpenVPN(logStatus,logLevel,msg);
                } else {
                    VpnStatus.logInfo("P:" + logline);
                }
			}


		} catch (IOException e) {
			VpnStatus.logException("Error reading from output of OpenVPN process" , e);
			stopProcess();
		}


	}

	private String genLibraryPath(String[] argv, ProcessBuilder pb) {
		// Hack until I find a good way to get the real library path
        System.out.println(argv[0]);
        String applibpath = argv[0].replace("/cache/" + MINIVPN , "/lib");

		String lbpath = pb.environment().get("LD_LIBRARY_PATH");


		if(lbpath==null)
			lbpath = applibpath;
		else
			lbpath = lbpath + ":" + applibpath;

		if (!applibpath.equals(mNativeDir)) {
			lbpath = lbpath + ":" + mNativeDir;
		}


		return lbpath;
	}
}