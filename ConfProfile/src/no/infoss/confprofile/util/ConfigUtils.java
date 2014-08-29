package no.infoss.confprofile.util;

import java.util.HashMap;
import java.util.Map;

import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.format.json.BuiltinTypeAdapterFactory;
import no.infoss.confprofile.vpn.NetworkConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ConfigUtils {
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
	
}
