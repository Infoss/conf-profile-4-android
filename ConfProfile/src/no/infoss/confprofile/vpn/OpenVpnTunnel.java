package no.infoss.confprofile.vpn;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import no.infoss.confprofile.BuildConfig;
import no.infoss.confprofile.util.NetUtils;
import no.infoss.confprofile.util.StringUtils;
import no.infoss.confprofile.vpn.VpnManagerService.VpnConfigInfo;

import org.bouncycastle.openssl.PEMWriter;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.util.Log;

public class OpenVpnTunnel extends VpnTunnel implements OpenVPNManagement {
	public static final String TAG = OpenVpnTunnel.class.getSimpleName();
	
	public static final String VPN_TYPE = "net.openvpn.OpenVPN-Connect.vpnplugin";
	
	private Map<String, Object> mOptions;
	private VpnManagerInterface mVpnMgr;
	private boolean mIsTerminating;
	private Thread mWorkerThread;
	private OpenVpnWorker mWorker;
	
	
	private LocalSocket mSocket;
	private LinkedList<FileDescriptor> mFDList=new LinkedList<FileDescriptor>();
    private LocalServerSocket mServerSocket;
	private boolean mReleaseHold=true;
	private boolean mWaitingForRelease=false;
	private long mLastHoldRelease=0;

	private static Vector<OpenVpnTunnel> active = new Vector<OpenVpnTunnel>();
    private LocalSocket mServerSocketLocal;

    private pauseReason lastPauseReason = pauseReason.noNetwork;
	
	
	/*package*/ OpenVpnTunnel(Context ctx, long vpnServiceCtx, VpnManagerInterface vpnMgr, VpnConfigInfo cfg) {
		super(ctx, cfg);
		mVpnServiceCtx = vpnServiceCtx;
		mVpnMgr = vpnMgr;
		mIsTerminating = false;
		
		boolean managemeNetworkState = true;
		if(managemeNetworkState)
			mReleaseHold=false;
	}

	@Override
	public String getTunnelId() {
		return VPN_TYPE;
	}

