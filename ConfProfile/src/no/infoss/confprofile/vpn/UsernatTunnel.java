package no.infoss.confprofile.vpn;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import no.infoss.confprofile.util.MiscUtils;
import no.infoss.confprofile.util.NetUtils;
import no.infoss.confprofile.vpn.VpnManagerService.VpnConfigInfo;
import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Log;

public class UsernatTunnel extends VpnTunnel {
	public static final String TAG = UsernatTunnel.class.getSimpleName();
	
	private static final VpnConfigInfo VPN_CFG_INFO;
	static {
		VPN_CFG_INFO = new VpnConfigInfo();
		VPN_CFG_INFO.configId = "0000";
	}
	
	private Thread mWorkerThread;
	private UsernatWorker mWorker;
	
	private LocalSocket mSocket;
	private LocalServerSocket mServerSocket;
	
	private final ConcurrentLinkedQueue<SocatTunnelContext> mQueue;
	private final byte[] mOutBuff = new byte[65537];

	public UsernatTunnel(Context ctx, RouterLoop routerLoop, VpnManagerInterface vpnMgr) {
		super(ctx, VPN_CFG_INFO, vpnMgr);
		mVpnServiceCtx = routerLoop.getRouterCtx();
		mQueue = new ConcurrentLinkedQueue<SocatTunnelContext>();
	}

	@Override
	public void run() {		
		if(MiscUtils.writeExecutableToCache(mCtx, UsernatWorker.USERNAT) == null) {
			Log.e(TAG, "Error writing usernat");
			terminateConnection();
			return;
		}
		
		File cacheDir = mCtx.getCacheDir();
		String sockFileName = String.format("mgmtsocket-%d", System.currentTimeMillis());
		String socketName = (new File(mCtx.getCacheDir(), sockFileName)).getAbsolutePath();
		List<String> args = new LinkedList<String>();
		args.add(cacheDir.getAbsolutePath() + "/" + UsernatWorker.USERNAT);
		args.add(socketName);
		

        int tries = 10;        
        mServerSocket = null;

        while(tries > 0) {
        	if(getConnectionStatus() == ConnectionStatus.TERMINATED) {
        		return;
        	}
        	
            try {
            	mServerSocket = NetUtils.bindLocalServerSocket(socketName);
                Log.d(TAG, "unix socket bound at " + socketName);
                break;
            } catch(IOException e) {
            	Log.w(TAG, e);
                try {
                	Thread.sleep(250);
                } catch (InterruptedException e1) {
                	//suppress this
                }
            }
            tries--;
            
            if(tries == 0) {
            	Log.e(TAG, "Can't connect to unix domain socket, terminating");
            	terminateConnection();
            	return;
            }
        }
        
        mWorker = new UsernatWorker(this, args, new HashMap<String, String>());
		mWorkerThread = new Thread(mWorker, "Usernat worker");
		mWorkerThread.start();

		InputStream instream = null;
		OutputStream outstream = null;
		try {
			mSocket = mServerSocket.accept();
			instream = mSocket.getInputStream();
			outstream = mSocket.getOutputStream();
			mServerSocket.close();
		} catch (IOException e) {
			teardown(e, "Unable to accept() due to ");
			return;
		}
		
		boolean isInterrupted = false;
		boolean isCommandMode = false;
		SocatTunnelContext socatData;
		List<String> pendingReports = new LinkedList<String>();
		
		String cmd = null;
		String resp;
		
		try {
			cmd = String.format("socat_path %s", ""); //obsoleted item
			processRequestSync(instream, outstream, cmd, pendingReports);
		} catch(Exception e) {
			teardown(e, String.format("Error while performing cmd '%s' :", cmd));
			return;
		}
		
		while(!isInterrupted) {
			pendingReports.clear();
			while((socatData = mQueue.poll()) != null) {
				isCommandMode = socatData.isRequestReady() & !socatData.isResponseReady();
	
				if(isCommandMode) {
					try {
						cmd = "accept_fd";
						mSocket.setFileDescriptorsForSend(new FileDescriptor[] {
								MiscUtils.intToFileDescriptor(socatData.inAcceptFd)
						});
						resp = processRequestSync(instream, outstream, cmd, pendingReports);
						
						cmd = "connect_fd";
						mSocket.setFileDescriptorsForSend(new FileDescriptor[] {
								MiscUtils.intToFileDescriptor(socatData.inConnectFd)
						});
						resp = processRequestSync(instream, outstream, cmd, pendingReports);
						
						cmd = String.format("socat %s:%d", socatData.inDstAddr, socatData.inDstPort);						
						Log.d(TAG, "Requesting " + cmd);
						resp = processRequestSync(instream, outstream, cmd, pendingReports); 
						socatData.outPid = Integer.valueOf(resp.trim());
						mSocket.setFileDescriptorsForSend(new FileDescriptor[]{});
					} catch(Exception e) {
						teardown(e, String.format("Error while performing cmd '%s' :", cmd));
						socatData.outPid = -1;
			            isInterrupted = true;
					} finally {
						//perform callback on socatData to set pid
						setPidForLink(mVpnTunnelCtx, socatData.inNativeLinkPtr, socatData.outPid);
						
						//send pending reports, it may report about socatData.outPid termination
						report(pendingReports);
						pendingReports.clear();
						socatData.setResponseReady(true);
					}
					
					if(isInterrupted) {
						break;
					}
				} //if(isCommandMode)
			}
			
			try {
				if(instream.available() > 0) {
					resp = processRequestSync(instream, outstream, null, pendingReports); 
				} else {
					try {
						Thread.sleep(100);
					} catch(InterruptedException e) {
						//suppress this
					}
				}
			} catch(Exception e) {
				Log.e(TAG, "Error while reading a command", e);
				isInterrupted = true;
				teardown(e, String.format("Error while receiving a message"));
			} finally {
				//send pending reports
				report(pendingReports);
				pendingReports.clear();
			}
		} //while(!isInterrupted)
		
		terminateConnection();
	}

