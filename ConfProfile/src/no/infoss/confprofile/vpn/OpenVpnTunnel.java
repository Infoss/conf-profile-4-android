package no.infoss.confprofile.vpn;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import no.infoss.confprofile.BuildConfig;
import no.infoss.confprofile.util.MiscUtils;
import no.infoss.confprofile.util.NetUtils;
import no.infoss.confprofile.util.StringUtils;
import no.infoss.confprofile.vpn.VpnManagerService.VpnConfigInfo;

import org.bouncycastle.openssl.PEMWriter;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class OpenVpnTunnel extends VpnTunnel {
	public static final String TAG = OpenVpnTunnel.class.getSimpleName();
	
	public static final String VPN_TYPE = "net.openvpn.OpenVPN-Connect.vpnplugin";
	
	private Map<String, Object> mOptions;
	private Thread mWorkerThread;
	private OpenVpnWorker mWorker;
	
	
	private LocalSocket mSocket;
	private LinkedList<FileDescriptor> mFDList = new LinkedList<FileDescriptor>();
    private LocalServerSocket mServerSocket;
	private boolean mReleaseHold = true;
	private boolean mWaitingForRelease = false;
	private long mLastHoldRelease = 0;

    private LocalSocket mServerSocketLocal;

    enum PauseReason {
        noNetwork,
        userPause,
        screenOff
    }

	int mBytecountInterval = 2;
	long mBytesIn;
	long mBytesOut;
    
    private PauseReason mLastPauseReason = PauseReason.noNetwork;
	
	
	/*package*/ OpenVpnTunnel(Context ctx, long vpnServiceCtx, VpnManagerInterface vpnMgr, VpnConfigInfo cfg) {
		super(ctx, cfg, vpnMgr);
		mVpnServiceCtx = vpnServiceCtx;
		
		boolean managemeNetworkState = true;
		if(managemeNetworkState) {
			mReleaseHold = false;
		}
	}

	@Override
	protected String getThreadName() {
		return VPN_TYPE;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void establishConnection(Map<String, Object> options) {
		if(getConnectionStatus() != ConnectionStatus.DISCONNECTED) {
			return;
		}
		
		mOptions = (Map<String, Object>) options.get(VpnConfigInfo.PARAMS_CUSTOM);
		mVpnTunnelCtx = initOpenVpnTun();
		
		startLoop();
	}
	
	public void terminateConnection() {
		if(!isTerminated()) {
			setConnectionStatus(ConnectionStatus.TERMINATED);
			managmentCommand("signal SIGINT\n");
			deinitOpenVpnTun(mVpnTunnelCtx);
			mVpnTunnelCtx = 0;
		}
	}

	@Override
	public void run() {
		if(MiscUtils.writeExecutableToCache(mCtx, OpenVpnWorker.MINIVPN) == null) {
			Log.e(TAG, "Error writing minivpn");
			terminateConnection();
			return;
		}
		
		mBytesIn = 0;
		mBytesOut = 0;
		
		byte[] buffer = new byte[2048];
		String pendingInput = "";
		
		String confFileName = mCfg.configId.concat(".ovpn");
		File cacheDir = mCtx.getCacheDir();
		File confFile = new File(cacheDir, confFileName);
		
		List<String> args = new ArrayList<String>(3);
        args.add(cacheDir.getAbsolutePath() + "/" + OpenVpnWorker.MINIVPN);
        args.add("--config");
        args.add(confFile.getAbsolutePath());

        if(!MiscUtils.writeStringToFile(confFile, buildConfig())) {
        	Log.e(TAG, "Terminating connection");
        	terminateConnection();
			return;
        }
		
		// Could take a while to open connection
        int tries=8;

        String socketName = (new File(mCtx.getCacheDir(), "mgmtsocket")).getAbsolutePath();
        // The mServerSocketLocal is transferred to the LocalServerSocket, ignore warning

        mServerSocketLocal = new LocalSocket();

        while(tries > 0) {
        	if(isTerminated()) {
        		return;
        	}
        	
            try {
                mServerSocketLocal.bind(new LocalSocketAddress(socketName,
                        LocalSocketAddress.Namespace.FILESYSTEM));
                Log.d(TAG, "unix socket bound at " + socketName);
                break;
            } catch (IOException e) {
                // wait 300 ms before retrying
            	Log.w(TAG, e);
                try {
                	Thread.sleep(300);
                } catch (InterruptedException e1) {
                }
            }
            tries--;
        }
        
        try {
            mServerSocket = new LocalServerSocket(mServerSocketLocal.getFileDescriptor());
            Log.d(TAG, "Management interface opened");
        } catch (IOException e) {
        	mLogger.logException(LOG_ERROR, "Error opening management interface", e);
        }
        
        setConnectionStatus(ConnectionStatus.CONNECTING);
        
        mWorker = new OpenVpnWorker(this, args, null);
		mWorkerThread = new Thread(mWorker, "OpenVpn worker");
		mWorkerThread.start();

		try {
			mSocket = mServerSocket.accept();
			InputStream instream = mSocket.getInputStream();
            mServerSocket.close();
            resume();

			while(true) {
				int numbytesread = instream.read(buffer);
				if(numbytesread == -1) {
					return;
				}

				FileDescriptor[] fds = null;
				try {
					fds = mSocket.getAncillaryFileDescriptors();
				} catch (IOException e) {
					mLogger.logException(LOG_INFO, "Error reading fds from socket", e);
				}
				if(fds != null){
                    Collections.addAll(mFDList, fds);
				}

				String input = new String(buffer, 0, numbytesread,"UTF-8");

				pendingInput += input;

				pendingInput = processInput(pendingInput);
			}
		} catch (IOException e) {
            if(!e.getMessage().equals("socket closed")) {
            	mLogger.logException(LOG_INFO, "Exception on the tunnel loop", e);
            }
		}
		
		terminateConnection();
	}

	public void managmentCommand(String cmd) {
        try {
		    if(mSocket!=null && mSocket.getOutputStream() !=null) {
				mSocket.getOutputStream().write(cmd.getBytes());
				mSocket.getOutputStream().flush();
			}
        }catch (IOException e) {
				// Ignore socket stack traces
        }
	}


	//! Hack O Rama 2000!
	private void protectFileDescriptor(FileDescriptor fd) {
		try {
			Method getInt =  FileDescriptor.class.getDeclaredMethod("getInt$");
			int fdint = (Integer) getInt.invoke(fd);

			boolean result = mVpnMgr.protect(fdint);
            if (!result) {
                Log.e(TAG, "Can't protect VPN socket");
            }

            ParcelFileDescriptor.adoptFd(fdint).close();
		} catch (Exception e) {
			Log.e(TAG, "Error while protecting fd=" + fd, e);
		}
	}

	private String processInput(String pendingInput) {
		while(pendingInput.contains("\n")) {
			String[] tokens = pendingInput.split("\\r?\\n", 2);
			processCommand(tokens[0]);
			if(tokens.length == 1) {
				// No second part, newline was at the end
				pendingInput = "";
			} else {
				pendingInput = tokens[1];
			}
		}
		return pendingInput;
	}


	private void processCommand(String command) {
		if(command == null) {
			return;
		}
		
        if(command.startsWith(">") && command.contains(":")) {
			String[] parts = command.split(":",2);
			String cmd = parts[0].substring(1);
			String argument = parts[1];

			if("INFO".equals(cmd)) {
				/* Ignore greeting from management */
                return;
			}else if("PASSWORD".equals(cmd)) {
				processPWCommand(argument);
			} else if("HOLD".equals(cmd)) {
				handleHold();
			} else if("NEED-OK".equals(cmd)) {
				processNeedCommand(argument);
			} else if("BYTECOUNT".equals(cmd)){
				processByteCount(argument);
			} else if("STATE".equals(cmd)) {
				processState(argument);
			} else if("PROXY".equals(cmd)) {
				processProxyCMD(argument);
			} else if("LOG".equals(cmd)) {
                 processLogMessage(argument);
			} else if("RSA_SIGN".equals(cmd)) {
				processSignCommand(argument);
			} else {
				Log.i(TAG, "Got unrecognized line from managment " + String.valueOf(command));
			}
		} else if (command.startsWith("SUCCESS:")) {
			/* Ignore this kind of message too */
            return;
        } else if (command.startsWith("PROTECTFD: ")) {
            FileDescriptor fdtoprotect = mFDList.pollFirst();
            if (fdtoprotect!=null) {
                protectFileDescriptor(fdtoprotect);
            }
		} else {
			Log.i(TAG, "Got unrecognized line from managment " + String.valueOf(command));
		}
	}

    private void processLogMessage(String argument) {
        String[] args = argument.split(",",4);
        // 0 unix time stamp
        // 1 log level N,I,E etc.
        /*
         (b) zero or more message flags in a single string:
          I -- informational
          F -- fatal error
          N -- non-fatal error
          W -- warning
          D -- debug, and
        */
        // 2 log message

        int level;
        if("I".equals(args[1])) {
            level = LOG_INFO;
        } else if("W".equals(args[1])) {
            level = LOG_WARN;
        } else if("D".equals(args[1])) {
            level = LOG_VERBOSE;
        } else if("F".equals(args[1])) {
            level = LOG_ERROR;
        } else {
            level = LOG_INFO;
        }

        int ovpnlevel = Integer.parseInt(args[2]) & 0x0F;
        String msg = args[3];

        if (msg.startsWith("MANAGEMENT: CMD"))
            ovpnlevel = Math.max(4, ovpnlevel);

        mLogger.log(level, msg);
    }

    private void handleHold() {
		if(mReleaseHold) {
			releaseHoldCmd();
		} else { 
			mWaitingForRelease = true;
            //VpnStatus.updateStatePause(mLastPauseReason);
		}
	}
    
	private void releaseHoldCmd() {
		if ((System.currentTimeMillis()- mLastHoldRelease) < 5000) {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
            }
			
		}
		mWaitingForRelease=false;
		mLastHoldRelease  = System.currentTimeMillis();
		managmentCommand("hold release\n");
		managmentCommand("bytecount " + mBytecountInterval + "\n");
        managmentCommand("state on\n");
        //managmentCommand("log on all\n");
	}
	
	public void releaseHold() {
		mReleaseHold=true;
		if(mWaitingForRelease)
			releaseHoldCmd();
			
	}

	private void processProxyCMD(String argument) {
		String[] args = argument.split(",",3);
		//TODO: what to do with proxy detection?
		SocketAddress proxyaddr = null;
		//SocketAddress proxyaddr = ProxyDetection.detectProxy(mProfile);

		
		if(args.length >= 2) {
			String proto = args[1];
			if(proto.equals("UDP")) {
				proxyaddr = null;
			}
		}

		if(proxyaddr instanceof InetSocketAddress ){
			InetSocketAddress isa = (InetSocketAddress) proxyaddr;
			
			mLogger.log(LOG_INFO, "Using proxy " + isa.getHostName() + ":" + isa.getPort());
			
			String proxycmd = String.format(Locale.ENGLISH,"proxy HTTP %s %d\n", isa.getHostName(),isa.getPort());
			managmentCommand(proxycmd);
		} else {
			managmentCommand("proxy NONE\n");
		}

	}
	private void processState(String argument) {
		String[] args = argument.split(",",3);
		String currentstate = args[1];

		mLogger.log(LOG_INFO, "State changed to " + currentstate + " (" + args[2] + ")");
		if("CONNECTED".equalsIgnoreCase(currentstate)) {
			setConnectionStatus(ConnectionStatus.CONNECTED);
		} else if("RECONNECTING".equalsIgnoreCase(currentstate)) {
			setConnectionStatus(ConnectionStatus.CONNECTING);
		} else if("EXITING".equalsIgnoreCase(currentstate)) {
			setConnectionStatus(ConnectionStatus.DISCONNECTING);
		} else {
			setConnectionStatus(ConnectionStatus.CONNECTING);
		}
	}

	private void processByteCount(String argument) {
		//   >BYTECOUNT:{BYTES_IN},{BYTES_OUT}
		int comma = argument.indexOf(',');
		long in = Long.parseLong(argument.substring(0, comma));
		long out = Long.parseLong(argument.substring(comma + 1));

		mBytesIn += in;
		mBytesOut += out;
	}

	private void processNeedCommand(String argument) {
		int p1 =argument.indexOf('\'');
		int p2 = argument.indexOf('\'',p1+1);

		String needed = argument.substring(p1+1, p2);
		String extra = argument.split(":",2)[1];

		String status = "ok";

		if ("PROTECTFD".equals(needed)) {
			FileDescriptor fdtoprotect = mFDList.pollFirst();
			protectFileDescriptor(fdtoprotect);
		} else if ("DNSSERVER".equals(needed)) {
			addDns(extra);
		}else if ("DNSDOMAIN".equals(needed)){
			setDomain(extra);
		} else if ("ROUTE".equals(needed)) {
			String[] routeparts = extra.split(" ");
            if(routeparts.length == 5 && "dev".equals(routeparts[3])) {
                addRoute4(routeparts[0], routeparts[1], routeparts[2], routeparts[4]);
            }  else if (routeparts.length >= 3) {
                addRoute4(routeparts[0], routeparts[1], routeparts[2], null);
            } else {
            	mLogger.log(LOG_ERROR, "Unrecognized ROUTE cmd:" + Arrays.toString(routeparts) + " | " + argument);
            }
		} else if ("ROUTE6".equals(needed)) {
            String[] routeparts = extra.split(" ");
			addRoute6(routeparts[0],routeparts[1]);
		} else if ("IFCONFIG".equals(needed)) {
			String[] ifconfigparts = extra.split(" ");
			int mtu = Integer.parseInt(ifconfigparts[2]);
			setLocalIp4(ifconfigparts[0], ifconfigparts[1], mtu, ifconfigparts[3]);
		} else if ("IFCONFIG6".equals(needed)) {
			setLocalIp6(extra);
		} else if ("PERSIST_TUN_ACTION".equals(needed)) {
            // check if tun cfg stayed the same
            status = "OPEN_AFTER_CLOSE"; //or OPEN_BEFORE_CLOSE
        } else if (needed.equals("OPENTUN")) {
			if(sendTunFD(needed,extra)) {
				return; 
			} else {
				status="cancel";
			}
		} else {
			Log.e(TAG,"Unkown needok command " + argument);
			return;
		}

		String cmd = String.format("needok '%s' %s\n", needed, status);
		managmentCommand(cmd);
	}

	private boolean sendTunFD (String needed, String extra) {
		boolean result = false;
		if(!extra.equals("tun")) {
			// We only support tun
			String logFmt = "Device type %s requested, but only tun is possible with the Android API";
			mLogger.log(LOG_ERROR, String.format(logFmt, extra));

			return false;
		}

		Method setInt;
		int fdint = getRemoteFd(mVpnTunnelCtx);
		Log.d(TAG, "Sending tun fd=" + fdint + " to OpenVPN");
		try {
			setInt = FileDescriptor.class.getDeclaredMethod("setInt$",int.class);
			FileDescriptor fdtosend = new FileDescriptor();

			setInt.invoke(fdtosend,fdint);

			FileDescriptor[] fds = {fdtosend};
			mSocket.setFileDescriptorsForSend(fds);

			// Trigger a send so we can close the fd on our side of the channel
			// The API documentation fails to mention that it will not reset the file descriptor to
			// be send and will happily send the file descriptor on every write ...
			String cmd = String.format("needok '%s' %s\n", needed, "ok");
			managmentCommand(cmd);

			// Set the FileDescriptor to null to stop this mad behavior 
			mSocket.setFileDescriptorsForSend(null);
	
			ParcelFileDescriptor pfd = ParcelFileDescriptor.adoptFd(fdint);
			try {
				pfd.close();
			} catch(IOException e) {
				Log.e(TAG, "Error while closing remote socket from socketpair", e);
			}

			result = true;
		} catch (Exception e) {
			Log.e(TAG, "Could not send fd over socket", e);
			result = false;
		}

        return result;
	}

	private void processPWCommand(String argument) {
		//argument has the form 	Need 'Private Key' password
		// or  ">PASSWORD:Verification Failed: '%s' ['%s']"
		String needed;
		
		try{

			int p1 = argument.indexOf('\'');
			int p2 = argument.indexOf('\'',p1+1);
			needed = argument.substring(p1+1, p2);
			if (argument.startsWith("Verification Failed")) {
				proccessPWFailed(needed, argument.substring(p2+1));
				return;
			}
		} catch (StringIndexOutOfBoundsException sioob) {
			mLogger.log(LOG_ERROR, "Could not parse management Password command: " + argument);
			return;
		}

		String pw=null;

		if("Private Key".equals(needed)) {
			pw = getPasswordPrivateKey();
		} else if ("Auth".equals(needed)) {
			String usercmd = String.format("username '%s' %s\n", 
					needed, openVpnEscape((String) mOptions.get("username")));
			managmentCommand(usercmd);
			pw = getPasswordAuth();
		} 
		if(pw != null) {
			String cmd = String.format("password '%s' %s\n", needed, openVpnEscape(pw));
			managmentCommand(cmd);
		} else {
			String logFmt = "Openvpn requires Authentication type '%s' but no credentials are available";
			mLogger.log(LOG_ERROR, String.format(logFmt, needed));
		}

	}

	private void proccessPWFailed(String needed, String args) {
		mLogger.log(LOG_ERROR, "Auth failed " + needed + args);
		mConnectionError = ConnectionError.AUTH_FAILED;
	}

    public void networkChange() {
        if(!mWaitingForRelease) {
            managmentCommand("network-change\n");
        }
    }

	public void signalusr1() {
		mReleaseHold=false;

		if(!mWaitingForRelease) {
			managmentCommand("signal SIGUSR1\n");
		} else {
            // If signalusr1 is called update the state string
            // if there is another for stopping
            //VpnStatus.updateStatePause(mLastPauseReason);
		}
	}

	public void reconnect() {
		signalusr1();
		releaseHold();
	}

	private void processSignCommand(String b64data) {

		String signed_string = getSignedData(b64data);
        if(signed_string == null) {
            managmentCommand("rsa-sig\n");
            managmentCommand("\nEND\n");
            stopVpn();
            return;
        }
        managmentCommand("rsa-sig\n");
		managmentCommand(signed_string);
        managmentCommand("\nEND\n");
	}

	public void pause(PauseReason reason) {
        mLastPauseReason = reason;
		signalusr1();
	}

	public void resume() {
		releaseHold();
        /* Reset the reason why we are disconnected */
        mLastPauseReason = PauseReason.noNetwork;
	}

	public boolean stopVpn() {
		boolean sendCMD=false;
		managmentCommand("signal SIGINT\n");
		sendCMD=true;
		try {
			if(mSocket !=null) {
				mSocket.close();
			}
		} catch (IOException e) {
			// Ignore close error on already closed socket
		}
		return sendCMD;	
	}
	
	/*
	 * OWN METHODS
	 */
	@Override
	/*package*/ void processDied() {
		//worker thread reports us that process died
	}
	
	private native long initOpenVpnTun();
	private native void deinitOpenVpnTun(long tunctx);
	private native int getLocalFd(long tunctx);
	private native int getRemoteFd(long tunctx);
	
	private String getSignedData(String b64data) {
		Log.d(TAG, "getSignedData");
		return "";
	}
	
	private String getPasswordPrivateKey() {
		return null;
	}
	
	private String getPasswordAuth() {
		return null;
	}
	
	private void addRoute4(String ip, String mask, String gateway, String device) {
		String devName = (device == null) ? "" : device;
		Log.d(TAG, "addRoute4 ip=" + ip + " mask=" + mask + " gw=" + gateway + " device=" + devName);
	}
	
	private void addRoute6(String network, String device) {
		Log.d(TAG, "addRoute6 network=" + network + " device=" + device);
	}
	
	private void addDns(String dns) {
		Log.d(TAG, "addDns " + dns);
	}
	
	private void setDomain(String domain) {
		Log.d(TAG, "setDomain " + domain);
	}
	
	private void setLocalIp4(String ip, String mask, int mtu, String mode) {
		Log.d(TAG, "setLocalIp4 ip=" + ip + " mask=" + mask + " mtu=" + mtu + " mode=" + mode);
		setMasqueradeIp4(NetUtils.ip4StrToInt(ip));
		setMasqueradeIp4Mode(true);
	}
	
	private void setLocalIp6(String ip) {
		Log.d(TAG, "setLocalIp6 ip=" + ip);
		setMasqueradeIp6(NetUtils.ip6StrToBytes(ip, null));
		setMasqueradeIp6Mode(true);
	}
	
	private String buildConfig() {
		StringBuilder builder = new StringBuilder();
		builder.append("management ");
		builder.append(mCtx.getCacheDir().getAbsolutePath() + "/" +  "mgmtsocket unix");
		builder.append("\n");
		
		builder.append("management-client\n");
		builder.append("management-query-passwords\n");
		builder.append("management-hold\n");
		
		builder.append("management-hold\n");
		//builder.append(String.format("setenv IV_GUI_VER %s \n", openVpnEscape(getVersionEnvString(context))));

		builder.append("machine-readable-output\n");
		
		if(BuildConfig.DEBUG) {
			builder.append("verb 5\n");
		}
		
		for(Entry<String, Object> entry : mOptions.entrySet()) {
			String key = entry.getKey();
			if("ca".equals(key) || 
					"tls-auth".equals(key)) {
				builder.append("<");
				builder.append(key);
				builder.append(">\n");
				String value = (String) entry.getValue();
				builder.append(StringUtils.join(value.split("\\\\n"), "\n", true));
				builder.append("\n</");
				builder.append(key);
				builder.append(">");
			} else {
				builder.append(key);
				if(!"NOARGS".equals(entry.getValue())) {
					builder.append(" ");
					builder.append(entry.getValue());
				}
			}
				
			builder.append("\n");
		}
		
		StringWriter strWriter = new StringWriter();
		PEMWriter pemWriter = new PEMWriter(strWriter);
		try {
			pemWriter.writeObject(mCfg.certificates[0]);
			pemWriter.flush();
			builder.append("<cert>\n");
			builder.append(strWriter.toString());
			builder.append("</cert>\n");
			pemWriter.close();
			
			strWriter = new StringWriter();
			pemWriter = new PEMWriter(strWriter);
			pemWriter.writeObject(mCfg.privateKey);
			pemWriter.flush();
			builder.append("<key>\n");
			builder.append(strWriter.toString());
			builder.append("</key>\n");
			pemWriter.close();
		} catch (IOException e) {
			Log.e(TAG, "Can't write pem", e);
		}
		
		return builder.toString();
	}
	
	public static String openVpnEscape(String unescaped) {
        if(unescaped == null) {
            return null;
        }
        
        String escapedString = unescaped.replace("\\", "\\\\");
        escapedString = escapedString.replace("\"", "\\\"");
        escapedString = escapedString.replace("\n", "\\n");

        if(escapedString.equals(unescaped) && 
        		!escapedString.contains(" ") &&
                !escapedString.contains("#") && 
                !escapedString.contains(";")) {
            return unescaped;
        } else {
            return '"' + escapedString + '"';
        }
    }

}

