package no.infoss.confprofile.vpn;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketImplFactory;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
	}
	
	private Thread mWorkerThread;
	private UsernatWorker mWorker;
	
	private LocalSocket mSocket;
	private LocalServerSocket mServerSocket;
	
	private ReentrantReadWriteLock mRWLock = new ReentrantReadWriteLock(true);
	private Lock mRLock = mRWLock.readLock();
	private Lock mWLock = mRWLock.writeLock();
	private final ConcurrentLinkedQueue<SocatTunnelContext> mQueue;
	private final byte[] mOutBuff = new byte[65537];

	public UsernatTunnel(Context ctx, RouterLoop routerLoop, VpnManagerInterface vpnMgr) {
		super(ctx, VPN_CFG_INFO, vpnMgr);
		mVpnServiceCtx = routerLoop.getRouterCtx();
		mQueue = new ConcurrentLinkedQueue<SocatTunnelContext>();
	}

	@Override
	public void run() {
		if(!MiscUtils.writeExecutableToCache(mCtx, UsernatWorker.SOCAT)) {
			Log.e(TAG, "Error writing socat");
			terminateConnection();
			return;
		}
		
		if(!MiscUtils.writeExecutableToCache(mCtx, UsernatWorker.USERNAT)) {
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
        	if(mConnectionStatus == ConnectionStatus.TERMINATED) {
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
			Log.e(TAG, "Unable to accept()", e);
			mLogger.log(LOG_ERROR, "Unable to accept() due to ".concat(e.toString()));
			terminateConnection();
			return;
		}
		
		boolean isInterrupted = false;
		boolean isCommandMode = false;
		SocatTunnelContext socatData;
		List<String> pendingReports = new LinkedList<String>();
		
		while(!isInterrupted) {
			pendingReports.clear();
			while((socatData = mQueue.poll()) != null) {
				isCommandMode = socatData.isRequestReady() & !socatData.isResponseReady();
	
				if(isCommandMode) {
					//mWLock.lock();
					try {
						String cmd = String.format("socat %s:%d", socatData.inDstAddr, socatData.inDstPort);
						/*mSocket.setFileDescriptorsForSend(new FileDescriptor[] {
								mServerSocket.getFileDescriptor(),
								mSocket.getFileDescriptor()
						});*/
						//DatagramSocket ds = new DatagramSocket(31337);
						//Socket ss = new Socket();
						Log.d(TAG, "accept_fd=" + socatData.inAcceptFd + " connect_fd=" + socatData.inConnectFd);
						mSocket.setFileDescriptorsForSend(new FileDescriptor[] {
								MiscUtils.intToFileDescriptor(socatData.inAcceptFd)//,
								//MiscUtils.intToFileDescriptor(socatData.inConnectFd)
						});
						
						
						Log.d(TAG, "Requesting " + cmd);
						if(writeMessage(outstream, cmd)) {
							//command successfully was sent
							while(true) {
								String resp = readMessage(instream);
								if(resp.startsWith("terminated ")) {
									//this is just a report about terminated child
									pendingReports.add(resp);
									continue;
								} else if(resp.startsWith("resp ")) {
									socatData.outPid = Integer.valueOf(resp.substring("resp ".length()));
									break;
								} else if(resp.startsWith("ping")) {
									//just skip this
									Log.d(TAG, "Received[1]: " + resp);
									continue;
								} else if(resp.startsWith("log ")) {
									mLogger.log(LOG_DEBUG, resp.substring("log ".length()));
								} else {
									Log.e(TAG, "Unexpected response: " + resp);
								}
								break;
							} //while(true)
						}
					} catch(Exception e) {
						Log.e(TAG, "Error while reading/writing a command", e);
						mLogger.log(LOG_ERROR, "Error while reading/writing a command: ".concat(e.toString()));
						socatData.outPid = -1;
			            isInterrupted = true;
					} finally {
						socatData.setResponseReady(true);
						mSocket.setFileDescriptorsForSend(new FileDescriptor[]{});
					}
					
					if(isInterrupted) {
						break;
					}
				} //if(isCommandMode)
			}
			
			try {
				if(instream.available() == 0) {
					Thread.sleep(100);
				} else {
					String resp = readMessage(instream);
					if(resp.startsWith("terminated ")) {
						//this is just a report about terminated child
						pendingReports.add(resp);
						continue;
					} else if(resp.startsWith("ping")) {
						//just skip this
						Log.d(TAG, "Received[2]: " + resp);
						writeMessage(outstream, "resp ping");
					} else if(resp.startsWith("log ")) {
						mLogger.log(LOG_DEBUG, resp.substring("log ".length()));
					} else if(resp.startsWith("resp ")) {
						mLogger.log(LOG_DEBUG, "Stray response: " + resp.substring("log ".length()));
					} else {
						Log.e(TAG, "Unexpected response: " + resp);
						isInterrupted = true;
					}
				}
			} catch(Exception e) {
				Log.e(TAG, "Error while reading a command", e);
				isInterrupted = true;
			}
			
			//send pending reports
			for(String report : pendingReports) {
				report(report);
			}
		}
		
		terminateConnection();
	}

	@Override
	protected String getThreadName() {
		return "Usernat";
	}

	@Override
	public void establishConnection(Map<String, Object> options) {
		if(mConnectionStatus != ConnectionStatus.DISCONNECTED) {
			return;
		}
		
		mVpnTunnelCtx = initUsernatTun();
		
		startLoop();
	}

	@Override
	public void terminateConnection() {
		if(mConnectionStatus != ConnectionStatus.TERMINATED) {
			mConnectionStatus = ConnectionStatus.TERMINATED;
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
	
	private void report(String report) {
		Log.d(TAG, "Reported: " + report);
	}
	
	private native long initUsernatTun();
	private native void deinitUsernatTun(long tunctx);
	
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