	@Override
	protected String getThreadName() {
		return "Usernat";
	}

	@Override
	public void establishConnection(Map<String, Object> options) {
		if(getConnectionStatus() != ConnectionStatus.DISCONNECTED) {
			return;
		}
		
		mVpnTunnelCtx = initUsernatTun();
		
		startLoop();
	}
	
	private void teardown(Exception e, String msg) {
		Log.e(TAG, msg, e);
		mLogger.log(LOG_ERROR, msg.concat(e.toString()));
		terminateConnection();
	}

	@Override
	public void terminateConnection() {
		if(getConnectionStatus() != ConnectionStatus.TERMINATED) {
			setConnectionStatus(ConnectionStatus.TERMINATED);
			deinitUsernatTun(mVpnTunnelCtx);
			mVpnTunnelCtx = 0;
			
			if(mSocket != null) {
				try {
					mSocket.getInputStream().close();
				} catch(Exception e) { 
					//suppress this
				}
				
				try {
					mSocket.getOutputStream().close();
				} catch(Exception e) { 
					//suppress this
				}
				
				try {
					mSocket.close();
				} catch(Exception e) { 
					//suppress this
				} finally {
					mSocket = null;
				}
			}
		}
	}
	
	private String processRequestSync(InputStream instream, 
			OutputStream outstream, 
			String cmd, 
			List<String> pendingReports) throws IOException {
		String result = null;
		boolean justRead = (cmd == null);
		boolean continueReading = true;
		
		if(justRead || writeMessage(outstream, cmd)) {
			//command successfully was sent or we're in justRead mode
			if(!justRead) {
				try {
					outstream.flush();
				} catch(Exception e) {
					Log.e(TAG, "", e);
				}
			}
			
			while(continueReading) {
				String resp = readMessage(instream);
				if(resp.startsWith("terminated ")) {
					//this is just a report about terminated child
					if(pendingReports != null) {
						pendingReports.add(resp);
					}
				} else if(resp.startsWith("resp ")) {
					result = resp.substring("resp ".length());
					continueReading = false;
				} else if(resp.startsWith("ping")) {
					//just skip this
					//Log.d(TAG, "Received[0]: " + resp);
				} else if(resp.startsWith("log ")) {
					mLogger.log(LOG_DEBUG, resp.substring("log ".length()));
				} else {
					Log.e(TAG, "Unexpected response: " + resp);
					continueReading = false;
				}
				
				if(!justRead & continueReading) {
					continue;
				}
				
				break;
			} //while(continueReading)
		}
		
		return result;
	}
	
