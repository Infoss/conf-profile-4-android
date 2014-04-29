package no.infoss.confprofile.format;

import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;

public class VpnPayload extends Payload { 
	public static final String VALUE_PAYLOAD_TYPE = "com.apple.vpn.managed";
	
	public static final String KEY_USER_DEFINED_NAME = "UserDefinedName";
	public static final String KEY_OVERRIDE_PRIMARY = "OverridePrimary";
	public static final String KEY_VPN_TYPE = "VPNType";
	public static final String KEY_ON_DEMAND_ENABLED = "OnDemandEnabled";
	public static final String KEY_ON_DEMAND_MATCH_DOMAINS_ALWAYS = "OnDemandMatchDomainsAlways";
	public static final String KEY_ON_DEMAND_MATCH_DOMAINS_NEVER = "OnDemandMatchDomainsNever";
	public static final String KEY_ON_DEMAND_MATCH_DOMAINS_ON_RETRY = "OnDemandMatchDomainsOnRetry";
	public static final String KEY_ON_DEMAND_RULES = "OnDemandRules";
	public static final String KEY_VENDOR_CONFIG = "VendorConfig";
	
	public static final String KEY_PPP = "PPP";
	public static final String KEY_IPSEC = "IPSec";
	

	public VpnPayload(Dictionary dict) throws ConfigurationProfileException {
		super(dict);
	}
	
	public String getUserDefinedName() {
		return getPayloadContent().getString(KEY_USER_DEFINED_NAME);
	}
	
	public boolean isOverridePrimary() {
		return getPayloadContent().getBoolean(KEY_OVERRIDE_PRIMARY, false);
	}
	
	public String getVpnType() {
		return getPayloadContent().getString(KEY_VPN_TYPE);
	}
	
	public boolean isOnDemandEnabled() {
		return getPayloadContent().getBoolean(KEY_ON_DEMAND_ENABLED, false);
	}
	
	public Array getOnDemandRules() {
		return getPayloadContent().getArray(KEY_ON_DEMAND_RULES);
	}
	
	public Dictionary getVendorConfig() {
		return getPayloadContent().getDictionary(KEY_VENDOR_CONFIG);
	}
}
