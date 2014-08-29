package no.infoss.confprofile.vpn;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

@Deprecated
public class VpnSocketFactory extends SocketFactory {
	public static final String TAG = VpnSocketFactory.class.getSimpleName();
	
	private Context mCtx;
	private SocketFactory mWrapped;
	private boolean mIsBound;
	private VpnManagerInterface mVpnMgr;
	private final Object mVpnMgrLock = new Object();
	private ServiceConnection mServiceConnection = new ServiceConnection() {
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
			mIsBound = false;
			synchronized (mVpnMgrLock) {
				mVpnMgr = null;
			}
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			synchronized (mVpnMgrLock) {
				mVpnMgr = (VpnManagerInterface) service.queryLocalInterface(VpnManagerInterface.TAG);
			}
		}
	};
	
	public VpnSocketFactory(Context ctx) {
		this(ctx, null);
	}
	
	public VpnSocketFactory(Context ctx, SocketFactory wrapped) {
		mCtx = ctx;
		mIsBound = false;
		
		if(wrapped == null) {
			mWrapped = SocketFactory.getDefault();
		} else {
			mWrapped = wrapped;
		}
		
		bindService();
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException,
			UnknownHostException {
		Socket result = mWrapped.createSocket(host, port);
		boolean ready = false;
		
		synchronized(mVpnMgrLock) {
			if(mVpnMgr != null && mVpnMgr.protect(result)) {
				ready = true;
			}
		}
		
		if(!ready) {
			throw new IOException("Can't protect socket: " + String.valueOf(result));
		}
		
		return result;
	}

	@Override
	public Socket createSocket(String host, 
			int port, 
			InetAddress localHost,
			int localPort) 
					throws IOException, UnknownHostException {
		Socket result = mWrapped.createSocket(host, port, localHost, localPort);
		boolean ready = false;
		
		synchronized(mVpnMgrLock) {
			if(mVpnMgr != null && mVpnMgr.protect(result)) {
				ready = true;
			}
		}
		
		if(!ready) {
			throw new IOException("Can't protect socket: " + String.valueOf(result));
		}
		
		return result;
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		Socket result = mWrapped.createSocket(host, port);
		boolean ready = false;
		
		synchronized(mVpnMgrLock) {
			if(mVpnMgr != null && mVpnMgr.protect(result)) {
				ready = true;
			}
		}
		
		if(!ready) {
			throw new IOException("Can't protect socket: " + String.valueOf(result));
		}
		
		return result;
	}

	@Override
	public Socket createSocket(InetAddress address, 
			int port,
			InetAddress localAddress, 
			int localPort) 
					throws IOException {
		Socket result = mWrapped.createSocket(address, port, localAddress, localPort);
		boolean ready = false;
		
		synchronized(mVpnMgrLock) {
			if(mVpnMgr != null && mVpnMgr.protect(result)) {
				ready = true;
			}
		}
		
		if(!ready) {
			throw new IOException("Can't protect socket: " + String.valueOf(result));
		}
		
		return result;
	}

	private void bindService() {
		if(!mIsBound) {
			Intent intent = new Intent(mCtx, VpnManagerService.class);
			mIsBound = mCtx.bindService(intent, mServiceConnection, 0);
		}
	}
}