	/**
	 * This method is usually called from native code. It is used to create socat tunnel.
	 * @param fdAccept
	 * @param fdConnect
	 * @param remoteAddr
	 * @param remotePort
	 * @return
	 */
	protected int buildSocatTunnel(int fdAccept, 
			int fdConnect, 
			String remoteAddr, 
			int remotePort, 
			long nativeLinkPtr) {
		SocatTunnelContext mSocatData = new SocatTunnelContext();
		mSocatData.clear();
		mSocatData.inAcceptFd = fdAccept;
		mSocatData.inConnectFd = fdConnect;
		mSocatData.inDstAddr = remoteAddr;
		mSocatData.inDstPort = remotePort;
		mSocatData.inNativeLinkPtr = nativeLinkPtr;
		mQueue.add(mSocatData);
		
		return 0;
	}
	
	/**
	 * This method is usually called from native code for retrieving local address assigned to 
	 * tun0 device
	 * @return
	 */
	protected int getLocalAddress4() {
		return mVpnMgr.getLocalAddress4();
	}
	
	/**
	 * This method is usually called from native code for retrieving "remote" address
	 * @return
	 */
	protected int getRemoteAddress4() {
		return mVpnMgr.getRemoteAddress4();
	}
	
	private String readMessage(InputStream instream) throws IOException {
		String result = null;
		boolean prologueMode = true;
        byte[] buffer = new byte[65536];
        int msgLen = 0;
        int pos = 0;
        
		while(true) {
			if(prologueMode) {
				int s1 = instream.read();
				if(s1 == -1) {
					break;
				}
				
				int s2 = instream.read();
				if(s2 == -1) {
					break;
				}
				
				msgLen = ((s1 & 0xff) << 8) & 0x0000ffff;
				msgLen |= s2 & 0x000000ff;
				
				prologueMode = false;
				pos = 0;
			}
			
			int numbytesread = instream.read(buffer, pos, msgLen - pos);
			if(numbytesread == -1) {
				break;
			}
			
			if(pos + numbytesread == msgLen) {
				result = new String(buffer, 0, msgLen);
				break;
			} else {
				pos += numbytesread;
			}
		}
		
		return result;
	}
	
	private boolean writeMessage(OutputStream outstream, String message) throws IOException {
		if(outstream == null || message == null) {
			//silently return
			Log.e(TAG, "Can't send a message: output stream or message is null.");
			return false;
		}
		
		byte[] msg = message.getBytes();
		if(msg.length > 65535) {
			Log.e(TAG, "Message too long");
			return false;
		}
		
		mOutBuff[0] = (byte)((msg.length >> 8) & 0x00ff);
		mOutBuff[1] = (byte)(msg.length & 0x00ff);
		for(int i = 0; i < msg.length; i++) {
			mOutBuff[i + 2] = msg[i];
		}
		
		outstream.write(mOutBuff, 0, msg.length + 2);
		
		return true;
	}
	
	private void report(List<String> reports) {
		if(reports != null) {
			for(String report : reports) {
				Log.d(TAG, "Reported: " + report);
			}
		}
	}
	
	private native long initUsernatTun();
	private native void deinitUsernatTun(long tunctx);
	private native void setPidForLink(long mVpnTunnelCtx, long inNativeLinkPtr, int outPid);
	
	private static class SocatTunnelContext {
		public int inAcceptFd;
		public int inConnectFd;
		public String inDstAddr;
		public int inDstPort;
		public long inNativeLinkPtr; //used in callback method for writing pid by appropriate address
		public int outPid;
		private boolean mIsReady;
		
		public SocatTunnelContext() {
			clear();
		}
		
		public void clear() {
			inAcceptFd = -1;
			inConnectFd = -1;
			inDstAddr = null;
			inDstPort = 0;
			inNativeLinkPtr = 0;
			outPid = -1;
			mIsReady = false;
		}
		
		public boolean isRequestReady() {
			if(inAcceptFd == -1 || inConnectFd == -1 || 
					inDstAddr == null || inDstPort == 0 || 
					inNativeLinkPtr == 0) {
				return false;
			}
			
			return true;
		}
		
		public boolean isResponseReady() {
			return mIsReady;
		}
		
		public void setResponseReady(boolean ready) {
			mIsReady = ready;
		}
	}
}
