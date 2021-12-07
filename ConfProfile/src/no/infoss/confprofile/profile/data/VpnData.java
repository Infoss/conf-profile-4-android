package no.infoss.confprofile.profile.data;

import android.content.ContentValues;
import android.database.Cursor;
import no.infoss.confprofile.entity.AppEntity;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.profile.VpnDataCursorLoader;
import no.infoss.confprofile.util.ConfigUtils;

public class VpnData extends AppEntity {
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
		clear();
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

	@Override
	public void mapCursor(Cursor cursor) {
		clear();
		
		mProfileId = cursor.getString(0);
		mPayloadUuid = cursor.getString(1);
		mUserDefinedName = cursor.getString(2);
		mOverridePrimary = cursor.getInt(3) != 0;
		mOnDemandEnabled = cursor.getInt(4) != 0;
		mOnDemandEnabledByUser = cursor.getInt(5) != 0;
		mOnDemandRules = cursor.getString(6);
		mOnDemandCredentials = cursor.getString(7);
		mRemoteServer = cursor.getString(8);
		mLogin = cursor.getString(9);
		mPassword = cursor.getString(10);
		mRsaSecurid = cursor.getInt(11) != 0;
		mCertificate = cursor.getString(12);
		mSharedSecret = cursor.getString(13);
		mPptpEncryption = cursor.getString(14);
		mIpsecGroupName = cursor.getString(15);
		mVpnType =cursor.getString(16);
	}

	@Override
	public void mapPayload(String profileId, Payload payload) {
		clear();
		
		if(payload == null || !(payload instanceof VpnPayload)) {
			mProfileId = profileId;
			return;
		}
		
		VpnPayload vpnPayload = (VpnPayload) payload;
		
		int vpnTypeCode = ConfigUtils.getVpnTypeCode(vpnPayload); 
		String vpnType = vpnPayload.getVpnType();
		if(VpnPayload.VPN_TYPE_CUSTOM.equals(vpnType)) {
			vpnType = vpnPayload.getVpnSubType();
		}
		
		//Apple violates own specs, so we should merge dictionaries here
		Dictionary vpnDict = ConfigUtils.mergeDicts(
				vpnPayload.getIpv4(), //lowest priority
				vpnPayload.getPpp(),
				vpnPayload.getIpsec(),
				vpnPayload.getVpn() //highest priority
				);
		
		int overridePrimary = vpnDict.getInteger(VpnPayload.KEY_OVERRIDE_PRIMARY, 0);
		int onDemandEnabled = vpnDict.getInteger(VpnPayload.KEY_ON_DEMAND_ENABLED, 0);
		
		mProfileId = profileId;
		mPayloadUuid = vpnPayload.getPayloadUUID();
		mUserDefinedName = vpnPayload.getUserDefinedName();
		mOverridePrimary = overridePrimary != 0;
		mOnDemandEnabled = onDemandEnabled != 0;
		mOnDemandEnabledByUser = onDemandEnabled != 0; //default
		mOnDemandRules = ConfigUtils.extractOnDemandRules(vpnPayload);
		mOnDemandCredentials = ConfigUtils.extractOnDemandCredentials(vpnPayload);
		
		mRemoteServer= vpnDict.getString("CommRemoteAddress", vpnDict.getString("RemoteAddress"));
		//TODO: fill OpenVPN login/password
		mLogin = vpnDict.getString("AuthName");
		mPassword = vpnDict.getString("AuthPassword");
		
		if(vpnDict.getInteger("XAuthEnabled", 0) != 0) {
			mLogin = vpnDict.getString("XAuthName");
			mPassword = vpnDict.getString("XAuthPassword");
		}

		mRsaSecurid = vpnDict.getInteger("TokenCard", 0) != 0;
		if(VpnPayload.AUTH_METHOD_CERTIFICATE.equals(vpnDict.getString(VpnPayload.KEY_AUTHENTICATION_METHOD))) {
			mCertificate = vpnDict.getString(VpnPayload.KEY_PAYLOAD_CERTIFICATE_UUID);
		} else {
			mSharedSecret = vpnDict.getString("SharedSecret");
		}
		
		
		mPptpEncryption = "none";
		if(vpnDict.getBoolean("CCPEnabled", false)) {
			if(vpnDict.getBoolean("CCPMPPE40Enabled", false)) {
				mPptpEncryption = "auto";
			}
			if(vpnDict.getBoolean("CCPMPPE128Enabled", false)) {
				mPptpEncryption = "max";
			}
		}
		
		mIpsecGroupName = vpnDict.getString("LocalIdentifier");
		
		mVpnType = vpnType;
	}

	@Override
	public ContentValues asContentValues() {
		ContentValues values = new ContentValues();
		values.put(VpnDataCursorLoader.COL_PROFILE_ID, mProfileId);
		values.put(VpnDataCursorLoader.COL_PAYLOAD_UUID, mPayloadUuid);
		values.put(VpnDataCursorLoader.COL_USER_DEFINED_NAME, mUserDefinedName);
		values.put(VpnDataCursorLoader.COL_OVERRIDE_PRIMARY, mOverridePrimary ? 1 : 0);
		values.put(VpnDataCursorLoader.COL_ON_DEMAND_ENABLED, mOnDemandEnabled ? 1 : 0);
		values.put(VpnDataCursorLoader.COL_ON_DEMAND_ENABLED_BY_USER, mOnDemandEnabledByUser ? 1 : 0);
		values.put(VpnDataCursorLoader.COL_ON_DEMAND_RULES, mOnDemandRules);
		values.put(VpnDataCursorLoader.COL_ON_DEMAND_CREDENTIALS, mOnDemandCredentials);
		values.put(VpnDataCursorLoader.COL_REMOTE_SERVER, mRemoteServer);
		values.put(VpnDataCursorLoader.COL_LOGIN, mLogin);
		values.put(VpnDataCursorLoader.COL_PASSWORD, mPassword);
		values.put(VpnDataCursorLoader.COL_RSA_SECURID, mRsaSecurid);
		values.put(VpnDataCursorLoader.COL_CERTIFICATE, mCertificate);
		values.put(VpnDataCursorLoader.COL_SHARED_SECRET, mSharedSecret);
		values.put(VpnDataCursorLoader.COL_PPTP_ENCRYPTION, mPptpEncryption);
		values.put(VpnDataCursorLoader.COL_IPSEC_GROUP_NAME, mIpsecGroupName);
		values.put(VpnDataCursorLoader.COL_VPN_TYPE, mVpnType);
		return values;
	}

	protected void clear() {
		mProfileId = null;
		mPayloadUuid = null;
		mUserDefinedName = null;
		mOverridePrimary = false;
		mOnDemandEnabled = false;
		mOnDemandEnabledByUser = false;
		mOnDemandRules = null;
		mOnDemandCredentials = null;
		mRemoteServer = null;
		mLogin = null;
		mPassword = null;
		mRsaSecurid = false;
		mCertificate = null;
		mSharedSecret = null;
		mPptpEncryption = null;
		mIpsecGroupName = null;
		mVpnType = null;
	}
}
