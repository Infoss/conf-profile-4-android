package no.infoss.confprofile.vpn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

	public UsernatTunnel(Context ctx, RouterLoop routerLoop, VpnManagerInterface vpnMgr) {
		super(ctx, VPN_CFG_INFO);
		mVpnServiceCtx = routerLoop.getRouterCtx();
		mVpnMgr = vpnMgr;
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
		List<String> args = new LinkedList<String>();
		args.add(cacheDir.getAbsolutePath() + "/" + UsernatWorker.USERNAT);
		
		// Could take a while to open connection
        int tries=8;

        String socketName = (new File(mCtx.getCacheDir(), "mgmtsocket")).getAbsolutePath();
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

		try {
			mSocket = mServerSocket.accept();
			InputStream instream = mSocket.getInputStream();
            mServerSocket.close();

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
					Log.d(socketName, new String(buffer, 0, msgLen));
					prologueMode = true;
				} else {
					pos += numbytesread;
				}
			}
		} catch (IOException e) {
            if (!e.getMessage().equals("socket closed"))
                VpnStatus.logException(e);
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
	
	private native long initUsernatTun();
	private native void deinitUsernatTun(long tunctx);
}