	@Override
	protected String getThreadName() {
		return VPN_TYPE;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void establishConnection(Map<String, Object> options) {
		if(mIsTerminating) {
			return;
		}
		
		mOptions = (Map<String, Object>) options.get(VpnConfigInfo.PARAMS_CUSTOM);
		mVpnTunnelCtx = initOpenVpnTun();
		
		startLoop();
	}
	
	public void terminateConnection() {
		mIsTerminating = true;
		managmentCommand("signal SIGINT\n");
		deinitOpenVpnTun(mVpnTunnelCtx);
		mVpnTunnelCtx = 0;
	}

	@Override
	public void run() {
		if(!writeMiniVPN(mCtx)) {
			Log.e(TAG, "Error writing minivpn");
		}
		
		byte [] buffer = new byte[2048];
		//	mSocket.setSoTimeout(5); // Setting a timeout cannot be that bad
		
		String pendingInput="";
		active.add(this);
		
		String confFileName = mCfg.configId.concat(".ovpn");
		File cacheDir = mCtx.getCacheDir();
		
		String[] argv = new String[3];
        argv[0] = cacheDir.getAbsolutePath() + "/" + OpenVpnWorker.MINIVPN;
        argv[1] = "--config";
        argv[2] = cacheDir.getAbsolutePath() + "/" + confFileName;

		ApplicationInfo info = mCtx.getApplicationInfo();

		FileOutputStream fos = null;
		try {
			String buildConfig = buildConfig();
			fos = new FileOutputStream(argv[2]);
			fos.write(buildConfig.getBytes("UTF-8"));
		} catch(Exception e) {
			Log.e(TAG, "Error while saving config", e);
		} finally {
			if(fos != null) {
				try {
					fos.flush();
				} catch(Exception e) {
					Log.w(TAG, e);
				}
				
				try {
					fos.close();
				} catch(Exception e) {
					Log.w(TAG, e);
				}
			}
		}
		
		// Could take a while to open connection
        int tries=8;

        String socketName = (mCtx.getCacheDir().getAbsolutePath() + "/" +  "mgmtsocket");
        // The mServerSocketLocal is transferred to the LocalServerSocket, ignore warning

        mServerSocketLocal = new LocalSocket();

        while(tries > 0) {
        	if(mIsTerminating) {
        		break;
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
        	Log.e(TAG, "Error opening management interface");
            VpnStatus.logException(e);
        }
        
        mWorker = new OpenVpnWorker(this, argv, new HashMap<String, String>(), info.nativeLibraryDir);
		mWorkerThread = new Thread(mWorker, "OpenVPN worker");
		mWorkerThread.start();
		
		/*
		tries = 8;
        while(tries > 0 && !mServerSocketLocal.isConnected()) {
        	if(mIsTerminating) {
        		break;
        	}
        	
        	try {
            	Thread.sleep(300);
            } catch (InterruptedException e1) {
            }
        }

        try {
            mServerSocket = new LocalServerSocket(mServerSocketLocal.getFileDescriptor());
            Log.d(TAG, "Management interface opened");
        } catch (IOException e) {
        	Log.e(TAG, "Error opening management interface");
            VpnStatus.logException(e);
        }
        */
        

		try {
			// Wait for a client to connect
			mSocket = mServerSocket.accept();
			InputStream instream = mSocket.getInputStream();
            // Close the management socket after client connected

            mServerSocket.close();
            // Closing one of the two sockets also closes the other
            //mServerSocketLocal.close();
            
            resume();

			while(true) {
				int numbytesread = instream.read(buffer);
				if(numbytesread==-1)
					return;

				FileDescriptor[] fds = null;
				try {
					fds = mSocket.getAncillaryFileDescriptors();
				} catch (IOException e) {
					VpnStatus.logException("Error reading fds from socket", e);
				}
				if(fds!=null){
                    Collections.addAll(mFDList, fds);
				}

				String input = new String(buffer,0,numbytesread,"UTF-8");

				pendingInput += input;

				pendingInput=processInput(pendingInput);



			}
		} catch (IOException e) {
            if (!e.getMessage().equals("socket closed"))
                VpnStatus.logException(e);
		}
		active.remove(this);
	}

    public boolean openManagementInterface() {
        int tries = 8;
        while(tries > 0 && !mServerSocketLocal.isConnected()) {
        	if(mIsTerminating) {
        		return false;
        	}
        	
        	try {
            	Thread.sleep(300);
            } catch (InterruptedException e1) {
            }
        }

        try {
            mServerSocket = new LocalServerSocket(mServerSocketLocal.getFileDescriptor());
            return true;
        } catch (IOException e) {
            VpnStatus.logException(e);
        }
        
        Log.e(TAG, "Error opening management interface");
        return false;
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
		Exception exp;
		try {
			Method getInt =  FileDescriptor.class.getDeclaredMethod("getInt$");
			int fdint = (Integer) getInt.invoke(fd);

			// You can even get more evil by parsing toString() and extract the int from that :)

			boolean result = mVpnMgr.protect(fdint);
            if (!result)
                VpnStatus.logWarning("Could not protect VPN socket");


            //TODO: check this
			//NativeUtils.jniclose(fdint);
			return;
		} catch (NoSuchMethodException e) {
			exp =e;
		} catch (IllegalArgumentException e) {
			exp =e;
		} catch (IllegalAccessException e) {
			exp =e;
		} catch (InvocationTargetException e) {
			exp =e;
		} catch (NullPointerException e) {
			exp =e;
		}

        Log.d(TAG, "Failed to retrieve fd from socket: " + fd);
        VpnStatus.logException("Failed to retrieve fd from socket (" + fd + ")" , exp);
	}

	private String processInput(String pendingInput) {


		while(pendingInput.contains("\n")) {
			String[] tokens = pendingInput.split("\\r?\\n", 2);
			processCommand(tokens[0]);
			if(tokens.length == 1)
				// No second part, newline was at the end
				pendingInput="";
			else
				pendingInput=tokens[1];
		}
		return pendingInput;
	}


	private void processCommand(String command) {
        //Log.i(TAG, "Line from managment" + command);


        if (command.startsWith(">") && command.contains(":")) {
			String[] parts = command.split(":",2);
			String cmd = parts[0].substring(1);
			String argument = parts[1];


			if(cmd.equals("INFO")) {
				/* Ignore greeting from management */
                return;
			}else if (cmd.equals("PASSWORD")) {
				processPWCommand(argument);
			} else if (cmd.equals("HOLD")) {
				handleHold();
			} else if (cmd.equals("NEED-OK")) {
				processNeedCommand(argument);
			} else if (cmd.equals("BYTECOUNT")){
				processByteCount(argument);
			} else if (cmd.equals("STATE")) {
				processState(argument);
			} else if (cmd.equals("PROXY")) {
				processProxyCMD(argument);
			} else if (cmd.equals("LOG")) {
                 processLogMessage(argument);
			} else if (cmd.equals("RSA_SIGN")) {
				processSignCommand(argument);
			} else {
				VpnStatus.logWarning("MGMT: Got unrecognized command" + command);
				Log.i(TAG, "Got unrecognized command" + command);
			}
		} else if (command.startsWith("SUCCESS:")) {
			/* Ignore this kind of message too */
            return;
        } else if (command.startsWith("PROTECTFD: ")) {
            FileDescriptor fdtoprotect = mFDList.pollFirst();
            if (fdtoprotect!=null)
                protectFileDescriptor(fdtoprotect);
		} else {
			Log.i(TAG, "Got unrecognized line from managment" + command);
			VpnStatus.logWarning("MGMT: Got unrecognized line from management:" + command);
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

        Log.d(TAG, argument);

        VpnStatus.LogLevel level;
        if (args[1].equals("I")) {
            level = VpnStatus.LogLevel.INFO;
        } else if (args[1].equals("W")) {
            level = VpnStatus.LogLevel.WARNING;
        } else if (args[1].equals("D")) {
            level = VpnStatus.LogLevel.VERBOSE;
        } else if (args[1].equals("F")) {
            level = VpnStatus.LogLevel.ERROR;
        } else {
            level = VpnStatus.LogLevel.INFO;
        }

        int ovpnlevel = Integer.parseInt(args[2]) & 0x0F;
        String msg = args[3];

        if (msg.startsWith("MANAGEMENT: CMD"))
            ovpnlevel = Math.max(4, ovpnlevel);

        VpnStatus.logMessageOpenVPN(level,ovpnlevel, msg);
    }

    private void handleHold() {
		if(mReleaseHold) {
			releaseHoldCmd();
		} else { 
			mWaitingForRelease=true;

            VpnStatus.updateStatePause(lastPauseReason);


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
				proxyaddr=null;
			}
		}

		if(proxyaddr instanceof InetSocketAddress ){
			InetSocketAddress isa = (InetSocketAddress) proxyaddr;
			
			VpnStatus.logInfo("R.string.using_proxy", isa.getHostName(), isa.getPort());
			
			String proxycmd = String.format(Locale.ENGLISH,"proxy HTTP %s %d\n", isa.getHostName(),isa.getPort());
			managmentCommand(proxycmd);
		} else {
			managmentCommand("proxy NONE\n");
		}

	}
	private void processState(String argument) {
		String[] args = argument.split(",",3);
		String currentstate = args[1];

		if(args[2].equals(",,"))
			VpnStatus.updateStateString(currentstate, "");
		else
			VpnStatus.updateStateString(currentstate, args[2]);
	}


	private void processByteCount(String argument) {
		//   >BYTECOUNT:{BYTES_IN},{BYTES_OUT}
		int comma = argument.indexOf(',');
		long in = Long.parseLong(argument.substring(0, comma));
		long out = Long.parseLong(argument.substring(comma+1));

		VpnStatus.updateByteCount(in, out);
		
	}



	private void processNeedCommand(String argument) {
		int p1 =argument.indexOf('\'');
		int p2 = argument.indexOf('\'',p1+1);

		String needed = argument.substring(p1+1, p2);
		String extra = argument.split(":",2)[1];

		String status = "ok";


		if (needed.equals("PROTECTFD")) {
			FileDescriptor fdtoprotect = mFDList.pollFirst();
			protectFileDescriptor(fdtoprotect);
		} else if (needed.equals("DNSSERVER")) {
			addDns(extra);
		}else if (needed.equals("DNSDOMAIN")){
			setDomain(extra);
		} else if (needed.equals("ROUTE")) {
			String[] routeparts = extra.split(" ");

            /*
            buf_printf (&out, "%s %s %s dev %s", network, netmask, gateway, rgi->iface);
            else
            buf_printf (&out, "%s %s %s", network, netmask, gateway);
            */

            if(routeparts.length==5) {
                assert(routeparts[3].equals("dev"));
                addRoute4(routeparts[0], routeparts[1], routeparts[2], routeparts[4]);
            }  else if (routeparts.length >= 3) {
                addRoute4(routeparts[0], routeparts[1], routeparts[2], null);
            } else {
                VpnStatus.logError("Unrecognized ROUTE cmd:" + Arrays.toString(routeparts) + " | " + argument);
            }

		} else if (needed.equals("ROUTE6")) {
            String[] routeparts = extra.split(" ");
			addRoute6(routeparts[0],routeparts[1]);
		} else if (needed.equals("IFCONFIG")) {
			String[] ifconfigparts = extra.split(" ");
			int mtu = Integer.parseInt(ifconfigparts[2]);
			setLocalIp4(ifconfigparts[0], ifconfigparts[1], mtu, ifconfigparts[3]);
		} else if (needed.equals("IFCONFIG6")) {
			setLocalIp6(extra);

		} else if (needed.equals("PERSIST_TUN_ACTION")) {
            // check if tun cfg stayed the same
            //status = mVpnMgr.getTunReopenStatus();
            status = "OPEN_AFTER_CLOSE"; //or OPEN_BEFORE_CLOSE
        } else if (needed.equals("OPENTUN")) {
			if(sendTunFD(needed,extra))
				return;
			else
				status="cancel";
			// This not nice or anything but setFileDescriptors accepts only FilDescriptor class :(

		} else {
			Log.e(TAG,"Unkown needok command " + argument);
			return;
		}

		String cmd = String.format("needok '%s' %s\n", needed, status);
		managmentCommand(cmd);
	}

	private boolean sendTunFD (String needed, String extra) {
		Exception exp;
		if(!extra.equals("tun")) {
			// We only support tun
			VpnStatus.logError(String.format("Device type %s requested, but only tun is possible with the Android API, sorry!",extra));

			return false;
		}

		Method setInt;
		int fdint = getRemoteFd(mVpnTunnelCtx);
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

			//pfd.close();			

			return true;
		} catch (NoSuchMethodException e) {
			exp =e;
		} catch (IllegalArgumentException e) {
			exp =e;
		} catch (IllegalAccessException e) {
			exp =e;
		} catch (InvocationTargetException e) {
			exp =e;
		}
        VpnStatus.logException("Could not send fd over socket" , exp);

        return false;
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
			VpnStatus.logError("Could not parse management Password command: " + argument);
			return;
		}

		String pw=null;

		if(needed.equals("Private Key")) {
			pw = getPasswordPrivateKey();
		} else if (needed.equals("Auth")) {
			String usercmd = String.format("username '%s' %s\n", 
					needed, openVpnEscape((String) mOptions.get("username")));
			managmentCommand(usercmd);
			pw = getPasswordAuth();
		} 
		if(pw != null) {
			String cmd = String.format("password '%s' %s\n", needed, openVpnEscape(pw));
			managmentCommand(cmd);
		} else {
			VpnStatus.logError(String.format("Openvpn requires Authentication type '%s' but no password/key information available", needed));
		}

	}




