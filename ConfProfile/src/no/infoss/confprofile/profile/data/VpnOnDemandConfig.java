package no.infoss.confprofile.profile.data;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import no.infoss.confprofile.format.Plist.Array;
import no.infoss.confprofile.format.Plist.Dictionary;
import no.infoss.confprofile.format.VpnPayload;
import no.infoss.confprofile.format.json.BuiltinTypeAdapterFactory;
import no.infoss.confprofile.util.HttpUtils;
import no.infoss.confprofile.vpn.NetworkConfig;
import no.infoss.confprofile.vpn.conn.ApacheSocketFactory;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class VpnOnDemandConfig {
	public static final String TAG = VpnOnDemandConfig.class.getSimpleName();
	
	public static final int ACTION_UNSUPPORTED = -1;
	public static final int ACTION_ALLOW = 0; //deprecated option
	public static final int ACTION_CONNECT = 1;
	public static final int ACTION_DISCONNECT = 2;
	public static final int ACTION_EVALUATE_CONNECTION = 3;
	public static final int ACTION_IGNORE = 4;
	
	private int mAction = ACTION_UNSUPPORTED;
	private NetworkConfig mNetworkConfig = NetworkConfig.NETWORK_CONFIG_ANY;
	private String mUrlStringProbe;
	private ActionParameter[] mActionParameters;
	
	public int getAction() {
		return mAction;
	}
	
	public NetworkConfig getNetworkConfig() {
		return mNetworkConfig;
	}
	
	public boolean match(NetworkConfig config) {
		return mNetworkConfig.match(config);
	}
	
	public boolean probe(ApacheSocketFactory socketFactory) {
		if(mUrlStringProbe == null) {
			return true;
		}
		
		if(socketFactory == null) {
			return false;
		}
		
		try {
			//TODO: beautify this part
			URL url = new URL(mUrlStringProbe);
			HttpClient client = HttpUtils.getClientForURL(url);			
			Scheme myhttp = new Scheme("http", socketFactory, 80);
			client.getConnectionManager().getSchemeRegistry().register(myhttp);
			 
			((DefaultHttpClient) client).setRedirectHandler(new RedirectHandler() {
				
				@Override
				public boolean isRedirectRequested(HttpResponse response,
						HttpContext context) {
					return false;
				}
				
				@Override
				public URI getLocationURI(HttpResponse response, HttpContext context)
						throws ProtocolException {
					return null;
				}
			});
			
			HttpGet getRequest = new HttpGet(url.toURI());
			HttpResponse response = client.execute(getRequest);
			if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				return true;
			}
			
		} catch(Exception e) {
			Log.e(TAG, "Exception while probing an url", e);
		}
				
		return false;
	}
	
	/**
	 * 
	 * @param address
	 * @return true if connection needed
	 */
	public boolean evaluate(String address) {
		if(mAction != ACTION_EVALUATE_CONNECTION || mActionParameters == null) {
			return false;
		}
		
		for(ActionParameter param : mActionParameters) {
			if(param.match(address)) {
				return (param.getDomainAction() == ActionParameter.DOMAIN_ACTIION_CONNECT_IF_NEEDED);
			}
		}
		
		return false;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static VpnOnDemandConfig[] fromJson(String json) {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapterFactory(new BuiltinTypeAdapterFactory());
		Gson gson = gsonBuilder.create();
		
		Map<String, Object> map = gson.fromJson(json, Map.class);
		
		ArrayList<String> domainsOnRetryNames = new ArrayList<String>(10);
		ArrayList<String> domainsNeverNames = new ArrayList<String>(10);
		
		if(map.containsKey(VpnPayload.KEY_ON_DEMAND_MATCH_DOMAINS_ALWAYS)) {
			List<String> tmpList = (List<String>) map.get(VpnPayload.KEY_ON_DEMAND_MATCH_DOMAINS_ALWAYS);
			domainsOnRetryNames.addAll(tmpList);
		}
		
		if(map.containsKey(VpnPayload.KEY_ON_DEMAND_MATCH_DOMAINS_ON_RETRY)) {
			List<String> tmpList = (List<String>) map.get(VpnPayload.KEY_ON_DEMAND_MATCH_DOMAINS_ON_RETRY);
			domainsOnRetryNames.addAll(tmpList);
		}
		
		if(map.containsKey(VpnPayload.KEY_ON_DEMAND_MATCH_DOMAINS_NEVER)) {
			List<String> tmpList = (List<String>) map.get(VpnPayload.KEY_ON_DEMAND_MATCH_DOMAINS_NEVER);
			domainsNeverNames.addAll(tmpList);
		}
		
		ArrayList<ActionParameter> actionParameters = new ArrayList<ActionParameter>(10);
		ActionParameter domainsOnRetry = null;
		ActionParameter domainsNever = null;
		
		//first action parameter should be "never" to exclude domains
		if(!domainsNeverNames.isEmpty()) {
			domainsNever = new ActionParameter();
			domainsNever.mDomainAction = ActionParameter.domainActionByString("ConnectIfNeeded");
			domainsNever.mDomains = domainsNeverNames.toArray(new String[domainsNeverNames.size()]);
			actionParameters.add(domainsNever);
		}
		
		//second action parameter
		if(!domainsOnRetryNames.isEmpty()) {
			domainsOnRetry = new ActionParameter();
			domainsOnRetry.mDomainAction = ActionParameter.domainActionByString("ConnectIfNeeded");
			domainsOnRetry.mDomains = domainsOnRetryNames.toArray(new String[domainsOnRetryNames.size()]);
			actionParameters.add(domainsOnRetry);
		}
		
		domainsOnRetryNames.clear();
		domainsOnRetryNames = null;
		
		domainsNeverNames.clear();
		domainsNeverNames = null;
		
		ActionParameter[] defaultActionParameters = 
				actionParameters.toArray(new ActionParameter[actionParameters.size()]);
		
		actionParameters.clear();
		
		ArrayList<VpnOnDemandConfig> configurations = new ArrayList<VpnOnDemandConfig>(10);
		VpnOnDemandConfig tmpCfg = null;
		if(!map.containsKey(VpnPayload.KEY_ON_DEMAND_RULES)) {
			//old behavior, just add this as EvaluateConnections
			tmpCfg = new VpnOnDemandConfig();
			tmpCfg.mAction = ACTION_EVALUATE_CONNECTION;
			tmpCfg.mNetworkConfig = NetworkConfig.NETWORK_CONFIG_ANY;
			tmpCfg.mActionParameters = defaultActionParameters;
			configurations.add(tmpCfg);
		} else {
			List<Object> tmpList = (List<Object>) map.get(VpnPayload.KEY_ON_DEMAND_RULES);
			for(Object obj : tmpList) {
				if(obj instanceof Map) {
					tmpCfg = fromMap((Map) obj);
					if(tmpCfg.mAction == ACTION_ALLOW) {
						//reload deprecated value
						tmpCfg.mAction = ACTION_EVALUATE_CONNECTION;
						tmpCfg.mActionParameters = defaultActionParameters;
					}
					configurations.add(tmpCfg);
				}
			}
		}
		
		VpnOnDemandConfig result[] = configurations.toArray(new VpnOnDemandConfig[configurations.size()]);
		configurations.clear();
		
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public static VpnOnDemandConfig fromMap(Map<String, Object> map) {
		VpnOnDemandConfig result = new VpnOnDemandConfig();
		
		Object testObject;
		List<Object> testList;
		Map<String, Object> testMap;
		
		result.mAction = actionByString((String) map.get("Action"));
		result.mNetworkConfig = NetworkConfig.fromMap(map);
		result.mUrlStringProbe = (String) map.get("URLStringProbe");
		
		testObject = map.get("ActionParameters");
		if(testObject instanceof Array) {
			testList = ((Array) testObject).asList();
		} else {
			testList = (List<Object>) testObject;
		}
		
		if(testList != null) {
			result.mActionParameters = new ActionParameter[testList.size()];
			int i = 0;
			for(Object obj : testList) {
				if(obj instanceof Dictionary) {
					testMap = ((Dictionary) obj).asMap();
				} else {
					testMap = (Map<String, Object>) obj; 
				}
				result.mActionParameters[i] = ActionParameter.fromMap(testMap);
				i++;
			}
		}
		
		return result;
	}
	
	public static int actionByString(String action) {
		int result = ACTION_UNSUPPORTED;
		
		if("Allow".equals(action)) {
			result = ACTION_ALLOW;
		} else if("Connect".equals(action)) {
			result = ACTION_CONNECT;
		} else if("Disconnect".equals(action)) {
			result = ACTION_DISCONNECT;
		} else if("EvaluateConnection".equals(action)) {
			result = ACTION_EVALUATE_CONNECTION;
		} else if("Ignore".equals(action)) {
			result = ACTION_IGNORE;
		}
		
		return result;
	}
	
	private static class ActionParameter {
		public static final int DOMAIN_ACTION_UNSUPPORTED = -1;
		public static final int DOMAIN_ACTIION_CONNECT_IF_NEEDED = 0;
		public static final int DOMAIN_ACTIION_NEVER_CONNECT = 1;
		
		private String[] mDomains;
		private int mDomainAction; //ConnectIfNeeded, NeverConnect
		private String[] mRequiredDnsServers;
		private String mRequiredUrlStringProbe;
		
		public int getDomainAction() {
			return mDomainAction;
		}
		
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
		
		@SuppressWarnings("unchecked")
		public static ActionParameter fromMap(Map<String, Object> map) {
			ActionParameter result = new ActionParameter();
			
			Object testObject;
			List<Object> testList;
			
			testObject = map.get("Domains");
			if(testObject instanceof Array) {
				testList = ((Array) testObject).asList();
			} else {
				testList = (List<Object>) testObject;
			}
			
			if(testList != null) {
				result.mDomains = new String[testList.size()];
				result.mDomains = testList.toArray(result.mDomains);
			}
			
			result.mDomainAction = domainActionByString((String) map.get("DomainAction"));
			
			testObject = map.get("RequiredDNSServers");
			if(testObject instanceof Array) {
				testList = ((Array) testObject).asList();
			} else {
				testList = (List<Object>) testObject;
			}
			
			if(testList != null) {
				result.mRequiredDnsServers = new String[testList.size()];
				result.mRequiredDnsServers = testList.toArray(result.mRequiredDnsServers);
			}
			
			result.mRequiredUrlStringProbe = (String) map.get("RequiredURLStringProbe");
			
			return result;
		}
		
		public static int domainActionByString(String action) {
			int result = DOMAIN_ACTION_UNSUPPORTED;
			
			if("ConnectIfNeeded".equals(action)) {
				result = DOMAIN_ACTIION_CONNECT_IF_NEEDED;
			} else if("NeverConnect".equals(action)) {
				result = DOMAIN_ACTIION_NEVER_CONNECT;
			}
			
			return result;
		}
	}
}
