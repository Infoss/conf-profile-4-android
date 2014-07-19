package no.infoss.confprofile.vpn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import no.infoss.confprofile.util.MiscUtils;
import no.infoss.confprofile.vpn.OpenVpnTunnel.VpnStatus;
import no.infoss.confprofile.vpn.VpnManagerService.VpnConfigInfo;
import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

public class UsernatTunnel extends VpnTunnel {
	public static final String TAG = UsernatTunnel.class.getSimpleName();
	
	private static final VpnConfigInfo VPN_CFG_INFO;
	static {
		VPN_CFG_INFO = new VpnConfigInfo();
	}
	
	private VpnManagerInterface mVpnMgr;
	private Thread mWorkerThread;
	private UsernatWorker mWorker;
	
	private LocalSocket mSocket;
	private LocalServerSocket mServerSocket;
	private LocalSocket mServerSocketLocal;
	
	private ReentrantReadWriteLock mRWLock = new ReentrantReadWriteLock(true);
	private Lock mRLock = mRWLock.readLock();
	private Lock mWLock = mRWLock.writeLock();
	private final SocatTunnelContext mSocatData = new SocatTunnelContext();

	public UsernatTunnel(Context ctx, RouterLoop routerLoop, VpnManagerInterface vpnMgr) {
		super(ctx, VPN_CFG_INFO, vpnMgr);
		mVpnServiceCtx = routerLoop.getRouterCtx();
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
		
		// Could take a while to open connection
        int tries=8;

        
        // The mServerSocketLocal is transferred to the LocalServerSocket, ignore warning

        mServerSocketLocal = new LocalSocket();

        while(tries > 0) {
        	if(mConnectionStatus == ConnectionStatus.TERMINATED) {
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
        	Log.e(TAG, "Error opening management interface");
            VpnStatus.logException(e);
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
		}
		
		boolean isInterrupted = false;
		boolean isCommandMode = false;
		List<String> pendingReports = new LinkedList<String>();
		
		while(!isInterrupted) {
			pendingReports.clear();
			mRLock.lock();
			isCommandMode = mSocatData.isRequestReady() & !mSocatData.isResponseReady();
			mRLock.unlock();
			
			if(isCommandMode) {
				mWLock.lock();
				try {
					String cmd = String.format("socat %s %d", mSocatData.inDstAddr, mSocatData.inDstPort);
					if(writeMessage(outstream, cmd)) {
						//comand successfully was sent
						while(true) {
							String resp = readMessage(instream);
							if(resp.startsWith("terminated ")) {
								//this is just a report about terminated child
								pendingReports.add(resp);
								continue;
							} else if(resp.startsWith("resp ")) {
								mSocatData.outPid = Integer.valueOf(resp.substring("resp ".length()));
							} else if(resp.startsWith("ping")) {
								//just skip this
							} else {
								Log.e(TAG, "Unexpected response: " + resp);
							}
							break;
						} //while(true)
					}
				} catch(Exception e) {
					Log.e(TAG, "Error while reading/writing a command", e);
					mLogger.log(LOG_ERROR, "Error while reading/writing a command: ".concat(e.toString()));
					mSocatData.outPid = -1;
		            isInterrupted = true;
				} finally {
					mSocatData.setResponseReady(true);
					mWLock.unlock();
				}
			} //if(isCommandMode)
			
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
		
		mConnectionStatus = ConnectionStatus.TERMINATED;
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
	protected synchronized int buildSocatTunnel(int fdAccept, int fdConnect, String remoteAddr, int remotePort) {
		mWLock.lock();
		mSocatData.clear();
		mSocatData.inAcceptFd = fdAccept;
		mSocatData.inConnectFd = fdConnect;
		mSocatData.inDstAddr = remoteAddr;
		mSocatData.inDstPort = remotePort;
		mWLock.unlock();
		
		boolean wait = true;
		int pid = -1;
		while(wait) {
			mRLock.lock();
			wait = !mSocatData.isResponseReady();
			pid = mSocatData.outPid;
			mRLock.unlock();
			try {
				Thread.sleep(100);
			} catch(Exception e) {
				//suppress interrupted sleep exception
			}
		}
		
		return pid;
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
				Log.d(TAG, result);
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
			return false;
		}
		
		byte[] msg = message.getBytes();
		if(msg.length > 65535) {
			Log.e(TAG, "Message too long");
			return false;
		}
		
		outstream.write((msg.length >> 8) & 0x00ff);
		outstream.write(msg.length & 0x00ff);
		outstream.write(msg);
		
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
			outPid = -1;
			mIsReady = false;
		}
		
		public boolean isRequestReady() {
			if(inAcceptFd == -1 || inConnectFd == -1 || inDstAddr == null || inDstPort == 0) {
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