	private void proccessPWFailed(String needed, String args) {
		VpnStatus.updateStateString("AUTH_FAILED", needed + args, "R.string.state_auth_failed", "ConnectionStatus.LEVEL_AUTH_FAILED");
	}


	private static boolean stopOpenVPN() {
		boolean sendCMD=false;
		for (OpenVpnTunnel mt : active){
			mt.managmentCommand("signal SIGINT\n");
			sendCMD=true;
			try {
				if(mt.mSocket !=null)
					mt.mSocket.close();
			} catch (IOException e) {
				// Ignore close error on already closed socket
			}
		}
		return sendCMD;		
	}

    @Override
    public void networkChange() {
        if(!mWaitingForRelease)
            managmentCommand("network-change\n");
    }

	public void signalusr1() {
		mReleaseHold=false;

		if(!mWaitingForRelease)
			managmentCommand("signal SIGUSR1\n");
        else
            // If signalusr1 is called update the state string
            // if there is another for stopping
            VpnStatus.updateStatePause(lastPauseReason);
	}

	public void reconnect() {
		signalusr1();
		releaseHold();
	}

	private void processSignCommand(String b64data) {

		String signed_string = getSignedData(b64data);
        if(signed_string==null) {
            managmentCommand("rsa-sig\n");
            managmentCommand("\nEND\n");
            stopOpenVPN();
            return;
        }
        managmentCommand("rsa-sig\n");
		managmentCommand(signed_string);
        managmentCommand("\nEND\n");
	}

