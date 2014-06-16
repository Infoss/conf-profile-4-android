package no.infoss.confprofile.vpn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import no.infoss.confprofile.vpn.IpSecVpnStateService.ErrorState;
import no.infoss.confprofile.vpn.IpSecVpnStateService.State;
import no.infoss.confprofile.vpn.VpnManagerService.VpnConfigInfo;
import no.infoss.confprofile.vpn.ipsec.imc.ImcState;
import no.infoss.confprofile.vpn.ipsec.imc.RemediationInstruction;

import org.apache.http.util.ByteArrayBuffer;

import android.content.Context;
import android.content.res.AssetManager;
import android.security.KeyChainException;
import android.util.Log;

public class IpSecVpnTunnel extends VpnTunnel {
	
	public static final String TAG = IpSecVpnTunnel.class.getSimpleName();
	public static final String VPN_TYPE = "net.strongswan.IpSecVPN-Connect.vpnplugin";

	private static boolean BYOD = false;

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
	
	public IpSecVpnTunnel(Context ctx, long vpnServiceCtx, VpnManagerInterface vpnMgr, VpnConfigInfo cfg) {
		super(ctx, cfg);
		mVpnServiceCtx = vpnServiceCtx;
		mVpnMgr = vpnMgr;
		mIsTerminating = false;
	}

