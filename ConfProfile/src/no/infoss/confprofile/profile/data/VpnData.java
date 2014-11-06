package no.infoss.confprofile.profile.data;

public class VpnData {
	private String mProfileId;
	private String mPayloadUuid;
	private String mUserDefinedName;
	private boolean mOverridePrimary;
	private boolean mOnDemandEnabled;
	private boolean mOnDemandEnabledByUser;
	private String mOnDemandRules;
	private String mOnDemandCredentials;
	private String mRemoteServer;
	private String mLogin;
	private String mPassword;
	private boolean mRsaSecurid;
	private String mCertificate;
	private String mSharedSecret;
	private String mPptpEncryption;
	private String mIpsecGroupName;
	private String mVpnType;
	
	public VpnData() {
		super();
	}
	
	public VpnData(VpnData data) {
		this();
		
		this.mProfileId = data.mProfileId;
		this.mPayloadUuid = data.mPayloadUuid;
		this.mUserDefinedName = data.mUserDefinedName;
		this.mOverridePrimary = data.mOverridePrimary;
		this.mOnDemandEnabled = data.mOnDemandEnabled;
		this.mOnDemandEnabledByUser = data.mOnDemandEnabledByUser;
		this.mOnDemandRules = data.mOnDemandRules;
		this.mOnDemandCredentials = data.mOnDemandCredentials;
		this.mRemoteServer = data.mRemoteServer;
		this.mLogin = data.mLogin;
		this.mPassword = data.mPassword;
		this.mRsaSecurid = data.mRsaSecurid;
		this.mCertificate = data.mCertificate;
		this.mSharedSecret = data.mSharedSecret;
		this.mPptpEncryption = data.mPptpEncryption;
		this.mIpsecGroupName = data.mIpsecGroupName;
		this.mVpnType = data.mVpnType;
	}
	
	public String getProfileId() {
		return mProfileId;
	}
	
	public void setProfileId(String profileId) {
		this.mProfileId = profileId;
	}
	
	public String getPayloadUuid() {
		return mPayloadUuid;
	}
	
	public void setPayloadUuid(String payloadUuid) {
		this.mPayloadUuid = payloadUuid;
	}
	
	public String getUserDefinedName() {
		return mUserDefinedName;
	}
	
	public void setUserDefinedName(String userDefinedName) {
		this.mUserDefinedName = userDefinedName;
	}
	
	public boolean isOverridePrimary() {
		return mOverridePrimary;
	}
	
	public void setOverridePrimary(boolean overridePrimary) {
		this.mOverridePrimary = overridePrimary;
	}
	
	public boolean isOnDemandEnabled() {
		return mOnDemandEnabled;
	}
	
	public void setOnDemandEnabled(boolean onDemandEnabled) {
		this.mOnDemandEnabled = onDemandEnabled;
	}
	
	public boolean isOnDemandEnabledByUser() {
		return mOnDemandEnabledByUser;
	}
	
	public void setOnDemandEnabledByUser(boolean onDemandEnabledByUser) {
		this.mOnDemandEnabledByUser = onDemandEnabledByUser;
	}
	
	public String getOnDemandRules() {
		return mOnDemandRules;
	}

	public void setOnDemandRules(String onDemandRules) {
		this.mOnDemandRules = onDemandRules;
	}

	public String getOnDemandCredentials() {
		return mOnDemandCredentials;
	}

	public void setOnDemandCredentials(String onDemandCredentials) {
		this.mOnDemandCredentials = onDemandCredentials;
	}

	public String getRemoteServer() {
		return mRemoteServer;
	}

	public void setRemoteServer(String remoteServer) {
		this.mRemoteServer = remoteServer;
	}

	public String getLogin() {
		return mLogin;
	}

	public void setLogin(String login) {
		this.mLogin = login;
	}

	public String getPassword() {
		return mPassword;
	}

	public void setPassword(String password) {
		this.mPassword = password;
	}

	public boolean isRsaSecurid() {
		return mRsaSecurid;
	}

	public void setRsaSecurid(boolean rsaSecurid) {
		this.mRsaSecurid = rsaSecurid;
	}

	public String getCertificate() {
		return mCertificate;
	}

	public void setCertificate(String certificate) {
		this.mCertificate = certificate;
	}

	public String getSharedSecret() {
		return mSharedSecret;
	}

	public void setSharedSecret(String sharedSecret) {
		this.mSharedSecret = sharedSecret;
	}

	public String getPptpEncryption() {
		return mPptpEncryption;
	}

	public void setPptpEncryption(String pptpEncryption) {
		this.mPptpEncryption = pptpEncryption;
	}

	public String getIpsecGroupName() {
		return mIpsecGroupName;
	}

	public void setIpsecGroupName(String ipsecGroupName) {
		this.mIpsecGroupName = ipsecGroupName;
	}

	public String getVpnType() {
		return mVpnType;
	}

	public void setVpnType(String vpnType) {
		this.mVpnType = vpnType;
	}

}