	@Override
	public void pause(pauseReason reason) {
        lastPauseReason = reason;
		signalusr1();
	}

	@Override
	public void resume() {
		releaseHold();
        /* Reset the reason why we are disconnected */
        lastPauseReason = pauseReason.noNetwork;
	}

	@Override
	public boolean stopVPN() {
		return stopOpenVPN();
	}
	
	/*
	 * OWN METHODS
	 */
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
			builder.append("verb 6\n");
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
	
	static private boolean writeMiniVPN(Context context) {
		File mvpnout = new File(context.getCacheDir(), OpenVpnWorker.MINIVPN);
		if (mvpnout.exists() && mvpnout.canExecute())
			return true;

		IOException e2 = null;

		try {
			InputStream mvpn;
			
			try {
				mvpn = context.getAssets().open("minivpn." + Build.CPU_ABI);
			}
			catch (IOException errabi) {
				VpnStatus.logInfo("Failed getting assets for archicture " + Build.CPU_ABI);
				e2=errabi;
				mvpn = context.getAssets().open("minivpn." + Build.CPU_ABI2);
				
			}


			FileOutputStream fout = new FileOutputStream(mvpnout);

			byte buf[]= new byte[4096];

			int lenread = mvpn.read(buf);
			while(lenread> 0) {
				fout.write(buf, 0, lenread);
				lenread = mvpn.read(buf);
			}
			fout.close();

			if(!mvpnout.setExecutable(true)) {
				VpnStatus.logError("Failed to set minivpn executable");
				return false;
			}
				
			
			return true;
		} catch (IOException e) {
			if(e2!=null)
				VpnStatus.logException(e2);
			VpnStatus.logException(e);

			return false;
		}
	}
	
