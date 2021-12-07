package no.infoss.confprofile.util;

import java.util.HashMap;
import java.util.Map;

import no.infoss.confprofile.R;
import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.format.json.BuiltinTypeAdapterFactory;
import no.infoss.confprofile.vpn.NetworkConfig;
import no.infoss.confprofile.vpn.OpenVpnTunnel;
import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ConfigUtils {
	public static final int VPN_UNKNOWN = 0;
	public static final int VPN_OPENVPN = 1;
	public static final int VPN_IPSEC = 2;
	public static final int VPN_L2TP = 3;
	public static final int VPN_PPTP = 4;
	
	public static NetworkConfig buildNetworkConfig(VpnPayload payload) {
		NetworkConfig config = new NetworkConfig();
		
		String type = payload.getVpnType();
	
		Dictionary rootDict = null;
		if(VpnPayload.KEY_VPN.equals(type)) {
			rootDict = payload.getVpn();
		}
		
		if(rootDict != null) {
			//TODO: fill config object
		}
		
		return config;
	}
	
	public static int getVpnTypeCode(VpnPayload payload) {
		int value = VPN_UNKNOWN;
		
		if(payload != null) {
			if(VpnPayload.VPN_TYPE_PPTP.equals(payload.getVpnType())) {
				value = VPN_PPTP;
			} else if(VpnPayload.VPN_TYPE_L2TP.equals(payload.getVpnType())) {
				value = VPN_L2TP;
			} else if(VpnPayload.VPN_TYPE_IPSEC.equals(payload.getVpnType())) {
				value = VPN_IPSEC;
			} else if(VpnPayload.VPN_TYPE_CUSTOM.equals(payload.getVpnType())) {
				if(OpenVpnTunnel.VPN_TYPE.equals(payload.getVpnSubType())) {
					value = VPN_OPENVPN;
				}
			}
		}
		
		return value;
	}
	
	public static String extractOnDemandRules(VpnPayload payload) {
		Map<String, Object> result = new HashMap<String, Object>();
		Dictionary testDict = null;
		
		testDict = payload.getDictionary().getDictionary(VpnPayload.KEY_ON_DEMAND_MATCH_DOMAINS_NEVER);
		if(testDict != null) {
			result.put(VpnPayload.KEY_ON_DEMAND_MATCH_DOMAINS_NEVER, testDict.asMap());
		}
		
		testDict = payload.getDictionary().getDictionary(VpnPayload.KEY_ON_DEMAND_MATCH_DOMAINS_ON_RETRY);
		if(testDict != null) {
			result.put(VpnPayload.KEY_ON_DEMAND_MATCH_DOMAINS_ON_RETRY, testDict.asMap());
		}
		
		testDict = payload.getDictionary().getDictionary(VpnPayload.KEY_ON_DEMAND_MATCH_DOMAINS_ALWAYS);
		if(testDict != null) {
			result.put(VpnPayload.KEY_ON_DEMAND_MATCH_DOMAINS_ALWAYS, testDict.asMap());
		}
		
		Array testArray = payload.getOnDemandRules();
		if(testArray != null) {
			result.put(VpnPayload.KEY_ON_DEMAND_RULES, testArray.asList());
		}
		
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapterFactory(new BuiltinTypeAdapterFactory());
		Gson gson = gsonBuilder.create();
		
		return gson.toJson(result);
	}
	
	public static String extractOnDemandCredentials(VpnPayload payload) {
		Map<String, Object> result = new HashMap<String, Object>();
		Dictionary testDict = null;
		
		testDict = payload.getIpsec();
		if(testDict != null) {
			result.put(VpnPayload.KEY_IPSEC, testDict);
		}
		
		testDict = payload.getPpp();
		if(testDict != null) {
			result.put(VpnPayload.KEY_PPP, testDict.asMap());
		}
		
		testDict = payload.getVendorConfig();
		if(testDict != null) {
			result.put(VpnPayload.KEY_VENDOR_CONFIG, testDict.asMap());
		}
		
		testDict = payload.getVpn();
		if(testDict != null) {
			result.put(VpnPayload.KEY_VPN, testDict.asMap());
		}
		
		result.put(VpnPayload.KEY_VPN_TYPE, payload.getVpnType());
		result.put(VpnPayload.KEY_VPN_SUB_TYPE, payload.getVpnSubType());
		
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapterFactory(new BuiltinTypeAdapterFactory());
		Gson gson = gsonBuilder.create();
		
		return gson.toJson(result);
	}
	
	public static String getPayloadNameByType(Context context, String type) {
		String result = null;
		
		if("com.apple.security.scep".equals(type)) {
			result = context.getString(R.string.payload_type_scep);
		} else if("com.apple.vpn.managed".equals(type)) {
			result = context.getString(R.string.payload_type_vpn);
		} else if("com.apple.security.root".equals(type)) {
			result = context.getString(R.string.payload_type_cert);
		} else {
			result = context.getString(R.string.payload_type_unknown);
		}
		
		return result;
	}
	
	public static Dictionary mergeDicts(Dictionary... dicts) {
		Map<String, Object> map = new HashMap<String, Object>();
		for(Dictionary dict : dicts) {
			if(dict == null) {
				continue;
			}
			
			map.putAll(dict.asMap());
		}
		
		return Dictionary.wrap(map);
	}
	
}