	@Override
	public void run() {
		while (true)
		{
			synchronized (this)
			{
				try
				{
					if (!mIsTerminating) {

						startConnection();

						if (initializeCharon(mLogFile, getEnableBYOD()))
						{
							Log.i(TAG, "charon started");
							initiate(getIdentifier(), getGateway(), getUsername(),getPassword());
						}
						else
						{
							Log.e(TAG, "failed to start charon");
							setError(ErrorState.GENERIC_ERROR);
							terminateConnection();
						}
					}
					else {
						setState(State.DISCONNECTING);
						deinitializeCharon();
						
						setState(State.DISABLED);
						Log.i(TAG, "ipsec stopped");
						break;
					}

					wait();
				}
				catch (InterruptedException ex)
				{
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
		
		mOptions = options;
		
		if(mOptions == null) {
			mOptions = new HashMap<String, Object>();
			
			mOptions.put("xauth-secret", new String("IlrmRRgNg8lffSv1"));
			mOptions.put("xauth-identity", new String("311@preprod.ruvpn.net"));
			mOptions.put("gateway", new String("ipsec.ruvpn.mobi"));//ios.ipsec.infoss.no"));
			mOptions.put("identifier", new String("ikev1-cert-xauth"));
			mOptions.put("cert", new String("cert_ipsec_test.pem"));
			mOptions.put("private-key", new String("private_key_ipsec_test.der"));
			mOptions.put("public-key", new String("public_key_ipsec_test.pem"));
			mOptions.put("server-certs", new String[]{"cert1.pem", "cert2.pem"});

/*
			mOptions.put("gateway", new String("litecoding.com"));//"ipsec.ruvpn.mobi"));//ios.ipsec.infoss.no"));
			mOptions.put("identifier", new String("ikev2-cert"));
			mOptions.put("cert", new String("remotecert.pem"));
			mOptions.put("private-key", new String("remotepriv.der"));
			mOptions.put("server-certs", new String[]{"testca.cert"});
*/			
		}
	
		startLoop();
	}

	@Override
	public void terminateConnection() {
		mIsTerminating = true;
		notifyAll();
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
	private void startConnection()
	{
		synchronized (mServiceLock)
		{
			if (mService != null)
			{
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
		return result;
	}

	private String getUsername() {
		String result = null; 
		if(mOptions != null) {
			result = (String)mOptions.get("user");
		}
		return result;
	}

	private String getGateway() {
		String result = null; 
		if(mOptions != null) {
			result = (String)mOptions.get("gateway");
		}
		return result;
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
	private void setState(State state)
	{
		synchronized (mServiceLock)
		{
			if (mService != null)
			{
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
	private void setError(ErrorState error)
	{
		synchronized (mServiceLock)
		{
			if (mService != null)
			{
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
	private void setImcState(ImcState state)
	{
		synchronized (mServiceLock)
		{
			if (mService != null)
			{
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
	private void setErrorDisconnect(ErrorState error)
	{
		synchronized (mServiceLock)
		{
			if (mService != null)
			{
				if (!mIsTerminating)
				{
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
	public void updateStatus(int status)
	{
		switch (status)
		{
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
	public void updateImcState(int value)
	{
		ImcState state = ImcState.fromValue(value);
		if (state != null)
		{
			setImcState(state);
		}
	}
	
	/**
	 * Add a remediation instruction to the VPN state service.
	 * Called via JNI by different threads (but not concurrently).
	 *
	 * @param xml XML text
	 */
	public void addRemediationInstruction(String xml)
	{
		for (RemediationInstruction instruction : RemediationInstruction.fromXml(xml))
		{
			synchronized (mServiceLock)
			{
				if (mService != null)
				{
					mService.addRemediationInstruction(instruction);
				}
			}
		}
	}
	
	public boolean protect (int socket) {
		return mVpnMgr.protect(socket);
	}

	/**
	 * Function called via JNI to generate a list of DER encoded CA certificates
	 * as byte array.
	 *
	 * @param hash optional alias (only hash part), if given matching certificates are returned
	 * @return a list of DER encoded CA certificates
	 */
	private byte[][] getTrustedCertificates(String hash)
	{
		ArrayList<byte[]> certs = new ArrayList<byte[]>();
		
		if (mOptions.containsKey("server-certs") == true)
		{
			AssetManager assetMgr = getContext().getAssets();
			String[] files = (String[])mOptions.get("server-certs");
			for(String file: files) {
				X509Certificate cert;
				CertificateFactory cf;
				try {
					cf = CertificateFactory.getInstance("X509");
					InputStream is = assetMgr.open("certs/" + file);
					cert = (X509Certificate) cf.generateCertificate(is);
					certs.add(cert.getEncoded());
				} catch (CertificateException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
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
	private byte[][] getUserCertificate() throws KeyChainException, InterruptedException, CertificateEncodingException
	{
		if (!mOptions.containsKey("cert")) return null;
		
		ArrayList<byte[]> encodings = new ArrayList<byte[]>();
		
		AssetManager assetMgr = getContext().getAssets();

		CertificateFactory cf;
		try {
			cf = CertificateFactory.getInstance("X509");
			InputStream is = assetMgr.open("certs/" + (String) mOptions.get("cert"));
			X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
			encodings.add(cert.getEncoded());
		} catch (CertificateException e) {
			e.printStackTrace();
			throw new InterruptedException();	
		} catch (IOException e) {
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
	private PrivateKey getUserKey() throws InterruptedException
	{
		if (!mOptions.containsKey("private-key")) return null;
		
		PrivateKey result = null;
		AssetManager assetMgr = getContext().getAssets();
	
		ByteArrayBuffer bb = new ByteArrayBuffer(1024);
		try {
			int av = 0;
			int off = 0;
			InputStream stream = assetMgr.open("certs/" + (String) mOptions.get("private-key"));
			while((av = stream.available())>0) {
				byte[] buffer = new byte[av];
				int len = stream.read(buffer);
				bb.append(buffer, off, len);
				off += len;
			}
		
			KeySpec p_key = new PKCS8EncodedKeySpec(bb.buffer());
		
			KeyFactory kf = KeyFactory.getInstance("RSA");
			result = kf.generatePrivate(p_key);
			
		} catch (IOException e) {
			e.printStackTrace();
			throw new InterruptedException();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			throw new InterruptedException();
		} catch (InvalidKeySpecException e) {
			e.printStackTrace();
			throw new InterruptedException();
		}
		
		return result;
	}
	
	private synchronized boolean addAddress(String address, int prefixLength)
	{
		//TODO
		try
		{
			//mBuilder.addAddress(address, prefixLength);
		}
		catch (IllegalArgumentException ex)
		{
			return false;
		}
		return true;
	}
	
	private synchronized boolean addRoute(String address, int prefixLength)
	{
		//TODO
		try
		{
			//mBuilder.addRoute(address, prefixLength);
		}
		catch (IllegalArgumentException ex)
		{
			return false;
		}
		return true;
	}
	
	private String getXAuthIdentity() {
		return (String)mOptions.get("xauth-identity");
	}

	private String getXAuthSecret() {
		return (String)mOptions.get("xauth-secret");
	}
	
	private native boolean initializeCharon(String logfile, boolean byod);
	private native void deinitializeCharon();
	private native void initiate(String type, String gateway, String username, String password);	
	private native void networkChanged(boolean disconnected);
	
	static {
		System.loadLibrary("strongswan");

		if(BYOD) {
			System.loadLibrary("tncif");
			System.loadLibrary("tnccs");
			System.loadLibrary("imcv");
			System.loadLibrary("pts");
		}
		
		System.loadLibrary("hydra");
		System.loadLibrary("charon");
		System.loadLibrary("ipsec");
		
		System.loadLibrary("strongswanbridge");
	}
}
