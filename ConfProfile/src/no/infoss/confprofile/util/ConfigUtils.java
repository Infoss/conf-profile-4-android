package no.infoss.confprofile.util;

import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.vpn.NetworkConfig;

public class ConfigUtils {
	public static NetworkConfig build(VpnPayload payload) {
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
	
}
