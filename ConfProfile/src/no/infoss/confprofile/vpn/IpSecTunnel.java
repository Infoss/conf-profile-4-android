package no.infoss.confprofile.vpn;

import java.io.File;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import no.infoss.confprofile.crypto.CertificateManager;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.util.NetUtils;
import no.infoss.confprofile.vpn.ipsec.imc.ImcState;
import no.infoss.confprofile.vpn.ipsec.imc.RemediationInstruction;
import android.content.Context;
import android.security.KeyChainException;
import android.util.Log;

public class IpSecTunnel extends VpnTunnel {
	public static final String TAG = IpSecTunnel.class.getSimpleName();
	public static final String VPN_TYPE = "IPSec";

	public static final boolean BYOD = false;

	private Map<String, Object> mGlobalOptions;	
	private Map<String, Object> mOptions;	
	public static final String LOG_FILE = "charon.log";
	private String mLogFile;
	private volatile String mCurrentCertificateAlias;
	private volatile String mCurrentUserCertificateAlias;
	private volatile int mDnsIdx = 0;
	
	/**
	 * as defined in charonservice.h
	 */
	static final int STATE_CHILD_SA_UP = 1;
	static final int STATE_CHILD_SA_DOWN = 2;
	static final int STATE_AUTH_ERROR = 3;
	static final int STATE_PEER_AUTH_ERROR = 4;
	static final int STATE_LOOKUP_ERROR = 5;
	static final int STATE_UNREACHABLE_ERROR = 6;
	static final int STATE_GENERIC_ERROR = 7;
	
	/*from IpSecVpnStateService*/
	private long mConnectionID = 0;
	private ErrorState mError = ErrorState.NO_ERROR;
	private ImcState mImcState = ImcState.UNKNOWN;
	private final LinkedList<RemediationInstruction> mRemediationInstructions = new LinkedList<RemediationInstruction>();

	public enum ErrorState {
		NO_ERROR,
		AUTH_FAILED,
		PEER_AUTH_FAILED,
		LOOKUP_FAILED,
		UNREACHABLE,
		GENERIC_ERROR,
	}
	
	/**
	 * Listener interface for bound clients that are interested in changes to
	 * this Service.
	 */
	
	public IpSecTunnel(Context ctx, VpnManagerInterface vpnMgr, String uuid, String cfg) {
		super(ctx, uuid, cfg, vpnMgr);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		mGlobalOptions = doParseCredentials();
		
		if(mGlobalOptions.containsKey(VpnPayload.KEY_IPSEC)) {
			mOptions = (Map<String, Object>) mGlobalOptions.get(VpnPayload.KEY_IPSEC);
		}
		
		//TODO: implement the following
		//AuthenticationMethod - Certificate or SharedSecret
		//XAuthEnabled - 0 / 1
		
		mOptions.put("identifier", new String("ikev1-cert-xauth"));	
		
		synchronized(this) {
			mDnsIdx = 0;
			startConnection();
			if(!initializeCharon(mLogFile, getEnableBYOD(), mVpnTunnelCtx)) {
				Log.e(TAG, "failed to start charon, exiting");
				terminateConnection();
				return;
			} else {
				Log.i(TAG, "charon started");
				initiate(getIdentifier(), getRemoteAddress(), getUsername(), getPassword());
				
				while(!isTerminated()) {
					try {
						wait();
					} catch(InterruptedException e) {
						//nothing to do here
					}
				}
			}
		}
		
		terminateConnection();
		deinitializeCharon();
		Log.i(TAG, "Connection " + getTunnelId() + " terminated");
	}

	@Override
	protected String getThreadName() {
		return VPN_TYPE;
	}

	@Override
	protected void doEstablishConnection() {
		mLogFile = getContext().getFilesDir().getAbsolutePath() + File.separator + LOG_FILE;
		mVpnTunnelCtx = initIpSecTun();
		startLoop();
	}

	@Override
	public void terminateConnection() {
		if(!isTerminated()) {
			setConnectionStatus(ConnectionStatus.TERMINATED);
			
			if(mVpnTunnelCtx != 0) {
				mVpnMgr.intlRemoveTunnel(this);
				deinitIpSecTun(mVpnTunnelCtx);
				mVpnTunnelCtx = 0;
			}
		}
		
		synchronized(this) {
			notifyAll();
		}
		
	}

	public void onNetworkChanged(boolean disconnected) {
		networkChanged(disconnected);
	}
	
