package no.infoss.confprofile.profile.data;

import no.infoss.confprofile.vpn.NetworkConfig;
import android.database.Cursor;

import com.litecoding.classkit.view.LazyCursorList.CursorMapper;

public class VpnOnDemandConfig {
	public static final CursorMapper<VpnOnDemandConfig> MAPPER = new CursorMapper<VpnOnDemandConfig>() {

		@Override
		public VpnOnDemandConfig mapRowToObject(Cursor arg0) {
			// TODO Auto-generated method stub
			return null;
		}
	};
	
	private String mAction;
	private NetworkConfig mNetworkConfig = NetworkConfig.NETWORK_CONFIG_ANY;
	private ActionParameter[] mActionParameters;
	
	//never, onretry, always
	
	private static class ActionParameter {
		private String[] mDomains;
		private String mDomainAction; //ConnectIfNeeded, NeverConnect
		private String[] mRequiredDnsServers;
		private String mRequiredUrlStringProbe;
		
		public boolean match(String testDomain) {
			boolean isMatch = false;
			for(String domain: mDomains) {
				if(domain.equalsIgnoreCase(testDomain)) {
					isMatch = true;
					break;
				}
			}
			
			if(!isMatch) {
				return false;
			}
			
			isMatch = true;
			if(mRequiredDnsServers != null) {
				isMatch = checkDns(testDomain);
			}
			
			if(!isMatch) {
				return false;
			}
			
			if(mRequiredUrlStringProbe != null) {
				isMatch = probeUrl();
			}
			
			return isMatch;
		}

		private boolean probeUrl() {
			// TODO Auto-generated method stub
			return false;
		}

		private boolean checkDns(String testDomain) {
			// TODO Auto-generated method stub
			return false;
		}
	}
}
