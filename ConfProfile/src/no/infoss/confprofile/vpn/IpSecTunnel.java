package no.infoss.confprofile.vpn;

import java.io.File;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import no.infoss.confprofile.crypto.CertificateManager;
import no.infoss.confprofile.util.NetUtils;
import no.infoss.confprofile.vpn.IpSecVpnStateService.ErrorState;
import no.infoss.confprofile.vpn.IpSecVpnStateService.State;
import no.infoss.confprofile.vpn.VpnManagerService.VpnConfigInfo;
import no.infoss.confprofile.vpn.ipsec.imc.ImcState;
import no.infoss.confprofile.vpn.ipsec.imc.RemediationInstruction;
import android.content.Context;
import android.security.KeyChainException;
import android.util.Log;

public class IpSecTunnel extends VpnTunnel {
	
	public static final String TAG = IpSecTunnel.class.getSimpleName();
	public static final String VPN_TYPE = "IPSec";

	public static final boolean BYOD = false;

	private boolean mIsTerminating;
	private Map<String, Object> mOptions;
	private IpSecVpnStateService mService;
	private final Object mServiceLock = new Object();	
	public static final String LOG_FILE = "charon.log";
	private String mLogFile;
	private volatile String mCurrentCertificateAlias;
	private volatile String mCurrentUserCertificateAlias;
	private VpnManagerInterface mVpnMgr;
	
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
	
	public IpSecTunnel(Context ctx, long vpnServiceCtx, VpnManagerInterface vpnMgr, VpnConfigInfo cfg) {
		super(ctx, cfg);
		mVpnServiceCtx = vpnServiceCtx;
		mVpnMgr = vpnMgr;
		mIsTerminating = false;
	}

	@Override
	public void run() {
		while (true) {
			synchronized (this) {
				try {
					if (!mIsTerminating) {
						startConnection();

						if (initializeCharon(mLogFile, getEnableBYOD(), mVpnTunnelCtx)) {
							Log.i(TAG, "charon started");
							initiate(getIdentifier(), getRemoteAddress(), getUsername(),getPassword());
						} else {
							Log.e(TAG, "failed to start charon");
							setError(ErrorState.GENERIC_ERROR);
							terminateConnection();
						}
					} else {
						setState(State.DISCONNECTING);
						deinitializeCharon();
						
						setState(State.DISABLED);
						Log.i(TAG, "ipsec stopped");
						break;
					}

					wait();
				} catch (InterruptedException ex) {
					terminateConnection();
					setState(State.DISABLED);
				}
			}
		}		
	}

	@Override
	protected String getThreadName() {
		return VPN_TYPE;
	}

	@Override
	public void establishConnection(Map<String, Object> options) {
		if(mIsTerminating) {
			return;
		}

		mLogFile = getContext().getFilesDir().getAbsolutePath() + File.separator + LOG_FILE;
		
		mOptions = new HashMap<String, Object>(); 
		if(options.containsKey(VpnConfigInfo.PARAMS_IPSEC)) {
			@SuppressWarnings("unchecked")
			Map<String, Object> tmpMap = (Map<String, Object>) options.get(VpnConfigInfo.PARAMS_IPSEC);
			mOptions.putAll(tmpMap);
		}
		
		//TODO: implement the following
		//AuthenticationMethod - Certificate or SharedSecret
		//XAuthEnabled - 0 / 1
		
		mOptions.put("identifier", new String("ikev1-cert-xauth"));

		mVpnTunnelCtx = initIpSecTun();
	
		startLoop();
	}

	@Override
	public void terminateConnection() {
		mIsTerminating = true;
		notifyAll();
		if(mVpnTunnelCtx != 0) {
			deinitIpSecTun(mVpnTunnelCtx);
			mVpnTunnelCtx = 0;
		}
	}

	public void onNetworkChanged(boolean disconnected) {
		networkChanged(disconnected);
	}
	
	/**
	 * Notify the state service about a new connection attempt.
	 * Called by the handler thread.
	 *
	 * @param profile currently active VPN profile
	 */
	private void startConnection() {
		synchronized (mServiceLock) {
			if (mService != null) {
				mService.startConnection(mOptions);
			}
		}
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
		return result;
	}

	private String getUsername() {
		String result = null; 
		if(mOptions != null) {
			result = (String)mOptions.get("user");
		}
		Log.d(TAG, "user=" + String.valueOf(result));
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
	 * Update the current VPN state on the state service. Called by the handler
	 * thread and any of charon's threads.
	 *
	 * @param state current state
	 */
	private void setState(State state) {
		synchronized (mServiceLock) {
			if (mService != null) {
				mService.setState(state);
			}
		}
	}

	/**
	 * Set an error on the state service. Called by the handler thread and any
	 * of charon's threads.
	 *
	 * @param error error state
	 */
	private void setError(ErrorState error) {
		synchronized (mServiceLock) {
			if (mService != null) {
				mService.setError(error);
			}
		}
	}

	/**
	 * Set the IMC state on the state service. Called by the handler thread and
	 * any of charon's threads.
	 *
	 * @param state IMC state
	 */
	private void setImcState(ImcState state) {
		synchronized (mServiceLock) {
			if (mService != null) {
				mService.setImcState(state);
			}
		}
	}
	
	/**
	 * Set an error on the state service and disconnect the current connection.
	 * This is not done by calling stopCurrentConnection() above, but instead
	 * is done asynchronously via state service.
	 *
	 * @param error error state
	 */
	private void setErrorDisconnect(ErrorState error) {
		synchronized (mServiceLock) {
			if (mService != null) {
				if (!mIsTerminating) {
					mService.setError(error);
					terminateConnection();
				}
			}
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
				setState(State.CONNECTED);
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
	 * Add a remediation instruction to the VPN state service.
	 * Called via JNI by different threads (but not concurrently).
	 *
	 * @param xml XML text
	 */
	public void addRemediationInstruction(String xml) {
		for (RemediationInstruction instruction : RemediationInstruction.fromXml(xml)) {
			synchronized (mServiceLock) {
				if (mService != null) {
					mService.addRemediationInstruction(instruction);
				}
			}
		}
	}
	
	public boolean protect(int socket) {
		return mVpnMgr.protect(socket);
	}

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
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			throw new InterruptedException();
		} catch (UnrecoverableKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new InterruptedException();
		} catch (KeyStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new InterruptedException();
		}
		
		return result;
	}
	
	private synchronized boolean addAddress(String address, int prefixLength) {
		if(address == null) {
			Log.e(TAG, "Error in addAddress(): trying to add null");
			return false;
		}
		
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
