package no.infoss.confprofile.vpn;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenVpnWorker extends AbstractWorker {
	public static final String TAG = OpenVpnWorker.class.getSimpleName();
	public static final String MINIVPN = "minivpn";
	
    public static final int M_FATAL = (1 << 4);
    public static final int M_NONFATAL = (1 << 5);
    public static final int M_WARN = (1 << 6);
    public static final int M_DEBUG = (1 << 7);
    
    private Pattern mLogPattern = Pattern.compile("(\\d+).(\\d+) ([0-9a-f])+ (.*)");

	public OpenVpnWorker(OpenVpnTunnel tunnel, List<String> args, Map<String, String> env) {
		super(tunnel, args, env);
	}
	
	@Override
	protected boolean logStdoutLine() throws IOException {
		String line = getStdoutLine();
		
		if(line == null) {
			return false;
		}
		
		Matcher m = mLogPattern.matcher(line);
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
            mTunnel.mLogger.log(logStatus, line);
        } else {
            mTunnel.mLogger.log(VpnTunnel.LOG_INFO, line);
        }
		return true;
	}

}
