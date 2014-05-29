package no.infoss.confprofile.format;

import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;

public class VpnPayload extends Payload { 
	public static final String VALUE_PAYLOAD_TYPE = "com.apple.vpn.managed";
	
	public static final String KEY_USER_DEFINED_NAME = "UserDefinedName";
	public static final String KEY_OVERRIDE_PRIMARY = "OverridePrimary";
	public static final String KEY_VPN_TYPE = "VPNType";
	public static final String KEY_VPN_SUB_TYPE = "VPNSubType";
	public static final String KEY_ON_DEMAND_ENABLED = "OnDemandEnabled";
	public static final String KEY_ON_DEMAND_MATCH_DOMAINS_ALWAYS = "OnDemandMatchDomainsAlways";
	public static final String KEY_ON_DEMAND_MATCH_DOMAINS_NEVER = "OnDemandMatchDomainsNever";
	public static final String KEY_ON_DEMAND_MATCH_DOMAINS_ON_RETRY = "OnDemandMatchDomainsOnRetry";
	public static final String KEY_ON_DEMAND_RULES = "OnDemandRules";
	public static final String KEY_VENDOR_CONFIG = "VendorConfig";
	public static final String KEY_AUTHENTICATION_METHOD = "AuthenticationMethod";
	public static final String KEY_PAYLOAD_CERTIFICATE_UUID = "PayloadCertificateUUID";
	
	public static final String KEY_PPP = "PPP";
	public static final String KEY_IPSEC = "IPSec";
	public static final String KEY_VPN = "VPN";
	public static final String KEY_IPv4 = "IPv4";
	
	public static final String IF_TYPE_ETHER = "Ethernet";
	public static final String IF_TYPE_WIFI  = "WiFi";
	public static final String IF_TYPE_CELL  = "Cellular";
	
	public static final String AUTH_METHOD_SHARED_SECRET = "SharedSecret";
	public static final String AUTH_METHOD_CERTIFICATE = "Certificate";
	
	public static final String VPN_TYPE_CUSTOM = "VPN";
	public static final String VPN_TYPE_PPTP   = "PPTP";
	public static final String VPN_TYPE_L2TP   = "L2TP";
	public static final String VPN_TYPE_IPSEC  = "IPSec";
	
	private final String mVpnDictKey;

	public VpnPayload(Dictionary dict) throws ConfigurationProfileException {
		super(dict);
		
		String vpnType = getVpnType();
		if(KEY_VPN.equals(vpnType)) {
			mVpnDictKey = KEY_VPN;
		} else if(KEY_IPSEC.equals(vpnType)) {
			mVpnDictKey = KEY_IPSEC;
		} else if(KEY_PPP.equals(vpnType)) {
			mVpnDictKey = KEY_PPP;
		} else {
			mVpnDictKey = null;
		}
	}
	
	public Dictionary getPpp() {
		return mDict.getDictionary(KEY_PPP);
	}
	
	public Dictionary getIpsec() {
		return mDict.getDictionary(KEY_IPSEC);
	}
	
	public Dictionary getVpn() {
		return mDict.getDictionary(KEY_VPN);
	}
	
	public Dictionary getIpv4() {
		return mDict.getDictionary(KEY_IPv4);
	}
	
	public String getUserDefinedName() {
		return getPayloadContentAsDictionary().getString(KEY_USER_DEFINED_NAME);
	}
	
	public boolean isOverridePrimary() {
		return getPayloadContentAsDictionary().getBoolean(KEY_OVERRIDE_PRIMARY, false);
	}
	
	public String getVpnType() {
		return mDict.getString(KEY_VPN_TYPE);
	}
	
	public String getVpnSubType() {
		return mDict.getString(KEY_VPN_SUB_TYPE);
	}
	
	public boolean isOnDemandEnabled() {
		return mDict.
				getDictionary(mVpnDictKey).
				getInteger(KEY_ON_DEMAND_ENABLED, 0) == 1;
	}
	
	public Array getOnDemandRules() {
		return mDict.
				getDictionary(mVpnDictKey).
				getArray(KEY_ON_DEMAND_RULES);
	}
	
	public Dictionary getVendorConfig() {
		return mDict.getDictionary(KEY_VENDOR_CONFIG);
	}
}