	public static String openVpnEscape(String unescaped) {
        if (unescaped == null)
            return null;
        String escapedString = unescaped.replace("\\", "\\\\");
        escapedString = escapedString.replace("\"", "\\\"");
        escapedString = escapedString.replace("\n", "\\n");

        if (escapedString.equals(unescaped) && !escapedString.contains(" ") &&
                !escapedString.contains("#") && !escapedString.contains(";"))
            return unescaped;
        else
            return '"' + escapedString + '"';
    }

	static class VpnStatus {
		
		enum LogLevel {
			INFO,
			WARNING,
			VERBOSE,
			ERROR
		}

		public static void logError(String format) {
			Log.e(TAG, format);
		}

		public static void updateStateString(String string, String string2,
				String string3, String string4) {
			// TODO Auto-generated method stub
			
		}

		public static void logInfo(String string, String hostName, int port) {
			Log.i(TAG, String.format("%s %s:%d", string, hostName, port));
		}

		public static void logMessageOpenVPN(
				no.infoss.confprofile.vpn.OpenVpnTunnel.VpnStatus.LogLevel level,
				int ovpnlevel, String msg) {
			Log.d(TAG, String.format("[level=%d] %s", ovpnlevel, msg));
		}

		public static void updateStateString(String currentstate, String string) {
			Log.d(TAG, "updateState from " + currentstate + " to " + string);
		}

		public static void updateByteCount(long in, long out) {
			// TODO Auto-generated method stub
			
		}

		public static void updateStatePause(pauseReason lastPauseReason) {
			// TODO Auto-generated method stub
			
		}

		public static void logException(String string, Exception exp) {
			Log.e(TAG, string, exp);
		}

		public static void logWarning(String string) {
			Log.w(TAG, string);
		}

		public static void logException(IOException e) {
			Log.e(TAG, "", e);
		}

		public static void logException(String string, IOException e) {
			Log.e(TAG, string, e);
		}

		public static void logInfo(String string) {
			Log.i(TAG, string);
		}
		
	}
}

interface OpenVPNManagement {
    enum pauseReason {
        noNetwork,
        userPause,
        screenOff
    }

	int mBytecountInterval = 2;

	void reconnect();

	void pause(pauseReason reason);

	void resume();

	boolean stopVPN();

    /*
     * Rebind the interface
     */
    void networkChange();
}
