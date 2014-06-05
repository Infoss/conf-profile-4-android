package no.infoss.confprofile.vpn;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import no.infoss.confprofile.util.MiscUtils;
import no.infoss.confprofile.vpn.VpnManagerService.VpnConfigInfo;
import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

public class L2tpTunnel extends VpnTunnel {
	public static final String TAG = L2tpTunnel.class.getSimpleName();
	
	public static final String VPN_TYPE = "L2TP/pure";
	
	private List<String> mOptions;
	private VpnManagerInterface mVpnMgr;
	private boolean mIsTerminating;
	private Thread mWorkerThread;
	private L2tpWorker mWorker;
	
	private LocalSocket mSocket;
	private LinkedList<FileDescriptor> mFds = new LinkedList<FileDescriptor>();
	
	/*package*/ L2tpTunnel(Context ctx, long vpnServiceCtx, VpnManagerInterface vpnMgr, VpnConfigInfo cfg) {
		super(ctx, cfg);
		mVpnServiceCtx = vpnServiceCtx;
		mVpnMgr = vpnMgr;
		mIsTerminating = false;
	}

	@Override
	public void run() {
		if(!MiscUtils.writeExecutableToCache(mCtx, L2tpWorker.MINIVPN)) {
			Log.e(TAG, "Error writing ocpamtpd");
		}

		int tries = 8;
		String unixSockName = (new File(mCtx.getCacheDir(), "mgmtsocket-mtpd")).getAbsolutePath();
		LocalSocket localSocket = new LocalSocket();

		while(tries > 0) {
			if(mIsTerminating) {
				try {
					localSocket.close();
				} catch (Exception e) {
					//ignore this
				}
				return;
			}
	
			try {
				localSocket.bind(
						new LocalSocketAddress(unixSockName, 
								LocalSocketAddress.Namespace.FILESYSTEM));
				Log.d(TAG, "unix socket bound at " + unixSockName);
				break;
			} catch (IOException e) {
				// wait 300 ms before retrying
				Log.w(TAG, e);
				try {
					Thread.sleep(300);
				} catch (InterruptedException ex) {
					//ignore this
				}
			}
			tries--;
		}

		LocalServerSocket localServerSocket = null;
		try {
			localServerSocket = new LocalServerSocket(localSocket.getFileDescriptor());
			Log.d(TAG, "Management interface opened");
		} catch (IOException e) {
			Log.e(TAG, "Error opening management interface, terminating connection");
			try {
				localSocket.close();
			} catch(Exception ex) {
				//ignore this
			}
			terminateConnection();
			return;
		}

		String[] argv = new String[2];
		argv[0] = (new File(mCtx.getCacheDir(), L2tpWorker.MINIVPN)).getAbsolutePath();
		argv[1] = unixSockName;
		mWorker = new L2tpWorker(this, argv, new HashMap<String, String>());
		mWorkerThread = new Thread(mWorker, "L2TP worker");
		mWorkerThread.start();

		try {
			mSocket = localServerSocket.accept();
			InputStream instream = mSocket.getInputStream();
			OutputStream outstream = mSocket.getOutputStream();
			
			try {
				localServerSocket.close();
			} catch(Exception e) {
				//ignore this
			}

			byte buff[] = new byte[2048];
			
			//sending parameters
			for(String param : mOptions) {
				try {
					byte[] paramArr = param.getBytes("UTF-8");
					System.arraycopy(paramArr, 0, buff, 2, paramArr.length);
					buff[0] = (byte)((paramArr.length >> 8) & 0x00ff);
					buff[1] = (byte)(paramArr.length & 0x00ff);
					outstream.write(buff, 0, paramArr.length + 2);
				} catch(Exception e) {
					Log.e(TAG, "Exception while sending an option to worker process", e);
				}
			}
			
			try {
				buff[0] = (byte) 0xff;
				buff[1] = (byte) 0xff;
				outstream.write(buff, 0, 2);
			} catch(Exception e) {
				Log.e(TAG, "Exception while sending an option to worker process", e);
			}
			Log.d(TAG, "Options were successfully sent to worker process");
			String pendingInput = "";
			
			while(true) {
				int numbytesread = instream.read(buff);
				if(numbytesread == -1) {
					return;
				}
				
				FileDescriptor[] fds = null;
				try {
					fds = mSocket.getAncillaryFileDescriptors();
				} catch (IOException e) {
					Log.e(TAG, "Error reading fds from socket", e);
				}
				
				if(fds != null){
					Collections.addAll(mFds, fds);
				}

				String input = new String(buff,0,numbytesread,"UTF-8");
				pendingInput += input;
				pendingInput = processInput(pendingInput);
			}
		} catch (IOException e) {
			Log.e(TAG, "Error occured while performing main loop", e);
		}
	}

	@Override
	protected String getThreadName() {
		return VPN_TYPE;
	}

	@Override
	public String getTunnelId() {
		return VPN_TYPE;
	}

	@Override
	public void establishConnection(Map<String, Object> options) {
		if(mIsTerminating) {
			return;
		}
		
		mOptions = prepareOptions(options);
		mVpnTunnelCtx = initL2tpTun();
		
		startLoop();
	}

	@Override
	public void terminateConnection() {
		mIsTerminating = true;
		deinitL2tpTun(mVpnTunnelCtx);
		mVpnTunnelCtx = 0;
	}
	
	private List<String> prepareOptions(Map<String, Object> options) {
		List<String> result = new LinkedList<String>();
		result.add("wlan0"); //interface
		result.add("l2tp"); //protocol
		
		@SuppressWarnings("unchecked")
		Map<String, Object> ppp = (Map<String, Object>) options.get(VpnConfigInfo.PARAMS_PPP);

		if(ppp != null) {
			result.add("AuthName ".concat(String.valueOf(ppp.get("AuthName"))));
			result.add("AuthPassword ".concat(String.valueOf(ppp.get("AuthPassword"))));
			result.add("TokenCard ".concat(String.valueOf(ppp.get("TokenCard"))));
			result.add("CommRemoteAddress ".concat(String.valueOf(ppp.get("CommRemoteAddress"))));
			result.add("AuthEAPPlugins ".concat(String.valueOf(ppp.get("AuthEAPPlugins"))));
			result.add("AuthProtocol ".concat(String.valueOf(ppp.get("AuthProtocol"))));
		}
		
		return result;
	}
	
	private String processInput(String pendingInput) {
		// TODO Implement this
		return pendingInput;
	}

	private native long initL2tpTun();
	private native void deinitL2tpTun(long tunctx);
}