	private void startConnection() {
		notifyListeners(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception
			{
				IpSecTunnel.this.mConnectionID++;
				IpSecTunnel.this.setConnectionStatus(ConnectionStatus.CONNECTING);
				IpSecTunnel.this.mError = ErrorState.NO_ERROR;
				IpSecTunnel.this.mImcState = ImcState.UNKNOWN;
				IpSecTunnel.this.mRemediationInstructions.clear();
				return true;
			}
		});
	}
	
	private boolean getEnableBYOD() {
		boolean result = BYOD; 
		if(mOptions != null) {
			Boolean byod = (Boolean)mOptions.get("byod");
			if(byod != null) result = byod;
		}
		return result;
	}
	
	private String getPassword() {
		String result = null; 
		if(mOptions != null) {
			result = (String)mOptions.get("password");
		}
		Log.d(TAG, "password=" + String.valueOf(result));
		if(result == null) {
			result = "<null>";
		}
		return result;
	}

	private String getUsername() {
		String result = null; 
		if(mOptions != null) {
			result = (String)mOptions.get("user");
		}
		Log.d(TAG, "user=" + String.valueOf(result));
		if(result == null) {
			result = "<null>";
		}
		return result;
	}

	private String getRemoteAddress() {
		Log.d(TAG, "RemoteAddress=" + String.valueOf(mOptions.get("RemoteAddress")));
		return (String) mOptions.get("RemoteAddress");
	}

	private String getIdentifier() {
		String result = null; 
		if(mOptions != null) {
			result = (String)mOptions.get("identifier");
		}
		return result;
	}
	
	/**
	 * Update the state and notify all listeners, if changed. 
	 * Called by the handler thread and any of charon's threads.
	 *
	 * @param state current state
	 */
	private void setState(final ConnectionStatus state) {
		notifyListeners(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return IpSecTunnel.this.setConnectionStatus(state);
			}
		});
	}

	/**
	 * Set the current error state and notify all listeners, if changed. 
	 * Called by the handler thread and any of charon's threads.
	 *
	 * @param error error state
	 */
	private void setError(final ErrorState error) {
		notifyListeners(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				if (IpSecTunnel.this.mError != error) {
					IpSecTunnel.this.mError = error;
					return true;
				}
				return false;
			}
		});
	}

	/**
	 * Set the current IMC state and notify all listeners, if changed. 
	 * Setting the state to UNKNOWN clears all remediation instructions.
	 * Called by the handler thread and any of charon's threads.
	 *
	 * @param state IMC state
	 */
	private void setImcState(final ImcState state) {
		notifyListeners(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				if (state == ImcState.UNKNOWN) {
					IpSecTunnel.this.mRemediationInstructions.clear();
				}
				
				if (IpSecTunnel.this.mImcState != state) {
					IpSecTunnel.this.mImcState = state;
					return true;
				}
				return false;
			}
		});
	}
	
	/**
	 * Set an error on the state service and disconnect the current connection.
	 * This is not done by calling stopCurrentConnection() above, but instead
	 * is done asynchronously via state service.
	 *
	 * @param error error state
	 */
	private void setErrorDisconnect(ErrorState error) {
		if(!isTerminated()) {
			setError(error);
			terminateConnection();
		}
	}
	
	/**
	 * Updates the state of the current connection.
	 * Called via JNI by different threads (but not concurrently).
	 *
	 * @param status new state
	 */
	public void updateStatus(int status) {
		switch (status) {
			case STATE_CHILD_SA_DOWN:
				/* we ignore this as we use closeaction=restart */
				break;
			case STATE_CHILD_SA_UP:
				setState(ConnectionStatus.CONNECTED);
				break;
			case STATE_AUTH_ERROR:
				setErrorDisconnect(ErrorState.AUTH_FAILED);
				break;
			case STATE_PEER_AUTH_ERROR:
				setErrorDisconnect(ErrorState.PEER_AUTH_FAILED);
				break;
			case STATE_LOOKUP_ERROR:
				setErrorDisconnect(ErrorState.LOOKUP_FAILED);
				break;
			case STATE_UNREACHABLE_ERROR:
				setErrorDisconnect(ErrorState.UNREACHABLE);
				break;
			case STATE_GENERIC_ERROR:
				setErrorDisconnect(ErrorState.GENERIC_ERROR);
				break;
			default:
				Log.e(TAG, "Unknown status code received");
				break;
		}
	}
	
	/**
	 * Updates the IMC state of the current connection.
	 * Called via JNI by different threads (but not concurrently).
	 *
	 * @param value new state
	 */
	public void updateImcState(int value) {
		ImcState state = ImcState.fromValue(value);
		if (state != null) {
			setImcState(state);
		}
	}
	
	/**
	 * Add the given remediation instruction to the internal list. 
	 * Listeners are not notified.
	 * 
	 * Instructions are cleared if the IMC state is set to UNKNOWN.
	 * 
	 * Called via JNI by different threads (but not concurrently).
	 *
	 * @param xml XML text
	 */
	public void addRemediationInstruction(String xml) {
		for (final RemediationInstruction instruction : RemediationInstruction.fromXml(xml)) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					IpSecTunnel.this.mRemediationInstructions.add(instruction);
				}
			});
		}
	}
	
	/*
	public boolean protect(int socket) {
		return mVpnMgr.protect(socket);
	}
	*/

	/**
	 * Function called via JNI to generate a list of DER encoded CA certificates
	 * as byte array.
	 *
	 * @param hash optional alias (only hash part), if given matching certificates are returned
	 * @return a list of DER encoded CA certificates
	 */
	private byte[][] getTrustedCertificates(String hash) {
		ArrayList<byte[]> certs = new ArrayList<byte[]>();
		CertificateManager certMan = CertificateManager.getManagerSync(mCtx, CertificateManager.MANAGER_INTERNAL);
		Map<String, Certificate> cas = certMan.getCertificates();
		for(Entry<String, Certificate> certEntry: cas.entrySet()) {
			Log.d(TAG, "Fetching certificate " + certEntry.getKey());
			try {
				certs.add(certEntry.getValue().getEncoded());
			} catch(Exception e) {
				Log.e(TAG, "Error while fetching certificate", e);
			}
		}
		
		return certs.toArray(new byte[certs.size()][]);
	}

	/**
	 * Function called via JNI to get a list containing the DER encoded certificates
	 * of the user selected certificate chain (beginning with the user certificate).
	 *
	 * Since this method is called from a thread of charon's thread pool we are safe
	 * to call methods on KeyChain directly.
	 *
	 * @return list containing the certificates (first element is the user certificate)
	 * @throws InterruptedException
	 * @throws KeyChainException
	 * @throws CertificateEncodingException
	 */
	private byte[][] getUserCertificate() throws KeyChainException, InterruptedException, CertificateEncodingException {
		String userCertUuid = (String) mOptions.get("PayloadCertificateUUID");
		
		CertificateManager certMgr = CertificateManager.getManagerSync(mCtx, CertificateManager.MANAGER_INTERNAL);
		ArrayList<byte[]> encodings = new ArrayList<byte[]>();
		
		try {
			Certificate[] certs = certMgr.getCertificateChain(userCertUuid);
			for(Certificate cert : certs) {
				encodings.add(cert.getEncoded());
			}
		} catch (KeyStoreException e) {
			e.printStackTrace();
			throw new InterruptedException();	
		} catch (CertificateException e) {
			e.printStackTrace();
			throw new InterruptedException();	
		}
		
		return encodings.toArray(new byte[encodings.size()][]);
	}
	
	/**
	 * Function called via JNI to get the private key the user selected.
	 *
	 * Since this method is called from a thread of charon's thread pool we are safe
	 * to call methods on KeyChain directly.
	 *
	 * @return the private key
	 * @throws InterruptedException
	 * @throws KeyChainException
	 * @throws CertificateEncodingException
	 */
	private PrivateKey getUserKey() throws InterruptedException {
		String userCertUuid = (String) mOptions.get("PayloadCertificateUUID");
		
		CertificateManager certMgr = CertificateManager.getManagerSync(mCtx, CertificateManager.MANAGER_INTERNAL);
		
		PrivateKey result = null;

		try {
			result = (PrivateKey) certMgr.getKey(userCertUuid);
		} catch (Exception e) {
			mLogger.logException(LOG_ERROR, "Can't get a key from internal certificate manager", e);
			throw new InterruptedException(); 
		}
		
		return result;
	}
	
	private synchronized boolean addAddress(String address, int prefixLength) {
		if(address == null) {
			Log.e(TAG, "Error in addAddress(): trying to add null");
			return false;
		}
		
		setLocalAddress(address);
		
		Log.d(TAG, "addAddress(): " + address + "/" + prefixLength);
		try {
			if(address.contains(":")) {
				//IPv6
				setMasqueradeIp6(NetUtils.ip6StrToBytes(address, null));
				setMasqueradeIp6Mode(true);
			} else {
				setMasqueradeIp4(NetUtils.ip4StrToInt(address));
				setMasqueradeIp4Mode(true);
			}
		} catch(Exception e) {
			Log.e(TAG, "Error while adding address", e);
			return false;
		}
		
		return true;
	}
	
	private synchronized boolean addRoute(String address, int prefixLength) {
		Log.d(TAG, "addRoute(): " + address + "/" + prefixLength);
		//TODO
		try {
			//mBuilder.addRoute(address, prefixLength);
		} catch (IllegalArgumentException ex) {
			return false;
		}
		return true;
	}
	
	private synchronized boolean addDns(String address) {
		Log.d(TAG, "addDns(): " + address);
		try {
			setDnsAddress(mDnsIdx, NetUtils.ip4StrToInt(address));
			mDnsIdx++;
		} catch (IllegalArgumentException ex) {
			mLogger.logException(LOG_WARN, 
					String.format("IpSecTunnel.addDns(): Can't set dns address: %s", address), 
					ex);
			return false;
		} catch (Exception e) {
			mLogger.logException(LOG_WARN, 
					String.format("IpSecTunnel.addDns(): Invalid dns address: %s", address), 
					e);
			return false;
		}
		return true;
	}
	
	private String getXAuthName() {
		return (String) mOptions.get("XAuthName");
	}

	private String getXAuthPassword() {
		return (String) mOptions.get("XAuthPassword");
	}
	
	private native boolean initializeCharon(String logfile, boolean byod, long tunCtx);
	private native void deinitializeCharon();
	private native void initiate(String type, String remoteAddr, String username, String password);	
	private native void networkChanged(boolean disconnected);
	private native long initIpSecTun();
	private native void deinitIpSecTun(long tunCtx);
}
